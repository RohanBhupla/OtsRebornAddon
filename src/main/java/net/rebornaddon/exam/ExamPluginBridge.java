package net.rebornaddon.exam;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import net.rebornaddon.gameplay.GameplaySystemsPluginBridge;

public final class ExamPluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Exam Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;

    private static volatile long checkedAt;
    private static volatile Object cachedPlugin;

    private ExamPluginBridge() {
    }

    public static boolean isAvailable() {
        return plugin() != null;
    }

    public static String[] playerSnapshot(EntityPlayerMP player) {
        Object sender = resolveSender(player);
        return playerSnapshot(sender);
    }

    static String[] playerSnapshot(Object sender) {
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("getExamPlayerSnapshot", new Object[]{sender}, 1);
    }

    public static String[] applyAction(EntityPlayerMP player, String action, String payload) {
        Object sender = resolveSender(player);
        return applyAction(sender, action, payload);
    }

    static String[] applyAction(Object sender, String action, String payload) {
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("applyExamAction", new Object[]{sender, clean(action, 64),
                bounded(payload, 262144)}, 3);
    }

    public static String[] runtimeSnapshot() {
        return invoke("getExamRuntimeSnapshot", new Object[0], 0);
    }

    public static String[] recordRuntimeEvent(String eventId, String type,
                                              EntityPlayerMP player, String payload) {
        String[] result = invoke("recordExamRuntimeEvent", new Object[]{clean(eventId, 96),
                clean(type, 64), player == null ? "" : player.getUniqueID().toString(),
                player == null ? "" : clean(player.getName(), 40), bounded(payload, 65536)}, 5);
        GameplaySystemsPluginBridge.record(eventId, "exam." + type, player, "exam", payload);
        return result;
    }

    public static String[] setRank(ICommandSender actor, EntityPlayerMP target, String rankId) {
        Object sender = resolveSender(actor);
        if (sender == null || target == null) {
            return failure("Could not resolve that rank assignment.");
        }
        return invoke("setExamPlayerRank", new Object[]{sender, target.getUniqueID().toString(),
                clean(target.getName(), 40), clean(rankId, 64)}, 4);
    }

    public static String[] getRank(EntityPlayerMP player) {
        Object sender = resolveSender(player);
        return getRank(sender);
    }

    static String[] getRank(Object sender) {
        return sender == null ? failure("Could not resolve the player session.")
                : invoke("getExamPlayerRank", new Object[]{sender}, 1);
    }

    public static boolean successful(String[] response) {
        return response != null && response.length > 0 && Boolean.parseBoolean(response[0]);
    }

    public static String message(String[] response) {
        return response != null && response.length > 1
                ? bounded(response[1], 400) : "The exam service did not respond.";
    }

    public static String payload(String[] response) {
        return response != null && response.length > 2 ? response[2] : "";
    }

    private static String[] invoke(String name, Object[] arguments, int count) {
        Object plugin = plugin();
        if (plugin == null) {
            return failure("The hosted exam service is unavailable or needs updating.");
        }
        try {
            Method method = findMethod(plugin.getClass(), name, count);
            if (method == null) {
                invalidate();
                return failure("The hosted server plugin does not support exams yet.");
            }
            Object value = method.invoke(plugin, arguments);
            if (!(value instanceof String[]) || ((String[]) value).length > 10000) {
                return failure("The hosted exam service returned an invalid response.");
            }
            String[] raw = (String[]) value;
            String[] result = new String[raw.length];
            for (int i = 0; i < raw.length; i++) {
                result[i] = bounded(raw[i], i == 2 ? 4194304 : 8192);
            }
            return result;
        } catch (Throwable throwable) {
            LOGGER.warn("Exam plugin bridge call {} failed.", name, throwable);
            invalidate();
            return failure("The hosted exam service could not complete that request.");
        }
    }

    private static Object plugin() {
        long now = System.currentTimeMillis();
        if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) {
            return cachedPlugin;
        }
        synchronized (ExamPluginBridge.class) {
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
            if (plugin == null || !booleanMethod(plugin, "isEnabled")
                    || !booleanMethod(plugin, "isExamSystemAvailable")
                    || findMethod(plugin.getClass(), "getExamPlayerSnapshot", 1) == null
                    || findMethod(plugin.getClass(), "applyExamAction", 3) == null) {
                return null;
            }
            return plugin;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object resolveSender(ICommandSender sender) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                Object result = bukkit.getMethod("getPlayer", UUID.class)
                        .invoke(null, player.getUniqueID());
                if (result != null) return result;
                return bukkit.getMethod("getPlayerExact", String.class)
                        .invoke(null, player.getName());
            }
            return bukkit.getMethod("getConsoleSender").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean booleanMethod(Object target, String name) {
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
