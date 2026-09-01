package net.rebornaddon.jutsu;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.rebornaddon.compat.CooldownTrackerCompatibility;
import net.minecraft.world.GameType;
import net.narutomod.Chakra;
import net.narutomod.item.ItemJutsu;
import net.rebornaddon.mode.ModeConfigurationService;
import net.rebornaddon.region.RegionPolicyService;
import net.rebornaddon.exam.ExamService;
import net.rebornaddon.gameplay.codex.ShinobiCodexService;
import net.rebornaddon.advancement.RebornAdvancementService;

import java.util.List;
import java.util.ArrayList;

public final class JutsuRuntimeHooks {
    private static final ThreadLocal<JutsuDefinition> ACTIVE = new ThreadLocal<JutsuDefinition>();
    private static final String COOLDOWN_DATA = "RebornAddonJutsuCooldowns";
    private static final String COOLDOWN_STARTED_DATA = "RebornAddonJutsuCooldownStarts";

    private JutsuRuntimeHooks() {
    }

    public static boolean executeJutsu(ItemJutsu.Base item, ItemStack stack,
                                       EntityLivingBase user, float power) {
        ItemJutsu.JutsuEnum jutsu = JutsuRegistry.INSTANCE.currentJutsu(item, stack);
        if (jutsu == null || jutsu.jutsu == null) {
            return false;
        }

        JutsuDefinition definition = JutsuRegistry.INSTANCE.resolve(item, stack, power);
        if (user instanceof EntityPlayer && !RegionPolicyService.INSTANCE
                .allowsJutsu((EntityPlayer) user)) {
            status(user, "Jutsu are disabled in this region.");
            return false;
        }
        if (definition != null && user instanceof EntityPlayer
                && !ExamService.INSTANCE.canUseJutsu((EntityPlayer) user, definition)) {
            return false;
        }
        if (definition != null && user instanceof EntityPlayer
                && !ModeConfigurationService.INSTANCE.canActivate((EntityPlayer) user, definition)) {
            return false;
        }
        if (definition != null && !JutsuSettingsCache.INSTANCE.enabled(definition)) {
            status(user, definition.displayName() + " is disabled on this server.");
            return false;
        }
        if (definition != null && user instanceof EntityPlayer
                && JutsuSettingsCache.INSTANCE.override(definition, "cooldown") != null
                && !bypassesCooldown((EntityPlayer) user)) {
            long remaining = configuredCooldownRemaining((EntityPlayer) user, definition);
            if (remaining > 0L) {
                cooldownStatus(user, remaining);
                return false;
            }
        }

        double originalCost = jutsu.chakraUsage * power;
        String configuredCost = definition == null ? null
                : JutsuSettingsCache.INSTANCE.override(definition, "chakra");
        double chakraCost = configuredCost == null ? originalCost
                : JutsuSettingsCache.INSTANCE.decimal(definition, "chakra", originalCost);
        Chakra.Pathway pathway = Chakra.pathway(user);
        if (power <= 0.0F || pathway.getAmount() < chakraCost) {
            return false;
        }

        JutsuDefinition previous = ACTIVE.get();
        ACTIVE.set(definition);
        boolean created;
        try {
            created = jutsu.jutsu.createJutsu(stack, user, power);
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
        if (!created) {
            return false;
        }

        if (definition != null && user instanceof EntityPlayerMP) {
            RebornAdvancementService.INSTANCE.onJutsuUsed((EntityPlayerMP) user, definition);
            ShinobiCodexService.INSTANCE.discoverTechnique((EntityPlayerMP) user,
                    definition.id(), definition.displayName(), definition.category());
        }

        pathway.consume(chakraCost);
        if (definition != null && JutsuSettingsCache.INSTANCE.override(definition, "cooldown") != null) {
            long cooldown = JutsuSettingsCache.INSTANCE.ticks(definition, "cooldown", 0L);
            if (user instanceof EntityPlayer && bypassesCooldown((EntityPlayer) user)) {
                cooldown = 0L;
            }
            item.setCurrentJutsuCooldown(stack, cooldown);
            if (user instanceof EntityPlayer && !user.world.isRemote) {
                setConfiguredCooldown((EntityPlayer) user, definition, cooldown);
            }
        }
        return true;
    }

    public static float resolvePower(float original, ItemJutsu.Base item, ItemStack stack,
                                     EntityLivingBase user, int remainingUseTicks) {
        ItemJutsu.JutsuEnum jutsu = JutsuRegistry.INSTANCE.currentJutsu(item, stack);
        if (jutsu == null || jutsu.jutsu == null) {
            return original;
        }
        List<JutsuDefinition> stages = JutsuRegistry.INSTANCE.stages(jutsu);
        int elapsed = Math.max(0, item.getMaxItemUseDuration(stack) - remainingUseTicks);
        if (stages.size() > 1 && "narutomod:explosive_clay".equals(stages.get(0).parentId())) {
            return resolveExplosiveClayPower(stages, elapsed);
        }

        JutsuDefinition definition = stages.isEmpty() ? null : stages.get(0);
        if (definition == null) {
            return original;
        }
        String configuredTime = JutsuSettingsCache.INSTANCE.override(definition, "charge-time");
        double speed = JutsuSettingsCache.INSTANCE.decimal(definition, "charge-speed", 1.0D);
        float base = jutsu.jutsu.getBasePower();
        float maximum = jutsu.jutsu.getMaxPower(stack, user);
        if (configuredTime != null) {
            long ticks = JutsuSettingsCache.INSTANCE.ticks(definition, "charge-time", 0L);
            if (ticks == 0L) {
                return maximum;
            }
            double progress = Math.min(1.0D, elapsed * speed / ticks);
            return (float) (base + (maximum - base) * progress);
        }
        if (speed != 1.0D && speed > 0.0D) {
            return (float) Math.min(maximum, base + Math.max(0.0F, original - base) * speed);
        }
        return original;
    }

    public static ActionResult<ItemStack> onItemRightClick(ItemJutsu.Base item, World world,
                                                            EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        ItemJutsu.JutsuEnum jutsu = JutsuRegistry.INSTANCE.currentJutsu(item, stack);
        List<JutsuDefinition> stages = JutsuRegistry.INSTANCE.stages(jutsu);
        if (!stages.isEmpty() && !ExamService.INSTANCE.canUseJutsu(player, stages.get(0))) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        if (!stages.isEmpty() && !ModeConfigurationService.INSTANCE.canActivate(player, stages.get(0))) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        if (!stages.isEmpty() && !hasEnabledStage(stages)) {
            if (!world.isRemote) {
                status(player, stages.get(0).displayName() + " is disabled on this server.");
            }
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }

        if (bypassesCooldown(player)) {
            item.setCurrentJutsuCooldown(stack, 0L);
            if (!world.isRemote) {
                for (JutsuDefinition stage : stages) setConfiguredCooldown(player, stage, 0L);
            }
        } else if (!world.isRemote && stages.size() == 1
                && JutsuSettingsCache.INSTANCE.override(stages.get(0), "cooldown") != null) {
            long remaining = configuredCooldownRemaining(player, stages.get(0));
            item.setCurrentJutsuCooldown(stack, remaining);
            if (remaining > 0L) {
                cooldownStatus(player, remaining);
                return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
            }
        }
        EnumActionResult result = jutsu == null ? EnumActionResult.FAIL
                : item.canActivateJutsu(stack, jutsu, player);
        if (result == EnumActionResult.PASS && !world.isRemote) {
            long expiry = stack.hasTagCompound()
                    ? stack.getTagCompound().getLong("JutsuCDMapKey" + jutsu.index) : 0L;
            long remaining = Math.max(0L, expiry - world.getTotalWorldTime());
            cooldownStatus(player, remaining);
        } else if (result == EnumActionResult.SUCCESS) {
            player.setActiveHand(hand);
        }
        return new ActionResult<ItemStack>(result, stack);
    }

    static JutsuDefinition activeDefinition() {
        return ACTIVE.get();
    }

    public static void sendStatusMessage(EntityPlayer player, ITextComponent component,
                                         boolean actionBar) {
        if (actionBar && component instanceof TextComponentTranslation) {
            TextComponentTranslation translation = (TextComponentTranslation) component;
            Object[] arguments = translation.getFormatArgs();
            if ("chattext.cooldown.formatted".equals(translation.getKey())
                    && arguments.length > 0 && arguments[0] instanceof Number) {
                double seconds = Math.max(0.0D, ((Number) arguments[0]).doubleValue());
                cooldownStatus(player, (long) Math.ceil(seconds * 20.0D));
                return;
            }
        }
        player.sendStatusMessage(component, actionBar);
    }

    public static String formatDuration(long ticks) {
        long safeTicks = Math.max(0L, ticks);
        long totalSeconds = safeTicks / 20L + (safeTicks % 20L == 0L ? 0L : 1L);
        long hours = totalSeconds / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder value = new StringBuilder();
        if (hours > 0L) {
            value.append(hours).append(hours == 1L ? " hour" : " hours");
        }
        if (minutes > 0L) {
            if (value.length() > 0) value.append(", ");
            value.append(minutes).append(minutes == 1L ? " minute" : " minutes");
        }
        if (seconds > 0L || value.length() == 0) {
            if (value.length() > 0) value.append(", ");
            value.append(seconds).append(seconds == 1L ? " second" : " seconds");
        }
        return value.toString();
    }

    private static boolean bypassesCooldown(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            GameType gameType = ((EntityPlayerMP) player).interactionManager.getGameType();
            return gameType == GameType.CREATIVE;
        }
        return player != null && player.world != null && player.world.isRemote
                && player.capabilities.isCreativeMode;
    }

    private static float resolveExplosiveClayPower(List<JutsuDefinition> stages, int elapsed) {
        long previousThreshold = 0L;
        JutsuDefinition selected = null;
        for (JutsuDefinition stage : stages) {
            long configured = JutsuSettingsCache.INSTANCE.ticks(stage, "charge-time",
                    stage.defaultChargeTicks());
            double speed = JutsuSettingsCache.INSTANCE.decimal(stage, "charge-speed", 1.0D);
            long threshold = speed <= 0.0D ? configured : Math.round(configured / speed);
            threshold = Math.max(previousThreshold, threshold);
            if (elapsed >= threshold) {
                selected = stage;
            }
            previousThreshold = threshold + 1L;
        }
        if (selected == null) {
            return 0.99F;
        }
        return Float.isInfinite(selected.maximumPower())
                ? selected.minimumPower() : selected.minimumPower() + 0.1F;
    }

    private static boolean hasEnabledStage(List<JutsuDefinition> stages) {
        for (JutsuDefinition stage : stages) {
            if (JutsuSettingsCache.INSTANCE.enabled(stage)) {
                return true;
            }
        }
        return false;
    }

    private static long configuredCooldownRemaining(EntityPlayer player,
                                                    JutsuDefinition definition) {
        if (player == null || definition == null || player.world.isRemote) return 0L;
        NBTTagCompound data = persistedData(player, false);
        if (data == null || !data.hasKey(COOLDOWN_DATA, 10)) return 0L;
        NBTTagCompound cooldowns = data.getCompoundTag(COOLDOWN_DATA);
        long expiresAt = cooldowns.getLong(definition.id());
        NBTTagCompound starts = data.hasKey(COOLDOWN_STARTED_DATA, 10)
                ? data.getCompoundTag(COOLDOWN_STARTED_DATA) : new NBTTagCompound();
        long now = System.currentTimeMillis();
        long configuredTicks = JutsuSettingsCache.INSTANCE.ticks(definition, "cooldown", 0L);
        long configuredMillis = millis(configuredTicks);
        if (configuredMillis <= 0L) {
            cooldowns.removeTag(definition.id());
            starts.removeTag(definition.id());
            storeCooldownData(player, data, cooldowns, starts);
            return 0L;
        }
        long startedAt = starts.getLong(definition.id());
        long configuredExpiry = safeExpiry(startedAt > 0L ? startedAt : now, configuredMillis);
        if (expiresAt > configuredExpiry) {
            expiresAt = configuredExpiry;
            cooldowns.setLong(definition.id(), expiresAt);
        }
        long remainingMillis = expiresAt - now;
        if (remainingMillis <= 0L) {
            cooldowns.removeTag(definition.id());
            starts.removeTag(definition.id());
            storeCooldownData(player, data, cooldowns, starts);
            return 0L;
        }
        storeCooldownData(player, data, cooldowns, starts);
        return Math.max(1L, (remainingMillis + 49L) / 50L);
    }

    private static void setConfiguredCooldown(EntityPlayer player, JutsuDefinition definition,
                                              long ticks) {
        if (player == null || definition == null || player.world.isRemote) return;
        NBTTagCompound data = persistedData(player, true);
        NBTTagCompound cooldowns = data.hasKey(COOLDOWN_DATA, 10)
                ? data.getCompoundTag(COOLDOWN_DATA) : new NBTTagCompound();
        NBTTagCompound starts = data.hasKey(COOLDOWN_STARTED_DATA, 10)
                ? data.getCompoundTag(COOLDOWN_STARTED_DATA) : new NBTTagCompound();
        if (ticks <= 0L) {
            cooldowns.removeTag(definition.id());
            starts.removeTag(definition.id());
        }
        else {
            long now = System.currentTimeMillis();
            cooldowns.setLong(definition.id(), safeExpiry(now, millis(ticks)));
            starts.setLong(definition.id(), now);
        }
        storeCooldownData(player, data, cooldowns, starts);
    }

    public static void reconcileCooldowns(MinecraftServer server, JutsuDefinition definition,
                                          long configuredTicks) {
        if (server == null || definition == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            reconcileCooldown(player, definition, configuredTicks);
        }
    }

    private static void reconcileCooldown(EntityPlayerMP player, JutsuDefinition definition,
                                          long configuredTicks) {
        NBTTagCompound data = persistedData(player, false);
        if (data == null || !data.hasKey(COOLDOWN_DATA, 10)) return;
        NBTTagCompound cooldowns = data.getCompoundTag(COOLDOWN_DATA);
        if (!cooldowns.hasKey(definition.id(), 99)) return;
        NBTTagCompound starts = data.hasKey(COOLDOWN_STARTED_DATA, 10)
                ? data.getCompoundTag(COOLDOWN_STARTED_DATA) : new NBTTagCompound();
        long now = System.currentTimeMillis();
        long duration = millis(configuredTicks);
        long startedAt = starts.getLong(definition.id());
        long maximum = duration <= 0L ? 0L : safeExpiry(startedAt > 0L ? startedAt : now, duration);
        if (maximum <= now) {
            cooldowns.removeTag(definition.id());
            starts.removeTag(definition.id());
        } else if (cooldowns.getLong(definition.id()) > maximum) {
            cooldowns.setLong(definition.id(), maximum);
        }
        storeCooldownData(player, data, cooldowns, starts);
    }

    public static int clearAllCooldowns(MinecraftServer server) {
        if (server == null) return 0;
        JutsuCooldownResetData data = JutsuCooldownResetData.get(server);
        if (data != null) data.resetAll(server);
        return server.getPlayerList().getCurrentPlayerCount();
    }

    public static void clearAllCooldowns(MinecraftServer server, java.util.UUID playerId) {
        JutsuCooldownResetData data = JutsuCooldownResetData.get(server);
        if (data != null) data.resetPlayer(server, playerId);
    }

    public static void clearAllCooldowns(EntityPlayerMP player) {
        if (player == null) return;
        NBTTagCompound data = persistedData(player, true);
        data.removeTag(COOLDOWN_DATA);
        data.removeTag(COOLDOWN_STARTED_DATA);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
        clearStacks(player, player.inventory.mainInventory);
        clearStacks(player, player.inventory.offHandInventory);
        clearStacks(player, player.inventory.armorInventory);
    }

    private static void clearStacks(EntityPlayerMP player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            CooldownTrackerCompatibility.clear(player, stack.getItem());
            if (!stack.hasTagCompound()) continue;
            NBTTagCompound tag = stack.getTagCompound();
            for (String key : new ArrayList<String>(tag.getKeySet())) {
                if (key.startsWith("JutsuCDMapKey")) tag.removeTag(key);
            }
            if (stack.getItem() instanceof ItemJutsu.Base) {
                ((ItemJutsu.Base) stack.getItem()).setCurrentJutsuCooldown(stack, 0L);
            }
        }
    }

    private static void storeCooldownData(EntityPlayer player, NBTTagCompound data,
                                          NBTTagCompound cooldowns, NBTTagCompound starts) {
        data.setTag(COOLDOWN_DATA, cooldowns);
        data.setTag(COOLDOWN_STARTED_DATA, starts);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
    }

    private static long millis(long ticks) {
        if (ticks <= 0L) return 0L;
        try {
            return Math.multiplyExact(ticks, 50L);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeExpiry(long start, long duration) {
        return duration >= Long.MAX_VALUE - start ? Long.MAX_VALUE : start + duration;
    }

    private static NBTTagCompound persistedData(EntityPlayer player, boolean create) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            if (!create) return null;
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static void status(EntityLivingBase user, String text) {
        if (user instanceof EntityPlayer) {
            ((EntityPlayer) user).sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + text), true);
        }
    }

    private static void cooldownStatus(EntityLivingBase user, long ticks) {
        if (user instanceof EntityPlayer) {
            ((EntityPlayer) user).sendStatusMessage(new TextComponentString(
                    TextFormatting.WHITE + "Cooldown: " + formatDuration(ticks)), true);
        }
    }
}
