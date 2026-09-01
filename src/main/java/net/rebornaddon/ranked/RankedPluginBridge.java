package net.rebornaddon.ranked;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class RankedPluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Ranked Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;

    private static volatile long lastAvailabilityCheck;
    private static volatile boolean cachedAvailable;

    private RankedPluginBridge() {
    }

    public static boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now >= lastAvailabilityCheck && now - lastAvailabilityCheck < CACHE_MILLIS) {
            return cachedAvailable;
        }
        synchronized (RankedPluginBridge.class) {
            now = System.currentTimeMillis();
            if (now >= lastAvailabilityCheck && now - lastAvailabilityCheck < CACHE_MILLIS) {
                return cachedAvailable;
            }
            cachedAvailable = resolvePlugin() != null;
            lastAvailabilityCheck = now;
            return cachedAvailable;
        }
    }

    public static String[] runtimeSnapshot() {
        return invoke("getRankedSeasonRuntimeSnapshot", new Object[0], 0, false);
    }

    public static String[] adminSnapshot(ICommandSender sender) {
        Object bukkitSender = resolveSender(sender);
        return bukkitSender == null ? failure("Could not resolve the server command sender.")
                : invoke("getRankedSeasonAdminSnapshot", new Object[] {bukkitSender}, 1, true);
    }

    public static String[] applyAction(ICommandSender sender, String action, String payload) {
        Object bukkitSender = resolveSender(sender);
        return bukkitSender == null ? failure("Could not resolve the server command sender.")
                : invoke("applyRankedSeasonAction", new Object[] {bukkitSender, action, payload}, 3, true);
    }

    public static String[] endSeason(ICommandSender sender, String standings) {
        Object bukkitSender = resolveSender(sender);
        return bukkitSender == null ? failure("Could not resolve the server command sender.")
                : invoke("endRankedSeason", new Object[] {bukkitSender, standings}, 2, true);
    }

    public static String[] confirmReset(String token) {
        return invoke("confirmRankedSeasonReset", new Object[] {token}, 1, true);
    }

    public static String[] pendingRewards(UUID playerId) {
        return invoke("getPendingRankedRewards",
                new Object[] {playerId == null ? "" : playerId.toString()}, 1, false);
    }

    public static String[] acknowledgeReward(UUID playerId, String rewardId) {
        return invoke("acknowledgeRankedReward",
                new Object[] {playerId == null ? "" : playerId.toString(), rewardId}, 2, true);
    }

    public static String[] syncPodium(String standings) {
        return invoke("syncRankedLeaderboardPodium", new Object[] {standings}, 1, true);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1 ? clean(response[1], 300) : "Request failed.";
    }

    public static String payload(String[] response) {
        return response != null && response.length > 2 ? response[2] : "";
    }

    private static String[] invoke(String name, Object[] arguments, int count, boolean warn) {
        Object plugin = resolvePlugin();
        if (plugin == null) {
            unavailable();
            return failure("The hosted server ranked configuration plugin is unavailable.");
        }
        try {
            Method method = findMethod(plugin.getClass(), name, count);
            if (method == null) {
                unavailable();
                return failure("The installed server plugin does not support ranked season configuration.");
            }
            Object result = method.invoke(plugin, arguments);
            if (!(result instanceof String[])) return failure("The server plugin returned an invalid response.");
            cachedAvailable = true;
            lastAvailabilityCheck = System.currentTimeMillis();
            return sanitize((String[]) result);
        } catch (Throwable throwable) {
            if (warn) LOGGER.warn("Ranked plugin bridge call {} failed.", name, throwable);
            unavailable();
            return failure("The server plugin could not complete that ranked request.");
        }
    }

    private static Object resolvePlugin() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            if (manager == null) return null;
            Object plugin = manager.getClass().getMethod("getPlugin", String.class)
                    .invoke(manager, PLUGIN_NAME);
            if (plugin == null || !invokeBoolean(plugin, "isEnabled")
                    || !invokeBoolean(plugin, "isRankedSeasonAvailable")
                    || findMethod(plugin.getClass(), "getRankedSeasonRuntimeSnapshot", 0) == null) {
                return null;
            }
            return plugin;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object resolveSender(ICommandSender sender) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                Object resolved = bukkit.getMethod("getPlayer", UUID.class)
                        .invoke(null, player.getUniqueID());
                if (resolved != null) return resolved;
                return bukkit.getMethod("getPlayerExact", String.class).invoke(null, player.getName());
            }
            return bukkit.getMethod("getConsoleSender").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Object target, String name) {
        try {
            Object result = target.getClass().getMethod(name).invoke(target);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, int count) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) return method;
        }
        return null;
    }

    private static String[] sanitize(String[] values) {
        if (values == null || values.length > 16) return failure("The server plugin returned an invalid response.");
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = clean(values[i], 1048576);
        return result;
    }

    private static String clean(String value, int maximumLength) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength);
    }

    private static void unavailable() {
        cachedAvailable = false;
        lastAvailabilityCheck = System.currentTimeMillis();
    }

    private static String[] failure(String message) {
        return new String[] {"false", message, ""};
    }
}
