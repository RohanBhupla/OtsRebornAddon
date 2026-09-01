package net.rebornaddon.discord;

import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class DiscordPluginBridge {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Discord Bridge");
    private static final String PLUGIN_NAME = "RebornDiscordRoleSync";
    private static final long AVAILABILITY_CACHE_MILLIS = 5000L;

    private static volatile long lastAvailabilityCheck;
    private static volatile boolean cachedAvailable;

    private DiscordPluginBridge() {
    }

    public static boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now >= lastAvailabilityCheck
                && now - lastAvailabilityCheck < AVAILABILITY_CACHE_MILLIS) {
            return cachedAvailable;
        }
        synchronized (DiscordPluginBridge.class) {
            now = System.currentTimeMillis();
            if (now >= lastAvailabilityCheck
                    && now - lastAvailabilityCheck < AVAILABILITY_CACHE_MILLIS) {
                return cachedAvailable;
            }
            cachedAvailable = resolveAvailablePlugin() != null;
            lastAvailabilityCheck = now;
            return cachedAvailable;
        }
    }

    public static LinkCodeResult requestLinkCode(EntityPlayerMP player) {
        if (player == null) {
            return LinkCodeResult.failure("A player is required.");
        }

        Object plugin = resolveAvailablePlugin();
        if (plugin == null) {
            cacheUnavailable();
            return LinkCodeResult.failure(unavailableReason());
        }

        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object bukkitPlayer = findBukkitPlayer(bukkit, player.getUniqueID(), player.getName());
            if (bukkitPlayer == null) {
                return LinkCodeResult.failure("Could not find your server player session. Please try again.");
            }

            Method createLinkCode = findMethod(plugin.getClass(), "createLinkCode", 1);
            Object value = createLinkCode.invoke(plugin, bukkitPlayer);
            if (!(value instanceof String[])) {
                return LinkCodeResult.failure("The Discord link plugin returned an invalid response.");
            }

            String[] data = (String[]) value;
            boolean success = data.length > 0 && Boolean.parseBoolean(data[0]);
            String code = data.length > 1 ? cleanCode(data[1]) : "";
            String message = data.length > 2 ? singleLine(data[2], 240) : "";
            String command = data.length > 3 ? cleanCommand(data[3]) : "/link";
            if (!success || code.isEmpty()) {
                return LinkCodeResult.failure(message.isEmpty()
                        ? "Could not create a Discord link code. Please try again." : message);
            }

            cachedAvailable = true;
            lastAvailabilityCheck = System.currentTimeMillis();
            return new LinkCodeResult(true, code, message, command);
        } catch (Throwable throwable) {
            LOGGER.warn("Unable to request a Discord link code from {}", PLUGIN_NAME, throwable);
            cacheUnavailable();
            return LinkCodeResult.failure("Could not create a Discord link code. Please try again.");
        }
    }

    public static LinkStatus linkStatus(EntityPlayerMP player) {
        Object plugin = resolveAvailablePlugin();
        if (plugin == null) {
            cacheUnavailable();
            return LinkStatus.unavailable(unavailableReason());
        }
        if (findMethod(plugin.getClass(), "getDiscordLinkStatus", 1) == null) {
            return new LinkStatus(true, false, "");
        }
        String[] response = invokePlayerMethod(player, plugin, "getDiscordLinkStatus");
        if (response == null || response.length < 2 || !Boolean.parseBoolean(response[0])) {
            return LinkStatus.unavailable(response != null && response.length > 2
                    ? singleLine(response[2], 240) : "Discord linking is not available on this server.");
        }
        return new LinkStatus(true, Boolean.parseBoolean(response[1]),
                response.length > 2 ? singleLine(response[2], 240) : "");
    }

    public static ActionResult unlink(EntityPlayerMP player) {
        Object plugin = resolveAvailablePlugin();
        if (plugin == null) {
            cacheUnavailable();
            return new ActionResult(false, unavailableReason());
        }
        if (findMethod(plugin.getClass(), "unlinkDiscordAccount", 1) == null) {
            return new ActionResult(false,
                    "The server Discord plugin must be updated before accounts can be unlinked here.");
        }
        String[] response = invokePlayerMethod(player, plugin, "unlinkDiscordAccount");
        if (response == null || response.length == 0) {
            return new ActionResult(false, "Discord linking is not available on this server.");
        }
        return new ActionResult(Boolean.parseBoolean(response[0]),
                response.length > 1 ? singleLine(response[1], 240) : "Discord link removed.");
    }

    private static String[] invokePlayerMethod(EntityPlayerMP player, Object plugin, String methodName) {
        if (player == null) {
            return null;
        }
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object bukkitPlayer = findBukkitPlayer(bukkit, player.getUniqueID(), player.getName());
            Method method = findMethod(plugin.getClass(), methodName, 1);
            Object value = bukkitPlayer == null || method == null ? null : method.invoke(plugin, bukkitPlayer);
            return value instanceof String[] ? (String[]) value : null;
        } catch (Throwable throwable) {
            LOGGER.warn("Discord plugin bridge call {} failed.", methodName, throwable);
            cacheUnavailable();
            return null;
        }
    }

    private static Object resolveAvailablePlugin() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            if (manager == null) {
                return null;
            }

            Object plugin = manager.getClass().getMethod("getPlugin", String.class)
                    .invoke(manager, PLUGIN_NAME);
            if (plugin == null || !invokeBoolean(plugin, "isEnabled")) {
                return null;
            }
            if (findMethod(plugin.getClass(), "createLinkCode", 1) == null) {
                return null;
            }
            return plugin;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String unavailableReason() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            if (manager == null) {
                return "Discord linking is unavailable because the Bukkit plugin manager is not ready.";
            }
            Object plugin = manager.getClass().getMethod("getPlugin", String.class)
                    .invoke(manager, PLUGIN_NAME);
            if (plugin == null) {
                return "Discord linking is unavailable because RebornDiscordRoleSync is not installed.";
            }
            if (!invokeBoolean(plugin, "isEnabled")) {
                return "Discord linking is unavailable because RebornDiscordRoleSync is disabled.";
            }
            if (findMethod(plugin.getClass(), "createLinkCode", 1) == null) {
                return "Discord linking is unavailable because RebornDiscordRoleSync must be updated.";
            }
            return "Discord linking is not available on this server.";
        } catch (ClassNotFoundException ignored) {
            return "Discord linking is only available on the Mohist server.";
        } catch (Throwable throwable) {
            return "Discord linking could not connect to RebornDiscordRoleSync.";
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

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static String cleanCode(String value) {
        String code = singleLine(value, 16).toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < code.length(); i++) {
            char character = code.charAt(i);
            if (!Character.isLetterOrDigit(character)) {
                return "";
            }
        }
        return code;
    }

    private static String cleanCommand(String value) {
        String command = singleLine(value, 40);
        if (command.isEmpty()) {
            return "/link";
        }
        return command.charAt(0) == '/' ? command : "/" + command;
    }

    private static String singleLine(String value, int maximumLength) {
        String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength).trim();
    }

    private static void cacheUnavailable() {
        cachedAvailable = false;
        lastAvailabilityCheck = System.currentTimeMillis();
    }

    public static final class LinkCodeResult {
        private final boolean success;
        private final String code;
        private final String message;
        private final String command;

        private LinkCodeResult(boolean success, String code, String message, String command) {
            this.success = success;
            this.code = code == null ? "" : code;
            this.message = message == null ? "" : message;
            this.command = command == null || command.isEmpty() ? "/link" : command;
        }

        private static LinkCodeResult failure(String message) {
            return new LinkCodeResult(false, "", message, "/link");
        }

        public boolean success() {
            return success;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }

        public String command() {
            return command;
        }
    }

    public static final class LinkStatus {
        private final boolean available;
        private final boolean linked;
        private final String message;

        private LinkStatus(boolean available, boolean linked, String message) {
            this.available = available;
            this.linked = linked;
            this.message = message == null ? "" : message;
        }

        private static LinkStatus unavailable(String message) {
            return new LinkStatus(false, false, message);
        }

        public boolean available() { return available; }
        public boolean linked() { return linked; }
        public String message() { return message; }
    }

    public static final class ActionResult {
        private final boolean success;
        private final String message;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public boolean success() { return success; }
        public String message() { return message; }
    }
}
