package co.uk.duelmonster.minersadvantage.workers;

import java.util.concurrent.TimeUnit;

import co.uk.duelmonster.minersadvantage.common.Functions;
import co.uk.duelmonster.minersadvantage.common.PacketID;
import co.uk.duelmonster.minersadvantage.common.Variables;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class ExcavationAgent extends Agent {
	
	public ExcavationAgent(EntityPlayerMP player, NBTTagCompound tags) {
		super(player, tags);
	}
	
	// Returns true when Excavation is complete or cancelled
	@Override
	public boolean tick() {
		if (originPos == null || player == null || !player.isEntityAlive() || processed.size() >= settings.iBlockLimit)
			return true;
		
		timer.start();
		
		boolean bIsComplete = false;
		
		for (int iQueueCount = 0; queued.size() > 0; iQueueCount++) {
			if (iQueueCount >= settings.iBreakSpeed
					|| processed.size() >= settings.iBlockLimit
					|| (settings.tpsGuard && timer.elapsed(TimeUnit.MILLISECONDS) > 40))
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
			
			if (!player.canHarvestBlock(state)) {
				// Add the non-harvestable blocks to the processed list so that they can be avoided.
				processed.add(oPos);
				continue;
			}
			
			int meta = block.getMetaFromState(state);
			
			// Process the current block if it is valid.
			if (packetID != PacketID.Veinate && settings.bMineVeins && settings.veinationOres.has(state.getBlock().getRegistryName().toString().trim())) {
				excavateOreVein(state, oPos);
			} else if ((block == originBlock && (settings.bIgnoreBlockVariants || originMeta == meta))
					|| (isRedStone && (block == Blocks.REDSTONE_ORE || block == Blocks.LIT_REDSTONE_ORE))) {
				world.captureBlockSnapshots = true;
				world.capturedBlockSnapshots.clear();
				
				if (player.interactionManager.tryHarvestBlock(oPos)) {
					processBlockSnapshots();
					spawnProgressParticle(oPos);
					autoIlluminate(oPos);
					addConnectedToQueue(oPos);
				}
				
				processed.add(oPos);
			}
		}
		
		timer.reset();
		
		return (bIsComplete || queued.isEmpty());
	}
	
	@Override
	public void addConnectedToQueue(BlockPos oPos) {
		int xStart = -1, yStart = -1, zStart = -1;
		int xEnd = 1, yEnd = 1, zEnd = 1;
		
		if (packetID == PacketID.Excavate && (oPos.getX() == originPos.getX() || oPos.getY() == originPos.getY() || oPos.getZ() == originPos.getZ()))
			switch (sideHit.getOpposite()) {
			case SOUTH: // Positive Z
				zStart = 0;
				break;
			case NORTH: // Negative Z
				zEnd = 0;
				break;
			case EAST: // Positive X
				xStart = 0;
				break;
			case WEST: // Negative X
				xEnd = 0;
				break;
			case UP: // Positive Y
				yStart = 0;
				break;
			case DOWN: // Negative Y
				yEnd = 0;
				break;
			default:
				break;
			}
		
		if (Variables.get(player.getUniqueID()).IsSingleLayerToggled) {
			yStart = 0;
			yEnd = 0;
		}
		
		for (int xOffset = xStart; xOffset <= xEnd; xOffset++)
			for (int yOffset = yStart; yOffset <= yEnd; yOffset++)
				for (int zOffset = zStart; zOffset <= zEnd; zOffset++)
					addToQueue(oPos.add(xOffset, yOffset, zOffset));
				
	}
}
