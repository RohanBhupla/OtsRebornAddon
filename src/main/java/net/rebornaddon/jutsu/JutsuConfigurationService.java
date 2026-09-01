package net.rebornaddon.jutsu;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.store.StoreCatalog;
import net.rebornaddon.store.StoreConfigurationService;
import net.rebornaddon.integration.PluginInvalidationBus;
import net.rebornaddon.store.StorePluginBridge;
import net.rebornaddon.store.StoreQuotaService;
import net.rebornaddon.mode.ModeConfigurationService;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.RankedPluginBridge;
import net.rebornaddon.ranked.season.SeasonManager;
import net.rebornaddon.mode.ModePluginBridge;
import net.rebornaddon.chakra.ChakraControlConfigurationService;
import net.rebornaddon.gameplay.GameplaySystemsPluginBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JutsuConfigurationService {
    public static final JutsuConfigurationService INSTANCE = new JutsuConfigurationService();
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Jutsu Configuration");

    public static final int STORE_UPDATE = 10;
    public static final int STORE_RESET = 11;
    public static final int STORE_RESET_ALL = 12;
    public static final int STORE_RELOAD = 13;
    public static final int STORE_SCOPE_UPDATE = 14;
    public static final int STORE_SCOPE_RESET = 15;
    public static final int QUOTA_LOAD = 20;
    public static final int QUOTA_SET = 21;
    public static final int QUOTA_ADD = 22;
    public static final int QUOTA_RESET = 23;
    public static final int ADMIN_OPEN = 30;
    public static final int COOLDOWN_CLEAR_PLAYER = 4;
    public static final int COOLDOWN_CLEAR_ALL = 5;

    private static final String ENTITY_JUTSU_KEY = "RebornAddonJutsuId";

    private boolean catalogPublished;
    private int publishRetryTicks;
    private long invalidationRevision;
    private final Map<Integer, EffectContext> effectContexts = new HashMap<Integer, EffectContext>();
    private final ThreadLocal<Boolean> replacingEffect = new ThreadLocal<Boolean>();

    private JutsuConfigurationService() {
    }

    public void initialize() {
        JutsuRegistry.INSTANCE.discover();
        catalogPublished = publishCatalog();
        publishRetryTicks = 0;
    }

    public void reset() {
        JutsuSettingsCache.INSTANCE.clear();
        JutsuRegistry.INSTANCE.clear();
        catalogPublished = false;
        publishRetryTicks = 0;
        effectContexts.clear();
    }

    public boolean refreshRuntime() {
        if (!JutsuPluginBridge.isAvailable()) {
            JutsuSettingsCache.INSTANCE.clear();
            catalogPublished = false;
            return false;
        }
        String[] overrides = JutsuPluginBridge.runtimeOverrides();
        if (overrides == null) {
            JutsuSettingsCache.INSTANCE.clear();
            catalogPublished = false;
            return false;
        }
        JutsuSettingsCache.INSTANCE.replace(overrides);
        catalogPublished = true;
        return true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long changed = PluginInvalidationBus.revision("jutsu");
        if (changed != invalidationRevision) {
            invalidationRevision = changed;
            refreshRuntime();
        }
        if (++publishRetryTicks < 100) {
            pruneEffectContexts();
            return;
        }
        publishRetryTicks = 0;
        pruneEffectContexts();
        if (catalogPublished && !JutsuPluginBridge.isAvailable()) {
            JutsuSettingsCache.INSTANCE.clear();
            catalogPublished = false;
        } else if (!catalogPublished) {
            catalogPublished = publishCatalog();
        }
    }

    public String[] update(ICommandSender sender, String id, String property, String value) {
        String[] response = JutsuPluginBridge.update(sender, id, property, value);
        if (JutsuPluginBridge.successful(response)) {
            JutsuDefinition definition = JutsuRegistry.INSTANCE.get(id);
            String accepted = response.length > 2 && !response[2].isEmpty() ? response[2] : value;
            boolean refreshed = refreshRuntime();
            if (definition != null && definition.supports(property)) {
                if (!refreshed || !JutsuSettingsCache.INSTANCE.hasOverride(
                        definition, property, accepted)) {
                    LOGGER.warn("The server plugin accepted {} {}={}, but its runtime snapshot "
                                    + "did not contain that value; applying the accepted value immediately.",
                            definition.id(), property, accepted);
                    JutsuSettingsCache.INSTANCE.applyOverride(definition, property, accepted);
                }
                if ("cooldown".equals(property)) {
                    reconcileCooldowns(sender, definition, accepted);
                }
            }
        }
        return response;
    }

    public String[] reset(ICommandSender sender, String id, String property) {
        String[] response = JutsuPluginBridge.reset(sender, id, property);
        if (JutsuPluginBridge.successful(response)) {
            refreshRuntime();
            JutsuDefinition definition = JutsuRegistry.INSTANCE.get(id);
            if (definition != null && ("cooldown".equals(property) || "all".equals(property))) {
                reconcileCooldowns(sender, definition, definition.defaultValue("cooldown"));
            }
        }
        return response;
    }

    public String[] resetAll(ICommandSender sender) {
        String[] response = JutsuPluginBridge.resetAll(sender);
        if (JutsuPluginBridge.successful(response)) {
            refreshRuntime();
            if (sender != null && sender.getServer() != null) {
                JutsuRuntimeHooks.clearAllCooldowns(sender.getServer());
            }
        }
        return response;
    }

    public String[] reload(ICommandSender sender) {
        String[] response = JutsuPluginBridge.reload(sender);
        if (JutsuPluginBridge.successful(response)) {
            String[] overrides = JutsuPluginBridge.registerCatalog(catalogDescriptors());
            if (overrides == null) {
                JutsuSettingsCache.INSTANCE.clear();
                catalogPublished = false;
                return new String[] {"false", "The jutsu catalog could not be republished.", ""};
            }
            JutsuSettingsCache.INSTANCE.replace(overrides);
            catalogPublished = true;
        }
        return response;
    }

    public String[] adminSnapshot(ICommandSender sender) {
        return JutsuPluginBridge.adminSnapshot(sender);
    }

    public void openAdmin(EntityPlayerMP player) {
        openAdmin(player, "jutsus");
    }

    public void openAdmin(EntityPlayerMP player, String tab) {
        String selectedTab = normalizeAdminTab(tab);
        String[] response;
        if ("jutsus".equals(selectedTab)) {
            refreshRuntime();
            response = adminSnapshot(player);
        } else {
            response = new String[] {"true", "Loaded server controls.", ""};
        }
        NBTTagCompound snapshot = snapshotTag(response, player,
                player == null ? "" : player.getName(), selectedTab);
        RebornAddonNetwork.sendJutsuAdminSnapshot(player, snapshot);
    }

    public void handleGuiAction(EntityPlayerMP player, int action, String id,
                                String property, String value) {
        if (action == ADMIN_OPEN) {
            if (player == null || !player.canUseCommand(2, "rebornadmin")) {
                if (player != null) {
                    player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                            net.minecraft.util.text.TextFormatting.RED
                                    + "Operator access is required."), true);
                }
                return;
            }
            String tab = "store".equals(id) || "sections".equals(id) || "limits".equals(id)
                    || "quests".equals(id) || "modes".equals(id) || "ranked".equals(id)
                    || "ranks".equals(id) || "cooldowns".equals(id)
                    || "chakra-control".equals(id)
                    ? id : "jutsus";
            openAdmin(player, tab);
            return;
        }
        if (action == COOLDOWN_CLEAR_PLAYER || action == COOLDOWN_CLEAR_ALL) {
            handleCooldownClear(player, action, value);
            return;
        }
        if (action >= ChakraControlConfigurationService.UPDATE
                && action <= ChakraControlConfigurationService.RELOAD) {
            handleChakraControlGuiAction(player, action, property, value);
            return;
        }
        if (action >= SeasonManager.ACTION_LIFECYCLE) {
            handleRankedGuiAction(player, action, id, property, value);
            return;
        }
        if (action >= ModeConfigurationService.UPDATE) {
            handleModeGuiAction(player, action, id, property, value);
            return;
        }
        if (action >= STORE_UPDATE) {
            handleStoreGuiAction(player, action, id, property, value);
            return;
        }
        String[] response;
        if (action == 0) {
            JutsuDefinition definition = JutsuRegistry.INSTANCE.get(id);
            if (definition == null || !definition.supports(property)) {
                response = new String[] {"false", "That jutsu property is not available.", ""};
            } else {
                try {
                    response = update(player, definition.id(), property,
                            JutsuValueParser.normalize(property, value));
                } catch (IllegalArgumentException exception) {
                    response = new String[] {"false", exception.getMessage(), ""};
                }
            }
        } else if (action == 1) {
            response = reset(player, id, property);
        } else if (action == 2) {
            response = resetAll(player);
        } else if (action == 3) {
            response = reload(player);
        } else {
            response = new String[] {"false", "Unknown jutsu administration action.", ""};
        }
        String[] snapshot = adminSnapshot(player);
        if (action == 0 && JutsuPluginBridge.successful(response)) {
            String accepted = response.length > 2 && !response[2].isEmpty() ? response[2]
                    : JutsuSettingsCache.INSTANCE.override(JutsuRegistry.INSTANCE.get(id), property);
            snapshot = withOverride(snapshot, id, property, accepted);
        }
        if (snapshot.length > 1) {
            snapshot[1] = JutsuPluginBridge.message(response);
        }
        RebornAddonNetwork.sendJutsuAdminSnapshot(player,
                snapshotTag(snapshot, player, player.getName(), "jutsus"));
    }

    private void handleCooldownClear(EntityPlayerMP player, int action, String targetName) {
        String[] response;
        if (player == null || !player.canUseCommand(2, "rebornadmin")) {
            response = new String[] {"false", "Operator access is required.", ""};
        } else if (action == COOLDOWN_CLEAR_ALL) {
            int count = JutsuRuntimeHooks.clearAllCooldowns(player.getServer());
            response = new String[] {"true", "Cleared current cooldowns for all players; "
                    + count + (count == 1 ? " online player was" : " online players were")
                    + " updated immediately.", ""};
        } else {
            String requested = targetName == null ? "" : targetName.trim();
            EntityPlayerMP target = player.getServer().getPlayerList()
                    .getPlayerByUsername(requested);
            GameProfile profile = target == null ? player.getServer().getPlayerProfileCache()
                    .getGameProfileForUsername(requested) : target.getGameProfile();
            if (profile == null || profile.getId() == null) {
                response = new String[] {"false", "That player could not be found.", ""};
            } else {
                JutsuRuntimeHooks.clearAllCooldowns(player.getServer(), profile.getId());
                response = new String[] {"true", "Cleared all current cooldowns for "
                        + profile.getName() + ".", ""};
            }
        }
        NBTTagCompound data = snapshotTag(response, player,
                player == null ? "" : player.getName(), "cooldowns");
        RebornAddonNetwork.sendJutsuAdminSnapshot(player, data);
    }

    private static void reconcileCooldowns(ICommandSender sender, JutsuDefinition definition,
                                           String ticksValue) {
        if (sender == null || sender.getServer() == null || definition == null) return;
        try {
            JutsuRuntimeHooks.reconcileCooldowns(sender.getServer(), definition,
                    Math.max(0L, Long.parseLong(ticksValue == null ? "0" : ticksValue)));
        } catch (NumberFormatException ignored) {
        }
    }

    private static String[] withOverride(String[] snapshot, String id, String property,
                                         String value) {
        if (snapshot == null || snapshot.length < 3 || id == null || property == null
                || value == null) {
            return snapshot;
        }
        String[] copy = snapshot.clone();
        for (int i = 2; i < copy.length; i++) {
            String[] fields = copy[i].split("\\t", -1);
            if (fields.length < 8 || !id.equals(fields[0])) continue;
            StringBuilder overrides = new StringBuilder();
            if (fields.length > 8 && !fields[8].isEmpty()) {
                for (String pair : fields[8].split(";")) {
                    int equals = pair.indexOf('=');
                    if (equals <= 0 || property.equals(pair.substring(0, equals))) continue;
                    if (overrides.length() > 0) overrides.append(';');
                    overrides.append(pair);
                }
            }
            if (overrides.length() > 0) overrides.append(';');
            overrides.append(property).append('=').append(value);
            StringBuilder record = new StringBuilder();
            for (int field = 0; field < Math.max(9, fields.length); field++) {
                if (field > 0) record.append('\t');
                if (field == 8) record.append(overrides);
                else if (field < fields.length) record.append(fields[field]);
            }
            copy[i] = record.toString();
            break;
        }
        return copy;
    }

    private void handleModeGuiAction(EntityPlayerMP player, int action, String id,
                                     String property, String value) {
        String[] response = ModeConfigurationService.INSTANCE.handleAdminAction(
                player, action, id, property, value);
        NBTTagCompound snapshot = snapshotTag(response, player, player.getName(), "modes");
        snapshot.setBoolean("Success", ModePluginBridge.successful(response));
        snapshot.setString("Message", ModePluginBridge.message(response));
        RebornAddonNetwork.sendJutsuAdminSnapshot(player, snapshot);
    }

    private void handleChakraControlGuiAction(EntityPlayerMP player, int action,
                                              String property, String value) {
        String[] response = ChakraControlConfigurationService.INSTANCE.handleAdminAction(
                player, action, property, value);
        NBTTagCompound snapshot = snapshotTag(response, player,
                player == null ? "" : player.getName(), "chakra-control");
        snapshot.setBoolean("Success", GameplaySystemsPluginBridge.successful(response));
        snapshot.setString("Message", GameplaySystemsPluginBridge.message(response));
        RebornAddonNetwork.sendJutsuAdminSnapshot(player, snapshot);
    }

    private void handleRankedGuiAction(EntityPlayerMP player, int action, String id,
                                       String property, String value) {
        String[] response = RankedSystem.seasonManager == null
                ? new String[] {"false", "Ranked season management is unavailable.", ""}
                : RankedSystem.seasonManager.handleAdminAction(player, action, id, property, value);
        NBTTagCompound snapshot = snapshotTag(response, player, player.getName(), "ranked");
        snapshot.setBoolean("Success", RankedPluginBridge.successful(response));
        snapshot.setString("Message", RankedPluginBridge.message(response));
        RebornAddonNetwork.sendJutsuAdminSnapshot(player, snapshot);
    }

    private void handleStoreGuiAction(EntityPlayerMP player, int action, String id,
                                      String property, String value) {
        String[] response;
        String quotaTarget = player.getName();
        if (!StoreConfigurationService.INSTANCE.canAdmin(player)) {
            response = new String[] {"false", "You do not have permission to manage the store.", ""};
        } else if (action == STORE_UPDATE) {
            StoreCatalog.Entry entry = StoreCatalog.find(id);
            if (entry == null || !("price".equals(property) || "purchasable".equals(property))) {
                response = new String[] {"false", "That store setting is not available.", ""};
            } else if ("price".equals(property) && !validPrice(value)) {
                response = new String[] {"false", "Price must be a whole number from 0 to 1,000,000,000.", ""};
            } else if ("purchasable".equals(property)
                    && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                response = new String[] {"false", "Purchasable must be true or false.", ""};
            } else {
                response = StoreConfigurationService.INSTANCE.update(player, entry.key(), property, value);
            }
        } else if (action == STORE_RESET) {
            StoreCatalog.Entry entry = StoreCatalog.find(id);
            if (entry == null || !("price".equals(property) || "purchasable".equals(property)
                    || "all".equals(property))) {
                response = new String[] {"false", "That store reset is not available.", ""};
            } else {
                response = StoreConfigurationService.INSTANCE.resetSetting(player, entry.key(), property);
            }
        } else if (action == STORE_RESET_ALL) {
            response = StoreConfigurationService.INSTANCE.resetAllSettings(player);
        } else if (action == STORE_RELOAD) {
            response = StoreConfigurationService.INSTANCE.reload(player);
        } else if (action == STORE_SCOPE_UPDATE) {
            if (StoreCatalog.findScope(id) == null
                    || !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                response = new String[] {"false", "That store section setting is not available.", ""};
            } else {
                response = StoreConfigurationService.INSTANCE.updateScope(player, id,
                        Boolean.parseBoolean(value));
            }
        } else if (action == STORE_SCOPE_RESET) {
            response = StoreConfigurationService.INSTANCE.resetScope(player, id);
        } else if (action == QUOTA_LOAD) {
            quotaTarget = id;
            response = new String[] {"true", "Loaded purchase limits for " + id + ".", ""};
        } else if (action == QUOTA_SET || action == QUOTA_ADD || action == QUOTA_RESET) {
            quotaTarget = id;
            StoreQuotaService.Result result;
            if (action == QUOTA_RESET) {
                result = StoreConfigurationService.INSTANCE.resetQuota(player, id, property);
            } else {
                Integer number = integer(value);
                if (number == null) {
                    result = StoreQuotaService.Result.failure("The purchase count must be a whole number.");
                } else if (action == QUOTA_SET) {
                    result = StoreConfigurationService.INSTANCE.setQuota(player, id, property,
                            number.intValue());
                } else {
                    result = StoreConfigurationService.INSTANCE.adjustQuota(player, id, property,
                            number.intValue());
                }
            }
            response = new String[] {Boolean.toString(result.success()), result.message(), ""};
            if (result.success()) StoreConfigurationService.INSTANCE.refreshPlayers(player.getServer());
        } else {
            response = new String[] {"false", "Unknown store administration action.", ""};
        }

        String storeTab = action == QUOTA_LOAD || action == QUOTA_SET || action == QUOTA_ADD
                || action == QUOTA_RESET ? "limits"
                : action == STORE_SCOPE_UPDATE || action == STORE_SCOPE_RESET
                ? "sections" : "store";
        NBTTagCompound snapshot = snapshotTag(response, player, quotaTarget, storeTab);
        if (action == QUOTA_LOAD) {
            NBTTagCompound store = snapshot.getCompoundTag("Store");
            if (!store.getBoolean("QuotaAvailable")) {
                response = new String[] {"false", store.getString("QuotaMessage"), ""};
            }
        }
        snapshot.setBoolean("Success", Boolean.parseBoolean(response[0]));
        snapshot.setString("Message", StorePluginBridge.message(response));
        RebornAddonNetwork.sendJutsuAdminSnapshot(player, snapshot);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        if (event.getEntity() instanceof EntityPlayerMP) {
            JutsuCooldownResetData data = JutsuCooldownResetData.get(
                    ((EntityPlayerMP) event.getEntity()).getServer());
            if (data != null) data.apply((EntityPlayerMP) event.getEntity());
        }
        JutsuDefinition definition = JutsuRuntimeHooks.activeDefinition();
        if (definition != null) {
            event.getEntity().getEntityData().setString(ENTITY_JUTSU_KEY, definition.id());
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }
        JutsuDefinition definition = JutsuRuntimeHooks.activeDefinition();
        if (definition == null) {
            definition = taggedDefinition(event.getSource().getImmediateSource());
        }
        if (definition == null) {
            definition = taggedDefinition(event.getSource().getTrueSource());
        }
        if (definition == null) {
            return;
        }

        effectContexts.put(Integer.valueOf(event.getEntityLiving().getEntityId()),
                new EffectContext(definition, event.getEntityLiving().world.getTotalWorldTime() + 2L,
                        System.currentTimeMillis() + 1000L));

        String property = event.getSource().isExplosion()
                && JutsuSettingsCache.INSTANCE.override(definition, "explosion-damage") != null
                ? "explosion-damage" : "damage";
        String configured = JutsuSettingsCache.INSTANCE.override(definition, property);
        if (configured != null) {
            event.setAmount((float) JutsuSettingsCache.INSTANCE.decimal(definition, property,
                    event.getAmount()));
        }
        applyAdditionalEffects(event.getEntityLiving(), definition);
    }

    @SubscribeEvent
    public void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        if (event.getEntityLiving().world.isRemote || Boolean.TRUE.equals(replacingEffect.get())) return;
        EffectContext context = effectContext(event.getEntityLiving());
        JutsuDefinition definition = context == null
                ? JutsuRuntimeHooks.activeDefinition() : context.definition;
        PotionEffect effect = event.getPotionEffect();
        if (definition != null && effect != null && effect.getPotion().isBadEffect()
                && !effectAllowed(definition, effect.getPotion())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onPotionAdded(PotionEvent.PotionAddedEvent event) {
        if (event.getEntityLiving().world.isRemote || Boolean.TRUE.equals(replacingEffect.get())) return;
        EffectContext context = effectContext(event.getEntityLiving());
        JutsuDefinition definition = context == null
                ? JutsuRuntimeHooks.activeDefinition() : context.definition;
        PotionEffect added = event.getPotionEffect();
        if (definition == null || added == null || !added.getPotion().isBadEffect()) return;

        double durationMultiplier = JutsuSettingsCache.INSTANCE.decimal(
                definition, "effect-duration-multiplier", 1.0D);
        int amplifierAdjustment = JutsuSettingsCache.INSTANCE.integer(
                definition, "effect-amplifier-adjustment", 0);
        JutsuEffectRules.Rule rule = effectRule(definition, added.getPotion());
        if (rule != null) {
            durationMultiplier *= rule.durationMultiplier();
            amplifierAdjustment += rule.amplifierAdjustment();
        }
        int duration = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                Math.round(added.getDuration() * durationMultiplier)));
        int amplifier = Math.max(0, Math.min(255, added.getAmplifier() + amplifierAdjustment));
        if (duration == added.getDuration() && amplifier == added.getAmplifier()) return;

        replacingEffect.set(Boolean.TRUE);
        try {
            event.getEntityLiving().removePotionEffect(added.getPotion());
            PotionEffect replacement = new PotionEffect(added.getPotion(), duration, amplifier,
                    added.getIsAmbient(), added.doesShowParticles());
            if (event.getOldPotionEffect() != null) {
                PotionEffect merged = new PotionEffect(event.getOldPotionEffect());
                merged.combine(replacement);
                replacement = merged;
            }
            event.getEntityLiving().addPotionEffect(replacement);
        } finally {
            replacingEffect.remove();
        }
    }

    private void applyAdditionalEffects(net.minecraft.entity.EntityLivingBase target,
                                        JutsuDefinition definition) {
        String configured = JutsuSettingsCache.INSTANCE.value(definition, "additional-negative-effects", "");
        if (configured.isEmpty()) return;
        for (String entry : configured.split(",")) {
            String[] parts = entry.trim().split("\\|", -1);
            if (parts.length != 3) continue;
            try {
                Potion potion = ForgeRegistries.POTIONS.getValue(new ResourceLocation(parts[0].trim()));
                int amplifier = Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
                long ticks = JutsuValueParser.parseEffectDuration(parts[2]);
                if (potion != null && potion.isBadEffect() && ticks > 0L
                        && effectAllowed(definition, potion)) {
                    target.addPotionEffect(new PotionEffect(potion,
                            (int) Math.min(Integer.MAX_VALUE, ticks), amplifier));
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static boolean effectAllowed(JutsuDefinition definition, Potion potion) {
        JutsuEffectRules.Rule rule = effectRule(definition, potion);
        if (rule != null && rule.state() != JutsuEffectRules.INHERIT) {
            return rule.state() == JutsuEffectRules.ENABLED;
        }
        return JutsuSettingsCache.INSTANCE.enabled(definition, "negative-effects-enabled", true);
    }

    private static JutsuEffectRules.Rule effectRule(JutsuDefinition definition, Potion potion) {
        ResourceLocation id = potion == null ? null : potion.getRegistryName();
        if (id == null) return null;
        return JutsuSettingsCache.INSTANCE.effectRule(definition, id.toString());
    }

    private EffectContext effectContext(net.minecraft.entity.EntityLivingBase entity) {
        EffectContext context = effectContexts.get(Integer.valueOf(entity.getEntityId()));
        if (context != null && context.expiresAt >= entity.world.getTotalWorldTime()) return context;
        effectContexts.remove(Integer.valueOf(entity.getEntityId()));
        return null;
    }

    private void pruneEffectContexts() {
        if (effectContexts.isEmpty()) return;
        Iterator<Map.Entry<Integer, EffectContext>> iterator = effectContexts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, EffectContext> entry = iterator.next();
            if (entry.getValue().expiresAtMillis < System.currentTimeMillis()) iterator.remove();
        }
    }

    private static JutsuDefinition taggedDefinition(Entity entity) {
        if (entity == null || !entity.getEntityData().hasKey(ENTITY_JUTSU_KEY)) {
            return null;
        }
        return JutsuRegistry.INSTANCE.get(entity.getEntityData().getString(ENTITY_JUTSU_KEY));
    }

    private static String[] catalogDescriptors() {
        List<JutsuDefinition> definitions = JutsuRegistry.INSTANCE.all();
        String[] descriptors = new String[definitions.size()];
        for (int i = 0; i < definitions.size(); i++) {
            descriptors[i] = definitions.get(i).descriptor();
        }
        return descriptors;
    }

    private static boolean publishCatalog() {
        if (!JutsuPluginBridge.isAvailable()) {
            JutsuSettingsCache.INSTANCE.clear();
            return false;
        }
        String[] overrides = JutsuPluginBridge.registerCatalog(catalogDescriptors());
        if (overrides == null) {
            JutsuSettingsCache.INSTANCE.clear();
            return false;
        }
        JutsuSettingsCache.INSTANCE.replace(overrides);
        return true;
    }

    private static NBTTagCompound snapshotTag(String[] response, EntityPlayerMP player,
                                               String quotaTarget, String tab) {
        NBTTagCompound data = new NBTTagCompound();
        String selectedTab = normalizeAdminTab(tab);
        boolean success = JutsuPluginBridge.successful(response);
        data.setBoolean("Success", success);
        data.setString("Message", JutsuPluginBridge.message(response));
        data.setBoolean("Partial", true);
        data.setString("OpenTab", selectedTab);
        if ("jutsus".equals(selectedTab)) {
            NBTTagList records = new NBTTagList();
            if (success && response != null) {
                for (int i = 2; i < response.length; i++) {
                    records.appendTag(new NBTTagString(response[i]));
                }
            }
            data.setTag("Records", records);
        } else if ("store".equals(selectedTab) || "sections".equals(selectedTab)
                || "limits".equals(selectedTab)) {
            data.setTag("Store", StoreConfigurationService.INSTANCE.adminSnapshot(player, quotaTarget));
        } else if ("modes".equals(selectedTab)) {
            data.setTag("ModeRecords", ModeConfigurationService.INSTANCE.adminRecords(player));
        } else if ("ranked".equals(selectedTab)) {
            data.setTag("Ranked", RankedSystem.seasonManager == null
                    ? new NBTTagCompound() : RankedSystem.seasonManager.adminSnapshot(player));
        } else if ("chakra-control".equals(selectedTab)) {
            data.setTag("ChakraControl",
                    ChakraControlConfigurationService.INSTANCE.adminSnapshot(player));
        }
        return data;
    }

    private static String normalizeAdminTab(String tab) {
        if ("store".equals(tab) || "sections".equals(tab) || "limits".equals(tab)
                || "quests".equals(tab) || "modes".equals(tab) || "ranked".equals(tab)
                || "ranks".equals(tab) || "cooldowns".equals(tab)
                || "chakra-control".equals(tab)) return tab;
        return "jutsus";
    }

    private static boolean validPrice(String value) {
        Integer parsed = integer(value);
        return parsed != null && parsed.intValue() >= 0 && parsed.intValue() <= 1000000000;
    }

    private static Integer integer(String value) {
        try {
            return Integer.valueOf(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class EffectContext {
        private final JutsuDefinition definition;
        private final long expiresAt;
        private final long expiresAtMillis;

        private EffectContext(JutsuDefinition definition, long expiresAt, long expiresAtMillis) {
            this.definition = definition;
            this.expiresAt = expiresAt;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
