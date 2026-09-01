package net.rebornaddon.content;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import net.rebornaddon.gameplay.GameplaySystemsPluginBridge;

public final class NativeContentPluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Content Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;

    private static volatile long checkedAt;
    private static volatile Object cachedPlugin;

    private NativeContentPluginBridge() {
    }

    public static boolean isAvailable() {
        return resolvePlugin() != null;
    }

    public static String[] runtimeSnapshot() {
        return invoke("getNativeContentRuntimeSnapshot", new Object[0], 0);
    }

    public static String[] playerSnapshot(EntityPlayerMP player) {
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("getNativeContentPlayerSnapshot", new Object[]{sender}, 1);
    }

    public static String[] playerRuntimeSnapshot(EntityPlayerMP player) {
        Object plugin = resolvePlugin();
        if (plugin == null || findMethod(plugin.getClass(), "getNativeContentPlayerRuntimeSnapshot", 1) == null) {
            return playerSnapshot(player);
        }
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("getNativeContentPlayerRuntimeSnapshot", new Object[]{sender}, 1);
    }

    public static String[] adminSnapshot(EntityPlayerMP player) {
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the operator session.")
                : invoke("getNativeContentAdminSnapshot", new Object[]{sender}, 1);
    }

    public static String[] adminAction(EntityPlayerMP player, String action, String payload) {
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the operator session.")
                : invoke("applyNativeContentAdminAction",
                new Object[]{sender, clean(action, 64), bounded(payload, 16777216)}, 3);
    }

    public static String[] playerAction(EntityPlayerMP player, String action, String payload) {
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("applyNativeContentPlayerAction",
                new Object[]{sender, clean(action, 64), bounded(payload, 65536)}, 3);
    }

    public static String[] recordEvent(String eventId, String type, EntityPlayerMP player,
                                       String contextId, String payload) {
        String[] result = invoke("recordNativeContentEvent", new Object[]{
                clean(eventId, 64), clean(type, 64),
                player == null ? "" : player.getUniqueID().toString(),
                player == null ? "" : clean(player.getName(), 40),
                clean(contextId, 96), bounded(payload, 65536)}, 6);
        GameplaySystemsPluginBridge.record(eventId, "native." + type, player, contextId, payload);
        return result;
    }

    public static String[] resolveBossRewards(String eventId, String bossId,
                                              EntityPlayerMP player, double contribution) {
        if (player == null) {
            return failure("A player is required.");
        }
        return invoke("resolveNativeBossRewards", new Object[]{
                clean(eventId, 64), clean(bossId, 96), player.getUniqueID().toString(),
                clean(player.getName(), 40), Double.valueOf(contribution)}, 5);
    }

    public static String[] pollWorldBossSpawns(long epochSeconds) {
        return invoke("pollNativeWorldBossSpawns", new Object[]{Long.valueOf(epochSeconds)}, 1);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1
                ? bounded(response[1], 400) : "The server content service did not respond.";
    }

    public static String payload(String[] response) {
        return response != null && response.length > 2 ? response[2] : "";
    }

    private static String[] invoke(String methodName, Object[] arguments, int parameterCount) {
        Object plugin = resolvePlugin();
        if (plugin == null) {
            return failure("The hosted server content plugin is unavailable or needs updating.");
        }
        try {
            Method method = findMethod(plugin.getClass(), methodName, parameterCount);
            if (method == null) {
                invalidate();
                return failure("The hosted server plugin does not support native content yet.");
            }
            Object value = method.invoke(plugin, arguments);
            if (!(value instanceof String[]) || ((String[]) value).length > 10000) {
                return failure("The hosted server plugin returned an invalid response.");
            }
            String[] response = (String[]) value;
            String[] sanitized = new String[response.length];
            for (int i = 0; i < response.length; i++) {
                sanitized[i] = bounded(response[i], i == 2 ? 2097152 : 8192);
            }
            return sanitized;
        } catch (Throwable throwable) {
            LOGGER.warn("Native content bridge call {} failed.", methodName, throwable);
            invalidate();
            return failure("The hosted server content service could not complete that request.");
        }
    }

    private static Object resolvePlugin() {
        long now = System.currentTimeMillis();
        Object current = cachedPlugin;
        if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) {
            return current;
        }
        synchronized (NativeContentPluginBridge.class) {
            now = System.currentTimeMillis();
            if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) {
                return cachedPlugin;
            }
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
            if (plugin == null || !invokeBoolean(plugin, "isEnabled")
                    || !invokeBoolean(plugin, "isNativeContentAvailable")
                    || findMethod(plugin.getClass(), "getNativeContentRuntimeSnapshot", 0) == null) {
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
                Object result = bukkit.getMethod("getPlayer", UUID.class)
                        .invoke(null, player.getUniqueID());
                if (result != null) {
                    return result;
                }
                return bukkit.getMethod("getPlayerExact", String.class)
                        .invoke(null, player.getName());
            }
            return bukkit.getMethod("getConsoleSender").invoke(null);
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

    private static Method findMethod(Class<?> type, String name, int count) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) {
                return method;
            }
        }
        return null;
    }

    private static void invalidate() {
        cachedPlugin = null;
        checkedAt = System.currentTimeMillis();
    }

    private static String clean(String value, int maximum) {
        return bounded(value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim(), maximum);
    }

    private static String bounded(String value, int maximum) {
        String result = value == null ? "" : value;
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static String[] failure(String message) {
        return new String[]{"false", message, ""};
    }
}
