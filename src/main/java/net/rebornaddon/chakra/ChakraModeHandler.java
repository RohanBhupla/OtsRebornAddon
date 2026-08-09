package net.rebornaddon.chakra;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.narutomod.Chakra;
import net.rebornaddon.village.LuckPermsBridge;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChakraModeHandler {
    public static final ChakraModeHandler INSTANCE = new ChakraModeHandler();

    private static final String VILLAGE_KEY = "RebornAddonVillage";
    private static final String LEARNED_PREFIX = "RebornAddonChakraMode_";

    private final Map<UUID, ChakraMode> activeModes = new HashMap<UUID, ChakraMode>();

    private ChakraModeHandler() {
    }

    public void learn(EntityPlayerMP player, ChakraMode mode) {
        if (player == null || mode == null || player.isDead) {
            return;
        }

        ItemStack scroll = matchingScroll(player, mode);
        if (scroll.isEmpty()) {
            error(player, "Hold the matching chakra mode scroll to learn this technique.");
            return;
        }
        if (!meetsRequirements(player, mode)) {
            return;
        }
        if (!NarutoJutsuIntegration.learn(player, mode, isOperator(player))) {
            error(player, "A compatible NarutoMod nature learner could not be created.");
            return;
        }
        if (!player.capabilities.isCreativeMode) {
            scroll.shrink(1);
        }
        player.inventory.markDirty();
        player.sendStatusMessage(new TextComponentString(TextFormatting.GREEN
                + mode.displayName() + " learned."), true);
    }

    public void toggle(EntityPlayerMP player, ChakraMode mode) {
        if (!hasLearned(player, mode)) {
            error(player, "You have not learned " + mode.displayName() + ".");
            return;
        }
        if (!meetsVillageRequirement(player, mode)) {
            return;
        }

        ChakraMode active = activeModes.get(player.getUniqueID());
        if (active == mode) {
            deactivate(player, mode, " deactivated.");
            return;
        }
        if (active != null) {
            clearTemporaryEffects(player, active);
            RebornAddonNetwork.sendChakraModeEffect(player, active, false);
        }

        activeModes.put(player.getUniqueID(), mode);
        player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA
                + mode.displayName() + " activated."), true);
        RebornAddonNetwork.sendChakraModeEffect(player, mode, true);
    }

    public boolean hasLearned(EntityPlayer player, ChakraMode mode) {
        return NarutoJutsuIntegration.hasLearned(player, mode);
    }

    public boolean isActive(net.minecraft.entity.EntityLivingBase entity, ChakraMode mode) {
        return entity != null && mode != null && activeModes.get(entity.getUniqueID()) == mode;
    }

    public void deactivateFromLearner(EntityPlayerMP player, ChakraMode mode) {
        if (player != null && activeModes.get(player.getUniqueID()) == mode) {
            deactivate(player, mode, " deactivated.");
        }
    }

    public void migrateLegacy(EntityPlayerMP player, ChakraMode mode) {
        if (player == null || mode == null) {
            return;
        }
        NBTTagCompound data = persisted(player);
        boolean oldFlag = data.getBoolean(LEARNED_PREFIX + mode.key());
        boolean oldItem = hasLegacyFocus(player, mode);
        if (!oldFlag && !oldItem) {
            return;
        }
        boolean migrated = hasLearned(player, mode);
        if ((oldFlag || oldItem) && !migrated) {
            migrated = NarutoJutsuIntegration.learn(player, mode, true);
        }
        if (oldFlag && migrated) {
            data.removeTag(LEARNED_PREFIX + mode.key());
        }
        if (oldItem && migrated) {
            removeLegacyFocus(player, mode);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        long time = player.world.getTotalWorldTime();
        if (time % 100L == 0L) {
            migrateLegacyModes(player);
            NarutoJutsuIntegration.ensurePlayerLearners(player);
        }

        ChakraMode mode = activeModes.get(player.getUniqueID());
        if (mode == null) {
            return;
        }
        if (player.isDead) {
            deactivate(player, mode, " ended.");
            return;
        }

        if (time % 20L == 0L) {
            if (!hasLearned(player, mode) || !meetsVillageRequirementQuiet(player, mode)) {
                deactivate(player, mode, " ended.");
                return;
            }
            if (!consumeChakra(player, mode.chakraPerSecond())) {
                deactivate(player, mode, ": not enough chakra.");
                return;
            }
        }
        if (time % 10L == 0L) {
            applyEffects(player, mode);
        }
        if (time % 8L == 0L) {
            RebornAddonNetwork.sendChakraModeEffect(player, mode, true);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            NarutoJutsuIntegration.repairPlayerLearners(player);
            NarutoJutsuIntegration.migrateLegacyRain(player);
            migrateLegacyModes(player);
            NarutoJutsuIntegration.ensurePlayerLearners(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!event.getEntityLiving().world.isRemote && event.getEntityLiving() instanceof EntityPlayerMP) {
            NarutoJutsuIntegration.repairLearnerStack(event.getFrom());
            NarutoJutsuIntegration.repairLearnerStack(event.getTo());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        if (!isOperator(player)) {
            return;
        }
        ChakraMode mode = NarutoJutsuIntegration.selectedMode(event.getItemStack());
        if (mode != null && hasLearned(player, mode)) {
            toggle(player, mode);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChakraMode mode = activeModes.remove(event.player.getUniqueID());
        clearTemporaryEffects(event.player, mode);
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ChakraMode mode = activeModes.remove(event.player.getUniqueID());
        clearTemporaryEffects(event.player, mode);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            ChakraMode mode = activeModes.remove(event.getEntityLiving().getUniqueID());
            clearTemporaryEffects(event.getEntityLiving(), mode);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote) {
            return;
        }

        ChakraMode defendingMode = activeModes.get(target.getUniqueID());
        DamageSource source = event.getSource();
        if (defendingMode == ChakraMode.WATER) {
            event.setAmount(event.getAmount()
                    * (source.isFireDamage() || source.isProjectile() ? 0.65F : 0.85F));
        } else if (defendingMode == ChakraMode.EARTH && source.isExplosion()) {
            event.setAmount(event.getAmount() * 0.55F);
        }

        Entity attacker = source.getTrueSource();
        if (attacker instanceof EntityPlayer
                && attacker != target
                && activeModes.get(attacker.getUniqueID()) == ChakraMode.RAIN) {
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1, false, false));
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, false));
        }
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (!event.getEntityLiving().world.isRemote
                && activeModes.get(event.getEntityLiving().getUniqueID()) == ChakraMode.EARTH) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.25F);
        }
    }

    @SubscribeEvent
    public void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!event.getEntityLiving().world.isRemote
                && activeModes.get(event.getEntityLiving().getUniqueID()) == ChakraMode.EARTH) {
            event.setStrength(event.getStrength() * 0.2F);
        }
    }

    private boolean meetsRequirements(EntityPlayerMP player, ChakraMode mode) {
        if (!meetsVillageRequirement(player, mode)) {
            return false;
        }
        if (!NarutoJutsuIntegration.isAvailable(mode)) {
            error(player, "NarutoMod learner integration is unavailable for this technique.");
            return false;
        }
        return true;
    }

    private boolean meetsVillageRequirement(EntityPlayerMP player, ChakraMode mode) {
        if (meetsVillageRequirementQuiet(player, mode)) {
            return true;
        }
        error(player, mode.displayName() + " is restricted to the "
                + mode.requiredVillage().displayName() + " Village.");
        return false;
    }

    private boolean meetsVillageRequirementQuiet(EntityPlayerMP player, ChakraMode mode) {
        return isOperator(player) || mode.requiredVillage() == null
                || mode.requiredVillage() == resolveVillage(player);
    }

    private static Village resolveVillage(EntityPlayerMP player) {
        Village village = Village.byGroup(persisted(player).getString(VILLAGE_KEY));
        return village == null ? LuckPermsBridge.INSTANCE.findKnownVillage(player) : village;
    }

    private static void applyEffects(EntityPlayerMP player, ChakraMode mode) {
        if (mode == ChakraMode.WATER) {
            add(player, MobEffects.WATER_BREATHING, 0);
            add(player, MobEffects.SPEED, 3);
            add(player, MobEffects.STRENGTH, 2);
            add(player, MobEffects.RESISTANCE, 1);
            if (player.isBurning()) {
                player.extinguish();
            }
        } else if (mode == ChakraMode.RAIN) {
            add(player, MobEffects.SPEED, 5);
            add(player, MobEffects.STRENGTH, 3);
            add(player, MobEffects.HASTE, 3);
            add(player, MobEffects.RESISTANCE, 1);
            player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 220, 0, false, false));
        } else {
            add(player, MobEffects.RESISTANCE, 3);
            add(player, MobEffects.STRENGTH, 5);
            add(player, MobEffects.FIRE_RESISTANCE, 0);
        }
    }

    private static void add(EntityPlayerMP player, net.minecraft.potion.Potion potion, int amplifier) {
        player.addPotionEffect(new PotionEffect(potion, 30, amplifier, false, false));
    }

    private static boolean consumeChakra(EntityPlayerMP player, double amount) {
        if (player.capabilities.isCreativeMode) {
            return true;
        }
        return Chakra.isInitialized(player) && Chakra.pathway(player).consume(amount);
    }

    private void deactivate(EntityPlayerMP player, ChakraMode mode, String suffix) {
        activeModes.remove(player.getUniqueID());
        clearTemporaryEffects(player, mode);
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                + mode.displayName() + suffix), true);
        RebornAddonNetwork.sendChakraModeEffect(player, mode, false);
    }

    private static void clearTemporaryEffects(EntityLivingBase entity, ChakraMode mode) {
        if (mode != ChakraMode.RAIN) {
            return;
        }
        PotionEffect nightVision = entity.getActivePotionEffect(MobEffects.NIGHT_VISION);
        if (nightVision != null && nightVision.getAmplifier() == 0
                && nightVision.getDuration() <= 230) {
            entity.removePotionEffect(MobEffects.NIGHT_VISION);
        }
    }

    private void migrateLegacyModes(EntityPlayerMP player) {
        for (ChakraMode mode : ChakraMode.values()) {
            migrateLegacy(player, mode);
        }
    }

    private static boolean hasLegacyFocus(EntityPlayer player, ChakraMode mode) {
        return containsLegacyFocus(player.inventory.mainInventory, mode)
                || containsLegacyFocus(player.inventory.offHandInventory, mode)
                || containsLegacyFocus(player.inventory.armorInventory, mode);
    }

    private static boolean containsLegacyFocus(NonNullList<ItemStack> stacks, ChakraMode mode) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.getItem() == ChakraModeItems.focus(mode)) {
                return true;
            }
        }
        return false;
    }

    private static void removeLegacyFocus(EntityPlayer player, ChakraMode mode) {
        removeLegacyFocus(player.inventory.mainInventory, mode);
        removeLegacyFocus(player.inventory.offHandInventory, mode);
        removeLegacyFocus(player.inventory.armorInventory, mode);
        player.inventory.markDirty();
    }

    private static void removeLegacyFocus(NonNullList<ItemStack> stacks, ChakraMode mode) {
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (!stack.isEmpty() && stack.getItem() == ChakraModeItems.focus(mode)) {
                stacks.set(index, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isOperator(EntityPlayerMP player) {
        return player.canUseCommand(2, "rebornaddon.chakra");
    }

    private static ItemStack matchingScroll(EntityPlayer player, ChakraMode mode) {
        ItemStack main = player.getHeldItemMainhand();
        if (!main.isEmpty() && main.getItem() == ChakraModeItems.scroll(mode)) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        return !off.isEmpty() && off.getItem() == ChakraModeItems.scroll(mode) ? off : ItemStack.EMPTY;
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static void error(EntityPlayerMP player, String message) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED + message), true);
    }
}
