package net.rebornaddon.chakra;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.gameplay.GameplaySystemsPluginBridge;
import net.rebornaddon.integration.PluginInvalidationBus;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.math.BigDecimal;
import java.util.Locale;

public final class ChakraControlConfigurationService {
    public static final ChakraControlConfigurationService INSTANCE =
            new ChakraControlConfigurationService();

    public static final int UPDATE = 6;
    public static final int RESET = 7;
    public static final int RELOAD = 8;

    public static final String ENABLED = "enabled";
    public static final String WATER_WALKING_ENABLED = "water-walking-enabled";
    public static final String WALL_CLIMBING_ENABLED = "wall-climbing-enabled";
    public static final String WALL_CLIMB_SPEED_MULTIPLIER = "wall-climb-speed-multiplier";

    private static final int RETRY_TICKS = 100;
    private static final int REFRESH_TICKS = 1200;
    private static final double MIN_WALL_CLIMB_MULTIPLIER = 0.1D;
    private static final double MAX_WALL_CLIMB_MULTIPLIER = 5.0D;

    private volatile boolean enabled = true;
    private volatile boolean waterWalkingEnabled = true;
    private volatile boolean wallClimbingEnabled = true;
    private volatile double wallClimbSpeedMultiplier = 1.0D;
    private boolean runtimeLoaded;
    private int pollTicks;
    private long invalidationRevision;

    private ChakraControlConfigurationService() {
    }

    public void initialize() {
        pollTicks = 0;
        runtimeLoaded = false;
        refreshRuntime();
    }

    public void reset() {
        enabled = true;
        waterWalkingEnabled = true;
        wallClimbingEnabled = true;
        wallClimbSpeedMultiplier = 1.0D;
        runtimeLoaded = false;
        pollTicks = 0;
        invalidationRevision = 0L;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long changed = PluginInvalidationBus.revision("chakra-control");
        int interval = runtimeLoaded ? REFRESH_TICKS : RETRY_TICKS;
        if (++pollTicks < interval && changed == invalidationRevision) return;
        pollTicks = 0;
        invalidationRevision = changed;
        refreshRuntime();
    }

    public boolean refreshRuntime() {
        String[] response = GameplaySystemsPluginBridge.runtimeSnapshot("chakra-control");
        if (!GameplaySystemsPluginBridge.successful(response)) {
            // An older hosted plugin can still serve every pre-existing section. Probe this
            // optional section at the low-frequency refresh interval until it is upgraded.
            runtimeLoaded = GameplaySystemsPluginBridge.isAvailable();
            return false;
        }
        JsonObject config = config(GameplaySystemsPluginBridge.payload(response));
        if (config == null) return false;
        if (apply(config)) syncAllPlayers();
        runtimeLoaded = true;
        return true;
    }

    public NBTTagCompound adminSnapshot(EntityPlayerMP player) {
        NBTTagCompound data = defaultsTag();
        if (player == null || !player.canUseCommand(2, "rebornadmin")) {
            data.setString("Message", "Operator access is required.");
            return data;
        }
        String[] response = GameplaySystemsPluginBridge.snapshot(player, "chakra-control");
        boolean success = GameplaySystemsPluginBridge.successful(response);
        data.setBoolean("Available", success);
        data.setBoolean("Success", success);
        data.setString("Message", GameplaySystemsPluginBridge.message(response));
        if (!success) return data;
        JsonObject config = config(GameplaySystemsPluginBridge.payload(response));
        if (config == null) {
            data.setBoolean("Available", false);
            data.setBoolean("Success", false);
            data.setString("Message", "The hosted plugin returned invalid chakra control settings.");
            return data;
        }
        write(data, config);
        return data;
    }

    public String[] handleAdminAction(EntityPlayerMP player, int action,
                                      String property, String value) {
        if (player == null || !player.canUseCommand(2, "rebornadmin")) {
            return failure("Operator access is required.");
        }
        String key = normalizeProperty(property);
        if (action != RELOAD && key.isEmpty()) {
            return failure("That chakra control setting is not available.");
        }

        String pluginAction;
        String requestedValue = "";
        JsonObject payload = new JsonObject();
        if (action == UPDATE) {
            String normalized;
            try {
                normalized = normalizeValue(key, value);
            } catch (IllegalArgumentException exception) {
                return failure(exception.getMessage());
            }
            pluginAction = "set";
            requestedValue = normalized;
            payload.addProperty("key", key);
            payload.addProperty("value", normalized);
        } else if (action == RESET) {
            pluginAction = "reset";
            payload.addProperty("key", key);
        } else if (action == RELOAD) {
            pluginAction = "reload";
        } else {
            return failure("Unknown chakra control administration action.");
        }

        String[] response = GameplaySystemsPluginBridge.action(player, "chakra-control",
                pluginAction, payload.toString());
        if (GameplaySystemsPluginBridge.successful(response)) {
            JsonObject accepted = config(GameplaySystemsPluginBridge.payload(response));
            if (accepted != null) {
                if (apply(accepted)) syncAllPlayers();
                runtimeLoaded = true;
            } else if (action == UPDATE) {
                if (applyProperty(key, requestedValue)) syncAllPlayers();
                runtimeLoaded = true;
            } else if (action == RESET) {
                if (applyProperty(key, defaultValue(key))) syncAllPlayers();
                runtimeLoaded = true;
            } else {
                refreshRuntime();
            }
        }
        return response;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean waterWalkingEnabled() {
        return enabled && waterWalkingEnabled;
    }

    public boolean wallClimbingEnabled() {
        return enabled && wallClimbingEnabled;
    }

    public double wallClimbSpeedMultiplier() {
        return wallClimbSpeedMultiplier;
    }

    public void applyClientRuntime(boolean masterEnabled, boolean waterEnabled,
                                   boolean wallEnabled, double wallSpeedMultiplier) {
        enabled = masterEnabled;
        waterWalkingEnabled = waterEnabled;
        wallClimbingEnabled = wallEnabled;
        wallClimbSpeedMultiplier = clampMultiplier(wallSpeedMultiplier, 1.0D);
    }

    static String normalizeValue(String property, String value) {
        if (ENABLED.equals(property) || WATER_WALKING_ENABLED.equals(property)
                || WALL_CLIMBING_ENABLED.equals(property)) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Choose Enabled or Disabled.");
            }
            return Boolean.toString(Boolean.parseBoolean(value));
        }
        if (WALL_CLIMB_SPEED_MULTIPLIER.equals(property)) {
            double parsed;
            try {
                parsed = Double.parseDouble(value == null ? "" : value.trim());
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("Wall climb speed must be a number from 0.1 to 5.");
            }
            if (!Double.isFinite(parsed) || parsed < MIN_WALL_CLIMB_MULTIPLIER
                    || parsed > MAX_WALL_CLIMB_MULTIPLIER) {
                throw new IllegalArgumentException("Wall climb speed must be a number from 0.1 to 5.");
            }
            return BigDecimal.valueOf(parsed).stripTrailingZeros().toPlainString();
        }
        throw new IllegalArgumentException("That chakra control setting is not available.");
    }

    private synchronized boolean apply(JsonObject config) {
        boolean nextEnabled = bool(config, "enabled", enabled);
        boolean nextWater = bool(config, "waterWalkingEnabled", waterWalkingEnabled);
        boolean nextWall = bool(config, "wallClimbingEnabled", wallClimbingEnabled);
        double nextMultiplier = number(config, "wallClimbSpeedMultiplier",
                wallClimbSpeedMultiplier);
        boolean changed = nextEnabled != enabled || nextWater != waterWalkingEnabled
                || nextWall != wallClimbingEnabled
                || Double.compare(nextMultiplier, wallClimbSpeedMultiplier) != 0;
        enabled = nextEnabled;
        waterWalkingEnabled = nextWater;
        wallClimbingEnabled = nextWall;
        wallClimbSpeedMultiplier = nextMultiplier;
        return changed;
    }

    private synchronized boolean applyProperty(String property, String value) {
        if (ENABLED.equals(property)) {
            boolean next = Boolean.parseBoolean(value);
            boolean changed = next != enabled;
            enabled = next;
            return changed;
        }
        if (WATER_WALKING_ENABLED.equals(property)) {
            boolean next = Boolean.parseBoolean(value);
            boolean changed = next != waterWalkingEnabled;
            waterWalkingEnabled = next;
            return changed;
        }
        if (WALL_CLIMBING_ENABLED.equals(property)) {
            boolean next = Boolean.parseBoolean(value);
            boolean changed = next != wallClimbingEnabled;
            wallClimbingEnabled = next;
            return changed;
        }
        if (WALL_CLIMB_SPEED_MULTIPLIER.equals(property)) {
            double next = clampMultiplier(Double.parseDouble(value), wallClimbSpeedMultiplier);
            boolean changed = Double.compare(next, wallClimbSpeedMultiplier) != 0;
            wallClimbSpeedMultiplier = next;
            return changed;
        }
        return false;
    }

    private static String defaultValue(String property) {
        return WALL_CLIMB_SPEED_MULTIPLIER.equals(property) ? "1" : "true";
    }

    private NBTTagCompound defaultsTag() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("Available", false);
        data.setBoolean("Success", false);
        data.setString("Message", "The hosted plugin needs the chakra control configuration update.");
        data.setBoolean("Enabled", enabled);
        data.setBoolean("WaterWalkingEnabled", waterWalkingEnabled);
        data.setBoolean("WallClimbingEnabled", wallClimbingEnabled);
        data.setDouble("WallClimbSpeedMultiplier", wallClimbSpeedMultiplier);
        return data;
    }

    private static void write(NBTTagCompound data, JsonObject config) {
        data.setBoolean("Enabled", bool(config, "enabled", true));
        data.setBoolean("WaterWalkingEnabled", bool(config, "waterWalkingEnabled", true));
        data.setBoolean("WallClimbingEnabled", bool(config, "wallClimbingEnabled", true));
        data.setDouble("WallClimbSpeedMultiplier",
                number(config, "wallClimbSpeedMultiplier", 1.0D));
    }

    private static JsonObject config(String payload) {
        if (payload == null || payload.trim().isEmpty()) return null;
        try {
            JsonObject root = new JsonParser().parse(payload).getAsJsonObject();
            return root.has("config") && root.get("config").isJsonObject()
                    ? root.getAsJsonObject("config") : root;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        try {
            double value = object.has(key) ? object.get(key).getAsDouble() : fallback;
            return clampMultiplier(value, fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double clampMultiplier(double value, double fallback) {
        return Double.isFinite(value) && value >= MIN_WALL_CLIMB_MULTIPLIER
                && value <= MAX_WALL_CLIMB_MULTIPLIER ? value : fallback;
    }

    private void syncAllPlayers() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            RebornAddonNetwork.sendChakraControlState(player,
                    net.rebornaddon.compat.ChakraControlCompatibility.isEnabled(player));
        }
    }

    private static String normalizeProperty(String value) {
        String property = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return ENABLED.equals(property) || WATER_WALKING_ENABLED.equals(property)
                || WALL_CLIMBING_ENABLED.equals(property)
                || WALL_CLIMB_SPEED_MULTIPLIER.equals(property) ? property : "";
    }

    private static String[] failure(String message) {
        return new String[] {"false", message, ""};
    }
}
