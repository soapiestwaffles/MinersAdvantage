package co.uk.duelmonster.minersadvantage.events;

import org.apache.commons.lang3.ArrayUtils;

import co.uk.duelmonster.minersadvantage.MinersAdvantage;
import co.uk.duelmonster.minersadvantage.client.ClientFunctions;
import co.uk.duelmonster.minersadvantage.client.KeyBindings;
import co.uk.duelmonster.minersadvantage.client.MAParticleManager;
import co.uk.duelmonster.minersadvantage.common.Constants;
import co.uk.duelmonster.minersadvantage.common.PacketID;
import co.uk.duelmonster.minersadvantage.common.Variables;
import co.uk.duelmonster.minersadvantage.config.MAConfig;
import co.uk.duelmonster.minersadvantage.handlers.GodItems;
import co.uk.duelmonster.minersadvantage.handlers.SubstitutionHandler;
import co.uk.duelmonster.minersadvantage.packets.NetworkPacket;
import co.uk.duelmonster.minersadvantage.settings.ConfigHandler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClientEvents {
	private static int iTickCount = 10;
	
	private enum AttackStage {
		IDLE, SWITCHED
	}
	
	private AttackStage			currentAttackStage	= AttackStage.IDLE;
	private EntityLivingBase	currentTarget		= null;
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
		if (event.getModID().equals(Constants.MOD_ID))
			ConfigHandler.save();
	}
	
	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		ClientFunctions.doJoinWorldEventStuff();
	}
	
	@SideOnly(Side.CLIENT)
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onEntityJoinWorldEvent(EntityJoinWorldEvent e) {
		if (e.getEntity() == ClientFunctions.getPlayer()) {
			Minecraft mc = ClientFunctions.getMC();
			MinersAdvantage.proxy.maParticleManager = new MAParticleManager(e.getWorld(), mc.renderEngine);
			
			if (MinersAdvantage.proxy.origParticleManager == null || (MinersAdvantage.proxy.origParticleManager != mc.effectRenderer && MinersAdvantage.proxy.origParticleManager != MinersAdvantage.proxy.maParticleManager))
				MinersAdvantage.proxy.origParticleManager = mc.effectRenderer;
			
			// if (Settings.get().bDisableParticleEffects())
			mc.effectRenderer = MinersAdvantage.proxy.maParticleManager;
		}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onItemPickup(EntityItemPickupEvent event) {
		MAConfig settings = MAConfig.get();
		
		event.setCanceled(settings.captivation.bEnabled()
				&& settings.captivation.bIsWhitelist()
				&& settings.captivation.blacklist() != null
				&& settings.captivation.blacklist().length > 0
				&& ArrayUtils.contains(settings.captivation.blacklist(), event.getItem().getEntityItem().getItem().getRegistryName().toString().trim()));
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (!TickEvent.Phase.START.equals(event.phase))
			return;
		
		Minecraft mc = ClientFunctions.getMC();
		EntityPlayerSP player = ClientFunctions.getPlayer();
		if (player == null)
			return;
		
		MAConfig settings = MAConfig.get();
		Variables variables = Variables.get();
		
		// Fire Captivation if enabled
		if (settings.captivation.bEnabled() && !mc.isGamePaused() && mc.inGameHasFocus) {
			NBTTagCompound tags = new NBTTagCompound();
			tags.setInteger("ID", PacketID.Captivate.value());
			
			MinersAdvantage.instance.network.sendToServer(new NetworkPacket(tags));
		}
		
		if (mc.playerController.isInCreativeMode())
			return;
		
		// If an entity was attacked last tick and weapon was switched, we attack now.
		if (currentAttackStage == AttackStage.SWITCHED) {
			player.swingArm(EnumHand.MAIN_HAND);;
			mc.playerController.attackEntity(player, currentTarget);
			currentTarget = null;
			currentAttackStage = AttackStage.IDLE;
			return;
		}
		
		if (!settings.excavation.bToggleMode())
			variables.IsExcavationToggled = KeyBindings.excavation_toggle.isKeyDown();
		variables.IsShaftanationToggled = KeyBindings.shaftanation_toggle.isKeyDown();
		variables.IsPlayerAttacking = ClientFunctions.isAttacking();
		
		ClientFunctions.syncSettings();
		
		GodItems.isWorthy(variables.IsExcavationToggled);
		
		// iTickCount = (iTickCount + 1) % 10;
		// if (iTickCount <= 0)
		// return;
		
		// Fire Illumination if enabled and the player is attacking
		if (settings.illumination.bEnabled() && variables.IsPlayerAttacking) {
			NBTTagCompound tags = new NBTTagCompound();
			tags.setInteger("ID", PacketID.Illuminate.value());
			
			MinersAdvantage.instance.network.sendToServer(new NetworkPacket(tags));
		}
		
		// Cancel the running Excavation agents when player lifts the key binding
		if (settings.excavation.bEnabled() && !variables.IsExcavationToggled)
			GodItems.isWorthy(false);
		
		if (variables.IsPlayerAttacking && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
			// Record the block face being attacked
			variables.sideHit = mc.objectMouseOver.sideHit;
			
			if (settings.substitution.bEnabled() && !SubstitutionHandler.instance.bCurrentlySwitched) {
				World world = player.getEntityWorld();
				BlockPos oPos = mc.objectMouseOver.getBlockPos();
				IBlockState state = world.getBlockState(oPos);
				Block block = state.getBlock();
				
				if (block == null || Blocks.AIR == block || Blocks.BEDROCK == block)
					return;
				
				SubstitutionHandler.instance.processToolSubtitution(world, player, oPos);
			}
		}
		
		// Switch back to previously held item if Substitution is enabled
		if (settings.substitution.bEnabled() && (!variables.IsPlayerAttacking || variables.IsVeinating)
				&& SubstitutionHandler.instance.bShouldSwitchBack && SubstitutionHandler.instance.bCurrentlySwitched
				&& SubstitutionHandler.instance.iPrevSlot >= 0 && player.inventory.currentItem != SubstitutionHandler.instance.iPrevSlot) {
			
			// System.out.println("Switching to Previous to ( " + SubstitutionHandler.instance.iPrevSlot + " )");
			
			player.inventory.currentItem = SubstitutionHandler.instance.iPrevSlot;
			ClientFunctions.syncCurrentPlayItem(player.inventory.currentItem);
			SubstitutionHandler.instance.reset();
		}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onAttackEntity(AttackEntityEvent event) {
		MAConfig settings = MAConfig.get();
		if (!settings.substitution.bEnabled() || !(event.getEntityPlayer() instanceof EntityPlayerSP) || (event.getEntityPlayer() instanceof FakePlayer))
			return;
		
		EntityPlayerSP player = (EntityPlayerSP) event.getEntityPlayer();
		
		if (settings.substitution.bIgnorePassiveMobs() && !(event.getTarget() instanceof EntityMob))
			return;
		
		if (currentAttackStage == AttackStage.IDLE && SubstitutionHandler.instance.processWeaponSubtitution(player, event.getTarget())) {
			// Because we are intercepting an attack & switching weapons, we need to cancel the attack & wait a tick to
			// execute it. This allows the weapon switch to cause the correct damage to the target.
			currentTarget = (EntityLivingBase) event.getTarget();
			currentAttackStage = AttackStage.SWITCHED;
			event.setCanceled(true);
			
		} else if (currentAttackStage != AttackStage.SWITCHED) {
			// Reset the current Attack Stage
			currentTarget = null;
			currentAttackStage = AttackStage.IDLE;
		}
	}
}
