package net.rebornaddon.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.UUID;

public final class PlayerStateRecoveryHandler {
    public static final PlayerStateRecoveryHandler INSTANCE = new PlayerStateRecoveryHandler();

    public static final int ABSORPTION_CLEARED = 1;
    public static final int PARALYSIS_CLEARED = 2;
    public static final int INVULNERABILITY_CLEARED = 4;
    public static final int HURT_RESISTANCE_CLEARED = 8;

    private static final UUID PARALYSIS_SPEED_MODIFIER =
            UUID.fromString("c69af92a-b96d-49b7-a396-9b3b0d77edd5");
    private static final ResourceLocation PARALYSIS_ID =
            new ResourceLocation("narutomod", "paralysis");
    private static volatile Potion paralysisPotion;

    private PlayerStateRecoveryHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            PlayerStateSafetyHooks.reconcileAbsorption(player);
            sanitizeSurvivalInvulnerability(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            clearParalysisState(player, false);
            player.setAbsorptionAmount(0.0F);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        clearParalysisState(event.getOriginal(), false);
        clearParalysisState(event.getEntityPlayer(), true);
        event.getEntityPlayer().setAbsorptionAmount(0.0F);
        if (event.getEntityPlayer() instanceof EntityPlayerMP) {
            sanitizeSurvivalInvulnerability((EntityPlayerMP) event.getEntityPlayer());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        clearParalysisState(event.player, true);
        PlayerStateSafetyHooks.reconcileAbsorption(event.player);
        if (event.player instanceof EntityPlayerMP) {
            sanitizeSurvivalInvulnerability((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        clearStaleParalysis(event.player);
        PlayerStateSafetyHooks.reconcileAbsorption(event.player);
        if (event.player instanceof EntityPlayerMP) {
            sanitizeSurvivalInvulnerability((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPotionExpiry(PotionEvent.PotionExpiryEvent event) {
        if (isAbsorption(event.getPotionEffect())) {
            event.getEntityLiving().setAbsorptionAmount(0.0F);
        }
        if (isParalysis(event.getPotionEffect())) {
            clearParalysisState(event.getEntityLiving(), false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPotionRemoved(PotionEvent.PotionRemoveEvent event) {
        PotionEffect effect = event.getPotionEffect();
        Potion removed = effect == null ? event.getPotion() : effect.getPotion();
        if (!event.isCanceled() && removed == MobEffects.ABSORPTION) {
            event.getEntityLiving().setAbsorptionAmount(0.0F);
        }
        if (isParalysis(removed)) {
            clearParalysisState(event.getEntityLiving(), false);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        if (event.player instanceof EntityPlayerMP) {
            sanitizeSurvivalInvulnerability((EntityPlayerMP) event.player);
        }
        if (event.player.ticksExisted % 20 == 0) {
            PlayerStateSafetyHooks.reconcileAbsorption(event.player);
            clearStaleParalysis(event.player);
        }
    }

    public int recover(EntityPlayerMP player) {
        int changes = 0;
        if (player.getAbsorptionAmount() != 0.0F) {
            player.setAbsorptionAmount(0.0F);
            changes |= ABSORPTION_CLEARED;
        }
        if (clearParalysisState(player, true)) {
            changes |= PARALYSIS_CLEARED;
        }
        if (player.hurtResistantTime > 0) {
            player.hurtResistantTime = 0;
            changes |= HURT_RESISTANCE_CLEARED;
        }
        if (!player.isCreative() && !player.isSpectator()) {
            boolean changed = player.getIsInvulnerable() || player.capabilities.disableDamage;
            player.setEntityInvulnerable(false);
            player.capabilities.disableDamage = false;
            if (changed) {
                player.sendPlayerAbilities();
                changes |= INVULNERABILITY_CLEARED;
            }
        }
        return changes;
    }

    private static boolean sanitizeSurvivalInvulnerability(EntityPlayerMP player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        boolean changed = player.getIsInvulnerable() || player.capabilities.disableDamage;
        if (!changed) {
            return false;
        }
        player.setEntityInvulnerable(false);
        player.capabilities.disableDamage = false;
        player.sendPlayerAbilities();
        return true;
    }

    private static void clearStaleParalysis(EntityPlayer player) {
        Potion potion = paralysis();
        if (potion == null || !player.isPotionActive(potion)) {
            clearParalysisState(player, false);
        }
    }

    private static boolean clearParalysisState(EntityLivingBase entity, boolean removeEffect) {
        boolean changed = false;
        Potion potion = paralysis();
        if (removeEffect && potion != null && entity.isPotionActive(potion)) {
            entity.removePotionEffect(potion);
            changed = true;
        }
        IAttributeInstance movement = entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (movement != null && movement.getModifier(PARALYSIS_SPEED_MODIFIER) != null) {
            movement.removeModifier(PARALYSIS_SPEED_MODIFIER);
            changed = true;
        }
        if (entity.getEntityData().hasKey("FearEffect")) {
            entity.getEntityData().removeTag("FearEffect");
            changed = true;
        }
        return changed;
    }

    private static boolean isParalysis(PotionEffect effect) {
        return effect != null && isParalysis(effect.getPotion());
    }

    private static boolean isAbsorption(PotionEffect effect) {
        return effect != null && effect.getPotion() == MobEffects.ABSORPTION;
    }

    private static boolean isParalysis(Potion potion) {
        return potion != null && (potion == paralysis() || PARALYSIS_ID.equals(potion.getRegistryName()));
    }

    private static Potion paralysis() {
        Potion cached = paralysisPotion;
        if (cached != null) return cached;
        cached = ForgeRegistries.POTIONS.getValue(PARALYSIS_ID);
        if (cached != null) paralysisPotion = cached;
        return cached;
    }
}
