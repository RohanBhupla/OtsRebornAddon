package net.rebornaddon.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.gameplay.combat.CombatAnalyticsService;
import net.rebornaddon.gameplay.market.MarketplaceService;
import net.rebornaddon.gameplay.codex.ShinobiCodexService;
import net.rebornaddon.mount.MountAdministrationService;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.exam.ExamService;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class GameplaySystemsService {
    public static final GameplaySystemsService INSTANCE = new GameplaySystemsService();
    private static final Set<String> SECTIONS = new HashSet<String>(Arrays.asList(
            "codex", "training", "reputation", "missions", "conflicts", "marketplace", "spectating",
            "moderation", "mounts"));
    private final ConcurrentHashMap<String, Long> requests = new ConcurrentHashMap<String, Long>();
    private final ConcurrentHashMap<String, CachedSnapshot> snapshots = new ConcurrentHashMap<String, CachedSnapshot>();
    private final Set<String> pending = java.util.Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
    private volatile MinecraftServer server;
    private volatile ExecutorService worker;
    private volatile long mountPolicyLoadedAt;

    private GameplaySystemsService() {
    }

    public synchronized void initialize(MinecraftServer server) {
        reset();
        this.server = server;
        worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "RebornAddon Gameplay Worker");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void reset() {
        server = null;
        if (worker != null) worker.shutdownNow();
        worker = null;
        snapshots.clear();
        pending.clear();
        requests.clear();
        mountPolicyLoadedAt = 0L;
        MountAdministrationService.INSTANCE.resetScalePolicy();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        MountAdministrationService.INSTANCE.applyScalePolicy(player);
        loadMountRuntimePolicy();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) return;
        String player = event.player.getUniqueID().toString();
        removePlayerEntries(requests, player);
        removePlayerEntries(snapshots, player);
        for (String key : pending.toArray(new String[0])) {
            if (key.startsWith("snapshot:" + player + ':')
                    || key.startsWith("action:" + player + ':')) pending.remove(key);
        }
    }

    public void handle(EntityPlayerMP player, String rawSection, String rawAction, String payload) {
        if (player == null) return;
        String section = clean(rawSection, 32).toLowerCase(Locale.ROOT);
        String action = clean(rawAction, 64).toLowerCase(Locale.ROOT);
        if (!SECTIONS.contains(section)) return;
        if (("moderation".equals(section) || "mounts".equals(section))
                && !player.canUseCommand(2, "rebornadmin")) {
            status(player, "You do not have permission to use that admin section.", true);
            return;
        }
        if (payload == null || payload.length() > 262144) {
            status(player, "That request was too large.", true);
            return;
        }
        if ("snapshot".equals(action)) {
            if (rateLimited(player.getUniqueID(), section)) return;
            sendSnapshot(player, section);
            return;
        }
        if ("training".equals(section) && "reset-recap".equals(action)) {
            CombatAnalyticsService.INSTANCE.reset(player.getUniqueID());
            sendSnapshot(player, section);
            return;
        }
        if ("spectating".equals(section) && RankedSystem.isReady()) {
            if ("ranked.leave".equals(action) || "ranked.spectate".equals(action)) {
                boolean result;
                if ("ranked.leave".equals(action)) {
                    result = RankedSystem.matchManager.leaveSpectator(player);
                } else if (ExamService.INSTANCE.isInActiveMatch(player.getUniqueID())) {
                    result = false;
                } else {
                    ExamService.INSTANCE.leaveSpectator(player);
                    result = RankedSystem.matchManager.spectate(player, jsonString(payload, "id"));
                }
                status(player, result ? "Spectator state updated."
                        : "That match is no longer available.", !result);
                sendSnapshot(player, section);
                return;
            }
        }
        if ("spectating".equals(section)
                && ("exam.leave".equals(action) || "exam.spectate".equals(action))) {
            boolean result = "exam.leave".equals(action)
                    ? ExamService.INSTANCE.leaveSpectator(player)
                    : ExamService.INSTANCE.spectate(player, jsonString(payload, "id"));
            status(player, result ? "Spectator state updated."
                    : "That exam match is no longer available.", !result);
            sendSnapshot(player, section);
            return;
        }
        if ("marketplace".equals(section)) {
            MarketplaceService.Result result = MarketplaceService.INSTANCE.transact(player, action, payload);
            status(player, result.message(), !result.success());
            sendSnapshot(player, section);
            return;
        }
        if ("moderation".equals(section)) {
            if (!GameplaySystemsPluginBridge.isAvailable()) {
                status(player, "The hosted moderation service is not enabled on this server yet.", true);
                RebornAddonNetwork.sendGameplaySnapshot(player, section,
                        unavailable(section, "The hosted moderation service needs its plugin update."));
                return;
            }
            submitAction(player, section, action, payload, true);
            return;
        }
        if ("mounts".equals(section)) {
            if (!GameplaySystemsPluginBridge.isAvailable()) {
                status(player, "The hosted mount administration service is not enabled yet.", true);
                RebornAddonNetwork.sendGameplaySnapshot(player, section,
                        unavailable(section, "The hosted plugin needs its mount administration update."));
                return;
            }
            if (!"mount.grant".equals(action) && !"mount.revoke".equals(action)
                    && !"mount.scale.set".equals(action)) {
                status(player, "That mount administration action is not supported.", true);
                return;
            }
            submitMountAction(player, action, payload);
            return;
        }
        if (!GameplaySystemsPluginBridge.isAvailable()) {
            status(player, "This hosted gameplay feature is not enabled on the server yet.", true);
            sendSnapshot(player, section);
            return;
        }
        submitAction(player, section, action, payload);
    }

    public void sendSnapshot(EntityPlayerMP player, String section) {
        String json;
        if ("training".equals(section)) {
            json = CombatAnalyticsService.INSTANCE.snapshot(player);
        } else if ("codex".equals(section)) {
            json = ShinobiCodexService.INSTANCE.snapshot(player);
        } else if ("spectating".equals(section) && RankedSystem.isReady()) {
            JsonObject root = RankedSystem.matchManager.spectatorSnapshot(player);
            mergeSpectating(root, ExamService.INSTANCE.spectatorSnapshot(player).toString());
            CachedSnapshot cached = snapshots.get(cacheKey(player.getUniqueID(), section));
            if (cached != null) mergeSpectating(root, cached.json);
            json = root.toString();
            loadPluginSnapshot(player, section);
        } else if ("mounts".equals(section)) {
            CachedSnapshot cached = snapshots.get(cacheKey(player.getUniqueID(), section));
            boolean pluginAvailable = GameplaySystemsPluginBridge.isAvailable();
            String base = cached != null ? cached.json : pluginAvailable
                    ? loading(section)
                    : unavailable(section, "Private mount changes require the hosted server plugin.");
            json = MountAdministrationService.INSTANCE.augmentSnapshot(player, base);
            if (pluginAvailable) loadPluginSnapshot(player, section);
        } else if (GameplaySystemsPluginBridge.isAvailable()) {
            CachedSnapshot cached = snapshots.get(cacheKey(player.getUniqueID(), section));
            json = cached == null ? loading(section) : cached.json;
            loadPluginSnapshot(player, section);
        } else {
            json = unavailable(section, "This section becomes available on the hosted server after its plugin update.");
        }
        RebornAddonNetwork.sendGameplaySnapshot(player, section, json);
    }

    public void recordEvent(String eventId, String type, EntityPlayerMP player,
                            String context, String payload) {
        if (GameplaySystemsPluginBridge.isAvailable()) {
            GameplaySystemsPluginBridge.record(eventId, type, player, context, payload);
        }
    }

    private static JsonObject base(String title, boolean available, String message) {
        JsonObject root = new JsonObject();
        root.addProperty("available", available);
        root.addProperty("title", title);
        root.addProperty("message", message);
        root.add("entries", new JsonArray());
        return root;
    }

    private static String unavailable(String section, String message) {
        return base(title(section), false, message).toString();
    }

    private static String loading(String section) {
        return base(title(section), true, "Loading hosted server data...").toString();
    }

    private static String title(String section) {
        if (section == null || section.isEmpty()) return "Gameplay";
        return Character.toUpperCase(section.charAt(0)) + section.substring(1);
    }

    private static void entry(JsonArray entries, String id, String title, String subtitle, String detail) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("title", title);
        value.addProperty("subtitle", subtitle);
        value.addProperty("detail", detail);
        entries.add(value);
    }

    private boolean rateLimited(UUID player, String section) {
        long now = System.currentTimeMillis();
        String key = player + ":" + section;
        Long previous = requests.put(key, Long.valueOf(now));
        return previous != null && now - previous.longValue() < 750L;
    }

    private static void status(EntityPlayerMP player, String message, boolean error) {
        player.sendStatusMessage(new TextComponentString((error ? TextFormatting.RED : TextFormatting.GREEN)
                + clean(message, 300)), true);
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private static String jsonString(String json, String key) {
        try {
            JsonObject value = new JsonParser().parse(json).getAsJsonObject();
            return value.has(key) ? clean(value.get(key).getAsString(), 96) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void mergeSpectating(JsonObject target, String json) {
        try {
            JsonObject source = new JsonParser().parse(json).getAsJsonObject();
            JsonArray combined = target.has("entries") && target.get("entries").isJsonArray()
                    ? target.getAsJsonArray("entries") : new JsonArray();
            JsonArray pluginEntries = source.has("entries") && source.get("entries").isJsonArray()
                    ? source.getAsJsonArray("entries") : new JsonArray();
            for (int i = 0; i < pluginEntries.size() && combined.size() < 128; i++) {
                combined.add(pluginEntries.get(i));
            }
            target.add("entries", combined);
            if (source.has("history") && source.get("history").isJsonArray()) {
                target.add("history", source.getAsJsonArray("history"));
            }
            if (source.has("message") && combined.size() == 0) {
                target.addProperty("message", source.get("message").getAsString());
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadPluginSnapshot(EntityPlayerMP player, final String section) {
        final ExecutorService currentWorker = worker;
        final MinecraftServer currentServer = server;
        if (player == null || currentWorker == null || currentWorker.isShutdown()
                || currentServer == null || !GameplaySystemsPluginBridge.isAvailable()) return;
        final UUID playerId = player.getUniqueID();
        final String key = cacheKey(playerId, section);
        CachedSnapshot cached = snapshots.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < 10000L) return;
        if (!pending.add("snapshot:" + key)) return;
        final Object sender = GameplaySystemsPluginBridge.resolveSender(player);
        currentWorker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = GameplaySystemsPluginBridge.snapshot(sender, section);
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove("snapshot:" + key);
                        if (server != currentServer) return;
                        EntityPlayerMP current = currentServer.getPlayerList().getPlayerByUUID(playerId);
                        if (current == null) return;
                        if (GameplaySystemsPluginBridge.successful(response)) {
                            String responsePayload = GameplaySystemsPluginBridge.payload(response);
                            if ("mounts".equals(section)) {
                                responsePayload = MountAdministrationService.INSTANCE
                                        .augmentSnapshot(current, responsePayload);
                            }
                            snapshots.put(key, new CachedSnapshot(
                                    responsePayload, System.currentTimeMillis()));
                            sendSnapshot(current, section);
                        } else {
                            String failurePayload = unavailable(section,
                                    GameplaySystemsPluginBridge.message(response));
                            if ("mounts".equals(section)) {
                                failurePayload = MountAdministrationService.INSTANCE
                                        .augmentSnapshot(current, failurePayload);
                            }
                            snapshots.put(key, new CachedSnapshot(
                                    failurePayload, System.currentTimeMillis()));
                            RebornAddonNetwork.sendGameplaySnapshot(current, section,
                                    failurePayload);
                        }
                    }
                });
            }
        });
    }

    private void loadMountRuntimePolicy() {
        final ExecutorService currentWorker = worker;
        final MinecraftServer currentServer = server;
        long now = System.currentTimeMillis();
        if (currentWorker == null || currentWorker.isShutdown() || currentServer == null
                || !GameplaySystemsPluginBridge.isAvailable()
                || now - mountPolicyLoadedAt < 60000L
                || !pending.add("runtime:mounts")) return;
        try {
            currentWorker.execute(new Runnable() {
                @Override
                public void run() {
                    final String[] response = GameplaySystemsPluginBridge.runtimeSnapshot("mounts");
                    currentServer.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            pending.remove("runtime:mounts");
                            if (server != currentServer) return;
                            mountPolicyLoadedAt = System.currentTimeMillis();
                            if (GameplaySystemsPluginBridge.successful(response)) {
                                MountAdministrationService.INSTANCE.acceptRuntimePolicy(
                                        currentServer, GameplaySystemsPluginBridge.payload(response));
                            }
                            for (EntityPlayerMP player : currentServer.getPlayerList().getPlayers()) {
                                MountAdministrationService.INSTANCE.applyScalePolicy(player);
                            }
                        }
                    });
                }
            });
        } catch (RuntimeException stopped) {
            pending.remove("runtime:mounts");
        }
    }

    private void submitAction(EntityPlayerMP player, final String section,
                              final String action, final String payload) {
        submitAction(player, section, action, payload, false);
    }

    private void submitAction(EntityPlayerMP player, final String section,
                              final String action, final String payload,
                              final boolean acceptResponseSnapshot) {
        final ExecutorService currentWorker = worker;
        final MinecraftServer currentServer = server;
        if (currentWorker == null || currentWorker.isShutdown() || currentServer == null) {
            status(player, "The hosted gameplay worker is not running.", true);
            return;
        }
        final UUID playerId = player.getUniqueID();
        final String pendingKey = "action:" + playerId + ':' + section;
        if (!pending.add(pendingKey)) {
            status(player, "That request is already being processed.", true);
            return;
        }
        final Object sender = GameplaySystemsPluginBridge.resolveSender(player);
        currentWorker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = GameplaySystemsPluginBridge.action(sender, section, action, payload);
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(pendingKey);
                        if (server != currentServer) return;
                        EntityPlayerMP current = currentServer.getPlayerList().getPlayerByUUID(playerId);
                        if (current == null) return;
                        boolean success = GameplaySystemsPluginBridge.successful(response);
                        if (!(success && "moderation".equals(section)
                                && "moderation.query".equals(action))) {
                            status(current, GameplaySystemsPluginBridge.message(response), !success);
                        }
                        String responsePayload = GameplaySystemsPluginBridge.payload(response);
                        if (success && acceptResponseSnapshot && validSnapshot(responsePayload)) {
                            snapshots.put(cacheKey(playerId, section), new CachedSnapshot(
                                    responsePayload, System.currentTimeMillis()));
                            RebornAddonNetwork.sendGameplaySnapshot(current, section, responsePayload);
                        } else {
                            snapshots.remove(cacheKey(playerId, section));
                            sendSnapshot(current, section);
                        }
                    }
                });
            }
        });
    }

    private void submitMountAction(EntityPlayerMP player, final String action,
                                   final String payload) {
        final ExecutorService currentWorker = worker;
        final MinecraftServer currentServer = server;
        if (currentWorker == null || currentWorker.isShutdown() || currentServer == null) {
            status(player, "The hosted gameplay worker is not running.", true);
            return;
        }
        final UUID playerId = player.getUniqueID();
        final String section = "mounts";
        final String pendingKey = "action:" + playerId + ':' + section;
        if (!pending.add(pendingKey)) {
            status(player, "That mount request is already being processed.", true);
            return;
        }
        final Object sender = GameplaySystemsPluginBridge.resolveSender(player);
        currentWorker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = GameplaySystemsPluginBridge
                        .action(sender, section, action, payload);
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (server != currentServer) {
                            pending.remove(pendingKey);
                            return;
                        }
                        EntityPlayerMP current = currentServer.getPlayerList().getPlayerByUUID(playerId);
                        if (current == null) {
                            pending.remove(pendingKey);
                            return;
                        }
                        if (!GameplaySystemsPluginBridge.successful(response)) {
                            pending.remove(pendingKey);
                            status(current, GameplaySystemsPluginBridge.message(response), true);
                            snapshots.remove(cacheKey(playerId, section));
                            sendSnapshot(current, section);
                            return;
                        }
                        final MountAdministrationService.ApplyResult result =
                                MountAdministrationService.INSTANCE.applyAuthorized(current, action,
                                        GameplaySystemsPluginBridge.payload(response));
                        status(current, result.message(), !result.success());
                        final String auditPayload = result.auditPayload().isEmpty()
                                ? mountResultAudit(result, current, action) : result.auditPayload();
                        if (auditPayload.isEmpty()) {
                            pending.remove(pendingKey);
                            snapshots.remove(cacheKey(playerId, section));
                            sendSnapshot(current, section);
                            return;
                        }
                        try {
                            currentWorker.execute(new Runnable() {
                                @Override
                                public void run() {
                                    GameplaySystemsPluginBridge.action(sender, section,
                                            "mount.result", auditPayload);
                                    currentServer.addScheduledTask(new Runnable() {
                                        @Override
                                        public void run() {
                                            pending.remove(pendingKey);
                                            EntityPlayerMP refreshed = currentServer.getPlayerList()
                                                    .getPlayerByUUID(playerId);
                                            if (server == currentServer && refreshed != null) {
                                                snapshots.remove(cacheKey(playerId, section));
                                                sendSnapshot(refreshed, section);
                                            }
                                        }
                                    });
                                }
                            });
                        } catch (RuntimeException stopped) {
                            pending.remove(pendingKey);
                            snapshots.remove(cacheKey(playerId, section));
                            sendSnapshot(current, section);
                        }
                    }
                });
            }
        });
    }

    private static String mountResultAudit(MountAdministrationService.ApplyResult result,
                                           EntityPlayerMP admin, String action) {
        if (result == null || result.operationId().isEmpty() || admin == null) return "";
        JsonObject audit = new JsonObject();
        audit.addProperty("operationId", result.operationId());
        audit.addProperty("success", result.success());
        audit.addProperty("message", clean(result.message(), 300));
        audit.addProperty("action", action);
        audit.addProperty("requestedByUuid", admin.getUniqueID().toString());
        audit.addProperty("requestedByName", admin.getName());
        audit.addProperty("completedAtEpochMs", System.currentTimeMillis());
        return audit.toString();
    }

    private static boolean validSnapshot(String json) {
        if (json == null || json.isEmpty() || json.length() > 4194304) return false;
        try {
            return new JsonParser().parse(json).isJsonObject();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String cacheKey(UUID player, String section) {
        return player + ":" + section;
    }

    private static void removePlayerEntries(ConcurrentHashMap<String, ?> values, String player) {
        for (String key : values.keySet()) {
            if (key.startsWith(player + ':')) values.remove(key);
        }
    }

    private static final class CachedSnapshot {
        private final String json;
        private final long loadedAt;

        private CachedSnapshot(String json, long loadedAt) {
            this.json = json == null || json.isEmpty() ? "{}" : json;
            this.loadedAt = loadedAt;
        }
    }
}
