package co.uk.duelmonster.minersadvantage.handlers;

import java.util.List;

import co.uk.duelmonster.minersadvantage.common.Functions;
import co.uk.duelmonster.minersadvantage.packets.PacketBase;
import co.uk.duelmonster.minersadvantage.settings.Settings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CaptivationHandler implements IPacketHandler {
	
	public static CaptivationHandler instance = new CaptivationHandler();
	
	@Override
	public void processClientMessage(PacketBase message, MessageContext context) {}
	
	@Override
	public void processServerMessage(PacketBase message, MessageContext context) {
		final EntityPlayerMP player = context.getServerHandler().player;
		if (player == null)
			return;
		
		Settings settings = Settings.get(player.getUniqueID());
		
		AxisAlignedBB captivateArea = player.getEntityBoundingBox().grow(settings.radiusHorizontal, settings.radiusVertical, settings.radiusHorizontal);
		
		if (player.world != null) {
			List<Entity> localDrops = Functions.getNearbyEntities(player.world, captivateArea);
			if (localDrops != null && !localDrops.isEmpty()) {
				for (Entity entity : localDrops) {
					if (entity instanceof EntityItem) {
						EntityItem eItem = (EntityItem) entity;
						
						if (!eItem.cannotPickup()
								&& (settings.captivationBlacklist == null
										|| settings.captivationBlacklist.size() == 0
										|| !settings.captivationBlacklist.has(eItem.getItem().getItem().getRegistryName().toString().trim())))
							entity.onCollideWithPlayer(player);
						
					} else if (entity instanceof EntityXPOrb)
						entity.onCollideWithPlayer(player);
				}
			}
		}
	}
}
