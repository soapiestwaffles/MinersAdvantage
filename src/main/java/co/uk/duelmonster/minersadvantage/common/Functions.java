package co.uk.duelmonster.minersadvantage.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import co.uk.duelmonster.minersadvantage.MinersAdvantage;
import co.uk.duelmonster.minersadvantage.config.MAConfig;
import co.uk.duelmonster.minersadvantage.config.MAConfig_Excavation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMultiTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.oredict.OreDictionary;

public class Functions {
	
	public static String localize(String key) {
		return (new TextComponentTranslation(key)).getFormattedText();
	}
	
	public static void NotifyClient(EntityPlayer player, String sMsg) {
		player.sendStatusMessage(new TextComponentString(Constants.MOD_NAME_MSG + sMsg), false);
	}
	
	public static void NotifyClient(EntityPlayer player, boolean bIsOn, String sFeatureName) {
		player.sendStatusMessage(new TextComponentString(Constants.MOD_NAME_MSG + TextFormatting.GOLD + sFeatureName + " " + (bIsOn ? TextFormatting.GREEN + "ON" : TextFormatting.RED + "OFF")), false);
	}
	
	public static ItemStack getHeldItemStack(EntityPlayer player) {
		ItemStack heldItem = player.getHeldItem(EnumHand.MAIN_HAND);
		return (heldItem == null || heldItem.isEmpty()) ? null : heldItem;
	}
	
	public static Item getHeldItem(EntityPlayer player) {
		ItemStack heldItem = getHeldItemStack(player);
		return heldItem == null ? null : heldItem.getItem();
	}
	
	public static boolean IsPlayerStarving(EntityPlayer player) {
		if (!Variables.get(player.getUniqueID()).HungerNotified && player.getFoodStats().getFoodLevel() <= Constants.MIN_HUNGER) {
			NotifyClient(player, TextFormatting.RED + localize("minersadvantage.hungery") + Constants.MOD_NAME);
			Variables.get(player.getUniqueID()).HungerNotified = true;
		}
		
		return Variables.get(player.getUniqueID()).HungerNotified;
	}
	
	public static String getStackTrace() {
		String				sRtrn	= "";
		StackTraceElement[]	stes	= Thread.currentThread().getStackTrace();
		
		for (int i = 2; i < stes.length; i++)
			sRtrn += System.getProperty("line.separator") + "	at " + stes[i].toString();
		
		return sRtrn;
	}
	
	public static boolean isWithinRange(BlockPos sourcePos, BlockPos targetPos, int range) {
		int	distanceX	= sourcePos.getX() - targetPos.getX();
		int	distanceY	= sourcePos.getY() - targetPos.getY();
		int	distanceZ	= sourcePos.getZ() - targetPos.getZ();
		
		return ((distanceX * distanceX) + (distanceY * distanceY) + (distanceZ * distanceZ)) <= (range * range);
	}
	
	public static boolean isWithinArea(BlockPos oPos, AxisAlignedBB area) {
		return area != null &&
				oPos.getX() >= area.minX && oPos.getX() <= area.maxX &&
				oPos.getY() >= area.minY && oPos.getY() <= area.maxY &&
				oPos.getZ() >= area.minZ && oPos.getZ() <= area.maxZ;
	}
	
	public static boolean isWithinArea(Entity entity, AxisAlignedBB area) {
		return area != null &&
				entity.getPosition().getX() >= area.minX && entity.getPosition().getX() <= area.maxX &&
				entity.getPosition().getY() >= area.minY && entity.getPosition().getY() <= area.maxY &&
				entity.getPosition().getZ() >= area.minZ && entity.getPosition().getZ() <= area.maxZ;
	}
	
	public static boolean isPosConnected(List<BlockPos> posList, BlockPos checkPos) {
		// Ensure we have a direct connection to the current Tree.
		for (int yOffset = -1; yOffset <= 1; yOffset++)
			for (int xOffset = -1; xOffset <= 1; xOffset++)
				for (int zOffset = -1; zOffset <= 1; zOffset++) {
					BlockPos oConPos = checkPos.add(xOffset, yOffset, zOffset);
					
					if (posList.contains(oConPos))
						return true;
				}
			
		return false;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static List<Entity> getNearbyEntities(World world, AxisAlignedBB area) {
		List<Entity> rtrn = new ArrayList();
		try {
			List<?> list = new ArrayList(world.getEntitiesWithinAABB(Entity.class, area));
			if (null == list || list.isEmpty())
				return null;
			
			for (Object o : list) {
				if (o != null) {
					Entity e = (Entity) o;
					
					if (!e.isDead && (e instanceof EntityItem || e instanceof EntityXPOrb))
						// && isEntityWithinArea(e, area))
						rtrn.add(e);
				}
			}
			return rtrn;
			
		}
		catch (ConcurrentModificationException e) {
			MinersAdvantage.logger.error("ConcurrentModification Exception Avoided...");
		}
		catch (IllegalStateException e) {
			MinersAdvantage.logger.error("IllegalStateException Exception Avoided...");
		}
		catch (Exception ex) {
			MinersAdvantage.logger.error(ex.getClass().getName() + " Exception: " + getStackTrace());
		}
		return rtrn;
	}
	
	public static boolean isItemBlacklisted(ItemStack item) {
		if (item == null || item.isEmpty())
			return MAConfig.get().excavation.bIsToolWhitelist();
		
		return isItemBlacklisted(item.getItem());
	}
	
	public static boolean isItemBlacklisted(Item item) {
		MAConfig_Excavation excavation = MAConfig.get().excavation;
		
		if (item == null)
			return excavation.bIsToolWhitelist();
		
		if (ArrayUtils.contains(excavation.toolBlacklist(), Item.REGISTRY.getNameForObject(item).toString()))
			return !excavation.bIsToolWhitelist();
		
		for (int id : OreDictionary.getOreIDs(new ItemStack(item)))
			if (ArrayUtils.contains(excavation.toolBlacklist(), OreDictionary.getOreName(id)))
				return !excavation.bIsToolWhitelist();
		
		return excavation.bIsToolWhitelist();
	}
	
	public static boolean isBlockBlacklisted(Block block) {
		MAConfig_Excavation excavation = MAConfig.get().excavation;
		
		if (block == null || block == Blocks.AIR)
			return excavation.bIsBlockWhitelist();
		
		if (ArrayUtils.contains(excavation.blockBlacklist(), Block.REGISTRY.getNameForObject(block).toString()))
			return !excavation.bIsBlockWhitelist();
		
		ItemStack blockStack = new ItemStack(Item.getItemFromBlock(block));
		
		if (!blockStack.isEmpty())
			for (int id : OreDictionary.getOreIDs(blockStack))
			if (ArrayUtils.contains(excavation.blockBlacklist(), OreDictionary.getOreName(id)))
				return !excavation.bIsBlockWhitelist();
		
		return excavation.bIsBlockWhitelist();
	}
	
	public static boolean isIdInList(Object oID, ArrayList<String> lumbinationLeaves) {
		return (lumbinationLeaves != null && lumbinationLeaves.indexOf(oID) >= 0);
	}
	
	public static List<Object> IDListToArray(String[] saIDs, boolean bIsBlock) {
		return IDListToArray(Arrays.asList(saIDs), bIsBlock);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static List<Object> IDListToArray(List<String> saIDs, boolean bIsBlock) {
		RegistryNamespaced	rn		= bIsBlock ? Block.REGISTRY : Item.REGISTRY;
		List<Object>		lReturn	= new ArrayList();
		
		for (String sID : saIDs) {
			Object oEntity = null;
			sID = sID.trim();
			
			try {
				int id = Integer.parseInt(sID.trim());
				oEntity = rn.getObjectById(id);
			}
			catch (NumberFormatException e) {
				oEntity = rn.getObject(new ResourceLocation(sID));
			}
			
			if (null != oEntity && Blocks.AIR != oEntity)
				lReturn.add(oEntity);
			
		}
		return lReturn;
	}
	
	public static void playSound(World world, BlockPos oPos, SoundEvent sound, SoundCategory soundCategory, float volume, float pitch) {
		playSound(world, null, oPos, sound, soundCategory, volume, pitch);
	}
	
	public static void playSound(World world, EntityPlayer player, BlockPos oPos, SoundEvent sound, SoundCategory soundCategory, float volume, float pitch) {
		world.playSound(player, oPos.getX() + 0.5F, oPos.getY() + 0.5F, oPos.getZ() + 0.5F, sound, soundCategory, volume, pitch);
	}
	
	public static void spawnAreaEffectCloud(World world, EntityPlayer player, BlockPos oPos) {
		if (!MAConfig.get().common.bDisableParticleEffects()) {
			EntityAreaEffectCloud entityareaeffectcloud = new EntityAreaEffectCloud(world, oPos.getX() + 0.5D, oPos.getY() + 0.5D, oPos.getZ() + 0.5D);
			entityareaeffectcloud.setOwner(player);
			entityareaeffectcloud.setRadius(1.0F);
			entityareaeffectcloud.setRadiusOnUse(-0.5F);
			entityareaeffectcloud.setWaitTime(1);
			entityareaeffectcloud.setDuration(20);
			entityareaeffectcloud.setRadiusPerTick(-entityareaeffectcloud.getRadius() / entityareaeffectcloud.getDuration());
			entityareaeffectcloud.addEffect(new PotionEffect(MobEffects.WEAKNESS, 10));
			
			world.spawnEntity(entityareaeffectcloud);
		}
	}
	
	/**
	 * Finds the stack or an equivalent one in the main inventory
	 */
	public static int getSlotFromInventory(EntityPlayer player, ItemStack stack) {
		for (int i = 0; i < player.inventory.mainInventory.size(); ++i)
			if (!player.inventory.mainInventory.get(i).isEmpty() && stackEqualExact(stack, player.inventory.mainInventory.get(i)))
				return i;
		
		return -1;
	}
	
	public static ItemStack getStackOfClassTypeFromHotBar(InventoryPlayer inventory, Class<?> classType) {
		return getStackOfClassTypeFromInventory(InventoryPlayer.getHotbarSize(), inventory, classType);
	}
	
	public static ItemStack getStackOfClassTypeFromInventory(InventoryPlayer inventory, Class<?> classType) {
		return getStackOfClassTypeFromInventory(inventory.getSizeInventory(), inventory, classType);
	}
	
	private static ItemStack getStackOfClassTypeFromInventory(int iInventorySize, InventoryPlayer inventory, Class<?> classType) {
		try {
			for (int iSlot = 0; iSlot < iInventorySize; iSlot++) {
				ItemStack itemStack = inventory.getStackInSlot(iSlot);
				if (itemStack != null && classType.isInstance(itemStack.getItem()))
					return itemStack;
			}
		}
		catch (Exception ex) {
			MinersAdvantage.logger.error(ex);
		}
		return null;
	}
	
	public static NonNullList<ItemStack> getAllStacksOfClassTypeFromInventory(InventoryPlayer inventory, Class<?> classType) {
		NonNullList<ItemStack> rtnStacks = NonNullList.create();
		try {
			for (int iSlot = 0; iSlot < inventory.getSizeInventory(); iSlot++) {
				ItemStack itemStack = inventory.getStackInSlot(iSlot);
				if (itemStack != null && itemStack.getItem() instanceof ItemMultiTexture && classType.isInstance(((ItemMultiTexture) itemStack.getItem()).getBlock()))
					rtnStacks.add(itemStack);
			}
		}
		catch (Exception ex) {
			MinersAdvantage.logger.error(ex);
		}
		return rtnStacks;
	}
	
	/**
	 * Checks item, NBT, and meta if the item is not damageable
	 */
	private static boolean stackEqualExact(ItemStack stack1, ItemStack stack2) {
		return stack1.getItem() == stack2.getItem() && (!stack1.getHasSubtypes() || stack1.getMetadata() == stack2.getMetadata()) && ItemStack.areItemStackTagsEqual(stack1, stack2);
	}
	
	public static String getItemName(ItemStack itemStack) {
		return getItemName(itemStack.getItem());
	}
	
	public static String getItemName(Item item) {
		return item.getRegistryName().toString().trim();
	}
	
	public static String getBlockName(Block block) {
		return block.getRegistryName().toString().trim();
	}
	
	public static Block getBlockFromWorld(World world, BlockPos oPos) {
		return world.getBlockState(oPos).getBlock();
	}
	
	public static Object getPropertyValue(IBlockState state, PropertyEnum<EnumType> variant) {
		// for (IProperty<?> prop : state.getProperties().keySet())
		// if (prop.getName().equals(variant))
		// return state.getValue(prop);
		try {
			return state.getValue(variant);// .getMetadata();
		}
		catch (Exception e) {
			return null;
		}
	}
	
	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {}
	}
	
	public static boolean canSustainPlant(World world, BlockPos oPos, IPlantable plantable) {
		return world.getBlockState(oPos).getBlock().canSustainPlant(world.getBlockState(oPos), world, oPos, EnumFacing.UP, plantable);
	}
	
	static volatile String[] saOreDictEntries = null;
	
	public static String[] getOreDictEntries(Class<?> clsInstanceOf) {
		Collection<String> saOreNames = Arrays.asList(OreDictionary.getOreNames());
		
		if (clsInstanceOf != null) {
			saOreNames.stream()
					.forEach(entry -> {
						OreDictionary.getOres(entry).stream()
								.filter(item -> clsInstanceOf.isInstance(item.getItem())).forEach(item -> {
									String sID = item.getItem().getRegistryName().toString().trim();
									if (!ArrayUtils.contains(saOreDictEntries, sID))
										saOreDictEntries = ArrayUtils.add(saOreDictEntries, sID);
								});
					});
		} else {
			saOreNames.stream()
					.forEach(entry -> {
						OreDictionary.getOres(entry).stream()
								.forEach(item -> {
									String sID = item.getItem().getRegistryName().toString().trim();
									if (!ArrayUtils.contains(saOreDictEntries, sID))
										saOreDictEntries = ArrayUtils.add(saOreDictEntries, sID);
								});
					});
		}
		return ArrayUtils.clone(saOreDictEntries);
	}
}
