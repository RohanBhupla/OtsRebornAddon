package net.rebornaddon.jutsu;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class JutsuPluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Jutsu Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long AVAILABILITY_CACHE_MILLIS = 5000L;

    private static volatile long lastAvailabilityCheck;
    private static volatile boolean cachedAvailable;

    private JutsuPluginBridge() {
    }

    public static boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now >= lastAvailabilityCheck && now - lastAvailabilityCheck < AVAILABILITY_CACHE_MILLIS) {
            return cachedAvailable;
        }
        synchronized (JutsuPluginBridge.class) {
            now = System.currentTimeMillis();
            if (now >= lastAvailabilityCheck && now - lastAvailabilityCheck < AVAILABILITY_CACHE_MILLIS) {
                return cachedAvailable;
            }
            cachedAvailable = resolvePlugin() != null;
            lastAvailabilityCheck = now;
            return cachedAvailable;
        }
    }

    public static String[] registerCatalog(String[] descriptors) {
        return invokeDataArray("registerJutsuCatalog", new Object[] {descriptors}, 1);
    }

    public static String[] runtimeOverrides() {
        return invokeDataArray("getJutsuRuntimeOverrides", new Object[0], 0);
    }

    public static String[] adminSnapshot(ICommandSender sender) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invokeArray("getJutsuAdminSnapshot", new Object[] {bukkitSender}, 1);
    }

    public static boolean canAdmin(ICommandSender sender) {
        Object plugin = resolvePlugin();
        Object bukkitSender = resolveSender(sender);
        if (plugin == null || bukkitSender == null) {
            return false;
        }
        try {
            Method method = findMethod(plugin.getClass(), "canManageJutsuConfiguration", 1);
            Object value = method == null ? null : method.invoke(plugin, bukkitSender);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String[] update(ICommandSender sender, String id, String property, String value) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invokeArray("updateJutsuConfiguration",
                new Object[] {bukkitSender, id, property, value}, 4);
    }

    public static String[] reset(ICommandSender sender, String id, String property) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invokeArray("resetJutsuConfiguration",
                new Object[] {bukkitSender, id, property}, 3);
    }

    public static String[] resetAll(ICommandSender sender) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invokeArray("resetAllJutsuConfiguration", new Object[] {bukkitSender}, 1);
    }

    public static String[] reload(ICommandSender sender) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invokeArray("reloadJutsuConfiguration", new Object[] {bukkitSender}, 1);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1 ? clean(response[1], 300) : "Request failed.";
    }

    private static String[] invokeArray(String methodName, Object[] arguments, int parameterCount) {
        Object plugin = resolvePlugin();
        if (plugin == null) {
            cacheUnavailable();
            return failure("The hosted server jutsu configuration plugin is unavailable.");
        }
        try {
            Method method = findMethod(plugin.getClass(), methodName, parameterCount);
            if (method == null) {
                cacheUnavailable();
                return failure("The installed server plugin does not support jutsu configuration.");
            }
            Object value = method.invoke(plugin, arguments);
            if (!(value instanceof String[])) {
                return failure("The server plugin returned an invalid response.");
            }
            cachedAvailable = true;
            lastAvailabilityCheck = System.currentTimeMillis();
            return sanitize((String[]) value);
        } catch (Throwable throwable) {
            LOGGER.warn("Jutsu plugin bridge call {} failed.", methodName, throwable);
            cacheUnavailable();
            return failure("The server plugin could not complete that request.");
        }
    }

    private static String[] invokeDataArray(String methodName, Object[] arguments,
                                            int parameterCount) {
        Object plugin = resolvePlugin();
        if (plugin == null) {
            cacheUnavailable();
            return null;
        }
        try {
            Method method = findMethod(plugin.getClass(), methodName, parameterCount);
            if (method == null) {
                cacheUnavailable();
                return null;
            }
            Object value = method.invoke(plugin, arguments);
            if (!(value instanceof String[])) {
                return null;
            }
            if (((String[]) value).length > 10000) {
                return null;
            }
            cachedAvailable = true;
            lastAvailabilityCheck = System.currentTimeMillis();
            return sanitize((String[]) value);
        } catch (Throwable throwable) {
            LOGGER.warn("Jutsu plugin bridge call {} failed.", methodName, throwable);
            cacheUnavailable();
            return null;
        }
    }

    private static Object resolvePlugin() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            if (manager == null) {
                return null;
            }
            Object plugin = manager.getClass().getMethod("getPlugin", String.class)
                    .invoke(manager, PLUGIN_NAME);
            if (plugin == null || !invokeBoolean(plugin, "isEnabled")
                    || !invokeBoolean(plugin, "isJutsuConfigurationAvailable")
                    || findMethod(plugin.getClass(), "registerJutsuCatalog", 1) == null) {
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
                return findBukkitPlayer(bukkit, player.getUniqueID(), player.getName());
            }
            return bukkit.getMethod("getConsoleSender").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findBukkitPlayer(Class<?> bukkit, UUID playerId, String playerName) {
        try {
            Object player = bukkit.getMethod("getPlayer", UUID.class).invoke(null, playerId);
            if (player != null) {
                return player;
            }
        } catch (Throwable ignored) {
        }
        try {
            return bukkit.getMethod("getPlayerExact", String.class).invoke(null, playerName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Object target, String name) {
        try {
            Object value = target.getClass().getMethod(name).invoke(target);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static String[] sanitize(String[] values) {
        if (values == null || values.length > 10000) {
            return failure("The server plugin returned an invalid response.");
        }
        String[] clean = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            clean[i] = clean(values[i], 8192);
        }
        return clean;
    }

    private static String clean(String value, int maximumLength) {
        String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength);
    }

    private static void cacheUnavailable() {
        cachedAvailable = false;
        lastAvailabilityCheck = System.currentTimeMillis();
    }

    private static String[] failure(String message) {
        return new String[] {"false", message, ""};
    }
}
