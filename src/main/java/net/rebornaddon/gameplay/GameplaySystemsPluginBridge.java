package net.rebornaddon.gameplay;

import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public final class GameplaySystemsPluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Gameplay Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;
    private static volatile long checkedAt;
    private static volatile Object cachedPlugin;

    private GameplaySystemsPluginBridge() {
    }

    public static boolean isAvailable() {
        return plugin() != null;
    }

    public static String[] snapshot(EntityPlayerMP player, String section) {
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("getGameplaySystemsSnapshot", new Object[]{sender, clean(section, 32)}, 2);
    }

    static String[] snapshot(Object sender, String section) {
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("getGameplaySystemsSnapshot", new Object[]{sender, clean(section, 32)}, 2);
    }

    public static String[] runtimeSnapshot(String section) {
        return invoke("getGameplaySystemsRuntimeSnapshot", new Object[]{clean(section, 32)}, 1);
    }

    public static String[] action(EntityPlayerMP player, String section, String action, String payload) {
        Object sender = resolveSender(player);
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("applyGameplaySystemsAction", new Object[]{sender, clean(section, 32),
                clean(action, 64), bounded(payload, 262144)}, 4);
    }

    static String[] action(Object sender, String section, String action, String payload) {
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("applyGameplaySystemsAction", new Object[]{sender, clean(section, 32),
                clean(action, 64), bounded(payload, 262144)}, 4);
    }

    public static String[] record(String eventId, String type, EntityPlayerMP player,
                                  String context, String payload) {
        return invoke("recordGameplaySystemsEvent", new Object[]{clean(eventId, 96),
                clean(type, 64), player == null ? "" : player.getUniqueID().toString(),
                player == null ? "" : clean(player.getName(), 40), clean(context, 96),
                bounded(payload, 65536)}, 6);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1 ? bounded(response[1], 400)
                : "The hosted gameplay service did not respond.";
    }

    public static String payload(String[] response) {
        return response != null && response.length > 2 ? bounded(response[2], 4194304) : "";
    }

    private static String[] invoke(String name, Object[] arguments, int count) {
        Object plugin = plugin();
        if (plugin == null) return failure("This feature needs the hosted gameplay service update.");
        try {
            Method method = findMethod(plugin.getClass(), name, count);
            if (method == null) {
                invalidate();
                return failure("The hosted gameplay service needs updating.");
            }
            Object value = method.invoke(plugin, arguments);
            if (!(value instanceof String[]) || ((String[]) value).length > 10000) {
                return failure("The hosted gameplay service returned an invalid response.");
            }
            String[] raw = (String[]) value;
            String[] result = new String[raw.length];
            for (int i = 0; i < raw.length; i++) result[i] = bounded(raw[i], i == 2 ? 4194304 : 8192);
            return result;
        } catch (Throwable throwable) {
            String rejection = expectedRejection(throwable);
            if (rejection != null) return failure(rejection);
            Throwable cause = invocationCause(throwable);
            LOGGER.warn("Gameplay bridge call {} failed.", name, cause);
            invalidate();
            return failure("The hosted gameplay service could not complete that request.");
        }
    }

    static String expectedRejection(Throwable throwable) {
        Throwable cause = invocationCause(throwable);
        if (!(cause instanceof IllegalArgumentException)) return null;
        String message = clean(cause.getMessage(), 400);
        if ("Unsupported gameplay section.".equalsIgnoreCase(message)) {
            return "The hosted gameplay plugin needs an update for this section.";
        }
        return message.isEmpty() ? "The hosted gameplay service rejected that request." : message;
    }

    private static Throwable invocationCause(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getCause() != null) {
            return ((InvocationTargetException) throwable).getCause();
        }
        return throwable;
    }

    private static Object plugin() {
        long now = System.currentTimeMillis();
        if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) return cachedPlugin;
        synchronized (GameplaySystemsPluginBridge.class) {
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
            if (plugin == null || !bool(plugin, "isEnabled")
                    || !bool(plugin, "isGameplaySystemsAvailable")
                    || findMethod(plugin.getClass(), "getGameplaySystemsSnapshot", 2) == null
                    || findMethod(plugin.getClass(), "applyGameplaySystemsAction", 4) == null) return null;
            return plugin;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object resolveSender(EntityPlayerMP player) {
        if (player == null) return null;
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object value = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUniqueID());
            return value != null ? value : bukkit.getMethod("getPlayerExact", String.class)
                    .invoke(null, player.getName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean bool(Object target, String name) {
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
