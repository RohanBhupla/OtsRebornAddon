package net.rebornaddon.store;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class StorePluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Store Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;

    private static volatile long checkedAt;
    private static volatile boolean available;

    private StorePluginBridge() {
    }

    public static boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) {
            return available;
        }
        synchronized (StorePluginBridge.class) {
            now = System.currentTimeMillis();
            if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) {
                return available;
            }
            available = resolvePlugin() != null;
            checkedAt = now;
            return available;
        }
    }

    public static String[] registerCatalog(String[] descriptors) {
        return invokeData("registerStoreCatalog", new Object[] {descriptors}, 1);
    }

    public static String[] runtimeOverrides() {
        return invokeData("getStoreRuntimeOverrides", new Object[0], 0);
    }

    public static boolean canAdmin(ICommandSender sender) {
        Object plugin = resolvePlugin();
        Object bukkitSender = resolveSender(sender);
        if (plugin == null || bukkitSender == null) {
            return false;
        }
        try {
            Method method = findMethod(plugin.getClass(), "canManageStoreConfiguration", 1);
            Object result = method == null ? null : method.invoke(plugin, bukkitSender);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String[] update(ICommandSender sender, String key, String property, String value) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invoke("updateStoreConfiguration", new Object[] {bukkitSender, key, property, value}, 4);
    }

    public static String[] updateScope(ICommandSender sender, String key, boolean enabled) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) return failure("Could not resolve the server command sender.");
        return invoke("updateStoreScope",
                new Object[] {bukkitSender, clean(key, 180), Boolean.toString(enabled)}, 3);
    }

    public static String[] resetScope(ICommandSender sender, String key) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) return failure("Could not resolve the server command sender.");
        return invoke("resetStoreScope", new Object[] {bukkitSender, clean(key, 180)}, 2);
    }

    public static String[] reload(ICommandSender sender) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) {
            return failure("Could not resolve the server command sender.");
        }
        return invoke("reloadStoreConfiguration", new Object[] {bukkitSender}, 1);
    }

    public static String[] reset(ICommandSender sender, String key, String property) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) return failure("Could not resolve the server command sender.");
        return invoke("resetStoreConfiguration",
                new Object[] {bukkitSender, key, property}, 3);
    }

    public static String[] resetAll(ICommandSender sender) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) return failure("Could not resolve the server command sender.");
        return invoke("resetAllStoreConfiguration", new Object[] {bukkitSender}, 1);
    }

    public static String[] purchaseCounts(UUID playerId, String playerName) {
        return invoke("getStorePurchaseCounts",
                new Object[] {playerId == null ? "" : playerId.toString(), clean(playerName, 40)}, 2);
    }

    public static String[] adjustPurchaseCount(UUID playerId, String playerName,
                                               String quotaKind, int delta) {
        return invoke("adjustStorePurchaseCount",
                new Object[] {playerId == null ? "" : playerId.toString(), clean(playerName, 40),
                        clean(quotaKind, 32), Integer.valueOf(delta)}, 4);
    }

    public static String[] setPurchaseCount(ICommandSender sender, UUID playerId, String playerName,
                                            String quotaKind, int value) {
        Object bukkitSender = resolveSender(sender);
        if (bukkitSender == null) return failure("Could not resolve the server command sender.");
        return invoke("setStorePurchaseCount",
                new Object[] {bukkitSender, playerId == null ? "" : playerId.toString(),
                        clean(playerName, 40), clean(quotaKind, 32), Integer.valueOf(value)}, 5);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1 ? clean(response[1], 300) : "Request failed.";
    }

    private static String[] invoke(String methodName, Object[] arguments, int parameterCount) {
        String[] response = invokeData(methodName, arguments, parameterCount);
        return response == null ? failure("The hosted server store configuration plugin is unavailable.") : response;
    }

    private static String[] invokeData(String methodName, Object[] arguments, int parameterCount) {
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
            if (!(value instanceof String[]) || ((String[]) value).length > 10000) {
                return null;
            }
            available = true;
            checkedAt = System.currentTimeMillis();
            return sanitize((String[]) value);
        } catch (Throwable throwable) {
            LOGGER.warn("Store plugin bridge call {} failed.", methodName, throwable);
            cacheUnavailable();
            return null;
        }
    }

    private static Object resolvePlugin() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = manager == null ? null : manager.getClass().getMethod("getPlugin", String.class)
                    .invoke(manager, PLUGIN_NAME);
            if (plugin == null || !invokeBoolean(plugin, "isEnabled")
                    || !invokeBoolean(plugin, "isStoreConfigurationAvailable")
                    || findMethod(plugin.getClass(), "registerStoreCatalog", 1) == null
                    || findMethod(plugin.getClass(), "getStoreRuntimeOverrides", 0) == null
                    || findMethod(plugin.getClass(), "canManageStoreConfiguration", 1) == null
                    || findMethod(plugin.getClass(), "updateStoreConfiguration", 4) == null
                    || findMethod(plugin.getClass(), "updateStoreScope", 3) == null
                    || findMethod(plugin.getClass(), "resetStoreScope", 2) == null
                    || findMethod(plugin.getClass(), "resetStoreConfiguration", 3) == null
                    || findMethod(plugin.getClass(), "resetAllStoreConfiguration", 1) == null
                    || findMethod(plugin.getClass(), "reloadStoreConfiguration", 1) == null
                    || findMethod(plugin.getClass(), "getStorePurchaseCounts", 2) == null
                    || findMethod(plugin.getClass(), "adjustStorePurchaseCount", 4) == null
                    || findMethod(plugin.getClass(), "setStorePurchaseCount", 5) == null) {
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
                Object result = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUniqueID());
                if (result != null) return result;
                return bukkit.getMethod("getPlayerExact", String.class).invoke(null, player.getName());
            }
            return bukkit.getMethod("getConsoleSender").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, int count) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) {
                return method;
            }
        }
        return null;
    }

    private static String[] sanitize(String[] values) {
        String[] clean = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            clean[i] = clean(values[i], 8192);
        }
        return clean;
    }

    private static String clean(String value, int maximumLength) {
        String result = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() <= maximumLength ? result : result.substring(0, maximumLength);
    }

    private static void cacheUnavailable() {
        available = false;
        checkedAt = System.currentTimeMillis();
    }

    private static String[] failure(String message) {
        return new String[] {"false", message, ""};
    }
}
