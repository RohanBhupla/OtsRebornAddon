package net.rebornaddon.village;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public final class LuckPermsBridge {
    public interface VillageLookupCallback {
        void onLookup(EntityPlayerMP player, Village village);
    }

    public static final LuckPermsBridge INSTANCE = new LuckPermsBridge();

    private static final long PROVIDER_CACHE_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final long VILLAGE_CACHE_NANOS = TimeUnit.SECONDS.toNanos(20L);

    private final Map<UUID, VillageCacheEntry> villageCache = new ConcurrentHashMap<UUID, VillageCacheEntry>();

    private Object luckPerms;
    private boolean checked;
    private long checkedAt;
    private Object bukkitPlugin;
    private boolean bukkitChecked;
    private long bukkitCheckedAt;

    private LuckPermsBridge() {
    }

    public boolean isAvailable(MinecraftServer server) {
        return resolveLuckPerms() != null || resolveBukkitPlugin("LuckPerms") != null;
    }

    public String providerName() {
        Object provider = resolveLuckPerms();
        if (provider != null) {
            return provider.getClass().getName();
        }

        Object plugin = resolveBukkitPlugin("LuckPerms");
        return plugin == null ? "none" : plugin.getClass().getName();
    }

    public boolean hasBukkitPlugin() {
        return resolveBukkitPlugin("LuckPerms") != null;
    }

    public void findVillage(EntityPlayerMP player, VillageLookupCallback callback) {
        if (player == null || callback == null) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        UUID id = playerId(player);
        if (server == null || id == null) {
            callback.onLookup(player, null);
            return;
        }

        VillageCacheEntry cached = cachedVillage(id);
        if (cached != null) {
            callbackOnServer(server, id, callback, cached.village);
            return;
        }

        Object api = resolveLuckPerms();
        if (api == null) {
            cacheVillage(id, null);
            callbackOnServer(server, id, callback, null);
            return;
        }

        try {
            Object userManager = invoke(api, "getUserManager");
            Object user = getLoadedUser(userManager, id);
            if (user != null) {
                Village village = findVillageOnUser(user);
                cacheVillage(id, village);
                callbackOnServer(server, id, callback, village);
                return;
            }

            Object loaded = loadUserObject(userManager, id, playerName(player));
            if (loaded instanceof CompletableFuture) {
                handleLoadedUser(server, id, callback, (CompletableFuture<?>) loaded);
                return;
            }

            Village village = findVillageOnUser(loaded);
            cacheVillage(id, village);
            callbackOnServer(server, id, callback, village);
        } catch (Throwable ignored) {
            cacheVillage(id, null);
            callbackOnServer(server, id, callback, null);
        }
    }

    public Village findKnownVillage(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }

        UUID id = playerId(player);
        if (id == null) {
            return null;
        }

        VillageCacheEntry cached = cachedVillage(id);
        if (cached != null) {
            return cached.village;
        }

        Object api = resolveLuckPerms();
        if (api == null) {
            return null;
        }

        try {
            Object userManager = invoke(api, "getUserManager");
            Object user = getLoadedUser(userManager, id);
            Village village = findVillageOnUser(user);
            cacheVillage(id, village);
            return village;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean applyVillage(EntityPlayerMP player, Village selected) {
        if (player == null || selected == null) {
            return false;
        }

        UUID id = playerId(player);
        if (id == null) {
            return false;
        }

        if (applyThroughApi(id, playerName(player), selected)) {
            cacheVillage(id, selected);
            return true;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return false;
        }

        for (Village village : Village.all()) {
            if (village != selected) {
                dispatchGroupCommand(server, id, village.group(), false);
            }
        }

        boolean applied = dispatchGroupCommand(server, id, selected.group(), true);
        if (applied) {
            cacheVillage(id, selected);
        }
        return applied;
    }

    public boolean clearVillage(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }

        UUID id = playerId(player);
        if (id == null) {
            return false;
        }

        villageCache.remove(id);
        if (clearThroughApi(id, playerName(player))) {
            return true;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return false;
        }

        boolean changed = false;
        for (Village village : Village.all()) {
            changed |= dispatchGroupCommand(server, id, village.group(), false);
        }
        return changed;
    }

    private void handleLoadedUser(final MinecraftServer server, final UUID id, final VillageLookupCallback callback,
                                  CompletableFuture<?> future) {
        future.handle(new BiFunction<Object, Throwable, Object>() {
            @Override
            public Object apply(Object user, Throwable throwable) {
                Village village = null;
                if (throwable == null && user != null) {
                    try {
                        village = findVillageOnUser(user);
                    } catch (Throwable ignored) {
                    }
                }

                cacheVillage(id, village);
                callbackOnServer(server, id, callback, village);
                return null;
            }
        });
    }

    private void callbackOnServer(final MinecraftServer server, final UUID id, final VillageLookupCallback callback,
                                  final Village village) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(id);
                if (current != null) {
                    callback.onLookup(current, village);
                }
            }
        };

        if (server.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            server.addScheduledTask(task);
        }
    }

    private VillageCacheEntry cachedVillage(UUID id) {
        VillageCacheEntry entry = villageCache.get(id);
        if (entry == null) {
            return null;
        }

        if (System.nanoTime() >= entry.expiresAt) {
            villageCache.remove(id, entry);
            return null;
        }
        return entry;
    }

    private void cacheVillage(UUID id, Village village) {
        if (id != null) {
            villageCache.put(id, new VillageCacheEntry(village, System.nanoTime() + VILLAGE_CACHE_NANOS));
        }
    }

    private boolean applyThroughApi(UUID id, String username, Village selected) {
        Object api = resolveLuckPerms();
        if (api == null) {
            return false;
        }

        try {
            ClassLoader loader = api.getClass().getClassLoader();
            Object userManager = invoke(api, "getUserManager");
            Object user = loadUser(userManager, id, username);
            if (user == null) {
                return false;
            }

            Object data = invoke(user, "data");
            for (Village village : Village.all()) {
                Object node = buildGroupNode(loader, village.group());
                if (node != null) {
                    invokeNodeMap(data, "remove", loader, node);
                }
            }

            Object node = buildGroupNode(loader, selected.group());
            if (node == null) {
                return false;
            }
            invokeNodeMap(data, "add", loader, node);
            saveUser(userManager, user);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean clearThroughApi(UUID id, String username) {
        Object api = resolveLuckPerms();
        if (api == null) {
            return false;
        }

        try {
            ClassLoader loader = api.getClass().getClassLoader();
            Object userManager = invoke(api, "getUserManager");
            Object user = loadUser(userManager, id, username);
            if (user == null) {
                return false;
            }

            Object data = invoke(user, "data");
            for (Village village : Village.all()) {
                Object node = buildGroupNode(loader, village.group());
                if (node != null) {
                    invokeNodeMap(data, "remove", loader, node);
                }
            }

            saveUser(userManager, user);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object getLoadedUser(Object userManager, UUID id) {
        if (userManager == null || id == null) {
            return null;
        }

        try {
            Method method = userManager.getClass().getMethod("getUser", UUID.class);
            return method.invoke(userManager, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object loadUser(Object userManager, UUID id, String username) throws Exception {
        Object loaded = loadUserObject(userManager, id, username);
        if (loaded instanceof CompletableFuture) {
            return ((CompletableFuture<?>) loaded).get(5, TimeUnit.SECONDS);
        }
        return loaded;
    }

    private Object loadUserObject(Object userManager, UUID id, String username) throws Exception {
        try {
            Method method = userManager.getClass().getMethod("loadUser", UUID.class, String.class);
            return method.invoke(userManager, id, username);
        } catch (NoSuchMethodException ignored) {
            Method method = userManager.getClass().getMethod("loadUser", UUID.class);
            return method.invoke(userManager, id);
        }
    }

    private void saveUser(Object userManager, Object user) throws Exception {
        Method method = findMethod(userManager.getClass(), "saveUser", 1);
        if (method == null) {
            return;
        }
        Object future = method.invoke(userManager, user);
        if (future instanceof CompletableFuture) {
            ((CompletableFuture<?>) future).get(5, TimeUnit.SECONDS);
        }
    }

    private Object buildGroupNode(ClassLoader loader, String group) throws Exception {
        Class<?> nodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode", true, loader);
        Object builder = nodeClass.getMethod("builder", String.class).invoke(null, group);
        return invoke(builder, "build");
    }

    private Object invokeNodeMap(Object data, String methodName, ClassLoader loader, Object node) throws Exception {
        Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node", true, loader);
        Method method = data.getClass().getMethod(methodName, nodeClass);
        return method.invoke(data, node);
    }

    private Village findVillageOnUser(Object user) {
        if (user == null) {
            return null;
        }

        Village village = findVillageInNodes(invokeAny(user, "getNodes"));
        if (village != null) {
            return village;
        }

        Object data = invokeAny(user, "data");
        if (data != null) {
            village = findVillageInNodes(invokeAny(data, "toCollection"));
            if (village != null) {
                return village;
            }
            village = findVillageInNodes(invokeAny(data, "getNodes"));
            if (village != null) {
                return village;
            }
            village = findVillageInNodes(invokeAny(data, "toSet"));
            if (village != null) {
                return village;
            }
        }

        return null;
    }

    private Village findVillageInNodes(Object nodes) {
        if (nodes instanceof Iterable) {
            for (Object node : (Iterable<?>) nodes) {
                Village village = findVillageInNode(node);
                if (village != null) {
                    return village;
                }
            }
            return null;
        }

        if (nodes instanceof Object[]) {
            Object[] array = (Object[]) nodes;
            for (int i = 0; i < array.length; i++) {
                Village village = findVillageInNode(array[i]);
                if (village != null) {
                    return village;
                }
            }
        }

        return null;
    }

    private Village findVillageInNode(Object node) {
        if (node == null) {
            return null;
        }

        Object value = invokeAny(node, "getValue");
        if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
            return null;
        }

        Object group = invokeAny(node, "getGroupName", "getGroup");
        if (group instanceof String) {
            Village village = Village.byGroup((String) group);
            if (village != null) {
                return village;
            }
        }

        Object key = invokeAny(node, "getKey");
        if (!(key instanceof String)) {
            return null;
        }

        String text = ((String) key).trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("group.")) {
            return Village.byGroup(text.substring("group.".length()));
        }
        return Village.byGroup(text);
    }

    private boolean dispatchGroupCommand(MinecraftServer server, UUID id, String group, boolean add) {
        if (server == null || id == null || group == null || group.trim().isEmpty()) {
            return false;
        }

        try {
            String command = "lp user " + id.toString() + " parent " + (add ? "add " : "remove ") + group.trim().toLowerCase();
            server.getCommandManager().executeCommand(server, command);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private synchronized Object resolveLuckPerms() {
        long now = System.nanoTime();
        if (checked && luckPerms != null) {
            return luckPerms;
        }
        if (checked && now - checkedAt < PROVIDER_CACHE_NANOS) {
            return luckPerms;
        }

        checked = true;
        checkedAt = now;
        luckPerms = tryProvider("net.luckperms.api.LuckPermsProvider", null);
        if (luckPerms == null) {
            luckPerms = tryProvider("me.lucko.luckperms.api.LuckPermsProvider", null);
        }
        if (luckPerms == null) {
            luckPerms = tryStaticApi("me.lucko.luckperms.LuckPerms", "getApi", null);
        }
        if (luckPerms == null) {
            luckPerms = resolveLuckPermsViaPlugin();
        }
        return luckPerms;
    }

    private Object resolveLuckPermsViaPlugin() {
        Object plugin = resolveBukkitPlugin("LuckPerms");
        if (plugin == null) {
            return null;
        }

        ClassLoader loader = plugin.getClass().getClassLoader();
        Object provider = tryProvider("net.luckperms.api.LuckPermsProvider", loader);
        if (provider != null) {
            return provider;
        }
        provider = tryProvider("me.lucko.luckperms.api.LuckPermsProvider", loader);
        if (provider != null) {
            return provider;
        }
        return tryStaticApi("me.lucko.luckperms.LuckPerms", "getApi", loader);
    }

    private Object tryProvider(String className, ClassLoader loader) {
        return tryStaticApi(className, "get", loader);
    }

    private Object tryStaticApi(String className, String methodName, ClassLoader loader) {
        try {
            Class<?> type = loader == null ? Class.forName(className) : Class.forName(className, true, loader);
            return type.getMethod(methodName).invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private synchronized Object resolveBukkitPlugin(String pluginName) {
        if (!"LuckPerms".equals(pluginName)) {
            return findBukkitPlugin(pluginName);
        }

        long now = System.nanoTime();
        if (bukkitChecked && bukkitPlugin != null) {
            return bukkitPlugin;
        }
        if (bukkitChecked && now - bukkitCheckedAt < PROVIDER_CACHE_NANOS) {
            return bukkitPlugin;
        }

        bukkitChecked = true;
        bukkitCheckedAt = now;
        bukkitPlugin = findBukkitPlugin(pluginName);
        return bukkitPlugin;
    }

    private Object findBukkitPlugin(String pluginName) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            if (manager == null) {
                return null;
            }
            Method method = manager.getClass().getMethod("getPlugin", String.class);
            return method.invoke(manager, pluginName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = findMethod(target.getClass(), methodName, 0);
        if (method == null) {
            throw new NoSuchMethodException(methodName);
        }
        return method.invoke(target);
    }

    private static UUID playerId(EntityPlayerMP player) {
        Object profile = invokeAny(player, "getGameProfile", "func_146103_bH", "func_175105_e");
        if (profile != null) {
            Object id = invokeAny(profile, "getId");
            if (id instanceof UUID) {
                return (UUID) id;
            }
        }

        Object id = invokeAny(player, "func_110124_au", "getUniqueID");
        return id instanceof UUID ? (UUID) id : null;
    }

    private static String playerName(EntityPlayerMP player) {
        Object profile = invokeAny(player, "getGameProfile", "func_146103_bH", "func_175105_e");
        if (profile != null) {
            Object name = invokeAny(profile, "getName");
            if (name instanceof String && !((String) name).trim().isEmpty()) {
                return (String) name;
            }
        }

        Object name = invokeAny(player, "func_70005_c_", "getName");
        return name instanceof String ? (String) name : "";
    }

    private static Object invokeAny(Object target, String... names) {
        if (target == null) {
            return null;
        }

        for (String name : names) {
            try {
                Method method = findMethod(target.getClass(), name, 0);
                if (method != null) {
                    return method.invoke(target);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static final class VillageCacheEntry {
        private final Village village;
        private final long expiresAt;

        private VillageCacheEntry(Village village, long expiresAt) {
            this.village = village;
            this.expiresAt = expiresAt;
        }
    }
}
