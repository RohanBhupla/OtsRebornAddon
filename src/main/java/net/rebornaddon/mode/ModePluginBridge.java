package net.rebornaddon.mode;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ModePluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Mode Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;
    private static volatile long checkedAt;
    private static volatile Object cachedPlugin;

    private ModePluginBridge() {
    }

    public static boolean isAvailable() { return plugin() != null; }
    public static String[] registerCatalog(String[] records) { return data("registerModeCatalog", new Object[]{records}, 1); }
    public static String[] runtimeOverrides() { return data("getModeRuntimeOverrides", new Object[0], 0); }

    public static String[] adminSnapshot(ICommandSender sender) {
        Object commandSender = sender(sender);
        return commandSender == null ? failure("Could not resolve the server command sender.")
                : invoke("getModeAdminSnapshot", new Object[]{commandSender}, 1);
    }

    public static boolean canAdmin(ICommandSender sender) {
        Object plugin = plugin();
        Object commandSender = sender(sender);
        if (plugin == null || commandSender == null) return false;
        try {
            Method method = method(plugin.getClass(), "canManageModeConfiguration", 1);
            Object result = method == null ? null : method.invoke(plugin, commandSender);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String[] update(ICommandSender sender, String id, String property, String value) {
        return senderCall(sender, "updateModeConfiguration", new Object[]{id, property, value}, 4);
    }

    public static String[] reset(ICommandSender sender, String id, String property) {
        return senderCall(sender, "resetModeConfiguration", new Object[]{id, property}, 3);
    }

    public static String[] resetAll(ICommandSender sender) {
        return senderCall(sender, "resetAllModeConfiguration", new Object[0], 1);
    }

    public static String[] reload(ICommandSender sender) {
        return senderCall(sender, "reloadModeConfiguration", new Object[0], 1);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1 ? response[1] : "Mode configuration request failed.";
    }

    private static String[] senderCall(ICommandSender sender, String name, Object[] values, int count) {
        Object commandSender = sender(sender);
        if (commandSender == null) return failure("Could not resolve the server command sender.");
        Object[] arguments = new Object[values.length + 1];
        arguments[0] = commandSender;
        System.arraycopy(values, 0, arguments, 1, values.length);
        return invoke(name, arguments, count);
    }

    private static String[] invoke(String name, Object[] arguments, int count) {
        Object plugin = plugin();
        if (plugin == null) return failure("The server mode configuration plugin needs updating.");
        try {
            Method method = method(plugin.getClass(), name, count);
            Object result = method == null ? null : method.invoke(plugin, arguments);
            return result instanceof String[] ? clean((String[]) result)
                    : failure("The server plugin does not support this mode operation.");
        } catch (Throwable throwable) {
            LOGGER.warn("Mode plugin bridge call {} failed.", name, throwable);
            invalidate();
            return failure("The server plugin could not complete that mode operation.");
        }
    }

    private static String[] data(String name, Object[] arguments, int count) {
        Object plugin = plugin();
        if (plugin == null) return null;
        try {
            Method method = method(plugin.getClass(), name, count);
            Object result = method == null ? null : method.invoke(plugin, arguments);
            return result instanceof String[] ? clean((String[]) result) : null;
        } catch (Throwable throwable) {
            LOGGER.warn("Mode plugin bridge data call {} failed.", name, throwable);
            invalidate();
            return null;
        }
    }

    private static Object plugin() {
        long now = System.currentTimeMillis();
        if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) return cachedPlugin;
        synchronized (ModePluginBridge.class) {
            now = System.currentTimeMillis();
            if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) return cachedPlugin;
            cachedPlugin = findPlugin();
            checkedAt = now;
            return cachedPlugin;
        }
    }

    private static Object findPlugin() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = manager == null ? null : manager.getClass()
                    .getMethod("getPlugin", String.class).invoke(manager, PLUGIN_NAME);
            if (plugin == null || !booleanMethod(plugin, "isEnabled")
                    || !booleanMethod(plugin, "isModeConfigurationAvailable")
                    || method(plugin.getClass(), "registerModeCatalog", 1) == null) return null;
            return plugin;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object sender(ICommandSender sender) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                Object result = bukkit.getMethod("getPlayer", UUID.class)
                        .invoke(null, player.getUniqueID());
                return result != null ? result : bukkit.getMethod("getPlayerExact", String.class)
                        .invoke(null, player.getName());
            }
            return bukkit.getMethod("getConsoleSender").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean booleanMethod(Object target, String name) {
        try {
            Object result = target.getClass().getMethod(name).invoke(target);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method method(Class<?> type, String name, int count) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) return method;
        }
        return null;
    }

    private static String[] clean(String[] values) {
        if (values == null || values.length > 10000) return failure("The server plugin returned invalid mode data.");
        String[] clean = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i] == null ? "" : values[i].replace('\r', ' ').replace('\n', ' ').trim();
            clean[i] = value.length() <= 8192 ? value : value.substring(0, 8192);
        }
        return clean;
    }

    private static void invalidate() {
        cachedPlugin = null;
        checkedAt = System.currentTimeMillis();
    }

    private static String[] failure(String message) { return new String[]{"false", message, ""}; }
}
