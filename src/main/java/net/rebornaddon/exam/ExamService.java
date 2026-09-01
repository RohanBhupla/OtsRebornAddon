package net.rebornaddon.exam;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraft.world.GameType;
import net.rebornaddon.jutsu.JutsuDefinition;
import net.rebornaddon.mode.ModeConfigurationService;
import net.rebornaddon.village.SingleplayerVillageRoles;
import net.rebornaddon.village.LuckPermsBridge;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageRoles;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.integration.PluginInvalidationBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class ExamService {
    public static final ExamService INSTANCE = new ExamService();
    private static final long ACTION_COOLDOWN_MILLIS = 150L;
    private static final long SNAPSHOT_CACHE_MILLIS = 15000L;
    private static final int ACTIVE_RUNTIME_POLL_TICKS = 100;
    private static final int IDLE_RUNTIME_POLL_TICKS = 1200;
    private static final String SPECTATOR_STATE = "RebornExamSpectator";
    private static final Set<String> PRIVATE_FIELDS = privateFields();

    private final Map<UUID, Long> lastAction = new HashMap<UUID, Long>();
    private final Map<UUID, CachedSnapshot> snapshots = new HashMap<UUID, CachedSnapshot>();
    private final Set<UUID> pendingActions = new HashSet<UUID>();
    private final Set<UUID> pendingSnapshots = new HashSet<UUID>();
    private final Set<UUID> pendingRankSync = new HashSet<UUID>();
    private final Map<UUID, String> teleportedMatch = new HashMap<UUID, String>();
    private final Map<String, ActiveMatch> matches = new HashMap<String, ActiveMatch>();
    private final Map<UUID, ActiveMatch> matchByPlayer = new HashMap<UUID, ActiveMatch>();
    private final Set<String> reportedViolations = new HashSet<String>();
    private final Map<UUID, ExamSpectator> spectators = new HashMap<UUID, ExamSpectator>();
    private MinecraftServer server;
    private ExecutorService worker;
    private int runtimeTicks;
    private long invalidationRevision;
    private volatile boolean runtimeRefreshInFlight;

    private ExamService() {
    }

    public void appendPerformanceData(net.minecraft.nbt.NBTTagCompound data) {
        if (data == null) return;
        data.setInteger("RebornExamSnapshots", snapshots.size());
        data.setInteger("RebornExamMatches", matches.size());
        data.setInteger("RebornExamPending", pendingActions.size() + pendingSnapshots.size()
                + pendingRankSync.size());
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "RebornAddon Exam Worker");
                thread.setDaemon(true);
                return thread;
            }
        });
        refreshRuntime();
    }

    public void reset() {
        server = null;
        runtimeTicks = 0;
        runtimeRefreshInFlight = false;
        if (worker != null) worker.shutdownNow();
        worker = null;
        lastAction.clear();
        snapshots.clear();
        pendingActions.clear();
        pendingSnapshots.clear();
        pendingRankSync.clear();
        teleportedMatch.clear();
        matches.clear();
        matchByPlayer.clear();
        reportedViolations.clear();
        spectators.clear();
    }

    public void handleAction(EntityPlayerMP player, String action, String payload) {
        String cleanAction = clean(action, 64);
        if (player == null || cleanAction.isEmpty()) return;
        if ("rank.catalog".equals(cleanAction)) {
            if (isOperator(player)) sendRankCatalog(player);
            else deny(player, "Operator access is required for rank controls.");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastAction.get(player.getUniqueID());
        if (!"snapshot".equals(cleanAction) && previous != null
                && now - previous.longValue() < ACTION_COOLDOWN_MILLIS) return;
        lastAction.put(player.getUniqueID(), Long.valueOf(now));

        if (!ExamPluginBridge.isAvailable()) {
            sendUnavailable(player);
            return;
        }
        boolean forceSnapshot = "snapshot".equals(cleanAction) && force(payload);
        if ("snapshot".equals(cleanAction)) {
            CachedSnapshot cached = snapshots.get(player.getUniqueID());
            if (cached != null) {
                sendCached(player, cached);
                if (!forceSnapshot && now - cached.createdAt < SNAPSHOT_CACHE_MILLIS) return;
            }
            if (pendingSnapshots.contains(player.getUniqueID())) return;
            authorizeSnapshot(player, payload);
            return;
        }
        if (requiresOperator(cleanAction) && !isOperator(player)) {
            deny(player, "Operator access is required for that exam action.");
            return;
        }
        if (requiresAuthor(cleanAction) && !isOperator(player)) {
            authorizeLeadership(player, cleanAction, payload);
            return;
        }
        submit(player, cleanAction, payload, new Access(isOperator(player), false));
    }

    private void authorizeSnapshot(final EntityPlayerMP player, final String payload) {
        if (isOperator(player)) {
            submit(player, "snapshot", payload, new Access(true, true));
            return;
        }
        if (SingleplayerVillageRoles.isTesting(player)) {
            submit(player, "snapshot", payload,
                    new Access(false, isLeadership(SingleplayerVillageRoles.groups(player))));
            return;
        }
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                submit(current, "snapshot", payload, new Access(false, isLeadership(groups)));
            }
        });
    }

    private void authorizeLeadership(final EntityPlayerMP player, final String action,
                                     final String payload) {
        if (SingleplayerVillageRoles.isTesting(player)) {
            if (isLeadership(SingleplayerVillageRoles.groups(player))) {
                submit(player, action, payload, new Access(false, true));
            }
            else deny(player, "Kage or village advisor access is required.");
            return;
        }
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                if (isLeadership(groups)) submit(current, action, payload, new Access(false, true));
                else deny(current, "Kage or village advisor access is required.");
            }
        });
    }

    private void submit(final EntityPlayerMP player, final String action, final String payload,
                        final Access access) {
        if ("exam.create".equals(action) || "exam.update".equals(action)) {
            try {
                String problem = ExamDraftValidator.validate(parseObject(payload));
                if (!problem.isEmpty()) {
                    deny(player, problem);
                    return;
                }
            } catch (Throwable ignored) {
                deny(player, "The exam draft could not be read.");
                return;
            }
        }
        final Set<UUID> pending = "snapshot".equals(action) ? pendingSnapshots : pendingActions;
        if (worker == null || worker.isShutdown() || !pending.add(player.getUniqueID())) return;
        final Object sender = ExamPluginBridge.resolveSender(player);
        final UUID playerId = player.getUniqueID();
        final MinecraftServer currentServer = server;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = "snapshot".equals(action)
                        ? ExamPluginBridge.playerSnapshot(sender)
                        : ExamPluginBridge.applyAction(sender, action, payload == null ? "{}" : payload);
                if (currentServer == null) return;
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(playerId);
                        if (server != currentServer) return;
                        EntityPlayerMP current = currentServer.getPlayerList().getPlayerByUUID(playerId);
                        if (current != null) finish(current, action, response, access);
                    }
                });
            }
        });
    }

    private void finish(EntityPlayerMP player, String action, String[] response, Access access) {
        if (!ExamPluginBridge.successful(response)) {
            sendFailure(player, ExamPluginBridge.message(response));
            return;
        }
        String json = ExamPluginBridge.payload(response);
        try {
            JsonObject snapshot = parseObject(json);
            enrichAccess(snapshot, access);
            applyRankDirective(player, snapshot);
            sendSnapshot(player, snapshot, ExamPluginBridge.message(response));
        } catch (Throwable ignored) {
            sendFailure(player, "The exam service returned malformed data.");
        }
        if (action.startsWith("tournament.") || action.startsWith("exam.approve")
                || action.startsWith("exam.schedule")) refreshRuntime();
    }

    public void syncRank(final EntityPlayerMP player) {
        if (player == null || worker == null || worker.isShutdown()
                || !ExamPluginBridge.isAvailable() || !pendingRankSync.add(player.getUniqueID())) return;
        final Object sender = ExamPluginBridge.resolveSender(player);
        final UUID playerId = player.getUniqueID();
        final MinecraftServer currentServer = server;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = ExamPluginBridge.getRank(sender);
                if (currentServer == null) return;
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        pendingRankSync.remove(playerId);
                        if (server != currentServer || !ExamPluginBridge.successful(response)) return;
                        EntityPlayerMP current = currentServer.getPlayerList().getPlayerByUUID(playerId);
                        if (current == null) return;
                        try {
                            JsonObject data = parseObject(ExamPluginBridge.payload(response));
                            RankService.INSTANCE.applyRank(current, string(data, "id"),
                                    data.has("xpCap") ? data.get("xpCap").getAsLong() : 0L);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        });
    }

    private void applyRankDirective(EntityPlayerMP player, JsonObject snapshot) {
        if (snapshot.has("rankCatalog") && snapshot.get("rankCatalog").isJsonArray()) {
            RankService.INSTANCE.updateCatalog(snapshot.getAsJsonArray("rankCatalog"));
        }
        if (snapshot.has("rankUpdate") && snapshot.get("rankUpdate").isJsonObject()) {
            JsonObject update = snapshot.getAsJsonObject("rankUpdate");
            EntityPlayerMP target = player;
            if (server != null && update.has("playerUuid")) {
                try {
                    EntityPlayerMP resolved = server.getPlayerList().getPlayerByUUID(
                            UUID.fromString(string(update, "playerUuid")));
                    if (resolved != null) target = resolved;
                } catch (Throwable ignored) {
                }
            }
            String id = string(update, "id");
            long cap = update.has("xpCap") ? update.get("xpCap").getAsLong() : 0L;
            RankService.INSTANCE.applyRank(target, id, cap);
            RankService.INSTANCE.notifyAssigned(target, id);
        }
    }

    public void refreshRuntime() {
        if (server == null || worker == null || worker.isShutdown()
                || runtimeRefreshInFlight || !ExamPluginBridge.isAvailable()) return;
        runtimeRefreshInFlight = true;
        final MinecraftServer currentServer = server;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final String[] response = ExamPluginBridge.runtimeSnapshot();
                if (currentServer == null) return;
                currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        runtimeRefreshInFlight = false;
                        if (server == currentServer && ExamPluginBridge.successful(response)) {
                            applyRuntimeSnapshot(response);
                        }
                    }
                });
            }
        });
    }

    private void applyRuntimeSnapshot(String[] response) {
        try {
            JsonObject root = parseObject(ExamPluginBridge.payload(response));
            if (root.has("rankCatalog") && root.get("rankCatalog").isJsonArray()) {
                RankService.INSTANCE.updateCatalog(root.getAsJsonArray("rankCatalog"));
            }
            Map<String, ActiveMatch> next = new HashMap<String, ActiveMatch>();
            JsonArray values = root.has("activeMatches") && root.get("activeMatches").isJsonArray()
                    ? root.getAsJsonArray("activeMatches") : new JsonArray();
            for (JsonElement element : values) {
                if (!element.isJsonObject()) continue;
                ActiveMatch match = ActiveMatch.read(element.getAsJsonObject());
                if (match != null) next.put(match.id, match);
            }
            matches.clear();
            matches.putAll(next);
            matchByPlayer.clear();
            for (ActiveMatch match : next.values()) {
                matchByPlayer.put(match.fighterA, match);
                matchByPlayer.put(match.fighterB, match);
            }
            Iterator<Map.Entry<UUID, String>> iterator = teleportedMatch.entrySet().iterator();
            while (iterator.hasNext()) if (!matches.containsKey(iterator.next().getValue())) iterator.remove();
            Iterator<String> violations = reportedViolations.iterator();
            while (violations.hasNext()) {
                String value = violations.next();
                int separator = value.indexOf('\n');
                if (separator < 0 || !matches.containsKey(value.substring(0, separator))) violations.remove();
            }
            for (Map.Entry<UUID, ExamSpectator> entry
                    : new ArrayList<Map.Entry<UUID, ExamSpectator>>(spectators.entrySet())) {
                if (!matches.containsKey(entry.getValue().matchId)) {
                    EntityPlayerMP player = server == null ? null
                            : server.getPlayerList().getPlayerByUUID(entry.getKey());
                    if (player != null) leaveSpectator(player);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        long changed = PluginInvalidationBus.revision("exams");
        if (changed != invalidationRevision) {
            invalidationRevision = changed;
            runtimeTicks = 0;
            refreshRuntime();
            return;
        }
        int interval = matches.isEmpty() ? IDLE_RUNTIME_POLL_TICKS : ACTIVE_RUNTIME_POLL_TICKS;
        if (++runtimeTicks >= interval) {
            runtimeTicks = 0;
            refreshRuntime();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 5 != 0) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ExamSpectator spectator = spectators.get(player.getUniqueID());
        if (spectator != null) {
            tickSpectator(player, spectator);
            return;
        }
        ActiveMatch match = match(player.getUniqueID());
        if (match == null) return;
        long now = System.currentTimeMillis();
        if (now < match.startsAt) {
            if (!match.id.equals(teleportedMatch.get(player.getUniqueID()))) {
                if (!match.modes) ModeConfigurationService.INSTANCE.deactivateAll(player);
                teleport(player, match.spawn(player.getUniqueID()));
                teleportedMatch.put(player.getUniqueID(), match.id);
            }
            player.motionX = 0.0D;
            player.motionY = 0.0D;
            player.motionZ = 0.0D;
            long seconds = Math.max(1L, (match.startsAt - now + 999L) / 1000L);
            player.sendStatusMessage(new TextComponentString(TextFormatting.GOLD
                    + "Exam match begins in " + seconds + "..."), true);
        } else if (!match.contains(player)) {
            teleport(player, match.spawn(player.getUniqueID()));
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + "Stay inside the exam arena."), true);
        } else if (!match.armor && hasArmor(player)) {
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            String violation = match.id + '\n' + player.getUniqueID();
            if (reportedViolations.add(violation)) {
                JsonObject payload = new JsonObject();
                payload.addProperty("matchId", match.id);
                payload.addProperty("playerUuid", player.getUniqueID().toString());
                payload.addProperty("rule", "armor");
                recordRuntimeEvent(match.id, "tournament.violation", player, payload.toString());
            }
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + "Remove your armor to continue this exam match."), true);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) recoverSpectator((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) leaveSpectator((EntityPlayerMP) event.player);
    }

    public JsonObject spectatorSnapshot(EntityPlayerMP viewer) {
        JsonObject root = new JsonObject();
        JsonArray entries = new JsonArray();
        for (ActiveMatch match : matches.values()) {
            if (match.has(viewer.getUniqueID())) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", match.id);
            row.addProperty("title", "Exam Match - " + nameOf(match.fighterA)
                    + " vs " + nameOf(match.fighterB));
            row.addProperty("subtitle", match.startsAt > System.currentTimeMillis()
                    ? "Starts shortly" : "Match in progress");
            row.addProperty("detail", "Server-authoritative exam arena spectating");
            row.addProperty("action", "exam.spectate");
            row.addProperty("actionLabel", "Spectate Match");
            entries.add(row);
        }
        if (spectators.containsKey(viewer.getUniqueID())) {
            JsonObject leave = new JsonObject();
            leave.addProperty("id", "exam-current");
            leave.addProperty("title", "Leave exam match view");
            leave.addProperty("subtitle", "Return to your previous location and game mode");
            leave.addProperty("action", "exam.leave");
            leave.addProperty("actionLabel", "Leave Spectating");
            entries.add(leave);
        }
        root.add("entries", entries);
        return root;
    }

    public boolean spectate(EntityPlayerMP player, String matchId) {
        ActiveMatch match = matches.get(clean(matchId, 96));
        if (player == null || match == null || match(player.getUniqueID()) != null) return false;
        if (net.rebornaddon.ranked.RankedSystem.isReady()
                && net.rebornaddon.ranked.RankedSystem.matchManager.isInMatch(player.getUniqueID())) return false;
        leaveSpectator(player);
        if (net.rebornaddon.ranked.RankedSystem.isReady()) {
            net.rebornaddon.ranked.RankedSystem.matchManager.leaveSpectator(player);
        }
        Point original = new Point();
        original.dimension = player.dimension;
        original.x = player.posX;
        original.y = player.posY;
        original.z = player.posZ;
        original.yaw = player.rotationYaw;
        original.pitch = player.rotationPitch;
        ExamSpectator session = new ExamSpectator(match.id, original,
                player.interactionManager.getGameType());
        spectators.put(player.getUniqueID(), session);
        saveSpectator(player, session);
        player.setGameType(GameType.SPECTATOR);
        teleportToMatch(player, match);
        return true;
    }

    public boolean isInActiveMatch(UUID player) {
        return player != null && match(player) != null;
    }

    public boolean leaveSpectator(EntityPlayerMP player) {
        if (player == null) return false;
        ExamSpectator session = spectators.remove(player.getUniqueID());
        if (session == null) return false;
        player.setGameType(session.gameType == GameType.SPECTATOR ? GameType.SURVIVAL : session.gameType);
        teleport(player, session.original);
        clearSpectator(player);
        return true;
    }

    private void recoverSpectator(EntityPlayerMP player) {
        NBTTagCompound persisted = persisted(player);
        if (!persisted.hasKey(SPECTATOR_STATE, 10)) return;
        NBTTagCompound state = persisted.getCompoundTag(SPECTATOR_STATE);
        Point original = new Point();
        original.dimension = state.getInteger("Dimension");
        original.x = state.getDouble("X");
        original.y = state.getDouble("Y");
        original.z = state.getDouble("Z");
        original.yaw = state.getFloat("Yaw");
        original.pitch = state.getFloat("Pitch");
        GameType gameType = GameType.getByID(state.getInteger("GameType"));
        player.setGameType(gameType == GameType.SPECTATOR ? GameType.SURVIVAL : gameType);
        teleport(player, original);
        clearSpectator(player);
    }

    private void tickSpectator(EntityPlayerMP player, ExamSpectator session) {
        ActiveMatch match = matches.get(session.matchId);
        if (match == null) {
            leaveSpectator(player);
            return;
        }
        double centerX = (match.minX + match.maxX) * 0.5D;
        double centerZ = (match.minZ + match.maxZ) * 0.5D;
        double dx = player.posX - centerX;
        double dz = player.posZ - centerZ;
        if (player.dimension != match.dimension || dx * dx + dz * dz > 16384.0D) {
            teleportToMatch(player, match);
        }
    }

    private void teleportToMatch(EntityPlayerMP player, ActiveMatch match) {
        Point destination = new Point();
        destination.dimension = match.dimension;
        destination.x = (match.minX + match.maxX) * 0.5D;
        destination.y = Math.max(match.maxY, match.minY) + 5.0D;
        destination.z = (match.minZ + match.maxZ) * 0.5D;
        teleport(player, destination);
    }

    private String nameOf(UUID player) {
        if (server != null) {
            EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(player);
            if (online != null) return online.getName();
            com.mojang.authlib.GameProfile profile = server.getPlayerProfileCache().getProfileByUUID(player);
            if (profile != null && profile.getName() != null) return profile.getName();
        }
        String value = player == null ? "Unknown" : player.toString();
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private static void saveSpectator(EntityPlayerMP player, ExamSpectator session) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Dimension", session.original.dimension);
        state.setDouble("X", session.original.x);
        state.setDouble("Y", session.original.y);
        state.setDouble("Z", session.original.z);
        state.setFloat("Yaw", session.original.yaw);
        state.setFloat("Pitch", session.original.pitch);
        state.setInteger("GameType", session.gameType.getID());
        persisted(player).setTag(SPECTATOR_STATE, state);
    }

    private static void clearSpectator(EntityPlayerMP player) {
        persisted(player).removeTag(SPECTATOR_STATE);
    }

    private static NBTTagCompound persisted(EntityPlayerMP player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayerMP.PERSISTED_NBT_TAG, 10)) {
            root.setTag(EntityPlayerMP.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return root.getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        Entity source = event.getSource().getTrueSource();
        ActiveMatch victimMatch = event.getEntityLiving() instanceof EntityPlayerMP
                ? match(event.getEntityLiving().getUniqueID()) : null;
        ActiveMatch attackerMatch = source instanceof EntityPlayerMP
                ? match(source.getUniqueID()) : null;
        if (victimMatch == null && attackerMatch == null) return;
        if (!(source instanceof EntityPlayerMP) || victimMatch == null
                || attackerMatch != victimMatch || System.currentTimeMillis() < victimMatch.startsAt
                || !equipmentAllowed((EntityPlayerMP) source, victimMatch)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP defeated = (EntityPlayerMP) event.getEntityLiving();
        ActiveMatch match = match(defeated.getUniqueID());
        if (match == null) return;
        UUID winnerId = match.opponent(defeated.getUniqueID());
        EntityPlayerMP winner = winnerId == null || server == null ? null
                : server.getPlayerList().getPlayerByUUID(winnerId);
        JsonObject result = new JsonObject();
        result.addProperty("matchId", match.id);
        result.addProperty("defeated", defeated.getUniqueID().toString());
        result.addProperty("winner", winnerId == null ? "" : winnerId.toString());
        recordRuntimeEvent(match.id, "tournament.result", winner, result.toString());
    }

    private void recordRuntimeEvent(final String eventId, final String type,
                                    final EntityPlayerMP player, final String payload) {
        if (worker == null || worker.isShutdown()) return;
        final MinecraftServer currentServer = server;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                ExamPluginBridge.recordRuntimeEvent(eventId, type, player, payload);
                if (currentServer != null) currentServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (server == currentServer) refreshRuntime();
                    }
                });
            }
        });
    }

    public boolean canUseJutsu(net.minecraft.entity.player.EntityPlayer player,
                               JutsuDefinition definition) {
        if (!(player instanceof EntityPlayerMP) || definition == null) return true;
        ActiveMatch match = match(player.getUniqueID());
        if (match == null || System.currentTimeMillis() < match.startsAt) return match == null;
        if (!equipmentAllowed((EntityPlayerMP) player, match)) return false;
        String category = definition.category().toLowerCase(Locale.ROOT);
        String id = definition.id().toLowerCase(Locale.ROOT);
        if (!match.modes && (category.contains("mode") || id.contains("mode"))) return deniedJutsu(player);
        if (!match.kekkeiGenkai && (category.contains("kekkei") || category.contains("dojutsu")
                || category.contains("clan") || id.contains("sharingan") || id.contains("rinnegan"))) {
            return deniedJutsu(player);
        }
        if (match.natureOnly && !isNature(category, id)) return deniedJutsu(player);
        return true;
    }

    private boolean deniedJutsu(net.minecraft.entity.player.EntityPlayer player) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                + "That technique is not allowed in this exam match."), true);
        return false;
    }

    private boolean equipmentAllowed(EntityPlayerMP player, ActiveMatch match) {
        if (!match.armor && hasArmor(player)) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + "Armor is not allowed in this exam match."), true);
            return false;
        }
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) return true;
        Item item = held.getItem();
        ResourceLocation id = item.getRegistryName();
        boolean weapon = item instanceof ItemSword || item instanceof ItemBow || item instanceof ItemTool
                || id != null && match.allowedWeapons.contains(id.toString())
                || held.getAttributeModifiers(EntityEquipmentSlot.MAINHAND)
                .containsKey(SharedMonsterAttributes.ATTACK_DAMAGE.getName());
        if (!weapon) return true;
        if (!match.weapons || id == null || (!match.allowedWeapons.isEmpty()
                && !match.allowedWeapons.contains(id.toString()))) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + "That weapon is not allowed in this exam match."), true);
            return false;
        }
        return true;
    }

    private static boolean hasArmor(EntityPlayerMP player) {
        for (ItemStack stack : player.inventory.armorInventory) if (!stack.isEmpty()) return true;
        return false;
    }

    private ActiveMatch match(UUID player) {
        return matchByPlayer.get(player);
    }

    private void teleport(EntityPlayerMP player, Point point) {
        if (point == null) return;
        if (player.dimension != point.dimension) player.changeDimension(point.dimension);
        player.connection.setPlayerLocation(point.x, point.y, point.z, point.yaw, point.pitch);
    }

    private void sendSnapshot(EntityPlayerMP player, JsonObject snapshot, String message) {
        String scope = string(snapshot, "viewerScope").toLowerCase(Locale.ROOT);
        if (!"admin".equals(scope) && !"creator".equals(scope)) removePrivate(snapshot);
        snapshot.addProperty("success", true);
        snapshot.addProperty("message", message == null ? "" : message);
        String json = snapshot.toString();
        snapshots.put(player.getUniqueID(), new CachedSnapshot(json, System.currentTimeMillis()));
        RebornAddonNetwork.sendExamSync(player, "snapshot", json);
    }

    private void sendCached(EntityPlayerMP player, CachedSnapshot cached) {
        if (player != null && cached != null) {
            RebornAddonNetwork.sendExamSync(player, "snapshot", cached.json);
        }
    }

    private static void sendRankCatalog(EntityPlayerMP player) {
        JsonObject data = new JsonObject();
        data.addProperty("available", true);
        data.add("rankCatalog", RankService.INSTANCE.catalog());
        RebornAddonNetwork.sendExamSync(player, "rank_catalog", data.toString());
    }

    private static void enrichAccess(JsonObject snapshot, Access access) {
        if (snapshot == null || access == null) return;
        JsonObject permissions = snapshot.has("access") && snapshot.get("access").isJsonObject()
                ? snapshot.getAsJsonObject("access") : new JsonObject();
        boolean pluginCreate = permissions.has("canCreate") && permissions.get("canCreate").getAsBoolean();
        boolean pluginApprove = permissions.has("canApprove") && permissions.get("canApprove").getAsBoolean();
        boolean pluginOperator = permissions.has("operator") && permissions.get("operator").getAsBoolean();
        permissions.addProperty("canCreate", pluginCreate || access.operator || access.leader);
        permissions.addProperty("canApprove", pluginApprove || access.operator);
        permissions.addProperty("operator", pluginOperator || access.operator);
        snapshot.add("access", permissions);
        if (access.operator) snapshot.addProperty("viewerScope", "admin");
        else if (access.leader) snapshot.addProperty("viewerScope", "creator");
    }

    private void sendFailure(EntityPlayerMP player, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("success", false);
        data.addProperty("available", ExamPluginBridge.isAvailable());
        data.addProperty("message", message);
        RebornAddonNetwork.sendExamSync(player, "snapshot", data.toString());
    }

    private void sendUnavailable(EntityPlayerMP player) {
        sendFailure(player, "Exams are not enabled on this server yet.");
    }

    private static void removePrivate(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) removePrivate(child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String field : PRIVATE_FIELDS) object.remove(field);
            for (Map.Entry<String, JsonElement> entry : new ArrayList<Map.Entry<String, JsonElement>>(object.entrySet())) {
                removePrivate(entry.getValue());
            }
        }
    }

    private static Set<String> privateFields() {
        Set<String> values = new HashSet<String>();
        Collections.addAll(values, "correct", "answerKey", "correctAnswerIds",
                "acceptedAnswerIds", "grading", "privateNotes", "dropChance");
        return values;
    }

    private static boolean isOperator(EntityPlayerMP player) {
        return player.canUseCommand(2, "rebornadmin") || SingleplayerVillageRoles.isOperator(player);
    }

    private static boolean isLeadership(Set<String> groups) {
        for (Village village : Village.all()) {
            if (VillageRoles.isKage(groups, village) || VillageRoles.isAdvisor(groups, village)) return true;
        }
        return false;
    }

    private static boolean requiresOperator(String action) {
        return action.startsWith("exam.approve") || action.startsWith("exam.reject")
                || action.startsWith("rank.") || action.startsWith("exam.admin.");
    }

    private static boolean requiresAuthor(String action) {
        return action.startsWith("exam.create") || action.startsWith("exam.update")
                || action.startsWith("exam.delete") || action.startsWith("exam.schedule")
                || action.startsWith("exam.open_editor")
                || action.startsWith("exam.stage") || action.startsWith("tournament.configure")
                || action.startsWith("tournament.bracket") || action.startsWith("tournament.start");
    }

    private static boolean isNature(String category, String id) {
        String value = category + ' ' + id;
        return value.contains("fire") || value.contains("wind") || value.contains("water")
                || value.contains("earth") || value.contains("lightning") || value.contains("nature");
    }

    private static void deny(EntityPlayerMP player, String message) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED + message), true);
    }

    public static JsonObject parseObject(String json) {
        return json == null || json.trim().isEmpty() ? new JsonObject()
                : new JsonParser().parse(json).getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String clean(String value, int maximum) {
        String result = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static boolean force(String payload) {
        try {
            JsonObject value = parseObject(payload);
            return value.has("force") && value.get("force").getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class Access {
        private final boolean operator;
        private final boolean leader;

        private Access(boolean operator, boolean leader) {
            this.operator = operator;
            this.leader = leader;
        }
    }

    private static final class CachedSnapshot {
        private final String json;
        private final long createdAt;

        private CachedSnapshot(String json, long createdAt) {
            this.json = json;
            this.createdAt = createdAt;
        }
    }

    private static final class ActiveMatch {
        private String id;
        private UUID fighterA;
        private UUID fighterB;
        private long startsAt;
        private Point first;
        private Point second;
        private int dimension;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;
        private boolean natureOnly;
        private boolean kekkeiGenkai;
        private boolean modes;
        private boolean armor;
        private boolean weapons;
        private final Set<String> allowedWeapons = new HashSet<String>();

        private static ActiveMatch read(JsonObject data) {
            try {
                ActiveMatch value = new ActiveMatch();
                value.id = string(data, "id");
                value.fighterA = UUID.fromString(string(data, "fighterA"));
                value.fighterB = UUID.fromString(string(data, "fighterB"));
                value.startsAt = data.get("startsAt").getAsLong();
                value.first = Point.read(data.getAsJsonObject("spawnA"));
                value.second = Point.read(data.getAsJsonObject("spawnB"));
                JsonObject bounds = data.getAsJsonObject("bounds");
                value.dimension = bounds.get("dimension").getAsInt();
                value.minX = bounds.get("minX").getAsDouble();
                value.minY = bounds.get("minY").getAsDouble();
                value.minZ = bounds.get("minZ").getAsDouble();
                value.maxX = bounds.get("maxX").getAsDouble();
                value.maxY = bounds.get("maxY").getAsDouble();
                value.maxZ = bounds.get("maxZ").getAsDouble();
                JsonObject rules = data.getAsJsonObject("rules");
                value.natureOnly = bool(rules, "natureOnly");
                value.kekkeiGenkai = bool(rules, "kekkeiGenkai");
                value.modes = bool(rules, "modes");
                value.armor = bool(rules, "armor");
                value.weapons = bool(rules, "weapons");
                JsonArray allowed = rules.has("allowedWeapons") && rules.get("allowedWeapons").isJsonArray()
                        ? rules.getAsJsonArray("allowedWeapons") : new JsonArray();
                for (JsonElement element : allowed) value.allowedWeapons.add(element.getAsString());
                return value.id.isEmpty() ? null : value;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private boolean has(UUID player) { return fighterA.equals(player) || fighterB.equals(player); }
        private UUID opponent(UUID player) { return fighterA.equals(player) ? fighterB : fighterA; }
        private Point spawn(UUID player) { return fighterA.equals(player) ? first : second; }
        private boolean contains(EntityPlayerMP player) {
            return player.dimension == dimension && player.posX >= Math.min(minX, maxX)
                    && player.posX <= Math.max(minX, maxX) && player.posY >= Math.min(minY, maxY) - 4.0D
                    && player.posY <= Math.max(minY, maxY) + 8.0D && player.posZ >= Math.min(minZ, maxZ)
                    && player.posZ <= Math.max(minZ, maxZ);
        }
    }

    private static final class ExamSpectator {
        private final String matchId;
        private final Point original;
        private final GameType gameType;

        private ExamSpectator(String matchId, Point original, GameType gameType) {
            this.matchId = matchId;
            this.original = original;
            this.gameType = gameType;
        }
    }

    private static final class Point {
        private int dimension;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        private static Point read(JsonObject data) {
            Point value = new Point();
            value.dimension = data.get("dimension").getAsInt();
            value.x = data.get("x").getAsDouble();
            value.y = data.get("y").getAsDouble();
            value.z = data.get("z").getAsDouble();
            value.yaw = data.has("yaw") ? data.get("yaw").getAsFloat() : 0.0F;
            value.pitch = data.has("pitch") ? data.get("pitch").getAsFloat() : 0.0F;
            return value;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).getAsBoolean();
    }
}
