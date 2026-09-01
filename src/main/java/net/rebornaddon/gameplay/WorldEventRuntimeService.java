package net.rebornaddon.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.region.RegionPolicyService;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageSelectionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class WorldEventRuntimeService {
    public static final WorldEventRuntimeService INSTANCE = new WorldEventRuntimeService();
    private static final int REFRESH_TICKS = 100;
    private static final int MAX_REGIONS = 256;
    private static final int MAX_CAPTURE_POINTS = 64;
    private final List<CapturePoint> capturePoints = new ArrayList<CapturePoint>();
    private final Map<String, String> captureStates = new HashMap<String, String>();
    private final Set<String> announcements = new HashSet<String>();
    private MinecraftServer server;
    private ExecutorService worker;
    private volatile boolean refreshInFlight;
    private int ticks;
    private long revision = Long.MIN_VALUE;

    private WorldEventRuntimeService() {
    }

    public void initialize(MinecraftServer server) {
        reset();
        this.server = server;
        worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "RebornAddon World Event Worker");
                thread.setDaemon(true);
                return thread;
            }
        });
        refresh();
    }

    public void reset() {
        server = null;
        refreshInFlight = false;
        if (worker != null) worker.shutdownNow();
        worker = null;
        ticks = 0;
        revision = Long.MIN_VALUE;
        capturePoints.clear();
        captureStates.clear();
        announcements.clear();
        RegionPolicyService.INSTANCE.replaceTransient("gameplay-events", null);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        ticks++;
        if (ticks % 20 == 0) updateCapturePoints();
        if (ticks % REFRESH_TICKS == 0) refresh();
    }

    private void refresh() {
        if (!GameplaySystemsPluginBridge.isAvailable()) {
            if (revision != Long.MIN_VALUE) resetRuntime();
            return;
        }
        final ExecutorService currentWorker = worker;
        final MinecraftServer currentServer = server;
        if (refreshInFlight || currentWorker == null || currentWorker.isShutdown() || currentServer == null) return;
        refreshInFlight = true;
        currentWorker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = GameplaySystemsPluginBridge.runtimeSnapshot("conflicts");
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        refreshInFlight = false;
                        if (server == currentServer && GameplaySystemsPluginBridge.successful(response)) {
                            applySnapshot(response);
                        }
                    }
                });
            }
        });
    }

    private void applySnapshot(String[] response) {
        try {
            JsonObject root = new JsonParser().parse(GameplaySystemsPluginBridge.payload(response))
                    .getAsJsonObject();
            long nextRevision = number(root, "revision", 0L);
            if (nextRevision == revision) return;
            List<RegionPolicyService.RegionDefinition> regions = parseRegions(array(root, "regions"));
            List<CapturePoint> points = parseCaptures(array(root, "capturePoints"));
            RegionPolicyService.INSTANCE.replaceTransient("gameplay-events", regions);
            capturePoints.clear();
            capturePoints.addAll(points);
            revision = nextRevision;
            announce(array(root, "announcements"));
        } catch (Throwable ignored) {
        }
    }

    private void resetRuntime() {
        revision = Long.MIN_VALUE;
        capturePoints.clear();
        captureStates.clear();
        RegionPolicyService.INSTANCE.replaceTransient("gameplay-events", null);
    }

    private void updateCapturePoints() {
        if (capturePoints.isEmpty() || server == null) return;
        for (CapturePoint point : capturePoints) {
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                if (player.dimension != point.dimension) continue;
                double dx = player.posX - point.x;
                double dy = player.posY - point.y;
                double dz = player.posZ - point.z;
                if (dx * dx + dy * dy + dz * dz > point.radius * point.radius) continue;
                Village village = VillageSelectionHandler.INSTANCE.getAssignedVillage(player);
                if (village == null) continue;
                Integer old = counts.get(village.group());
                counts.put(village.group(), Integer.valueOf(old == null ? 1 : old.intValue() + 1));
            }
            String state = counts.toString();
            if (state.equals(captureStates.get(point.id))) continue;
            captureStates.put(point.id, state);
            JsonObject payload = new JsonObject();
            payload.addProperty("dimension", point.dimension);
            payload.addProperty("x", point.x);
            payload.addProperty("y", point.y);
            payload.addProperty("z", point.z);
            JsonObject villages = new JsonObject();
            for (Map.Entry<String, Integer> count : counts.entrySet()) {
                villages.addProperty(count.getKey(), count.getValue());
            }
            payload.add("villages", villages);
            recordPresence(point.id + ':' + System.currentTimeMillis() / 1000L,
                    point.id, payload.toString());
        }
    }

    private void recordPresence(final String eventId, final String pointId, final String payload) {
        ExecutorService current = worker;
        if (current == null || current.isShutdown()) return;
        current.execute(new Runnable() {
            @Override
            public void run() {
                GameplaySystemsPluginBridge.record(eventId, "conflict.capture-presence",
                        null, pointId, payload);
            }
        });
    }

    private void announce(JsonArray values) {
        if (server == null) return;
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String id = string(value, "id", "");
            String message = string(value, "message", "");
            if (id.isEmpty() || message.isEmpty() || !announcements.add(id)) continue;
            server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GOLD + message));
        }
        if (announcements.size() > 256) announcements.clear();
    }

    private static List<RegionPolicyService.RegionDefinition> parseRegions(JsonArray values) {
        List<RegionPolicyService.RegionDefinition> result = new ArrayList<RegionPolicyService.RegionDefinition>();
        for (JsonElement element : values) {
            if (!element.isJsonObject() || result.size() >= MAX_REGIONS) break;
            JsonObject value = element.getAsJsonObject();
            RegionPolicyService.RegionDefinition region = new RegionPolicyService.RegionDefinition();
            region.id = "event-" + string(value, "id", "region");
            region.dimension = integer(value, "dimension", 0);
            region.minX = integer(value, "minX", 0);
            region.minY = integer(value, "minY", 0);
            region.minZ = integer(value, "minZ", 0);
            region.maxX = integer(value, "maxX", 0);
            region.maxY = integer(value, "maxY", 255);
            region.maxZ = integer(value, "maxZ", 0);
            region.priority = integer(value, "priority", 1000);
            JsonObject rules = object(value, "rules");
            for (Map.Entry<String, JsonElement> rule : rules.entrySet()) {
                if (rule.getValue().isJsonPrimitive()) {
                    region.rules.put(rule.getKey(), rule.getValue().getAsString());
                }
            }
            result.add(region);
        }
        return result;
    }

    private static List<CapturePoint> parseCaptures(JsonArray values) {
        List<CapturePoint> result = new ArrayList<CapturePoint>();
        for (JsonElement element : values) {
            if (!element.isJsonObject() || result.size() >= MAX_CAPTURE_POINTS) break;
            JsonObject value = element.getAsJsonObject();
            String id = string(value, "id", "");
            if (id.isEmpty()) continue;
            result.add(new CapturePoint(id, integer(value, "dimension", 0),
                    decimal(value, "x", 0.0D), decimal(value, "y", 64.0D),
                    decimal(value, "z", 0.0D), Math.max(2.0D,
                    Math.min(128.0D, decimal(value, "radius", 12.0D)))));
        }
        return result;
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try { return root.has(key) ? root.get(key).getAsInt() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static long number(JsonObject root, String key, long fallback) {
        try { return root.has(key) ? root.get(key).getAsLong() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static double decimal(JsonObject root, String key, double fallback) {
        try { return root.has(key) ? root.get(key).getAsDouble() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static final class CapturePoint {
        private final String id;
        private final int dimension;
        private final double x;
        private final double y;
        private final double z;
        private final double radius;

        private CapturePoint(String id, int dimension, double x, double y, double z, double radius) {
            this.id = id;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }
}
