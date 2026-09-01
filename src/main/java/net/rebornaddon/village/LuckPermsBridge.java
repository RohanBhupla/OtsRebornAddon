package net.rebornaddon.village;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.rebornaddon.config.RebornAddonConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public final class LuckPermsBridge {
    public interface VillageLookupCallback {
        void onLookup(EntityPlayerMP player, Village village);
    }

    public interface GroupsLookupCallback {
        void onLookup(EntityPlayerMP player, Set<String> groups);
    }

    public interface GroupMembersCallback {
        void onLookup(Map<UUID, String> members);
    }

    public interface ChangeCallback {
        void onComplete(boolean success);
    }

    public static final LuckPermsBridge INSTANCE = new LuckPermsBridge();

    private static final long PROVIDER_CACHE_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final long VILLAGE_CACHE_NANOS = TimeUnit.SECONDS.toNanos(20L);
    private static final long NAME_CACHE_NANOS = TimeUnit.MINUTES.toNanos(10L);

    private final Map<UUID, VillageCacheEntry> villageCache = new ConcurrentHashMap<UUID, VillageCacheEntry>();
    private final Map<UUID, GroupsCacheEntry> groupsCache = new ConcurrentHashMap<UUID, GroupsCacheEntry>();
    private final Map<String, GroupMembersCacheEntry> membersCache =
            new ConcurrentHashMap<String, GroupMembersCacheEntry>();
    private final Map<UUID, NameCacheEntry> nameCache = new ConcurrentHashMap<UUID, NameCacheEntry>();

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

    public void findGroups(EntityPlayerMP player, GroupsLookupCallback callback) {
        findGroups(player, false, callback);
    }

    public void findGroups(EntityPlayerMP player, boolean forceRefresh, GroupsLookupCallback callback) {
        if (player == null || callback == null) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        UUID id = playerId(player);
        if (server == null || id == null) {
            callback.onLookup(player, Collections.<String>emptySet());
            return;
        }

        GroupsCacheEntry cached = forceRefresh ? null : cachedGroups(id);
        if (cached != null) {
            callbackGroupsOnServer(server, id, callback, cached.groups);
            return;
        }

        Object api = resolveLuckPerms();
        if (api == null) {
            callbackGroupsOnServer(server, id, callback, Collections.<String>emptySet());
            return;
        }

        try {
            Object userManager = invoke(api, "getUserManager");
            Object user = getLoadedUser(userManager, id);
            if (user != null) {
                finishGroupsLookup(server, id, callback, user);
                return;
            }

            Object loaded = loadUserObject(userManager, id, playerName(player));
            if (loaded instanceof CompletableFuture) {
                ((CompletableFuture<?>) loaded).handle(new BiFunction<Object, Throwable, Object>() {
                    @Override
                    public Object apply(Object value, Throwable throwable) {
                        finishGroupsLookup(server, id, callback, throwable == null ? value : null);
                        return null;
                    }
                });
            } else {
                finishGroupsLookup(server, id, callback, loaded);
            }
        } catch (Throwable ignored) {
            callbackGroupsOnServer(server, id, callback, Collections.<String>emptySet());
        }
    }

    public void findGroupMembers(MinecraftServer server, String group, boolean forceRefresh,
                                 GroupMembersCallback callback) {
        if (server == null || callback == null) {
            return;
        }
        final String normalized = normalizeGroup(group);
        if (normalized.isEmpty()) {
            callback.onLookup(Collections.<UUID, String>emptyMap());
            return;
        }

        GroupMembersCacheEntry cached = membersCache.get(normalized);
        if (!forceRefresh && cached != null && System.nanoTime() < cached.expiresAt) {
            callbackMembersOnServer(server, callback, cached.members);
            return;
        }

        Object api = resolveLuckPerms();
        if (api == null) {
            callbackMembersOnServer(server, callback, Collections.<UUID, String>emptyMap());
            return;
        }

        try {
            ClassLoader loader = api.getClass().getClassLoader();
            Object userManager = invoke(api, "getUserManager");
            Class<?> matcherClass = Class.forName("net.luckperms.api.node.matcher.NodeMatcher", true, loader);
            Object matcher = matcherClass.getMethod("key", String.class).invoke(null, "group." + normalized);
            Method search = findMethod(userManager.getClass(), "searchAll", 1);
            if (search == null) {
                throw new NoSuchMethodException("searchAll");
            }
            Object searched = search.invoke(userManager, matcher);
            if (searched instanceof CompletableFuture) {
                ((CompletableFuture<?>) searched).handle(new BiFunction<Object, Throwable, Object>() {
                    @Override
                    public Object apply(Object result, Throwable throwable) {
                        Set<UUID> ids = throwable == null ? memberIds(result) : Collections.<UUID>emptySet();
                        resolveMemberNames(server, userManager, normalized, ids, callback);
                        return null;
                    }
                });
            } else {
                resolveMemberNames(server, userManager, normalized, memberIds(searched), callback);
            }
        } catch (Throwable ignored) {
            callbackMembersOnServer(server, callback, Collections.<UUID, String>emptyMap());
        }
    }

    public void changeGroups(MinecraftServer server, UUID id, String username,
                             Collection<String> add, Collection<String> remove,
                             ChangeCallback callback) {
        if (server == null || id == null) {
            completeChange(server, callback, false);
            return;
        }
        Object api = resolveLuckPerms();
        if (api == null) {
            fallbackGroupChanges(server, id, username, add, remove, callback);
            return;
        }

        try {
            Object userManager = invoke(api, "getUserManager");
            Object loaded = getLoadedUser(userManager, id);
            if (loaded == null) {
                loaded = loadUserObject(userManager, id, username == null ? "" : username);
            }
            if (loaded instanceof CompletableFuture) {
                final Object manager = userManager;
                ((CompletableFuture<?>) loaded).handle(new BiFunction<Object, Throwable, Object>() {
                    @Override
                    public Object apply(Object user, Throwable throwable) {
                        if (throwable != null || user == null) {
                            fallbackGroupChanges(server, id, username, add, remove, callback);
                        } else {
                            applyGroupChanges(server, manager, user, id, username, add, remove, callback);
                        }
                        return null;
                    }
                });
            } else {
                applyGroupChanges(server, userManager, loaded, id, username, add, remove, callback);
            }
        } catch (Throwable ignored) {
            fallbackGroupChanges(server, id, username, add, remove, callback);
        }
    }

    public void invalidateGroup(String group) {
        String normalized = normalizeGroup(group);
        if (!normalized.isEmpty()) {
            membersCache.remove(normalized);
        }
    }

    public void invalidateUser(UUID id) {
        if (id != null) {
            villageCache.remove(id);
            groupsCache.remove(id);
        }
    }

    public void resetCaches() {
        villageCache.clear();
        groupsCache.clear();
        membersCache.clear();
        nameCache.clear();
    }

    public void applyVillageAsync(EntityPlayerMP player, Village selected, ChangeCallback callback) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (player == null || selected == null) {
            completeChange(server, callback, false);
            return;
        }
        UUID id = playerId(player);
        if (server == null || id == null) {
            completeChange(server, callback, false);
            return;
        }
        List<String> remove = villageGroups();
        changeGroups(server, id, playerName(player), Collections.singleton(selected.group()), remove,
                new ChangeCallback() {
                    @Override
                    public void onComplete(boolean success) {
                        if (success) {
                            cacheVillage(id, selected);
                        }
                        if (callback != null) {
                            callback.onComplete(success);
                        }
                    }
                });
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

    private GroupsCacheEntry cachedGroups(UUID id) {
        GroupsCacheEntry entry = groupsCache.get(id);
        if (entry == null) {
            return null;
        }
        if (System.nanoTime() >= entry.expiresAt) {
            groupsCache.remove(id, entry);
            return null;
        }
        return entry;
    }

    private void finishGroupsLookup(MinecraftServer server, UUID id, GroupsLookupCallback callback, Object user) {
        Set<String> groups = user == null ? Collections.<String>emptySet() : groupNamesOnUser(user);
        Set<String> immutable = Collections.unmodifiableSet(new HashSet<String>(groups));
        groupsCache.put(id, new GroupsCacheEntry(immutable, System.nanoTime() + groupCacheNanos()));
        Village village = VillageRoles.villageForGroups(immutable);
        cacheVillage(id, village);
        callbackGroupsOnServer(server, id, callback, immutable);
    }

    private Set<String> groupNamesOnUser(Object user) {
        Set<String> groups = new HashSet<String>();
        addGroupNames(groups, invokeAny(user, "getNodes"));
        Object data = invokeAny(user, "data");
        if (data != null) {
            addGroupNames(groups, invokeAny(data, "toCollection"));
            addGroupNames(groups, invokeAny(data, "getNodes"));
            addGroupNames(groups, invokeAny(data, "toSet"));
        }
        return groups;
    }

    private void addGroupNames(Set<String> groups, Object nodes) {
        if (nodes instanceof Iterable) {
            for (Object node : (Iterable<?>) nodes) {
                String group = groupName(node);
                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }
        } else if (nodes instanceof Object[]) {
            for (Object node : (Object[]) nodes) {
                String group = groupName(node);
                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }
        }
    }

    private String groupName(Object node) {
        if (node == null) {
            return "";
        }
        Object value = invokeAny(node, "getValue");
        if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
            return "";
        }
        Object direct = invokeAny(node, "getGroupName", "getGroup");
        if (direct instanceof String) {
            return normalizeGroup((String) direct);
        }
        Object key = invokeAny(node, "getKey");
        if (key instanceof String) {
            String text = ((String) key).trim().toLowerCase(Locale.ROOT);
            return text.startsWith("group.") ? normalizeGroup(text.substring(6)) : "";
        }
        return "";
    }

    private void callbackGroupsOnServer(final MinecraftServer server, final UUID id,
                                        final GroupsLookupCallback callback, final Set<String> groups) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(id);
                if (current != null) {
                    callback.onLookup(current, groups);
                }
            }
        };
        if (server.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            server.addScheduledTask(task);
        }
    }

    private Set<UUID> memberIds(Object result) {
        if (!(result instanceof Map)) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<UUID>();
        for (Object key : ((Map<?, ?>) result).keySet()) {
            if (key instanceof UUID) {
                ids.add((UUID) key);
            }
        }
        return ids;
    }

    private void resolveMemberNames(final MinecraftServer server, final Object userManager, final String group,
                                    Set<UUID> ids, final GroupMembersCallback callback) {
        if (!server.isCallingFromMinecraftThread()) {
            final Set<UUID> copiedIds = new HashSet<UUID>(ids);
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    resolveMemberNames(server, userManager, group, copiedIds, callback);
                }
            });
            return;
        }
        if (ids.isEmpty()) {
            cacheAndSendMembers(server, group, Collections.<UUID, String>emptyMap(), callback);
            return;
        }

        final Map<UUID, String> names = new ConcurrentHashMap<UUID, String>();
        final List<UUID> unresolved = new ArrayList<UUID>();
        long now = System.nanoTime();
        for (UUID id : ids) {
            EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(id);
            if (online != null) {
                names.put(id, online.getName());
                cacheName(id, online.getName());
                continue;
            }
            NameCacheEntry cached = nameCache.get(id);
            if (cached != null && now < cached.expiresAt && !cached.name.isEmpty()) {
                names.put(id, cached.name);
            } else {
                unresolved.add(id);
            }
        }

        if (unresolved.isEmpty()) {
            cacheAndSendMembers(server, group, names, callback);
            return;
        }

        final AtomicInteger pending = new AtomicInteger(unresolved.size());
        for (final UUID id : unresolved) {
            try {
                Method lookup = userManager.getClass().getMethod("lookupUsername", UUID.class);
                Object result = lookup.invoke(userManager, id);
                if (result instanceof CompletableFuture) {
                    ((CompletableFuture<?>) result).handle(new BiFunction<Object, Throwable, Object>() {
                        @Override
                        public Object apply(Object value, Throwable throwable) {
                            if (throwable == null && value instanceof String && !((String) value).trim().isEmpty()) {
                                String name = ((String) value).trim();
                                names.put(id, name);
                                cacheName(id, name);
                            }
                            if (pending.decrementAndGet() == 0) {
                                cacheAndSendMembers(server, group, names, callback);
                            }
                            return null;
                        }
                    });
                } else {
                    if (result instanceof String && !((String) result).trim().isEmpty()) {
                        String name = ((String) result).trim();
                        names.put(id, name);
                        cacheName(id, name);
                    }
                    if (pending.decrementAndGet() == 0) {
                        cacheAndSendMembers(server, group, names, callback);
                    }
                }
            } catch (Throwable ignored) {
                if (pending.decrementAndGet() == 0) {
                    cacheAndSendMembers(server, group, names, callback);
                }
            }
        }
    }

    private void cacheAndSendMembers(MinecraftServer server, String group, Map<UUID, String> names,
                                     GroupMembersCallback callback) {
        List<Map.Entry<UUID, String>> entries = new ArrayList<Map.Entry<UUID, String>>(names.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<UUID, String>>() {
            @Override
            public int compare(Map.Entry<UUID, String> left, Map.Entry<UUID, String> right) {
                return left.getValue().compareToIgnoreCase(right.getValue());
            }
        });
        Map<UUID, String> sorted = new LinkedHashMap<UUID, String>();
        for (Map.Entry<UUID, String> entry : entries) {
            if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                sorted.put(entry.getKey(), entry.getValue());
            }
        }
        Map<UUID, String> immutable = Collections.unmodifiableMap(sorted);
        membersCache.put(group, new GroupMembersCacheEntry(immutable,
                System.nanoTime() + groupCacheNanos()));
        callbackMembersOnServer(server, callback, immutable);
    }

    private void callbackMembersOnServer(MinecraftServer server, final GroupMembersCallback callback,
                                         final Map<UUID, String> members) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                callback.onLookup(members);
            }
        };
        if (server.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            server.addScheduledTask(task);
        }
    }

    private void cacheName(UUID id, String name) {
        if (id != null && name != null && !name.trim().isEmpty()) {
            nameCache.put(id, new NameCacheEntry(name.trim(), System.nanoTime() + NAME_CACHE_NANOS));
        }
    }

    private void applyGroupChanges(final MinecraftServer server, final Object userManager, Object user,
                                   final UUID id, final String username, final Collection<String> add,
                                   final Collection<String> remove,
                                   final ChangeCallback callback) {
        if (user == null) {
            fallbackGroupChanges(server, id, username, add, remove, callback);
            return;
        }
        try {
            ClassLoader loader = userManager.getClass().getClassLoader();
            Object data = invoke(user, "data");
            for (String group : safeGroups(remove)) {
                Object node = buildGroupNode(loader, group);
                invokeNodeMap(data, "remove", loader, node);
            }
            for (String group : safeGroups(add)) {
                Object node = buildGroupNode(loader, group);
                invokeNodeMap(data, "add", loader, node);
            }
            invalidateChangedGroups(add, remove);
            invalidateUser(id);

            Method save = findMethod(userManager.getClass(), "saveUser", 1);
            if (save == null) {
                fallbackGroupChanges(server, id, username, add, remove, callback);
                return;
            }
            Object saved = save.invoke(userManager, user);
            if (saved instanceof CompletableFuture) {
                ((CompletableFuture<?>) saved).handle(new BiFunction<Object, Throwable, Object>() {
                    @Override
                    public Object apply(Object value, Throwable throwable) {
                        if (throwable == null) {
                            completeChange(server, callback, true);
                        } else {
                            fallbackGroupChanges(server, id, username, add, remove, callback);
                        }
                        return null;
                    }
                });
            } else {
                completeChange(server, callback, true);
            }
        } catch (Throwable ignored) {
            fallbackGroupChanges(server, id, username, add, remove, callback);
        }
    }

    private void fallbackGroupChanges(final MinecraftServer server, final UUID id,
                                      final String username, final Collection<String> add,
                                      final Collection<String> remove, final ChangeCallback callback) {
        if (server == null || id == null) {
            completeChange(server, callback, false);
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                completeChange(server, callback,
                        changeGroupsWithCommands(server, id, username, add, remove));
            }
        };
        if (server.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            server.addScheduledTask(task);
        }
    }

    private void completeChange(MinecraftServer server, final ChangeCallback callback, final boolean success) {
        if (callback == null) {
            return;
        }
        if (server == null) {
            callback.onComplete(success);
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                callback.onComplete(success);
            }
        };
        if (server.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            server.addScheduledTask(task);
        }
    }

    private boolean changeGroupsWithCommands(MinecraftServer server, UUID id, String username,
                                             Collection<String> add, Collection<String> remove) {
        boolean changed = false;
        for (String group : safeGroups(remove)) {
            changed |= dispatchGroupCommand(server, id, username, group, false);
        }
        for (String group : safeGroups(add)) {
            changed |= dispatchGroupCommand(server, id, username, group, true);
        }
        invalidateChangedGroups(add, remove);
        invalidateUser(id);
        return changed;
    }

    public void clearVillageAsync(EntityPlayerMP player, ChangeCallback callback) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (player == null) {
            completeChange(server, callback, false);
            return;
        }
        UUID id = playerId(player);
        if (server == null || id == null) {
            completeChange(server, callback, false);
            return;
        }
        changeGroups(server, id, playerName(player), Collections.<String>emptyList(), villageGroups(), callback);
    }

    private List<String> villageGroups() {
        List<String> groups = new ArrayList<String>();
        for (Village village : Village.all()) {
            groups.add(village.group());
            groups.add(VillageRoles.kage(village));
            groups.add(VillageRoles.advisor(village));
            groups.add(VillageRoles.anbu(village));
            groups.add(VillageRoles.captain(village));
            groups.add(VillageRoles.legacyCaptain(village));
        }
        return groups;
    }

    private void invalidateChangedGroups(Collection<String> add, Collection<String> remove) {
        for (String group : safeGroups(add)) {
            invalidateGroup(group);
        }
        for (String group : safeGroups(remove)) {
            invalidateGroup(group);
        }
    }

    private List<String> safeGroups(Collection<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String group : groups) {
            String normalized = normalizeGroup(group);
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalizeGroup(String group) {
        if (group == null) {
            return "";
        }
        String normalized = group.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,64}") ? normalized : "";
    }

    private static long groupCacheNanos() {
        return TimeUnit.SECONDS.toNanos(Math.max(10, RebornAddonConfig.villageLeadershipCacheSeconds));
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

    private Object loadUserObject(Object userManager, UUID id, String username) throws Exception {
        try {
            Method method = userManager.getClass().getMethod("loadUser", UUID.class, String.class);
            return method.invoke(userManager, id, username);
        } catch (NoSuchMethodException ignored) {
            Method method = userManager.getClass().getMethod("loadUser", UUID.class);
            return method.invoke(userManager, id);
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

    private boolean dispatchGroupCommand(MinecraftServer server, UUID id, String username,
                                         String group, boolean add) {
        if (server == null || id == null || group == null || group.trim().isEmpty()) {
            return false;
        }

        String cleanName = username == null ? "" : username.trim();
        String target = cleanName.matches("[A-Za-z0-9_]{1,16}") ? cleanName : id.toString();
        String command = "lp user " + target + " parent " + (add ? "add " : "remove ")
                + group.trim().toLowerCase(Locale.ROOT);
        try {
            if (server.getCommandManager().executeCommand(server, command) > 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return dispatchBukkitCommand(command);
    }

    private boolean dispatchBukkitCommand(String command) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object console = bukkit.getMethod("getConsoleSender").invoke(null);
            Method dispatch = findMethod(bukkit, "dispatchCommand", 2);
            Object result = dispatch == null ? null : dispatch.invoke(null, console, command);
            return Boolean.TRUE.equals(result);
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

    private static final class GroupsCacheEntry {
        private final Set<String> groups;
        private final long expiresAt;

        private GroupsCacheEntry(Set<String> groups, long expiresAt) {
            this.groups = groups;
            this.expiresAt = expiresAt;
        }
    }

    private static final class GroupMembersCacheEntry {
        private final Map<UUID, String> members;
        private final long expiresAt;

        private GroupMembersCacheEntry(Map<UUID, String> members, long expiresAt) {
            this.members = members;
            this.expiresAt = expiresAt;
        }
    }

    private static final class NameCacheEntry {
        private final String name;
        private final long expiresAt;

        private NameCacheEntry(String name, long expiresAt) {
            this.name = name;
            this.expiresAt = expiresAt;
        }
    }
}
