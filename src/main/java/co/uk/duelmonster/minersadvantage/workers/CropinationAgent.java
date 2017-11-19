package co.uk.duelmonster.minersadvantage.workers;

import java.util.concurrent.TimeUnit;

import co.uk.duelmonster.minersadvantage.common.Functions;
import co.uk.duelmonster.minersadvantage.common.PacketID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public class CropinationAgent extends Agent {
	
	private int iHarvestedCount = 0;
	
	public CropinationAgent(EntityPlayerMP player, NBTTagCompound tags) {
		super(player, tags, false);
		
		if (this.packetID == PacketID.TileFarmland) {
			detectFarmableLandArea();
			
			// Reset the Queue now that we have the harvest area.
			this.queued.clear();
			addConnectedToQueue(originPos);
		}
	}
	
	// Returns true when Excavation is complete or cancelled
	@Override
	public boolean tick() {
		if (originPos == null || player == null || !player.isEntityAlive() || processed.size() >= settings.iBlockLimit())
			return true;
		
		timer.start();
		
		boolean bIsComplete = false;
		
		for (int iQueueCount = 0; queued.size() > 0; iQueueCount++) {
			if (iQueueCount >= settings.iBlocksPerTick()
					|| processed.size() >= settings.iBlockLimit()
					|| (settings.tpsGuard() && timer.elapsed(TimeUnit.MILLISECONDS) > 40))
				break;
			
			if (heldItem != Functions.getHeldItem(player) || Functions.IsPlayerStarving(player)) {
				bIsComplete = true;
				break;
			}
			
			BlockPos oPos = queued.remove(0);
			if (oPos == null)
				continue;
			
			IBlockState state = world.getBlockState(oPos);
			Block block = state.getBlock();
			
			if (this.packetID == PacketID.TileFarmland) {
				if (!(block instanceof BlockDirt || block instanceof BlockGrass)) {
					// Add the non-harvestable blocks to the processed list so that they can be avoided.
					processed.add(oPos);
					continue;
				}
				
				world.captureBlockSnapshots = true;
				world.capturedBlockSnapshots.clear();
				
				if (world.isAirBlock(oPos.up())) {
					Functions.playSound(world, oPos, SoundEvents.ITEM_HOE_TILL, SoundCategory.PLAYERS, 1.0F, world.rand.nextFloat() + 0.5F);
					
					world.setBlockState(oPos, Blocks.FARMLAND.getDefaultState().withProperty(BlockFarmland.MOISTURE, Integer.valueOf(7)), 11);
					heldItemStack.damageItem(1, player);
					
					processBlockSnapshots();
					addConnectedToQueue(oPos);
				}
			} else if (this.packetID == PacketID.HarvestCrops) {
				if (!(block instanceof BlockCrops || block instanceof BlockNetherWart)) {
					// Add the non-harvestable blocks to the processed list so that they can be avoided.
					processed.add(oPos);
					continue;
				}
				
				boolean isFullyGrown = false;
				
				IBlockState newState = block.getDefaultState();
				
				if (block instanceof BlockCrops) {
					BlockCrops crop = (BlockCrops) block;
					isFullyGrown = crop.isMaxAge(state);
					
					// !! DEBUG USE ONLY !!
					// newState = crop.withAge(crop.getMaxAge());
					
				} else if (block instanceof BlockNetherWart) {
					isFullyGrown = block.getMetaFromState(state) == 3;
				}
				
				if (isFullyGrown) {
					int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, heldItemStack);
					
					NonNullList<ItemStack> drops = NonNullList.create();
					block.getDrops(drops, world, oPos, state, fortune);
					
					for (ItemStack item : drops)
						if (settings.bHarvestSeeds() || !(item.getItem() instanceof ItemSeeds))
							Block.spawnAsEntity(world, oPos, item);
						
					iHarvestedCount++;
					
					world.captureBlockSnapshots = true;
					world.capturedBlockSnapshots.clear();
					
					world.setBlockState(oPos, newState, 11);
					
					// Apply Item damage every 4 crops harvested. This makes item damage 1/4 per crop
					if (iHarvestedCount > 0 && iHarvestedCount % 4 == 0
							&& heldItem != null && heldItemStack.isItemStackDamageable())
						heldItemStack.damageItem(1, player);
					
					if (heldItemStack.getMaxDamage() <= 0) {
						player.inventory.removeStackFromSlot(player.inventory.currentItem);
						player.openContainer.detectAndSendChanges();
					}
					
					processBlockSnapshots();
				}
				
				Functions.playSound(world, oPos, SoundEvents.ITEM_HOE_TILL, SoundCategory.PLAYERS, (isFullyGrown ? 1.0F : 0.25F), world.rand.nextFloat() + 0.5F);
				
				addConnectedToQueue(oPos);
			}
			
			processed.add(oPos);
		}
		
		timer.reset();
		
		return (bIsComplete || queued.isEmpty());
	}
	
	private void detectFarmableLandArea() {
		BlockPos waterSource = getWaterSource();
		if (waterSource != null)
			harvestArea = new AxisAlignedBB(
					waterSource.getX() - 4, waterSource.getY(), waterSource.getZ() - 4,
					waterSource.getX() + 4, waterSource.getY(), waterSource.getZ() + 4);
		else
			harvestArea = new AxisAlignedBB(originPos, originPos);
	}
	
	private BlockPos getWaterSource() {
		// for (int yOffset = originPos.getY() - 1; yOffset <= originPos.getY() + 1; ++yOffset)
		for (int xOffset = originPos.getX() - 4; xOffset <= originPos.getX() + 4; ++xOffset)
			for (int zOffset = originPos.getZ() - 4; zOffset <= originPos.getZ() + 4; ++zOffset) {
				BlockPos oPos = new BlockPos(xOffset, originPos.getY(), zOffset);
				IBlockState state = world.getBlockState(oPos);
				if (state.getMaterial() == Material.WATER)
					return oPos;
			}
		
		return null;
	}
	
	@Override
	public void addToQueue(BlockPos oPos) {
		Block block = world.getBlockState(oPos).getBlock();
		
		if ((this.packetID == PacketID.TileFarmland && (block instanceof BlockDirt || block instanceof BlockGrass))
				|| (this.packetID == PacketID.HarvestCrops && (block instanceof BlockCrops || block instanceof BlockNetherWart)))
			super.addToQueue(oPos);
	}
}
