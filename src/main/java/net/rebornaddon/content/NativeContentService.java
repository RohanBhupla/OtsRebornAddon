package net.rebornaddon.content;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.rebornaddon.gameplay.codex.ShinobiCodexService;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.content.NativeContentModel.Boss;
import net.rebornaddon.content.NativeContentModel.BossPhase;
import net.rebornaddon.content.NativeContentModel.PhaseSummon;
import net.rebornaddon.content.NativeContentModel.Dungeon;
import net.rebornaddon.content.NativeContentModel.DungeonSpawn;
import net.rebornaddon.content.NativeContentModel.ItemAmount;
import net.rebornaddon.content.NativeContentModel.Jutsu;
import net.rebornaddon.content.NativeContentModel.NpcTemplate;
import net.rebornaddon.content.NativeContentModel.QuestStep;
import net.rebornaddon.content.entity.EntityMissionNpc;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;
import net.rebornaddon.integration.PluginInvalidationBus;
import net.rebornaddon.jutsu.JutsuDefinition;
import net.rebornaddon.jutsu.JutsuRegistry;
import net.rebornaddon.jutsu.JutsuSettingsCache;
import net.rebornaddon.jutsu.NativeNpcJutsuExecutor;
import net.rebornaddon.quest.NativeQuestService;
import net.rebornaddon.compat.NarutoLearnerDropProtectionHandler;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageSelectionHandler;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class NativeContentService {
    public static final NativeContentService INSTANCE = new NativeContentService();

    private static final Gson GSON = new Gson();
    private static final int CONTENT_REFRESH_TICKS = 1200;
    private static final int PLAYER_REFRESH_TICKS = 40;
    private static final int ACTIVATION_TICKS = 20;
    private static final int CLEANUP_TICKS = 100;
    private static final int WORLD_BOSS_POLL_TICKS = 600;
    private static final int CELL_SIZE = 64;
    private static final long ABANDONED_NPC_MILLIS = 120000L;

    private final Random random = new Random();
    private final Map<String, NpcTemplate> templates = new LinkedHashMap<String, NpcTemplate>();
    private final Map<String, NativeContentModel.Quest> quests =
            new LinkedHashMap<String, NativeContentModel.Quest>();
    private final Map<String, Boss> bosses = new LinkedHashMap<String, Boss>();
    private final Map<String, Dungeon> dungeons = new LinkedHashMap<String, Dungeon>();
    private final Map<String, NativeContentModel.PathDefinition> paths =
            new LinkedHashMap<String, NativeContentModel.PathDefinition>();
    private final Map<UUID, Integer> pathCursor = new HashMap<UUID, Integer>();
    private final Map<UUID, Long> pathWaitUntil = new HashMap<UUID, Long>();
    private final Map<Integer, Map<Long, List<SpawnPoint>>> spawnIndex =
            new HashMap<Integer, Map<Long, List<SpawnPoint>>>();
    private final Map<String, NativeContentModel.SpawnDefinition> spawnDefinitions =
            new HashMap<String, NativeContentModel.SpawnDefinition>();
    private final Map<UUID, PlayerRuntime> playerRuntime = new HashMap<UUID, PlayerRuntime>();
    private final Map<String, UUID> activeSpawns = new HashMap<String, UUID>();
    private final Map<String, Long> respawnAfter = new HashMap<String, Long>();
    private final Map<String, BossRuntime> bossRuntime = new HashMap<String, BossRuntime>();
    private final Map<String, DungeonRuntime> dungeonRuntime = new HashMap<String, DungeonRuntime>();
    private final Map<String, String> dungeonSpawnRuns = new HashMap<String, String>();
    private final Map<UUID, Long> orphanedSince = new HashMap<UUID, Long>();
    private final List<WorldBossSpawn> pendingWorldBosses = new ArrayList<WorldBossSpawn>();
    private final Set<String> pendingWorldBossIds = new HashSet<String>();
    private final ThreadLocal<CastContext> activeCast = new ThreadLocal<CastContext>();
    private final Map<Class<?>, List<Method>> ownerMethods =
            Collections.synchronizedMap(new HashMap<Class<?>, List<Method>>());
    private final Map<Class<?>, List<Field>> ownerFields =
            Collections.synchronizedMap(new HashMap<Class<?>, List<Field>>());
    private final Map<Class<?>, List<Method>> ownerUuidMethods =
            Collections.synchronizedMap(new HashMap<Class<?>, List<Method>>());

    private MinecraftServer server;
    private long revision = Long.MIN_VALUE;
    private int ticks;
    private long invalidationRevision;

    private NativeContentService() {
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        refreshRuntime();
    }

    public void reset() {
        server = null;
        revision = Long.MIN_VALUE;
        ticks = 0;
        templates.clear();
        quests.clear();
        bosses.clear();
        dungeons.clear();
        paths.clear();
        pathCursor.clear();
        pathWaitUntil.clear();
        spawnIndex.clear();
        spawnDefinitions.clear();
        playerRuntime.clear();
        activeSpawns.clear();
        respawnAfter.clear();
        bossRuntime.clear();
        dungeonRuntime.clear();
        dungeonSpawnRuns.clear();
        orphanedSince.clear();
        pendingWorldBosses.clear();
        pendingWorldBossIds.clear();
        activeCast.remove();
    }

    public boolean refreshRuntime() {
        String[] response = NativeContentPluginBridge.runtimeSnapshot();
        if (!NativeContentPluginBridge.successful(response)) {
            return false;
        }
        try {
            NativeContentModel.Catalog next = GSON.fromJson(
                    NativeContentPluginBridge.payload(response), NativeContentModel.Catalog.class);
            if (next == null) {
                return false;
            }
            if (next.revision == revision) {
                return true;
            }
            replaceCatalog(next);
            revision = next.revision;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public int questCount() {
        return quests.size();
    }

    public void appendPerformanceData(NBTTagCompound data) {
        if (data == null) return;
        data.setInteger("RebornTemplates", templates.size());
        data.setInteger("RebornQuests", quests.size());
        data.setInteger("RebornActiveSpawns", activeSpawns.size());
        data.setInteger("RebornBosses", bossRuntime.size());
        data.setInteger("RebornDungeons", dungeonRuntime.size());
        data.setInteger("RebornTrackedPlayers", playerRuntime.size());
        data.setInteger("RebornReflectionCaches", ownerMethods.size() + ownerFields.size() + ownerUuidMethods.size());
    }

    public boolean isConfiguredSkin(String skin) {
        if (skin == null || skin.isEmpty()) return false;
        for (NpcTemplate template : templates.values()) {
            if (skin.equals(template.skin)) return true;
        }
        return false;
    }

    private void replaceCatalog(NativeContentModel.Catalog catalog) {
        templates.clear();
        quests.clear();
        bosses.clear();
        dungeons.clear();
        paths.clear();
        spawnIndex.clear();
        spawnDefinitions.clear();
        if (catalog.templates != null) {
            for (NpcTemplate template : catalog.templates) {
                sanitize(template);
                if (!template.id.isEmpty() && templates.size() < 4096) {
                    templates.put(template.id, template);
                }
            }
        }
        if (catalog.quests != null) {
            for (NativeContentModel.Quest quest : catalog.quests) {
                if (quest != null) {
                    quest.id = id(quest.id);
                    if (quest.enabled && !quest.id.isEmpty() && quests.size() < 4096) {
                        quests.put(quest.id, quest);
                    }
                }
            }
        }
        if (catalog.paths != null) {
            for (NativeContentModel.PathDefinition path : catalog.paths) {
                if (path != null) {
                    path.id = id(path.id);
                    if (!path.id.isEmpty() && path.points != null) paths.put(path.id, path);
                }
            }
        }
        if (catalog.actors != null) {
            for (NativeContentModel.Actor actor : catalog.actors) {
                sanitize(actor);
                if (actor.enabled && !actor.id.isEmpty()) index(new SpawnPoint(actor.id, actor, "actor"));
            }
        }
        if (catalog.bosses != null) {
            for (Boss boss : catalog.bosses) {
                sanitize(boss);
                if (!boss.id.isEmpty() && bosses.size() < 2048) {
                    bosses.put(boss.id, boss);
                    if (boss.enabled && !boss.worldBoss) {
                        index(new SpawnPoint(boss.id, boss, "boss"));
                    }
                }
            }
        }
        if (catalog.dungeons != null) {
            for (Dungeon dungeon : catalog.dungeons) {
                if (dungeon == null) {
                    continue;
                }
                sanitize(dungeon);
                if (dungeon.spawns == null) dungeon.spawns = new ArrayList<DungeonSpawn>();
                for (DungeonSpawn spawn : dungeon.spawns) {
                    sanitize(spawn);
                    spawn.count = Math.max(1, Math.min(64, spawn.count));
                    spawn.wave = Math.max(1, Math.min(128, spawn.wave));
                    spawn.waveDelaySeconds = Math.max(0, Math.min(86400, spawn.waveDelaySeconds));
                }
                dungeon.resetSeconds = Math.max(0L, Math.min(31536000L, dungeon.resetSeconds));
                dungeon.mode = text(dungeon.mode, 16, "waves").toLowerCase(Locale.ROOT);
                if (!"fixed".equals(dungeon.mode)) dungeon.mode = "waves";
                if (!dungeon.id.isEmpty() && dungeons.size() < 1024) {
                    dungeons.put(dungeon.id, dungeon);
                    if (dungeon.enabled) {
                        if ("fixed".equals(dungeon.mode)) indexFixedDungeon(dungeon);
                        else index(new SpawnPoint(dungeon.id, dungeon, "dungeon_controller"));
                    }
                }
            }
        }
        reconcileLoadedEntities();
    }

    private void indexFixedDungeon(Dungeon dungeon) {
        int ordinal = 0;
        for (DungeonSpawn spawn : dungeon.spawns) {
            if (spawn == null || !spawn.enabled) continue;
            if (spawn.id == null || spawn.id.isEmpty()) spawn.id = "spawn-" + (ordinal + 1);
            index(new SpawnPoint("df/" + shortId(dungeon.id) + "/" + shortId(spawn.id) + "/" + ordinal,
                    spawn, "dungeon_static"));
            ordinal++;
        }
    }

    private void index(SpawnPoint point) {
        spawnDefinitions.put(point.key, point.definition);
        Map<Long, List<SpawnPoint>> dimension = spawnIndex.get(Integer.valueOf(point.definition.dimension));
        if (dimension == null) {
            dimension = new HashMap<Long, List<SpawnPoint>>();
            spawnIndex.put(Integer.valueOf(point.definition.dimension), dimension);
        }
        long cell = cell(point.definition.x, point.definition.z);
        List<SpawnPoint> points = dimension.get(Long.valueOf(cell));
        if (points == null) {
            points = new ArrayList<SpawnPoint>();
            dimension.put(Long.valueOf(cell), points);
        }
        points.add(point);
    }

    public void updatePlayerRuntime(EntityPlayerMP player, String json) {
        if (player == null || json == null || json.isEmpty()) {
            return;
        }
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonElement trackerElement = root.has("Tracker") ? root.get("Tracker") : root.get("tracker");
            if (trackerElement == null || !trackerElement.isJsonObject()) {
                playerRuntime.remove(player.getUniqueID());
                return;
            }
            PlayerRuntime state = GSON.fromJson(trackerElement, PlayerRuntime.class);
            if (state != null && state.active) {
                state.updatedAt = System.currentTimeMillis();
                playerRuntime.put(player.getUniqueID(), state);
            } else {
                playerRuntime.remove(player.getUniqueID());
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            NativeQuestService.send((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        playerRuntime.remove(event.player.getUniqueID());
        NativeQuestService.removePlayer(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        ticks++;
        long changed = PluginInvalidationBus.revision("content");
        if (changed != invalidationRevision || ticks % CONTENT_REFRESH_TICKS == 0) {
            invalidationRevision = changed;
            refreshRuntime();
        }
        if (ticks % PLAYER_REFRESH_TICKS == 0) {
            refreshPlayerTrackers();
        }
        if (ticks % ACTIVATION_TICKS == 0) {
            activateNearbyContent();
        }
        if (ticks % CLEANUP_TICKS == 0) {
            cleanupEntities();
        }
        if (ticks % WORLD_BOSS_POLL_TICKS == 0) {
            pollWorldBosses();
        }
    }

    private void refreshPlayerTrackers() {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            String[] response = NativeContentPluginBridge.playerRuntimeSnapshot(player);
            if (NativeContentPluginBridge.successful(response)) {
                updatePlayerRuntime(player, NativeContentPluginBridge.payload(response));
            }
        }
    }

    private void activateNearbyContent() {
        Set<String> checked = new HashSet<String>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            activateQuest(player);
            activateIndexed(player, checked);
            activatePendingWorldBoss(player, checked);
        }
        refreshBossHuds();
    }

    private void refreshBossHuds() {
        for (BossRuntime runtime : new ArrayList<BossRuntime>(bossRuntime.values())) {
            EntityMissionNpc npc = findNpc(runtime.entityId);
            if (npc != null && !npc.isDead) {
                updateBossRuntime(runtime, npc);
                sendBossHud(runtime, npc);
            }
        }
    }

    private void updateBossRuntime(BossRuntime runtime, EntityMissionNpc npc) {
        long now = System.currentTimeMillis();
        if (runtime.invulnerableUntil > 0L && now >= runtime.invulnerableUntil) {
            npc.setEntityInvulnerable(false);
            runtime.invulnerableUntil = 0L;
        }
        if (!runtime.enraged && runtime.definition.enrageSeconds > 0L
                && now - runtime.spawnedAt >= runtime.definition.enrageSeconds * 1000L) {
            runtime.enraged = true;
            npc.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(
                    runtime.baseDamage * clamp(runtime.definition.enrageDamageMultiplier, 1.0D, 10.0D));
            npc.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(
                    runtime.baseSpeed * clamp(runtime.definition.enrageSpeedMultiplier, 1.0D, 3.0D));
            announceBossState(npc, npc.getName() + " has enraged.", TextFormatting.RED);
            NativeContentPluginBridge.recordEvent(runtime.instance + ":enrage", "boss.enraged", null,
                    runtime.definition.id, "{}");
        }
        double arena = clamp(runtime.definition.arenaRadius, 8.0D, 256.0D);
        if (distanceSq(npc.posX, npc.posY, npc.posZ, runtime.x, runtime.y, runtime.z) > arena * arena) {
            npc.setPositionAndUpdate(runtime.x, runtime.y, runtime.z);
            npc.getNavigator().clearPath();
        }
        if (runtime.definition.phases == null || runtime.definition.phases.isEmpty()) return;
        double healthPercent = npc.getHealth() * 100.0D / Math.max(1.0D, npc.getMaxHealth());
        BossPhase selected = null;
        for (BossPhase phase : runtime.definition.phases) {
            if (phase == null || runtime.completedPhases.contains(id(phase.id))) continue;
            if (healthPercent <= clamp(phase.healthPercent, 0.0D, 100.0D)
                    && (selected == null || phase.healthPercent > selected.healthPercent)) selected = phase;
        }
        if (selected != null) activateBossPhase(runtime, npc, selected, now);
    }

    private void activateBossPhase(BossRuntime runtime, EntityMissionNpc npc,
                                   BossPhase phase, long now) {
        String phaseId = id(phase.id);
        runtime.completedPhases.add(phaseId);
        runtime.phaseName = phase.name == null || phase.name.trim().isEmpty() ? phaseId : phase.name.trim();
        if (phase.telegraph != null && !phase.telegraph.trim().isEmpty()) {
            announceBossState(npc, phase.telegraph.trim(), TextFormatting.GOLD);
        }
        if (phase.invulnerableSeconds > 0L) {
            npc.setEntityInvulnerable(true);
            runtime.invulnerableUntil = now + Math.min(60L, phase.invulnerableSeconds) * 1000L;
        }
        if (phase.jutsus != null && !phase.jutsus.isEmpty()
                && npc.getConfiguredTemplate() != null) {
            npc.getConfiguredTemplate().jutsus = new ArrayList<Jutsu>(phase.jutsus);
        }
        spawnPhaseSummons(runtime, npc, phase);
        JsonObject payload = new JsonObject();
        payload.addProperty("phaseId", phaseId);
        payload.addProperty("healthPercent", npc.getHealth() * 100.0D / Math.max(1.0D, npc.getMaxHealth()));
        NativeContentPluginBridge.recordEvent(runtime.instance + ":phase:" + phaseId,
                "boss.phase", null, runtime.definition.id, payload.toString());
    }

    private void spawnPhaseSummons(BossRuntime runtime, EntityMissionNpc boss, BossPhase phase) {
        if (phase.summons == null || phase.summons.isEmpty() || !(boss.world instanceof WorldServer)) return;
        int ordinal = 0;
        for (PhaseSummon summon : phase.summons) {
            if (summon == null || summon.templateId == null || summon.templateId.trim().isEmpty()) continue;
            NpcTemplate template = effectiveTemplate(summon.templateId, summon.overrides);
            if (template == null) continue;
            int count = Math.max(1, Math.min(16, summon.count));
            double radius = clamp(summon.radius, 1.0D, 24.0D);
            for (int copy = 0; copy < count; copy++) {
                double angle = (ordinal++ * 2.39996322973D) + random.nextDouble() * 0.25D;
                String instance = runtime.instance + "/phase/" + id(phase.id) + "/" + ordinal;
                EntityMissionNpc npc = spawn((WorldServer) boss.world, template,
                        boss.posX + Math.cos(angle) * radius, boss.posY,
                        boss.posZ + Math.sin(angle) * radius, runtime.definition.id,
                        instance, null, "boss_summon");
                if (npc != null) runtime.summons.add(npc.getUniqueID());
            }
        }
    }

    private void announceBossState(EntityMissionNpc npc, String message, TextFormatting color) {
        if (server == null || npc == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.dimension == npc.dimension && player.getDistanceSq(npc) <= 16384.0D) {
                player.sendMessage(new TextComponentString(color + message));
            }
        }
    }

    private EntityMissionNpc findNpc(UUID id) {
        if (id == null || server == null) return null;
        for (WorldServer world : server.worlds) {
            Entity entity = world.getEntityFromUuid(id);
            if (entity instanceof EntityMissionNpc) return (EntityMissionNpc) entity;
        }
        return null;
    }

    private void activateQuest(EntityPlayerMP player) {
        PlayerRuntime state = playerRuntime.get(player.getUniqueID());
        if (state == null || !state.active) {
            return;
        }
        if ("item".equals(state.type)) {
            checkItemObjective(player, state);
            return;
        }
        if (state.dimension != player.dimension) return;
        double distance = distanceSq(player.posX, player.posY, player.posZ, state.x, state.y, state.z);
        if (distance > state.radius * state.radius) {
            return;
        }
        if ("travel".equals(state.type)) {
            NativeQuestService.action(player, "quest.arrive", state.payload());
            return;
        }
        if (!state.spawnNpc || state.templateId.isEmpty()) {
            return;
        }
        String spawnKey = "quest/" + player.getUniqueID() + "/" + state.questId + "/" + state.stepId;
        if (live(activeSpawns.get(spawnKey))) {
            return;
        }
        NpcTemplate template = effectiveTemplate(state.templateId, state.overrides);
        if (template == null) {
            return;
        }
        WorldServer world = server.getWorld(state.dimension);
        double spawnX = state.spawnX == null ? state.x : state.spawnX.doubleValue();
        double spawnY = state.spawnY == null ? state.y : state.spawnY.doubleValue();
        double spawnZ = state.spawnZ == null ? state.z : state.spawnZ.doubleValue();
        EntityMissionNpc npc = spawn(world, template, spawnX, spawnY, spawnZ,
                state.questId, spawnKey, player.getUniqueID(), "quest");
        if (npc != null) {
            activeSpawns.put(spawnKey, npc.getUniqueID());
        }
    }

    private void checkItemObjective(EntityPlayerMP player, PlayerRuntime state) {
        JsonObject payload = new JsonObject();
        payload.addProperty("questId", state.questId);
        payload.addProperty("stepId", state.stepId);
        JsonArray counts = new JsonArray();
        for (ItemAmount requirement : state.requirements) {
            JsonObject item = new JsonObject();
            item.addProperty("item", requirement.item);
            item.addProperty("meta", requirement.meta);
            item.addProperty("count", inventoryCount(player, requirement));
            counts.add(item);
        }
        payload.add("items", counts);
        String[] response = NativeContentPluginBridge.playerAction(player,
                "quest.item-progress", payload.toString());
        if (!NativeContentPluginBridge.successful(response)) return;
        JsonObject result;
        try { result = object(NativeContentPluginBridge.payload(response)); }
        catch (Throwable ignored) { return; }
        if (result.has("consumeToken") && result.has("consume") && result.get("consume").isJsonArray()) {
            consumeQuestItems(player, result.get("consumeToken").getAsString(),
                    result.getAsJsonArray("consume"));
        }
        applyPlayerResponse(player, response);
    }

    private void consumeQuestItems(EntityPlayerMP player, String token, JsonArray requested) {
        List<ItemAmount> amounts = new ArrayList<ItemAmount>();
        for (JsonElement element : requested) if (element.isJsonObject()) {
            amounts.add(GSON.fromJson(element, ItemAmount.class));
        }
        List<ItemStack> removed = new ArrayList<ItemStack>();
        for (ItemAmount amount : amounts) {
            int remaining = Math.max(0, amount.count);
            if (inventoryCount(player, amount) < remaining) {
                restore(player, removed);
                return;
            }
            for (int slot = 0; slot < player.inventory.mainInventory.size() && remaining > 0; slot++) {
                ItemStack stack = player.inventory.mainInventory.get(slot);
                if (!matches(stack, amount)) continue;
                int move = Math.min(remaining, stack.getCount());
                ItemStack taken = stack.splitStack(move);
                removed.add(taken);
                remaining -= move;
            }
        }
        JsonObject commit = new JsonObject();
        commit.addProperty("token", token);
        String[] response = NativeContentPluginBridge.playerAction(player,
                "quest.item-consume.commit", commit.toString());
        if (!NativeContentPluginBridge.successful(response)) restore(player, removed);
        else {
            player.inventory.markDirty();
            applyPlayerResponse(player, response);
            NativeQuestService.send(player);
        }
    }

    private static void restore(EntityPlayerMP player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) player.inventory.addItemStackToInventory(stack);
        player.inventory.markDirty();
    }

    private static int inventoryCount(EntityPlayerMP player, ItemAmount amount) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) if (matches(stack, amount)) count += stack.getCount();
        return count;
    }

    private static boolean matches(ItemStack stack, ItemAmount amount) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null
                || !stack.getItem().getRegistryName().toString().equals(amount.item)
                || !amount.ignoreMeta && stack.getMetadata() != amount.meta) return false;
        if (amount.ignoreNbt || amount.nbt == null || amount.nbt.trim().isEmpty()) return true;
        try { return stack.hasTagCompound() && stack.getTagCompound().equals(JsonToNBT.getTagFromJson(amount.nbt)); }
        catch (Throwable ignored) { return false; }
    }

    private void activateIndexed(EntityPlayerMP player, Set<String> checked) {
        Map<Long, List<SpawnPoint>> dimension = spawnIndex.get(Integer.valueOf(player.dimension));
        if (dimension == null) {
            return;
        }
        int cellX = floor(player.posX / CELL_SIZE);
        int cellZ = floor(player.posZ / CELL_SIZE);
        for (int x = cellX - 4; x <= cellX + 4; x++) {
            for (int z = cellZ - 4; z <= cellZ + 4; z++) {
                List<SpawnPoint> points = dimension.get(Long.valueOf(cell(x, z)));
                if (points == null) {
                    continue;
                }
                for (SpawnPoint point : points) {
                    if (checked.contains(point.key) || !point.definition.enabled) {
                        continue;
                    }
                    double radius = clamp(point.definition.activationRadius, 4.0D, 256.0D);
                    if (distanceSq(player.posX, player.posY, player.posZ,
                            point.definition.x, point.definition.y, point.definition.z) > radius * radius) {
                        continue;
                    }
                    checked.add(point.key);
                    if ("dungeon_controller".equals(point.purpose)) {
                        activateDungeon(player, (Dungeon) point.definition, point.key);
                        continue;
                    }
                    if ("dungeon_static".equals(point.purpose)
                            && point.definition instanceof DungeonSpawn) {
                        activateStaticDungeonSpawn(player, point);
                        continue;
                    }
                    Long wait = respawnAfter.get(point.key);
                    if (wait != null && wait.longValue() > System.currentTimeMillis()) {
                        continue;
                    }
                    if (live(activeSpawns.get(point.key))) {
                        continue;
                    }
                    NpcTemplate template = effectiveTemplate(point.definition.templateId,
                            point.definition.overrides);
                    EntityMissionNpc npc = spawn(player.getServerWorld(), template,
                            point.definition.x, point.definition.y, point.definition.z,
                            point.key, point.key, null, point.purpose);
                    if (npc != null) {
                        npc.setHomePosAndDistance(new BlockPos(point.definition.x,
                                        point.definition.y, point.definition.z),
                                homeRadius(template, point.definition.leashRadius));
                        activeSpawns.put(point.key, npc.getUniqueID());
                        if (point.definition instanceof Boss) {
                            registerBoss(npc, (Boss) point.definition, point.key, point.key);
                        }
                    }
                }
            }
        }
    }

    private void activateStaticDungeonSpawn(EntityPlayerMP player, SpawnPoint point) {
        DungeonSpawn definition = (DungeonSpawn) point.definition;
        WorldServer world = server.getWorld(definition.dimension);
        if (world == null || !chunkLoaded(world, definition.x, definition.z)) return;
        NpcTemplate template = effectiveTemplate(definition.templateId, definition.overrides);
        if (template == null) return;
        int spawned = 0;
        long now = System.currentTimeMillis();
        for (int copy = 0; copy < definition.count; copy++) {
            String instance = point.key + "/" + copy;
            if (live(activeSpawns.get(instance))) continue;
            Long wait = respawnAfter.get(instance);
            if (wait != null && wait.longValue() > now) continue;
            double angle = copy * 2.39996322973D;
            double distance = copy == 0 ? 0.0D : Math.min(3.0D, 0.75D + copy * 0.35D);
            EntityMissionNpc npc = spawn(world, template,
                    definition.x + Math.cos(angle) * distance,
                    definition.y,
                    definition.z + Math.sin(angle) * distance,
                    point.key, instance, null, "dungeon_static");
            if (npc == null) continue;
            npc.setHomePosAndDistance(new BlockPos(definition.x, definition.y, definition.z),
                    homeRadius(template, definition.leashRadius));
            spawnDefinitions.put(instance, definition);
            spawned++;
        }
        if (spawned > 0) {
            JsonObject event = new JsonObject();
            event.addProperty("spawnId", definition.id);
            event.addProperty("count", spawned);
            NativeContentPluginBridge.recordEvent(point.key, "dungeon.fixed-spawned", player,
                    point.key, event.toString());
        }
    }

    private void activatePendingWorldBoss(EntityPlayerMP player, Set<String> checked) {
        for (WorldBossSpawn spawn : new ArrayList<WorldBossSpawn>(pendingWorldBosses)) {
            if (spawn.dimension != player.dimension || checked.contains(spawn.eventId)) {
                continue;
            }
            Boss boss = bosses.get(spawn.bossId);
            if (boss == null || !boss.enabled || live(activeSpawns.get(spawn.eventId))) {
                pendingWorldBosses.remove(spawn);
                pendingWorldBossIds.remove(spawn.eventId);
                continue;
            }
            double radius = clamp(spawn.activationRadius > 0.0D
                    ? spawn.activationRadius : boss.activationRadius, 8.0D, 256.0D);
            if (distanceSq(player.posX, player.posY, player.posZ,
                    spawn.x, spawn.y, spawn.z) > radius * radius) {
                continue;
            }
            checked.add(spawn.eventId);
            NpcTemplate template = effectiveTemplate(boss.templateId, boss.overrides);
            EntityMissionNpc npc = spawn(player.getServerWorld(), template,
                    spawn.x, spawn.y, spawn.z, boss.id, spawn.eventId, null, "world_boss");
            if (npc != null) {
                npc.setHomePosAndDistance(new BlockPos(spawn.x, spawn.y, spawn.z),
                        homeRadius(template, boss.leashRadius));
                activeSpawns.put(spawn.eventId, npc.getUniqueID());
                registerBoss(npc, boss, spawn.eventId, spawn.eventId);
                announceSpawn(npc, spawn.x, spawn.y, spawn.z);
                JsonObject event = new JsonObject();
                event.addProperty("dimension", spawn.dimension);
                event.addProperty("x", spawn.x);
                event.addProperty("y", spawn.y);
                event.addProperty("z", spawn.z);
                event.addProperty("activationRadius", radius);
                NativeContentPluginBridge.recordEvent(spawn.eventId, "boss.spawned", null,
                        boss.id, event.toString());
                pendingWorldBosses.remove(spawn);
                pendingWorldBossIds.remove(spawn.eventId);
            }
        }
    }

    private void activateDungeon(EntityPlayerMP player, Dungeon dungeon, String key) {
        if (dungeon == null || dungeon.spawns == null || dungeon.spawns.isEmpty()) return;
        long now = System.currentTimeMillis();
        DungeonRuntime runtime = dungeonRuntime.get(key);
        if (runtime == null) {
            runtime = new DungeonRuntime(key, dungeon, UUID.randomUUID().toString(), minimumWave(dungeon));
            dungeonRuntime.put(key, runtime);
            JsonObject event = new JsonObject();
            event.addProperty("dimension", dungeon.dimension);
            event.addProperty("x", dungeon.x);
            event.addProperty("y", dungeon.y);
            event.addProperty("z", dungeon.z);
            NativeContentPluginBridge.recordEvent(runtime.sessionId, "dungeon.started", player,
                    dungeon.id, event.toString());
        }
        pruneDungeon(runtime);
        if (!runtime.live.isEmpty() || now < runtime.nextWaveAt) return;
        if (runtime.wave > maximumWave(dungeon)) {
            completeDungeon(runtime, player);
            return;
        }
        spawnDungeonWave(runtime, player.getServerWorld());
    }

    private void spawnDungeonWave(DungeonRuntime runtime, WorldServer nearbyWorld) {
        int spawned = 0;
        int ordinal = 0;
        for (DungeonSpawn definition : runtime.definition.spawns) {
            if (!definition.enabled || definition.wave != runtime.wave) continue;
            WorldServer world = server.getWorld(definition.dimension);
            if (world == null || !chunkLoaded(world, definition.x, definition.z)) continue;
            NpcTemplate template = effectiveTemplate(definition.templateId, definition.overrides);
            if (template == null) continue;
            for (int copy = 0; copy < definition.count; copy++) {
                double angle = (copy + ordinal * 0.61803398875D) * 2.39996322973D;
                double distance = copy == 0 ? 0.0D : Math.min(3.0D, 0.75D + copy * 0.35D);
                double x = definition.x + Math.cos(angle) * distance;
                double z = definition.z + Math.sin(angle) * distance;
                String instance = "d/" + shortId(runtime.sessionId) + "/" + runtime.wave
                        + "/" + id(definition.id) + "/" + copy;
                EntityMissionNpc npc = spawn(world, template, x, definition.y, z,
                        runtime.definition.id, instance, null, "dungeon");
                if (npc == null) continue;
                npc.setHomePosAndDistance(new BlockPos(definition.x, definition.y, definition.z),
                        homeRadius(template, definition.leashRadius));
                runtime.live.add(npc.getUniqueID());
                dungeonSpawnRuns.put(instance, runtime.key);
                spawnDefinitions.put(instance, definition);
                spawned++;
            }
            ordinal++;
        }
        if (spawned > 0) {
            JsonObject event = new JsonObject();
            event.addProperty("wave", runtime.wave);
            event.addProperty("count", spawned);
            NativeContentPluginBridge.recordEvent(runtime.sessionId + ":" + runtime.wave,
                    "dungeon.wave-started", null, runtime.definition.id, event.toString());
        } else {
            runtime.nextWaveAt = System.currentTimeMillis() + 5000L;
        }
    }

    private void finishDungeonSpawn(EntityMissionNpc npc) {
        String runKey = dungeonSpawnRuns.remove(npc.getInstanceId());
        DungeonRuntime runtime = runKey == null ? null : dungeonRuntime.get(runKey);
        if (runtime == null) return;
        runtime.live.remove(npc.getUniqueID());
        pruneDungeon(runtime);
        if (!runtime.live.isEmpty()) return;
        runtime.wave++;
        runtime.nextWaveAt = System.currentTimeMillis()
                + waveDelay(runtime.definition, runtime.wave) * 1000L;
    }

    private void abandonDungeonSpawn(EntityMissionNpc npc) {
        String runKey = dungeonSpawnRuns.remove(npc.getInstanceId());
        DungeonRuntime runtime = runKey == null ? null : dungeonRuntime.get(runKey);
        if (runtime == null) return;
        runtime.live.remove(npc.getUniqueID());
        pruneDungeon(runtime);
        if (!runtime.live.isEmpty()) return;
        dungeonRuntime.remove(runtime.key);
        respawnAfter.put(runtime.key, Long.valueOf(System.currentTimeMillis() + 30000L));
        NativeContentPluginBridge.recordEvent(runtime.sessionId, "dungeon.abandoned", null,
                runtime.definition.id, "{}");
    }

    private void completeDungeon(DungeonRuntime runtime, EntityPlayerMP player) {
        dungeonRuntime.remove(runtime.key);
        respawnAfter.put(runtime.key, Long.valueOf(System.currentTimeMillis()
                + runtime.definition.resetSeconds * 1000L));
        NativeContentPluginBridge.recordEvent(runtime.sessionId, "dungeon.completed", player,
                runtime.definition.id, "{}");
        if (runtime.definition.announceCompletion) {
            server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.DARK_AQUA
                    + runtime.definition.name + TextFormatting.AQUA + " has been cleared."));
        }
    }

    private void pruneDungeon(DungeonRuntime runtime) {
        for (UUID entityId : new ArrayList<UUID>(runtime.live)) {
            if (!live(entityId)) runtime.live.remove(entityId);
        }
    }

    private static int minimumWave(Dungeon dungeon) {
        int result = Integer.MAX_VALUE;
        for (DungeonSpawn spawn : dungeon.spawns) if (spawn.enabled) result = Math.min(result, spawn.wave);
        return result == Integer.MAX_VALUE ? 1 : result;
    }

    private static int maximumWave(Dungeon dungeon) {
        int result = 0;
        for (DungeonSpawn spawn : dungeon.spawns) if (spawn.enabled) result = Math.max(result, spawn.wave);
        return result;
    }

    private static long waveDelay(Dungeon dungeon, int wave) {
        long delay = 0L;
        for (DungeonSpawn spawn : dungeon.spawns) if (spawn.enabled && spawn.wave == wave) {
            delay = Math.max(delay, spawn.waveDelaySeconds);
        }
        return delay;
    }

    private static String shortId(String value) {
        String clean = id(value);
        return clean.length() <= 16 ? clean : clean.substring(0, 16);
    }

    private EntityMissionNpc spawn(WorldServer world, NpcTemplate template,
                                   double x, double y, double z, String context,
                                   String instance, UUID owner, String purpose) {
        if (world == null || template == null || !chunkLoaded(world, x, z)) {
            return null;
        }
        EntityMissionNpc npc = new EntityMissionNpc(world);
        npc.configure(template, context, instance, owner, purpose);
        npc.setPosition(x, y, z);
        npc.setHomePosAndDistance(new BlockPos(x, y, z), homeRadius(template, 48.0D));
        activeSpawns.put(instance, npc.getUniqueID());
        if (world.spawnEntity(npc)) return npc;
        activeSpawns.remove(instance);
        return null;
    }

    private void registerBoss(EntityMissionNpc npc, Boss definition,
                              String instance, String spawnKey) {
        int nearby = 0;
        double radius = clamp(definition.activationRadius, 8.0D, 128.0D);
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && player.dimension == npc.dimension
                    && player.getDistanceSq(npc) <= radius * radius) nearby++;
        }
        int scaledPlayers = Math.max(1, Math.min(Math.max(1, definition.maximumScaledPlayers), nearby));
        double healthScale = 1.0D + (scaledPlayers - 1)
                * clamp(definition.healthPerAdditionalPlayer, 0.0D, 5.0D);
        npc.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
                .setBaseValue(Math.max(1.0D, npc.getMaxHealth() * healthScale));
        npc.setHealth(npc.getMaxHealth());
        BossRuntime runtime = new BossRuntime(instance, spawnKey, definition, npc.getUniqueID(),
                npc.posX, npc.posY, npc.posZ, scaledPlayers,
                npc.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue(),
                npc.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue());
        bossRuntime.put(instance, runtime);
        sendBossHud(runtime, npc);
    }

    public void interact(EntityPlayerMP player, EntityMissionNpc npc) {
        if (player == null || npc == null) {
            return;
        }
        UUID owner = npc.getOwningPlayer();
        if (owner != null && !owner.equals(player.getUniqueID()) && !player.canUseCommand(2, "reborncontent")) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", npc.getInstanceId());
        payload.addProperty("contextId", npc.getContextId());
        payload.addProperty("templateId", npc.getTemplateId());
        payload.addProperty("purpose", npc.getPurpose());
        String[] response = NativeContentPluginBridge.playerAction(player, "npc.interact", payload.toString());
        if (!NativeContentPluginBridge.successful(response)) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + NativeContentPluginBridge.message(response)), true);
            return;
        }
        String data = NativeContentPluginBridge.payload(response);
        if (!data.isEmpty()) {
            RebornAddonNetwork.sendNativeContentSync(player, "interaction", data);
        }
        applyPlayerResponse(player, response);
        NativeQuestService.send(player);
    }

    public void handlePlayerAction(EntityPlayerMP player, String action, String payload) {
        if (player == null) {
            return;
        }
        if (action.startsWith("admin.")) {
            handleAdminAction(player, action.substring(6), payload);
            return;
        }
        if ("mail.send".equals(action)) {
            sendMail(player, payload);
            return;
        }
        if ("mail.claim".equals(action)) {
            claimMail(player, payload);
            return;
        }
        String[] response = NativeContentPluginBridge.playerAction(player, action, payload);
        if (!NativeContentPluginBridge.successful(response)) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + NativeContentPluginBridge.message(response)), true);
        } else if (!NativeContentPluginBridge.payload(response).isEmpty()) {
            RebornAddonNetwork.sendNativeContentSync(player, "action", NativeContentPluginBridge.payload(response));
        }
        applyPlayerResponse(player, response);
        NativeQuestService.send(player);
    }

    public void applyPlayerResponse(EntityPlayerMP player, String[] response) {
        if (player == null || !NativeContentPluginBridge.successful(response)
                || NativeContentPluginBridge.payload(response).isEmpty()) return;
        try {
            JsonObject root = object(NativeContentPluginBridge.payload(response));
            JsonArray items = root.has("deliver") && root.get("deliver").isJsonArray()
                    ? root.getAsJsonArray("deliver")
                    : root.has("items") && root.get("items").isJsonArray()
                    ? root.getAsJsonArray("items") : new JsonArray();
            for (ItemStack stack : stacks(items)) {
                if (!player.inventory.addItemStackToInventory(stack)) {
                    NativeContentPluginBridge.playerAction(player, "mail.system-delivery",
                            stackJson(stack).toString());
                }
            }
            if (root.has("xp")) player.addExperience(Math.max(0, root.get("xp").getAsInt()));
            if (root.has("levels")) player.addExperienceLevel(Math.max(0, root.get("levels").getAsInt()));
            if (root.has("teleport") && root.get("teleport").isJsonObject()) {
                teleport(player, root.getAsJsonObject("teleport"));
            }
            if (root.has("commands") && root.get("commands").isJsonArray()) {
                runRewardCommands(player, root.getAsJsonArray("commands"));
            }
            player.inventory.markDirty();
        } catch (Throwable ignored) {
        }
    }

    private void teleport(EntityPlayerMP player, JsonObject value) {
        int dimension = value.has("dimension") ? value.get("dimension").getAsInt() : player.dimension;
        WorldServer destination = server.getWorld(dimension);
        if (destination == null) return;
        double x = value.has("x") ? value.get("x").getAsDouble() : player.posX;
        double y = value.has("y") ? value.get("y").getAsDouble() : player.posY;
        double z = value.has("z") ? value.get("z").getAsDouble() : player.posZ;
        float yaw = value.has("yaw") ? value.get("yaw").getAsFloat() : player.rotationYaw;
        float pitch = value.has("pitch") ? value.get("pitch").getAsFloat() : player.rotationPitch;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        if (player.dimension != dimension) player.changeDimension(dimension);
        player.connection.setPlayerLocation(x, y, z, yaw, pitch);
    }

    private void runRewardCommands(EntityPlayerMP player, JsonArray commands) {
        int count = 0;
        for (JsonElement element : commands) {
            if (++count > 64 || !element.isJsonPrimitive()) break;
            String command = element.getAsString().replace("{player}", player.getName())
                    .replace('\r', ' ').replace('\n', ' ').trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isEmpty() && command.length() <= 512) {
                server.getCommandManager().executeCommand(server, command);
            }
        }
    }

    private void sendMail(EntityPlayerMP player, String json) {
        try {
            JsonObject request = new JsonParser().parse(json).getAsJsonObject();
            String target = cleanLine(request, "target", 40);
            String subject = cleanLine(request, "subject", 80);
            String body = cleanText(request, "body", 1000);
            if (target.isEmpty() || subject.isEmpty() || body.isEmpty()) {
                status(player, false, "Recipient, subject, and note are required.");
                return;
            }
            List<MailTransfer> transfers = new ArrayList<MailTransfer>();
            Set<Integer> usedSlots = new HashSet<Integer>();
            JsonArray requested = request.has("attachments") && request.get("attachments").isJsonArray()
                    ? request.getAsJsonArray("attachments") : new JsonArray();
            if (requested.size() == 0 && request.has("slot")) {
                JsonObject legacy = new JsonObject();
                legacy.addProperty("slot", request.get("slot").getAsInt());
                legacy.addProperty("count", request.has("count") ? request.get("count").getAsInt() : 0);
                requested.add(legacy);
            }
            for (JsonElement element : requested) {
                if (!element.isJsonObject() || transfers.size() >= 9) break;
                JsonObject value = element.getAsJsonObject();
                int slot = value.has("slot") ? value.get("slot").getAsInt() : -1;
                if (slot < 0 || slot >= player.inventory.mainInventory.size()
                        || !usedSlots.add(Integer.valueOf(slot))) continue;
                ItemStack source = player.inventory.mainInventory.get(slot);
                if (source.isEmpty()) continue;
                if (NarutoLearnerDropProtectionHandler.isProtectedLearner(source)) {
                    status(player, false, "Learners and kekkei genkai items cannot be mailed.");
                    return;
                }
                if (ContentPolicyService.INSTANCE.denies(PolicyAction.MAIL, source)) {
                    status(player, false, "That item cannot be sent through mail.");
                    return;
                }
                int count = value.has("count") ? value.get("count").getAsInt() : source.getCount();
                transfers.add(new MailTransfer(slot, Math.max(1, Math.min(source.getCount(), count))));
            }
            JsonObject prepare = new JsonObject();
            prepare.addProperty("target", target);
            prepare.addProperty("subject", subject);
            prepare.addProperty("body", body);
            prepare.addProperty("attachmentCount", transfers.size());
            JsonArray manifest = new JsonArray();
            for (MailTransfer transfer : transfers) {
                ItemStack source = player.inventory.mainInventory.get(transfer.slot);
                JsonObject item = stackJson(source.copy());
                item.addProperty("count", transfer.count);
                manifest.add(item);
            }
            prepare.add("attachments", manifest);
            String[] response = NativeContentPluginBridge.playerAction(player, "mail.prepare", prepare.toString());
            if (!NativeContentPluginBridge.successful(response)) {
                status(player, false, NativeContentPluginBridge.message(response));
                return;
            }
            JsonObject prepared = object(NativeContentPluginBridge.payload(response));
            String token = cleanLine(prepared, "token", 96);
            if (token.isEmpty()) {
                status(player, false, "The mail service returned an invalid send token.");
                return;
            }
            List<ItemStack> attachments = new ArrayList<ItemStack>();
            for (MailTransfer transfer : transfers) {
                ItemStack source = player.inventory.mainInventory.get(transfer.slot);
                if (source.isEmpty() || source.getCount() < transfer.count) {
                    restore(player, attachments);
                    status(player, false, "An attachment changed before the message was sent.");
                    return;
                }
                attachments.add(source.splitStack(transfer.count));
            }
            JsonObject commit = new JsonObject();
            commit.addProperty("token", token);
            JsonArray committed = new JsonArray();
            for (ItemStack attachment : attachments) committed.add(stackJson(attachment));
            commit.add("attachments", committed);
            response = NativeContentPluginBridge.playerAction(player, "mail.commit", commit.toString());
            if (!NativeContentPluginBridge.successful(response)) {
                restore(player, attachments);
                status(player, false, NativeContentPluginBridge.message(response));
                return;
            }
            player.inventory.markDirty();
            status(player, true, NativeContentPluginBridge.message(response));
            NativeQuestService.send(player);
        } catch (Throwable ignored) {
            status(player, false, "That mail request was invalid.");
        }
    }

    private void claimMail(EntityPlayerMP player, String json) {
        String[] response = NativeContentPluginBridge.playerAction(player, "mail.claim.prepare", json);
        if (!NativeContentPluginBridge.successful(response)) {
            status(player, false, NativeContentPluginBridge.message(response));
            return;
        }
        try {
            JsonObject prepared = object(NativeContentPluginBridge.payload(response));
            String token = cleanLine(prepared, "token", 96);
            List<ItemStack> items = stacks(prepared.has("items") && prepared.get("items").isJsonArray()
                    ? prepared.getAsJsonArray("items") : new JsonArray());
            if (token.isEmpty() || items.isEmpty()) {
                status(player, false, "That message has no claimable attachments.");
                return;
            }
            if (!fits(player, items)) {
                status(player, false, "Make room in your inventory before claiming this mail.");
                return;
            }
            JsonObject commit = new JsonObject();
            commit.addProperty("token", token);
            response = NativeContentPluginBridge.playerAction(player, "mail.claim.commit", commit.toString());
            if (!NativeContentPluginBridge.successful(response)) {
                status(player, false, NativeContentPluginBridge.message(response));
                return;
            }
            for (ItemStack stack : items) player.inventory.addItemStackToInventory(stack);
            player.inventory.markDirty();
            status(player, true, NativeContentPluginBridge.message(response));
            NativeQuestService.send(player);
        } catch (Throwable ignored) {
            status(player, false, "The mail attachment data was invalid.");
        }
    }

    private static List<ItemStack> stacks(JsonArray items) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            ItemStack stack = itemStack(cleanLine(item, "item", 160),
                    item.has("count") ? item.get("count").getAsInt() : 1,
                    item.has("meta") ? item.get("meta").getAsInt() : 0,
                    item.has("nbt") ? item.get("nbt").getAsString() : "");
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    private static boolean fits(EntityPlayerMP player, List<ItemStack> additions) {
        List<ItemStack> slots = new ArrayList<ItemStack>();
        for (ItemStack current : player.inventory.mainInventory) slots.add(current.copy());
        for (ItemStack addition : additions) {
            ItemStack remaining = addition.copy();
            for (ItemStack current : slots) {
                if (remaining.isEmpty()) break;
                if (!current.isEmpty() && ItemStack.areItemsEqual(current, remaining)
                        && ItemStack.areItemStackTagsEqual(current, remaining)) {
                    int move = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                    if (move > 0) {
                        current.grow(move);
                        remaining.shrink(move);
                    }
                }
            }
            for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
                if (slots.get(i).isEmpty()) {
                    int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                    ItemStack placed = remaining.copy();
                    placed.setCount(move);
                    slots.set(i, placed);
                    remaining.shrink(move);
                }
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private static JsonObject stackJson(ItemStack stack) {
        JsonObject result = new JsonObject();
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) return result;
        result.addProperty("item", stack.getItem().getRegistryName().toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("meta", stack.getMetadata());
        result.addProperty("nbt", stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
        return result;
    }

    private static JsonObject object(String json) {
        return json == null || json.isEmpty() ? new JsonObject()
                : new JsonParser().parse(json).getAsJsonObject();
    }

    private static String cleanLine(JsonObject object, String key, int maximum) {
        String value = object.has(key) ? object.get(key).getAsString() : "";
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String cleanText(JsonObject object, String key, int maximum) {
        String value = object.has(key) ? object.get(key).getAsString().trim() : "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static void status(EntityPlayerMP player, boolean success, String message) {
        player.sendStatusMessage(new TextComponentString((success ? TextFormatting.GREEN : TextFormatting.RED)
                + (message == null || message.isEmpty() ? (success ? "Done." : "Request failed.") : message)), true);
    }

    public void sendAdminSnapshot(EntityPlayerMP player) {
        if (player == null || !player.canUseCommand(2, "reborncontent")) {
            return;
        }
        String[] response = NativeContentPluginBridge.adminSnapshot(player);
        JsonObject root = new JsonObject();
        root.addProperty("success", NativeContentPluginBridge.successful(response));
        root.addProperty("message", NativeContentPluginBridge.message(response));
        if (NativeContentPluginBridge.successful(response)
                && !NativeContentPluginBridge.payload(response).isEmpty()) {
            try {
                root.add("data", new JsonParser().parse(NativeContentPluginBridge.payload(response)));
            } catch (Throwable ignored) {
            }
        }
        JsonArray jutsus = new JsonArray();
        for (JutsuDefinition definition : JutsuRegistry.INSTANCE.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", definition.id());
            entry.addProperty("name", definition.displayName());
            entry.addProperty("source", definition.source());
            entry.addProperty("category", definition.category());
            entry.addProperty("restricted", definition.restricted());
            jutsus.add(entry);
        }
        root.add("jutsuCatalog", jutsus);
        RebornAddonNetwork.sendNativeContentSync(player, "admin", root.toString());
    }

    private void handleAdminAction(EntityPlayerMP player, String action, String payload) {
        if (!player.canUseCommand(2, "reborncontent")) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + "Operator access is required."), true);
            return;
        }
        if ("snapshot".equals(action)) {
            sendAdminSnapshot(player);
            return;
        }
        if ("draft.upsert".equals(action)) {
            List<String> errors = NativeContentDraftValidator.validateUpsert(payload);
            if (!errors.isEmpty()) {
                status(player, false, errors.get(0));
                return;
            }
        }
        String[] response = NativeContentPluginBridge.adminAction(player, action, payload);
        if (NativeContentPluginBridge.successful(response)) {
            refreshRuntime();
        }
        player.sendStatusMessage(new TextComponentString(
                (NativeContentPluginBridge.successful(response) ? TextFormatting.GREEN : TextFormatting.RED)
                        + NativeContentPluginBridge.message(response)), true);
        sendAdminSnapshot(player);
    }

    public void updatePath(EntityPlayerMP player, String pathId, BlockPos pos, boolean removeLast) {
        if (player == null || pos == null || !player.canUseCommand(2, "reborncontent")) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("pathId", id(pathId));
        payload.addProperty("dimension", player.dimension);
        payload.addProperty("x", pos.getX() + 0.5D);
        payload.addProperty("y", pos.getY());
        payload.addProperty("z", pos.getZ() + 0.5D);
        String[] response = NativeContentPluginBridge.adminAction(player,
                removeLast ? "path.remove-last" : "path.add-point", payload.toString());
        status(player, NativeContentPluginBridge.successful(response),
                NativeContentPluginBridge.message(response));
        if (NativeContentPluginBridge.successful(response)) refreshRuntime();
    }

    public List<String> pathIds(EntityPlayerMP player) {
        List<String> result = new ArrayList<String>();
        if (player == null || !player.canUseCommand(2, "reborncontent")) return result;
        String[] response = NativeContentPluginBridge.adminSnapshot(player);
        if (!NativeContentPluginBridge.successful(response)) return result;
        try {
            JsonObject root = object(NativeContentPluginBridge.payload(response));
            JsonArray paths = root.has("paths") && root.get("paths").isJsonArray()
                    ? root.getAsJsonArray("paths") : new JsonArray();
            for (JsonElement element : paths) if (element.isJsonObject()) {
                String value = cleanLine(element.getAsJsonObject(), "id", 96);
                if (!value.isEmpty()) result.add(value);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    public void updateNpcTarget(EntityMissionNpc npc) {
        if (npc == null || npc.world.isRemote) {
            return;
        }
        if ("quest".equals(npc.getPurpose()) && npc.getOwningPlayer() != null) {
            EntityPlayer owner = npc.world.getPlayerEntityByUUID(npc.getOwningPlayer());
            PlayerRuntime state = owner == null ? null : playerRuntime.get(owner.getUniqueID());
            if (state != null && "escort".equals(state.type)) {
                if (distanceSq(npc.posX, npc.posY, npc.posZ,
                        state.x, state.y, state.z) <= state.radius * state.radius) {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("questId", state.questId);
                    payload.addProperty("stepId", state.stepId);
                    payload.addProperty("instanceId", npc.getInstanceId());
                    NativeQuestService.action((EntityPlayerMP) owner,
                            "quest.escort-arrive", payload.toString());
                    npc.setDead();
                    return;
                }
                npc.setAttackTarget(null);
                if (npc.getDistanceSq(owner) > 9.0D) {
                    npc.getNavigator().tryMoveToEntityLiving(owner, 1.0D);
                }
                return;
            }
            if (state != null && "fight".equals(state.type) && owner != null) {
                npc.setAttackTarget(owner);
                return;
            }
        }
        NpcTemplate template = configuredTemplate(npc);
        if (template == null) {
            npc.setAttackTarget(null);
            return;
        }
        EntityLivingBase current = npc.getAttackTarget();
        boolean proactive = template.hostile || "hostile".equals(template.aggression)
                || !template.hostileVillages.isEmpty() || !template.hostileFactions.isEmpty();
        if (!proactive) {
            if (current == null || !current.isEntityAlive()
                    || npc.getDistanceSq(current) > template.followRange * template.followRange) {
                npc.setAttackTarget(null);
                followPath(npc, template);
            }
            return;
        }
        EntityLivingBase closest = null;
        double best = template.followRange * template.followRange;
        for (EntityPlayer player : npc.world.playerEntities) {
            if (player.isSpectator() || player.capabilities.isCreativeMode
                    || player.isInvisible() && !template.attacksInvisible
                    || template.requiresLineOfSight && !npc.getEntitySenses().canSee(player)
                    || !isHostile(template, player)) {
                continue;
            }
            double distance = npc.getDistanceSq(player);
            if (distance < best) {
                best = distance;
                closest = player;
            }
        }
        for (EntityMissionNpc other : npc.world.getEntitiesWithinAABB(EntityMissionNpc.class,
                npc.getEntityBoundingBox().grow(template.followRange))) {
            if (other == npc) continue;
            NpcTemplate otherTemplate = configuredTemplate(other);
            if (otherTemplate == null || !contains(template.hostileFactions, otherTemplate.faction)
                    || contains(template.friendlyFactions, otherTemplate.faction)
                    || template.requiresLineOfSight && !npc.getEntitySenses().canSee(other)) continue;
            double distance = npc.getDistanceSq(other);
            if (distance < best) {
                best = distance;
                closest = other;
            }
        }
        npc.setAttackTarget(closest);
        if (closest == null) followPath(npc, template);
    }

    public void alertNpcAllies(EntityMissionNpc source, EntityLivingBase attacker) {
        if (source == null || attacker == null || source.world.isRemote) return;
        NpcTemplate sourceTemplate = configuredTemplate(source);
        if (sourceTemplate == null || !sourceTemplate.assistsAllies) return;
        double radius = clamp(sourceTemplate.followRange, 4.0D, 64.0D);
        for (EntityMissionNpc ally : source.world.getEntitiesWithinAABB(EntityMissionNpc.class,
                source.getEntityBoundingBox().grow(radius))) {
            if (ally == source || ally.getAttackTarget() != null) continue;
            NpcTemplate allyTemplate = configuredTemplate(ally);
            if (allyTemplate == null || !allyTemplate.assistsAllies
                    || !sourceTemplate.faction.equalsIgnoreCase(allyTemplate.faction)) continue;
            ally.setRevengeTarget(attacker);
            ally.setAttackTarget(attacker);
        }
    }

    private void followPath(EntityMissionNpc npc, NpcTemplate template) {
        if (template == null || template.linkedTarget == null || template.linkedTarget.isEmpty()) return;
        NativeContentModel.PathDefinition path = paths.get(id(template.linkedTarget));
        if (path == null || path.points == null || path.points.isEmpty()) return;
        int cursor = pathCursor.containsKey(npc.getUniqueID()) ? pathCursor.get(npc.getUniqueID()).intValue() : 0;
        cursor = Math.max(0, Math.min(cursor, path.points.size() - 1));
        NativeContentModel.PathPoint point = path.points.get(cursor);
        if (point.dimension != npc.dimension) return;
        Long waitUntil = pathWaitUntil.get(npc.getUniqueID());
        if (waitUntil != null && waitUntil.longValue() > ticks) return;
        if (distanceSq(npc.posX, npc.posY, npc.posZ, point.x, point.y, point.z) <= 2.25D) {
            if (point.waitTicks > 0 && waitUntil == null) {
                pathWaitUntil.put(npc.getUniqueID(), Long.valueOf(ticks + Math.min(72000, point.waitTicks)));
                return;
            }
            pathWaitUntil.remove(npc.getUniqueID());
            if (cursor + 1 < path.points.size()) cursor++;
            else cursor = path.loop ? 0 : cursor;
            pathCursor.put(npc.getUniqueID(), Integer.valueOf(cursor));
            point = path.points.get(cursor);
        }
        npc.getNavigator().tryMoveToXYZ(point.x, point.y, point.z,
                clamp(point.speed, 0.05D, 1.5D));
    }

    private boolean isHostile(NpcTemplate template, EntityPlayer player) {
        Village village = player instanceof EntityPlayerMP
                ? VillageSelectionHandler.INSTANCE.getAssignedVillage((EntityPlayerMP) player) : null;
        String group = village == null ? "" : village.group();
        if (contains(template.friendlyVillages, group)) {
            return false;
        }
        return contains(template.hostileVillages, group) || template.hostile;
    }

    public int tryNpcJutsu(EntityMissionNpc npc, EntityLivingBase target) {
        NpcTemplate template = configuredTemplate(npc);
        if (template == null || template.jutsus == null || template.jutsus.isEmpty()) {
            return 20;
        }
        double distance = npc.getDistance(target);
        List<Jutsu> eligible = new ArrayList<Jutsu>();
        int totalWeight = 0;
        for (Jutsu configured : template.jutsus) {
            JutsuDefinition definition = JutsuRegistry.INSTANCE.get(configured.id);
            if (definition == null || distance < configured.minimumRange
                    || distance > configured.maximumRange
                    || (configured.obeyGlobalRules && !JutsuSettingsCache.INSTANCE.enabled(definition))) {
                continue;
            }
            eligible.add(configured);
            totalWeight += Math.max(1, configured.weight);
        }
        if (eligible.isEmpty()) {
            return 20;
        }
        int choice = random.nextInt(Math.max(1, totalWeight));
        Jutsu selected = eligible.get(0);
        for (Jutsu candidate : eligible) {
            choice -= Math.max(1, candidate.weight);
            if (choice < 0) {
                selected = candidate;
                break;
            }
        }
        JutsuDefinition definition = JutsuRegistry.INSTANCE.get(selected.id);
        npc.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        double damage = selected.damage;
        if (damage < 0.0D && selected.obeyGlobalRules) {
            damage = JutsuSettingsCache.INSTANCE.decimal(definition, "damage", -1.0D);
        }
        long configuredDuration = Math.max(1, selected.durationTicks);
        if (selected.obeyGlobalRules) {
            configuredDuration = JutsuSettingsCache.INSTANCE.ticks(
                    definition, "max-duration", configuredDuration);
        }
        boolean used = NativeNpcJutsuExecutor.execute(npc, target, definition,
                Math.max(0.01F, selected.power), damage,
                (int) Math.min(Integer.MAX_VALUE, configuredDuration));
        if (!used) {
            return 20;
        }
        if (!selected.obeyCooldowns) {
            return 10;
        }
        long cooldown = Math.max(1, selected.cooldownTicks);
        if (selected.obeyGlobalRules) {
            cooldown = JutsuSettingsCache.INSTANCE.ticks(definition, "cooldown", cooldown);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, cooldown));
    }

    public void beginNpcJutsu(EntityMissionNpc npc, double damage) {
        activeCast.set(new CastContext(npc, damage));
    }

    public void endNpcJutsu() {
        activeCast.remove();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && event.getEntity() instanceof EntityMissionNpc
                && server != null) {
            trackLoadedNpc((EntityMissionNpc) event.getEntity());
            return;
        }
        CastContext cast = activeCast.get();
        if (cast == null || event.getWorld().isRemote || event.getEntity() == cast.npc) {
            return;
        }
        NBTTagCompound data = event.getEntity().getEntityData();
        data.setString("RebornNativeNpc", cast.npc.getUniqueID().toString());
        data.setString("RebornNativeInstance", cast.npc.getInstanceId());
        if (cast.damage >= 0.0D) {
            data.setDouble("RebornNativeDamage", cast.damage);
        }
    }

    private void reconcileLoadedEntities() {
        if (server == null) return;
        for (WorldServer world : server.worlds) {
            for (EntityMissionNpc npc : world.getEntities(EntityMissionNpc.class, entity -> true)) {
                trackLoadedNpc(npc);
            }
        }
    }

    private void trackLoadedNpc(EntityMissionNpc npc) {
        if (npc == null || npc.getInstanceId().isEmpty()
                || live(activeSpawns.get(npc.getInstanceId()))) return;
        activeSpawns.put(npc.getInstanceId(), npc.getUniqueID());
        if (("boss".equals(npc.getPurpose()) || "world_boss".equals(npc.getPurpose()))
                && !bossRuntime.containsKey(npc.getInstanceId())) {
            Boss definition = bosses.get(id(npc.getContextId()));
            if (definition != null) registerBoss(npc, definition,
                    npc.getInstanceId(), npc.getInstanceId());
        }
        if ("dungeon".equals(npc.getPurpose())) {
            Dungeon definition = dungeons.get(id(npc.getContextId()));
            if (definition != null && !"fixed".equals(definition.mode)) {
                DungeonRuntime runtime = dungeonRuntime.get(definition.id);
                if (runtime == null) {
                    runtime = new DungeonRuntime(definition.id, definition,
                            "recovered-" + definition.id, recoveredWave(npc.getInstanceId()));
                    dungeonRuntime.put(definition.id, runtime);
                }
                runtime.live.add(npc.getUniqueID());
                dungeonSpawnRuns.put(npc.getInstanceId(), definition.id);
            }
        }
        if ("dungeon_static".equals(npc.getPurpose())) {
            NativeContentModel.SpawnDefinition definition = spawnDefinitions.get(npc.getContextId());
            if (definition != null) spawnDefinitions.put(npc.getInstanceId(), definition);
        }
    }

    private static int recoveredWave(String instance) {
        if (instance != null) {
            String[] parts = instance.split("/");
            if (parts.length > 2) {
                try { return Math.max(1, Integer.parseInt(parts[2])); }
                catch (NumberFormatException ignored) {
                }
            }
        }
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }
        DamageSource source = event.getSource();
        Double configuredDamage = configuredNpcDamage(source);
        if (configuredDamage != null) {
            event.setAmount((float) Math.max(0.0D, configuredDamage.doubleValue()));
        }
        if (!(event.getEntityLiving() instanceof EntityMissionNpc)) {
            return;
        }
        EntityMissionNpc npc = (EntityMissionNpc) event.getEntityLiving();
        BossRuntime boss = bossRuntime.get(npc.getInstanceId());
        if (boss == null) {
            return;
        }
        EntityPlayerMP player = sourcePlayer(source);
        if (player == null) {
            return;
        }
        ShinobiCodexService.INSTANCE.discoverBoss(player, boss.definition.id,
                npc.hasCustomName() ? npc.getCustomNameTag() : npc.getName());
        double damage = Math.min(Math.max(0.0D, event.getAmount()), Math.max(0.0D, npc.getHealth()));
        Double previous = boss.damage.get(player.getUniqueID());
        boss.damage.put(player.getUniqueID(), Double.valueOf((previous == null ? 0.0D : previous) + damage));
        sendBossHud(boss, npc);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }
        EntityPlayerMP killer = sourcePlayer(event.getSource());
        if (killer != null) recordQuestKill(killer, event.getEntityLiving());
        if (!(event.getEntityLiving() instanceof EntityMissionNpc)) return;
        EntityMissionNpc npc = (EntityMissionNpc) event.getEntityLiving();
        activeSpawns.remove(npc.getInstanceId());
        finishDungeonSpawn(npc);
        BossRuntime boss = bossRuntime.remove(npc.getInstanceId());
        if (boss != null) {
            finishBoss(npc, boss);
            long delay = Math.max(0L, boss.definition.respawnSeconds) * 1000L;
            respawnAfter.put(boss.spawnKey, Long.valueOf(System.currentTimeMillis() + delay));
        }
        NativeContentModel.SpawnDefinition spawnDefinition = spawnDefinitions.get(npc.getInstanceId());
        if (spawnDefinition != null && boss == null) {
            respawnAfter.put(npc.getInstanceId(), Long.valueOf(System.currentTimeMillis()
                    + Math.max(0L, spawnDefinition.respawnSeconds) * 1000L));
            if (killer != null && spawnDefinition.drops != null && !spawnDefinition.drops.isEmpty()) {
                JsonObject payload = new JsonObject();
                payload.addProperty("eventId", UUID.randomUUID().toString());
                payload.addProperty("spawnId", spawnDefinition.id);
                payload.addProperty("instanceId", npc.getInstanceId());
                String[] response = NativeContentPluginBridge.playerAction(killer,
                        "npc.resolve-drops", payload.toString());
                applyPlayerResponse(killer, response);
            }
        }
        if ("quest".equals(npc.getPurpose()) && npc.getOwningPlayer() != null) {
            EntityPlayerMP owner = server.getPlayerList().getPlayerByUUID(npc.getOwningPlayer());
            if (owner != null) {
                JsonObject payload = new JsonObject();
                payload.addProperty("questId", npc.getContextId());
                payload.addProperty("instanceId", npc.getInstanceId());
                NativeQuestService.action(owner, "quest.npc-killed", payload.toString());
            }
        }
    }

    private void recordQuestKill(EntityPlayerMP player, EntityLivingBase killed) {
        PlayerRuntime state = playerRuntime.get(player.getUniqueID());
        if (state == null || !state.active || !"kill".equals(state.type)) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("questId", state.questId);
        payload.addProperty("stepId", state.stepId);
        payload.addProperty("target", state.target);
        payload.addProperty("entityName", killed.getName());
        ResourceLocation registryName = EntityList.getKey(killed);
        payload.addProperty("entityId", registryName == null ? "" : registryName.toString());
        payload.addProperty("nativeTemplateId", killed instanceof EntityMissionNpc
                ? ((EntityMissionNpc) killed).getTemplateId() : "");
        payload.addProperty("eventId", UUID.randomUUID().toString());
        NativeQuestService.action(player, "quest.kill", payload.toString());
    }

    private void finishBoss(EntityMissionNpc npc, BossRuntime boss) {
        clearBossSummons(boss);
        String eventId = boss.instance;
        double maximum = Math.max(1.0D, npc.getMaxHealth());
        for (Map.Entry<UUID, Double> entry : boss.damage.entrySet()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            double exact = Math.min(100.0D, entry.getValue().doubleValue() * 100.0D / maximum);
            long rounded = Math.round(exact);
            RebornAddonNetwork.sendNativeContentSync(player, "boss_clear", "{}");
            if (rounded + 0.000001D < boss.definition.minimumContribution) {
                player.sendMessage(new TextComponentString(TextFormatting.GRAY
                        + "Your contribution did not qualify for this boss's rewards."));
                continue;
            }
            String[] response = NativeContentPluginBridge.resolveBossRewards(
                    eventId, boss.definition.id, player, exact);
            if (!NativeContentPluginBridge.successful(response)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "The boss reward service could not resolve your reward."));
                continue;
            }
            deliverRewards(player, NativeContentPluginBridge.payload(response));
        }
        NativeContentPluginBridge.recordEvent(eventId, "boss.defeated", null,
                boss.definition.id, "{}");
    }

    private void clearBossSummons(BossRuntime boss) {
        if (boss == null) return;
        for (UUID entityId : new ArrayList<UUID>(boss.summons)) {
            EntityMissionNpc summon = findNpc(entityId);
            if (summon != null) {
                activeSpawns.remove(summon.getInstanceId());
                summon.setDead();
            }
        }
        boss.summons.clear();
    }

    private void deliverRewards(EntityPlayerMP player, String json) {
        boolean awarded = false;
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray items = root.has("items") && root.get("items").isJsonArray()
                    ? root.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : items) {
                ItemAmount amount = GSON.fromJson(element, ItemAmount.class);
                ItemStack stack = itemStack(amount.item, amount.count, amount.meta, amount.nbt);
                if (stack.isEmpty()) {
                    continue;
                }
                awarded = true;
                if (!player.inventory.addItemStackToInventory(stack)) {
                    JsonObject delivery = new JsonObject();
                    delivery.addProperty("item", amount.item);
                    delivery.addProperty("count", amount.count);
                    delivery.addProperty("nbt", amount.nbt);
                    NativeContentPluginBridge.playerAction(player, "mail.system-delivery", delivery.toString());
                }
            }
            String message = root.has("message") ? root.get("message").getAsString() : "";
            if (!message.isEmpty()) {
                player.sendMessage(new TextComponentString((awarded ? TextFormatting.GREEN : TextFormatting.GRAY)
                        + message));
            }
        } catch (Throwable ignored) {
            player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "The boss reward response was invalid."));
        }
    }

    private void sendBossHud(BossRuntime boss, EntityMissionNpc npc) {
        if (server == null || npc == null) {
            return;
        }
        double maximum = Math.max(1.0D, npc.getMaxHealth());
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.dimension != npc.dimension || player.getDistanceSq(npc) > 16384.0D) {
                continue;
            }
            double damage = boss.damage.containsKey(player.getUniqueID())
                    ? boss.damage.get(player.getUniqueID()).doubleValue() : 0.0D;
            JsonObject data = new JsonObject();
            data.addProperty("name", npc.getName());
            data.addProperty("health", Math.max(0.0D, npc.getHealth()));
            data.addProperty("maximum", maximum);
            data.addProperty("contribution", Math.min(100.0D, damage * 100.0D / maximum));
            data.addProperty("phase", boss.phaseName);
            data.addProperty("enraged", boss.enraged);
            data.addProperty("scaledPlayers", boss.scaledPlayers);
            RebornAddonNetwork.sendNativeContentSync(player, "boss", data.toString());
        }
    }

    private void cleanupEntities() {
        long now = System.currentTimeMillis();
        for (WorldServer world : server.worlds) {
            for (EntityMissionNpc npc : new ArrayList<EntityMissionNpc>(
                    world.getEntities(EntityMissionNpc.class, entity -> true))) {
                boolean keep = shouldKeep(npc);
                if (keep) {
                    orphanedSince.remove(npc.getUniqueID());
                    continue;
                }
                Long since = orphanedSince.get(npc.getUniqueID());
                if (since == null) {
                    orphanedSince.put(npc.getUniqueID(), Long.valueOf(now));
                } else if (now - since.longValue() >= ABANDONED_NPC_MILLIS) {
                    activeSpawns.remove(npc.getInstanceId());
                    BossRuntime boss = bossRuntime.remove(npc.getInstanceId());
                    if (boss != null) {
                        clearBossSummons(boss);
                        long delay = Math.max(0L, boss.definition.respawnSeconds) * 1000L;
                        respawnAfter.put(boss.spawnKey, Long.valueOf(now + delay));
                        NativeContentPluginBridge.recordEvent(boss.instance, "boss.despawned", null,
                                boss.definition.id, "{}");
                    }
                    NativeContentModel.SpawnDefinition spawnDefinition =
                            spawnDefinitions.get(npc.getInstanceId());
                    if (spawnDefinition != null && boss == null) {
                        respawnAfter.put(npc.getInstanceId(), Long.valueOf(now
                                + Math.max(0L, spawnDefinition.respawnSeconds) * 1000L));
                    }
                    npc.setDead();
                    abandonDungeonSpawn(npc);
                    orphanedSince.remove(npc.getUniqueID());
                    pathCursor.remove(npc.getUniqueID());
                    pathWaitUntil.remove(npc.getUniqueID());
                }
            }
        }
    }

    private boolean shouldKeep(EntityMissionNpc npc) {
        UUID owner = npc.getOwningPlayer();
        if (owner != null) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(owner);
            return player != null && player.dimension == npc.dimension
                    && player.getDistanceSq(npc) <= 16384.0D;
        }
        BossRuntime boss = bossRuntime.get(npc.getInstanceId());
        NativeContentModel.SpawnDefinition spawnDefinition = spawnDefinitions.get(npc.getInstanceId());
        double leash = boss != null ? clamp(boss.definition.leashRadius, 16.0D, 256.0D)
                : spawnDefinition != null ? clamp(spawnDefinition.leashRadius, 16.0D, 256.0D) : 64.0D;
        for (EntityPlayer player : npc.world.playerEntities) {
            if (player.getDistanceSq(npc) <= leash * leash) {
                return true;
            }
        }
        return false;
    }

    private void pollWorldBosses() {
        String[] response = NativeContentPluginBridge.pollWorldBossSpawns(
                System.currentTimeMillis() / 1000L);
        if (!NativeContentPluginBridge.successful(response)
                || NativeContentPluginBridge.payload(response).isEmpty()) {
            return;
        }
        try {
            JsonArray array = new JsonParser().parse(NativeContentPluginBridge.payload(response)).getAsJsonArray();
            for (JsonElement element : array) {
                WorldBossSpawn spawn = GSON.fromJson(element, WorldBossSpawn.class);
                if (spawn != null && !id(spawn.eventId).isEmpty() && bosses.containsKey(id(spawn.bossId))
                        && pendingWorldBosses.size() < 128) {
                    spawn.eventId = id(spawn.eventId);
                    spawn.bossId = id(spawn.bossId);
                    Boss boss = bosses.get(spawn.bossId);
                    spawn.activationRadius = clamp(spawn.activationRadius > 0.0D
                            ? spawn.activationRadius : boss.activationRadius, 8.0D, 256.0D);
                    if (pendingWorldBossIds.add(spawn.eventId)) pendingWorldBosses.add(spawn);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void announceSpawn(EntityMissionNpc npc, double x, double y, double z) {
        TextComponentString message = new TextComponentString(TextFormatting.DARK_PURPLE
                + npc.getName() + TextFormatting.LIGHT_PURPLE + " has appeared at "
                + TextFormatting.AQUA + (int) Math.floor(x) + ", " + (int) Math.floor(y)
                + ", " + (int) Math.floor(z) + TextFormatting.LIGHT_PURPLE + ".");
        server.getPlayerList().sendMessage(message);
    }

    private Double configuredNpcDamage(DamageSource source) {
        for (Entity entity : sourceEntities(source)) {
            if (entity != null && entity.getEntityData().hasKey("RebornNativeDamage")) {
                return Double.valueOf(entity.getEntityData().getDouble("RebornNativeDamage"));
            }
        }
        return null;
    }

    private EntityPlayerMP sourcePlayer(DamageSource source) {
        Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
        List<Entity> queue = sourceEntities(source);
        for (int depth = 0; depth < 4 && !queue.isEmpty(); depth++) {
            List<Entity> next = new ArrayList<Entity>();
            for (Entity entity : queue) {
                if (entity == null || !visited.add(entity)) {
                    continue;
                }
                if (entity instanceof EntityPlayerMP) {
                    return (EntityPlayerMP) entity;
                }
                String ownerId = ownerUuid(entity);
                if (!ownerId.isEmpty()) {
                    try {
                        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(UUID.fromString(ownerId));
                        if (player != null) {
                            return player;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                for (Method method : ownerMethods(entity.getClass())) {
                    try {
                        Object owner = method.invoke(entity);
                        if (owner instanceof Entity) {
                            next.add((Entity) owner);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                for (Method method : ownerUuidMethods(entity.getClass())) {
                    try {
                        Object owner = method.invoke(entity);
                        EntityPlayerMP player = owner instanceof UUID
                                ? server.getPlayerList().getPlayerByUUID((UUID) owner)
                                : owner == null ? null : server.getPlayerList().getPlayerByUUID(
                                UUID.fromString(owner.toString()));
                        if (player != null) return player;
                    } catch (Throwable ignored) {
                    }
                }
                for (Field field : ownerFields(entity.getClass())) {
                    try {
                        Object owner = field.get(entity);
                        if (owner instanceof EntityPlayerMP) return (EntityPlayerMP) owner;
                        if (owner instanceof Entity) next.add((Entity) owner);
                        else if (owner instanceof UUID) {
                            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID((UUID) owner);
                            if (player != null) return player;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            queue = next;
        }
        return null;
    }

    private List<Entity> sourceEntities(DamageSource source) {
        List<Entity> entities = new ArrayList<Entity>();
        if (source != null) {
            entities.add(source.getTrueSource());
            if (source.getImmediateSource() != source.getTrueSource()) {
                entities.add(source.getImmediateSource());
            }
        }
        return entities;
    }

    private List<Method> ownerMethods(Class<?> type) {
        List<Method> cached = ownerMethods.get(type);
        if (cached != null) {
            return cached;
        }
        List<Method> methods = new ArrayList<Method>();
        String[] names = {"getOwner", "getThrower", "getShooter", "getSummoner", "getUser", "getCaster"};
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterTypes().length == 0
                        && Entity.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            } catch (Throwable ignored) {
            }
        }
        methods = Collections.unmodifiableList(methods);
        ownerMethods.put(type, methods);
        return methods;
    }

    private List<Method> ownerUuidMethods(Class<?> type) {
        List<Method> cached = ownerUuidMethods.get(type);
        if (cached != null) return cached;
        List<Method> methods = new ArrayList<Method>();
        String[] names = {"getOwnerId", "getOwnerUUID", "getThrowerId", "getShooterId",
                "getCasterId", "getSummonerId"};
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterTypes().length == 0
                        && (UUID.class.isAssignableFrom(method.getReturnType())
                        || String.class == method.getReturnType())) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            } catch (Throwable ignored) {
            }
        }
        methods = Collections.unmodifiableList(methods);
        ownerUuidMethods.put(type, methods);
        return methods;
    }

    private List<Field> ownerFields(Class<?> type) {
        List<Field> cached = ownerFields.get(type);
        if (cached != null) return cached;
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!(name.contains("owner") || name.contains("shooter") || name.contains("thrower")
                        || name.contains("caster") || name.contains("summoner") || name.equals("player"))) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (!Entity.class.isAssignableFrom(fieldType) && fieldType != UUID.class) continue;
                try {
                    field.setAccessible(true);
                    fields.add(field);
                } catch (Throwable ignored) {
                }
            }
        }
        fields = Collections.unmodifiableList(fields);
        ownerFields.put(type, fields);
        return fields;
    }

    private static String ownerUuid(Entity entity) {
        if (entity == null) return "";
        String[] keys = {"OwnerUUID", "ownerUUID", "Owner", "owner", "CasterUUID",
                "casterUUID", "ShooterUUID", "ThrowerUUID", "SummonerUUID"};
        for (String key : keys) {
            String value = entity.getEntityData().getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private NpcTemplate effectiveTemplate(String templateId, JsonObject overrides) {
        NpcTemplate base = templates.get(id(templateId));
        if (base == null) {
            return null;
        }
        if (overrides == null) {
            return base;
        }
        JsonObject merged = GSON.toJsonTree(base).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        NpcTemplate result = GSON.fromJson(merged, NpcTemplate.class);
        sanitize(result);
        return result;
    }

    private NpcTemplate configuredTemplate(EntityMissionNpc npc) {
        if (npc == null) return null;
        NpcTemplate configured = npc.getConfiguredTemplate();
        return configured == null ? templates.get(npc.getTemplateId()) : configured;
    }

    private boolean live(UUID id) {
        if (id == null || server == null) {
            return false;
        }
        for (WorldServer world : server.worlds) {
            Entity entity = world.getEntityFromUuid(id);
            if (entity != null && !entity.isDead) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack itemStack(String registryName) {
        return itemStack(registryName, 1, 0, "");
    }

    public static ItemStack itemStack(String registryName, int count, String nbt) {
        return itemStack(registryName, count, 0, nbt);
    }

    public static ItemStack itemStack(String registryName, int count, int meta, String nbt) {
        if (registryName == null || registryName.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName.trim()));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, count)), Math.max(0, meta));
            if (nbt != null && !nbt.trim().isEmpty()) {
                stack.setTagCompound(JsonToNBT.getTagFromJson(nbt));
            }
            return stack;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void sanitize(NpcTemplate template) {
        if (template == null) {
            return;
        }
        template.id = id(template.id);
        template.name = text(template.name, 64, "NPC");
        template.skin = text(template.skin, 1024, "");
        template.health = clamp(template.health, 1.0D, 1000000.0D);
        template.meleeDamage = clamp(template.meleeDamage, 0.0D, 100000.0D);
        template.damageReduction = clamp(template.damageReduction, 0.0D, 0.95D);
        template.projectileDamageReduction = clamp(template.projectileDamageReduction, 0.0D, 0.95D);
        template.magicDamageReduction = clamp(template.magicDamageReduction, 0.0D, 0.95D);
        template.explosionDamageReduction = clamp(template.explosionDamageReduction, 0.0D, 0.95D);
        template.knockbackResistance = clamp(template.knockbackResistance, 0.0D, 1.0D);
        template.attackKnockback = clamp(template.attackKnockback, 0.0D, 8.0D);
        template.followRange = clamp(template.followRange, 4.0D, 128.0D);
        template.moveSpeed = clamp(template.moveSpeed, 0.05D, 1.25D);
        template.wanderRadius = clamp(template.wanderRadius, 0.0D, 128.0D);
        template.meleeReach = clamp(template.meleeReach, 0.0D, 16.0D);
        template.regenerationPerSecond = clamp(template.regenerationPerSecond, 0.0D, 100000.0D);
        template.combatRegenerationPerSecond = clamp(template.combatRegenerationPerSecond,
                0.0D, 100000.0D);
        template.meleeIntervalTicks = Math.max(1, Math.min(1200, template.meleeIntervalTicks));
        template.aggression = text(template.aggression, 16,
                template.hostile ? "hostile" : "passive").toLowerCase(Locale.ROOT);
        if (!"hostile".equals(template.aggression) && !"defensive".equals(template.aggression)) {
            template.aggression = "passive";
        }
        template.faction = id(template.faction);
        if (template.faction.isEmpty()) template.faction = "neutral";
        template.ambientSound = text(template.ambientSound, 160, "");
        template.hurtSound = text(template.hurtSound, 160, "");
        template.deathSound = text(template.deathSound, 160, "");
        if (template.equipment == null) template.equipment = new NativeContentModel.Equipment();
        if (template.jutsus == null) template.jutsus = new ArrayList<Jutsu>();
        if (template.friendlyVillages == null) template.friendlyVillages = new ArrayList<String>();
        if (template.hostileVillages == null) template.hostileVillages = new ArrayList<String>();
        if (template.friendlyFactions == null) template.friendlyFactions = new ArrayList<String>();
        if (template.hostileFactions == null) template.hostileFactions = new ArrayList<String>();
    }

    private static void sanitize(NativeContentModel.SpawnDefinition spawn) {
        if (spawn == null) return;
        spawn.id = id(spawn.id);
        spawn.name = text(spawn.name, 64, spawn.id);
        spawn.templateId = id(spawn.templateId);
        spawn.activationRadius = clamp(spawn.activationRadius, 4.0D, 256.0D);
        spawn.leashRadius = clamp(spawn.leashRadius, 16.0D, 256.0D);
        spawn.respawnSeconds = Math.max(0L, Math.min(31536000L, spawn.respawnSeconds));
        if (spawn.drops == null) spawn.drops = new ArrayList<NativeContentModel.ItemAmount>();
        if (spawn.drops.size() > 64) spawn.drops = new ArrayList<NativeContentModel.ItemAmount>(
                spawn.drops.subList(0, 64));
    }

    private static void sanitize(Boss boss) {
        if (boss == null) return;
        sanitize((NativeContentModel.SpawnDefinition) boss);
        boss.minimumContribution = clamp(boss.minimumContribution, 0.0D, 100.0D);
        boss.arenaRadius = clamp(boss.arenaRadius, 16.0D, 256.0D);
        boss.healthPerAdditionalPlayer = clamp(boss.healthPerAdditionalPlayer, 0.0D, 5.0D);
        boss.maximumScaledPlayers = Math.max(1, Math.min(100, boss.maximumScaledPlayers));
        boss.enrageSeconds = Math.max(0L, Math.min(86400L, boss.enrageSeconds));
        boss.enrageDamageMultiplier = clamp(boss.enrageDamageMultiplier, 0.1D, 20.0D);
        boss.enrageSpeedMultiplier = clamp(boss.enrageSpeedMultiplier, 0.1D, 5.0D);
        if (boss.phases == null) boss.phases = new ArrayList<BossPhase>();
        if (boss.phases.size() > 16) boss.phases = new ArrayList<BossPhase>(boss.phases.subList(0, 16));
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < boss.phases.size(); i++) {
            BossPhase phase = boss.phases.get(i);
            if (phase == null) {
                phase = new BossPhase();
                boss.phases.set(i, phase);
            }
            phase.id = id(phase.id);
            if (phase.id.isEmpty() || !ids.add(phase.id)) phase.id = "phase-" + (i + 1);
            ids.add(phase.id);
            phase.name = text(phase.name, 80, "Boss Phase " + (i + 1));
            phase.telegraph = text(phase.telegraph, 240, "");
            phase.healthPercent = clamp(phase.healthPercent, 0.0D, 100.0D);
            phase.invulnerableSeconds = Math.max(0L, Math.min(60L, phase.invulnerableSeconds));
            if (phase.jutsus == null) phase.jutsus = new ArrayList<Jutsu>();
            if (phase.jutsus.size() > 32) phase.jutsus = new ArrayList<Jutsu>(phase.jutsus.subList(0, 32));
            if (phase.summons == null) phase.summons = new ArrayList<NativeContentModel.PhaseSummon>();
            if (phase.summons.size() > 16) phase.summons = new ArrayList<NativeContentModel.PhaseSummon>(
                    phase.summons.subList(0, 16));
            for (NativeContentModel.PhaseSummon summon : phase.summons) {
                if (summon == null) continue;
                summon.templateId = id(summon.templateId);
                summon.count = Math.max(1, Math.min(16, summon.count));
                summon.radius = clamp(summon.radius, 1.0D, 64.0D);
            }
        }
        Collections.sort(boss.phases, new Comparator<BossPhase>() {
            @Override
            public int compare(BossPhase left, BossPhase right) {
                return Double.compare(right.healthPercent, left.healthPercent);
            }
        });
    }

    private static boolean contains(List<String> values, String value) {
        if (values == null) return false;
        for (String entry : values) {
            if (value.equalsIgnoreCase(entry)) return true;
        }
        return false;
    }

    private static boolean chunkLoaded(WorldServer world, double x, double z) {
        return world.getChunkProvider().getLoadedChunk(floor(x) >> 4, floor(z) >> 4) != null;
    }

    private static long cell(double x, double z) {
        return cell(floor(x / CELL_SIZE), floor(z / CELL_SIZE));
    }

    private static long cell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double distanceSq(double x1, double y1, double z1,
                                     double x2, double y2, double z2) {
        double x = x1 - x2;
        double y = y1 - y2;
        double z = z1 - z2;
        return x * x + y * y + z * z;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int homeRadius(NpcTemplate template, double configured) {
        double radius = clamp(configured, 1.0D, 256.0D);
        if (template != null && template.wanderRadius > 0.0D) {
            radius = Math.min(radius, template.wanderRadius);
        }
        return (int) Math.max(1.0D, Math.round(radius));
    }

    private static String id(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        result = result.replaceAll("[^a-z0-9_./-]", "-");
        return result.length() <= 96 ? result : result.substring(0, 96);
    }

    private static String text(String value, int maximum, String fallback) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) result = fallback;
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static final class PlayerRuntime {
        private boolean active;
        private String questId = "";
        private String stepId = "";
        private String type = "";
        private String templateId = "";
        private JsonObject overrides;
        private int dimension;
        private double x;
        private double y;
        private double z;
        private double radius = 8.0D;
        private boolean spawnNpc;
        private Double spawnX;
        private Double spawnY;
        private Double spawnZ;
        private int amount = 1;
        private String target = "";
        private List<ItemAmount> requirements = new ArrayList<ItemAmount>();
        private long updatedAt;

        private String payload() {
            JsonObject data = new JsonObject();
            data.addProperty("questId", questId);
            data.addProperty("stepId", stepId);
            return data.toString();
        }
    }

    private static final class SpawnPoint {
        private final String key;
        private final NativeContentModel.SpawnDefinition definition;
        private final String purpose;

        private SpawnPoint(String key, NativeContentModel.SpawnDefinition definition, String purpose) {
            this.key = key;
            this.definition = definition;
            this.purpose = purpose;
        }
    }

    private static final class BossRuntime {
        private final String instance;
        private final String spawnKey;
        private final Boss definition;
        private final UUID entityId;
        private final Map<UUID, Double> damage = new HashMap<UUID, Double>();
        private final Set<String> completedPhases = new HashSet<String>();
        private final Set<UUID> summons = new HashSet<UUID>();
        private final double x;
        private final double y;
        private final double z;
        private final int scaledPlayers;
        private final double baseDamage;
        private final double baseSpeed;
        private final long spawnedAt = System.currentTimeMillis();
        private long invulnerableUntil;
        private boolean enraged;
        private String phaseName = "";

        private BossRuntime(String instance, String spawnKey, Boss definition, UUID entityId,
                            double x, double y, double z, int scaledPlayers,
                            double baseDamage, double baseSpeed) {
            this.instance = instance;
            this.spawnKey = spawnKey;
            this.definition = definition;
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scaledPlayers = scaledPlayers;
            this.baseDamage = baseDamage;
            this.baseSpeed = baseSpeed;
        }
    }

    private static final class WorldBossSpawn {
        private String eventId = "";
        private String bossId = "";
        private int dimension;
        private double x;
        private double y;
        private double z;
        private double activationRadius;
    }

    private static final class DungeonRuntime {
        private final String key;
        private final Dungeon definition;
        private final String sessionId;
        private final Set<UUID> live = new HashSet<UUID>();
        private int wave;
        private long nextWaveAt;

        private DungeonRuntime(String key, Dungeon definition, String sessionId, int wave) {
            this.key = key;
            this.definition = definition;
            this.sessionId = sessionId;
            this.wave = wave;
        }
    }

    private static final class CastContext {
        private final EntityMissionNpc npc;
        private final double damage;

        private CastContext(EntityMissionNpc npc, double damage) {
            this.npc = npc;
            this.damage = damage;
        }
    }

    private static final class MailTransfer {
        private final int slot;
        private final int count;

        private MailTransfer(int slot, int count) {
            this.slot = slot;
            this.count = count;
        }
    }
}
