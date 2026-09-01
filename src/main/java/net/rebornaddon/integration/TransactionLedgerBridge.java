package net.rebornaddon.integration;

import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TransactionLedgerBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Transaction Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long CACHE_MILLIS = 5000L;
    private static volatile Object cachedPlugin;
    private static volatile long checkedAt;

    private TransactionLedgerBridge() {
    }

    public static boolean isAvailable() {
        return plugin() != null;
    }

    public static Result begin(String type, String idempotencyKey, EntityPlayerMP player, String payload) {
        if (!isAvailable()) return Result.fallback();
        return call("beginRebornTransaction", new Object[]{clean(type, 48), clean(idempotencyKey, 160),
                player == null ? "" : player.getUniqueID().toString(),
                player == null ? "" : clean(player.getName(), 40), bounded(payload, 65536)}, 5);
    }

    public static Result commit(String token, String payload) {
        if (!isAvailable()) return Result.fallback();
        return call("commitRebornTransaction", new Object[]{clean(token, 160), bounded(payload, 65536)}, 2);
    }

    public static void abort(String token, String reason) {
        if (!isAvailable() || token == null || token.isEmpty()) return;
        call("abortRebornTransaction", new Object[]{clean(token, 160), clean(reason, 240)}, 2);
    }

    private static Result call(String name, Object[] arguments, int count) {
        Object plugin = plugin();
        if (plugin == null) return Result.fallback();
        try {
            Method method = find(plugin.getClass(), name, count);
            if (method == null) return Result.failure("The transaction ledger plugin API needs updating.");
            Object value = method.invoke(plugin, arguments);
            if (!(value instanceof String[])) return Result.failure("The transaction ledger returned an invalid response.");
            String[] response = (String[]) value;
            return new Result(response.length > 0 && Boolean.parseBoolean(response[0]), false,
                    response.length > 1 ? bounded(response[1], 400) : "",
                    response.length > 2 ? bounded(response[2], 65536) : "");
        } catch (Throwable failure) {
            LOGGER.warn("Transaction ledger call {} failed.", name, failure);
            cachedPlugin = null;
            checkedAt = System.currentTimeMillis();
            return Result.failure("The transaction ledger could not complete the request.");
        }
    }

    private static Object plugin() {
        long now = System.currentTimeMillis();
        if (now >= checkedAt && now - checkedAt < CACHE_MILLIS) return cachedPlugin;
        synchronized (TransactionLedgerBridge.class) {
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
            Object plugin = manager == null ? null : manager.getClass().getMethod("getPlugin", String.class)
                    .invoke(manager, PLUGIN_NAME);
            if (plugin == null || !booleanMethod(plugin, "isEnabled")
                    || !booleanMethod(plugin, "isTransactionLedgerAvailable")
                    || find(plugin.getClass(), "beginRebornTransaction", 5) == null
                    || find(plugin.getClass(), "commitRebornTransaction", 2) == null
                    || find(plugin.getClass(), "abortRebornTransaction", 2) == null) return null;
            return plugin;
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

    private static Method find(Class<?> type, String name, int count) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) return method;
        }
        return null;
    }

    private static String clean(String value, int maximum) {
        return bounded(value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim(), maximum);
    }

    private static String bounded(String value, int maximum) {
        String clean = value == null ? "" : value;
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }

    public static final class Result {
        private final boolean success;
        private final boolean legacy;
        private final String message;
        private final String token;

        private Result(boolean success, boolean legacy, String message, String token) {
            this.success = success;
            this.legacy = legacy;
            this.message = message;
            this.token = token;
        }

        public boolean success() { return success; }
        public boolean legacy() { return legacy; }
        public String message() { return message; }
        public String token() { return token; }

        private static Result fallback() { return new Result(true, true, "", ""); }
        private static Result failure(String message) { return new Result(false, false, message, ""); }
    }
}
