package net.rebornaddon.compat;

import net.minecraft.entity.player.EntityPlayerMP;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Optional WorldGuard access that keeps Bukkit classes out of the Forge mod's
 * static class path. All resolved objects are cached after the first query.
 */
final class WorldGuardEntryBridge {
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(30L);

    enum Access {
        ALLOWED,
        DENIED,
        UNAVAILABLE
    }

    private Api api;
    private long retryAfterNanos;

    Access entryAccess(EntityPlayerMP player, double x, double y, double z) {
        Api resolved = api;
        if (resolved == null) {
            long now = System.nanoTime();
            if (now < retryAfterNanos) return Access.UNAVAILABLE;
            try {
                resolved = Api.resolve();
                api = resolved;
            } catch (Throwable ignored) {
                retryAfterNanos = now + RETRY_DELAY_NANOS;
                return Access.UNAVAILABLE;
            }
        }

        try {
            return resolved.entryAccess(player.getUniqueID(), x, y, z);
        } catch (Throwable ignored) {
            api = null;
            retryAfterNanos = System.nanoTime() + RETRY_DELAY_NANOS;
            return Access.UNAVAILABLE;
        }
    }

    void reset() {
        api = null;
        retryAfterNanos = 0L;
    }

    private static final class Api {
        private final Object plugin;
        private final Method getPlayer;
        private final Method getPlayerWorld;
        private final Constructor<?> locationConstructor;
        private final Object query;
        private final Method queryState;
        private final Object entryFlag;
        private final Class<?> flagComponentType;
        private final Method wrapPlayer;
        private final Method adaptLocation;
        private final Method adaptWorld;
        private final Object sessionManager;
        private final Method hasBypass;

        private Api(Object plugin, Method getPlayer, Method getPlayerWorld, Constructor<?> locationConstructor,
                    Object query, Method queryState, Object entryFlag, Method wrapPlayer,
                    Method adaptLocation, Method adaptWorld, Object sessionManager,
                    Method hasBypass) {
            this.plugin = plugin;
            this.getPlayer = getPlayer;
            this.getPlayerWorld = getPlayerWorld;
            this.locationConstructor = locationConstructor;
            this.query = query;
            this.queryState = queryState;
            this.entryFlag = entryFlag;
            this.flagComponentType = queryState.getParameterTypes()[2].getComponentType();
            this.wrapPlayer = wrapPlayer;
            this.adaptLocation = adaptLocation;
            this.adaptWorld = adaptWorld;
            this.sessionManager = sessionManager;
            this.hasBypass = hasBypass;
        }

        static Api resolve() throws Exception {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "WorldGuard");
            if (plugin == null || !isEnabled(plugin)) throw new IllegalStateException("WorldGuard is unavailable");

            ClassLoader pluginLoader = plugin.getClass().getClassLoader();
            Class<?> playerClass = Class.forName("org.bukkit.entity.Player", true, pluginLoader);
            Class<?> worldClass = Class.forName("org.bukkit.World", true, pluginLoader);
            Class<?> locationClass = Class.forName("org.bukkit.Location", true, pluginLoader);
            Method getPlayer = bukkitClass.getMethod("getPlayer", UUID.class);
            Method getPlayerWorld = playerClass.getMethod("getWorld");
            Constructor<?> locationConstructor = locationClass.getConstructor(
                    worldClass, double.class, double.class, double.class);

            try {
                Object query = createLegacyQuery(plugin);
                Object entryFlag = staticField(pluginLoader,
                        "com.sk89q.worldguard.protection.flags.DefaultFlag", "ENTRY");
                Method queryState = findQueryState(query.getClass(), locationClass, playerClass);
                if (queryState != null) {
                    Object sessionManager = plugin.getClass().getMethod("getSessionManager").invoke(plugin);
                    Method hasBypass = findCompatibleMethod(sessionManager.getClass(), "hasBypass",
                            playerClass, worldClass);
                    return new Api(plugin, getPlayer, getPlayerWorld, locationConstructor, query,
                            queryState, entryFlag, null, null, null, sessionManager, hasBypass);
                }
            } catch (ReflectiveOperationException ignored) {
                // WorldGuard 7 moved the region API behind its platform object.
            }

            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, pluginLoader);
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuard.getClass().getMethod("getPlatform").invoke(worldGuard);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Object entryFlag = staticField(pluginLoader,
                    "com.sk89q.worldguard.protection.flags.Flags", "ENTRY");

            Method wrapPlayer = findSingleArgumentMethod(plugin.getClass(), "wrapPlayer", playerClass);
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, pluginLoader);
            Method adaptLocation = findStaticAdapter(adapterClass, locationClass);
            Method adaptWorld = findStaticAdapter(adapterClass, worldClass);
            Method queryState = findQueryState(query.getClass(), adaptLocation.getReturnType(),
                    wrapPlayer.getReturnType());
            if (queryState == null) throw new NoSuchMethodException("WorldGuard queryState");

            Object sessionManager = platform.getClass().getMethod("getSessionManager").invoke(platform);
            Method hasBypass = findCompatibleMethod(sessionManager.getClass(), "hasBypass",
                    wrapPlayer.getReturnType(), adaptWorld.getReturnType());
            return new Api(plugin, getPlayer, getPlayerWorld, locationConstructor, query, queryState,
                    entryFlag, wrapPlayer, adaptLocation, adaptWorld, sessionManager, hasBypass);
        }

        Access entryAccess(UUID playerId, double x, double y, double z) throws Exception {
            Object bukkitPlayer = getPlayer.invoke(null, playerId);
            if (bukkitPlayer == null) return Access.UNAVAILABLE;
            Object bukkitWorld = getPlayerWorld.invoke(bukkitPlayer);
            Object bukkitLocation = locationConstructor.newInstance(bukkitWorld, x, y, z);

            Object actor = bukkitPlayer;
            Object world = bukkitWorld;
            Object location = bukkitLocation;
            if (wrapPlayer != null) {
                actor = wrapPlayer.invoke(plugin, bukkitPlayer);
                world = adaptWorld.invoke(null, bukkitWorld);
                location = adaptLocation.invoke(null, bukkitLocation);
            }

            if (hasBypass != null && Boolean.TRUE.equals(hasBypass.invoke(sessionManager, actor, world))) {
                return Access.ALLOWED;
            }

            Object flags = Array.newInstance(flagComponentType, 1);
            Array.set(flags, 0, entryFlag);
            Object state = queryState.invoke(query, new Object[] {location, actor, flags});
            return state != null && "DENY".equalsIgnoreCase(String.valueOf(state))
                    ? Access.DENIED : Access.ALLOWED;
        }

        private static Object createLegacyQuery(Object plugin) throws Exception {
            try {
                Object container = plugin.getClass().getMethod("getRegionContainer").invoke(plugin);
                return container.getClass().getMethod("createQuery").invoke(container);
            } catch (NoSuchMethodException ignored) {
                return new Object();
            }
        }

        private static boolean isEnabled(Object plugin) {
            try {
                return Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin));
            } catch (Exception ignored) {
                return true;
            }
        }

        private static Object staticField(ClassLoader loader, String className, String fieldName)
                throws Exception {
            return Class.forName(className, true, loader).getField(fieldName).get(null);
        }

        private static Method findQueryState(Class<?> type, Class<?> locationType, Class<?> actorType) {
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"queryState".equals(method.getName()) || parameters.length != 3
                        || !parameters[2].isArray()) continue;
                if (parameters[0].isAssignableFrom(locationType)
                        && parameters[1].isAssignableFrom(actorType)) return method;
            }
            return null;
        }

        private static Method findCompatibleMethod(Class<?> type, String name,
                                                   Class<?> first, Class<?> second) {
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (name.equals(method.getName()) && parameters.length == 2
                        && parameters[0].isAssignableFrom(first)
                        && parameters[1].isAssignableFrom(second)) return method;
            }
            return null;
        }

        private static Method findSingleArgumentMethod(Class<?> type, String name, Class<?> argument) {
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (name.equals(method.getName()) && parameters.length == 1
                        && parameters[0].isAssignableFrom(argument)) return method;
            }
            throw new IllegalStateException("Missing " + name);
        }

        private static Method findStaticAdapter(Class<?> type, Class<?> argument) {
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("adapt".equals(method.getName()) && Modifier.isStatic(method.getModifiers())
                        && parameters.length == 1 && parameters[0].isAssignableFrom(argument)) return method;
            }
            throw new IllegalStateException("Missing Bukkit adapter");
        }

    }
}
