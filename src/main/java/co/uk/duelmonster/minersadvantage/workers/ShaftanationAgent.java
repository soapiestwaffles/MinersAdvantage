package co.uk.duelmonster.minersadvantage.workers;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;

import co.uk.duelmonster.minersadvantage.common.BreakBlockController;
import co.uk.duelmonster.minersadvantage.common.Functions;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public class ShaftanationAgent extends Agent {
	
	public ShaftanationAgent(EntityPlayerMP player, NBTTagCompound tags) {
		super(player, tags, false);
		
		setupShaft();
	}
	
	// Returns true when Shaftanation is complete or cancelled
	@Override
	public boolean tick() {
		if (originPos == null || player == null || !player.isEntityAlive() || processed.size() >= settings.common.iBlockLimit())
			return true;
		
		boolean bIsComplete = false;
		
		for (int iQueueCount = 0; queued.size() > 0; iQueueCount++) {
			if ((settings.common.bBreakAtToolSpeeds() && iQueueCount > 0)
					|| iQueueCount >= settings.common.iBlocksPerTick()
					|| processed.size() >= settings.common.iBlockLimit()
					|| (!settings.common.bBreakAtToolSpeeds() && settings.common.tpsGuard() && timer.elapsed(TimeUnit.MILLISECONDS) > 40))
				break;
			
			if (Functions.IsPlayerStarving(player)) {
				bIsComplete = true;
				break;
			}
			
			BlockPos oPos = queued.remove(0);
			if (oPos == null || !Functions.isWithinArea(oPos, harvestArea) || world.getBlockState(oPos).getBlock() == Blocks.TORCH)
				continue;
			
			IBlockState	state	= world.getBlockState(oPos);
			Block		block	= state.getBlock();
			
			if (!fakePlayer().canHarvestBlock(state)) {
				// Avoid the non-harvestable blocks.
				processed.add(oPos);
				continue;
			}
			
			boolean bIsOreBlock = ArrayUtils.contains(settings.veination.ores(), state.getBlock().getRegistryName().toString().trim());
			
			// Process the current block if it is valid.
			if (settings.common.bMineVeins() && bIsOreBlock) {
				processed.add(oPos);
				excavateOreVein(state, oPos);
			} else {
				world.captureBlockSnapshots = true;
				world.capturedBlockSnapshots.clear();
				
				boolean bBlockHarvested = false;
				
				if (settings.common.bBreakAtToolSpeeds()) {
					this.breakController = new BreakBlockController(fakePlayer());
					breakController.onPlayerDamageBlock(oPos, sideHit);
					if (breakController.bBlockDestroyed)
						bBlockHarvested = HarvestBlock(oPos);
					
				} else bBlockHarvested = HarvestBlock(oPos);
				
				if (bBlockHarvested) {
					processBlockSnapshots();
					
					SoundType soundtype = block.getSoundType(state, world, oPos, null);
					reportProgessToClient(oPos, soundtype.getBreakSound());
					
					autoIlluminate((new BlockPos(oPos.getX(), iFeetPos, oPos.getZ())).offset(sideHit.getOpposite()), settings.shaftanation.TorchPlacement());
					
					addConnectedToQueue(oPos);
					
					processed.add(oPos);
				}
			}
		}
		
		return (bIsComplete || queued.isEmpty());
	}
	
	private void setupShaft() {
		// Shaft area info
		int	xStart	= 0;
		int	xEnd	= 0;
		int	yBottom	= iFeetPos;
		int	yTop	= iFeetPos + (settings.shaftanation.iShaftHeight() - 1);
		int	zStart	= 0;
		int	zEnd	= 0;
		
		// if the ShaftWidth is divisible by 2 we don't want to do anything
		double dDivision = ((settings.shaftanation.iShaftWidth() & 1) != 0 ? 0 : 0.5);
		
		switch (sideHit) {
		case SOUTH: // Positive Z
			xStart = originPos.getX() + ((int) ((settings.shaftanation.iShaftWidth() / 2) - dDivision));
			xEnd = originPos.getX() - (settings.shaftanation.iShaftWidth() / 2);
			zStart = originPos.getZ();
			zEnd = originPos.getZ() - settings.shaftanation.iShaftLength();
			break;
		case NORTH: // Negative Z
			xStart = originPos.getX() - (settings.shaftanation.iShaftWidth() / 2);
			xEnd = originPos.getX() + ((int) ((settings.shaftanation.iShaftWidth() / 2) - dDivision));
			zStart = originPos.getZ();
			zEnd = originPos.getZ() + settings.shaftanation.iShaftLength();
			break;
		case EAST: // Positive X
			xStart = originPos.getX();
			xEnd = originPos.getX() - settings.shaftanation.iShaftLength();
			zStart = originPos.getZ() + ((int) ((settings.shaftanation.iShaftWidth() / 2) - dDivision));
			zEnd = originPos.getZ() - (settings.shaftanation.iShaftWidth() / 2);
			break;
		case WEST: // Negative X
			xStart = originPos.getX();
			xEnd = originPos.getX() + settings.shaftanation.iShaftLength();
			zStart = originPos.getZ() - (settings.shaftanation.iShaftWidth() / 2);
			zEnd = originPos.getZ() + ((int) ((settings.shaftanation.iShaftWidth() / 2) - dDivision));
			break;
		default:
			break;
		}
		
		harvestArea = new AxisAlignedBB(
				xStart, yBottom, zStart,
				xEnd, yTop, zEnd);
		
	}
	
	@Override
	public void addToQueue(BlockPos oPos) {
		IBlockState state = world.getBlockState(oPos);
		
		if (fakePlayer().canHarvestBlock(state))
			super.addToQueue(oPos);
	}
	
}