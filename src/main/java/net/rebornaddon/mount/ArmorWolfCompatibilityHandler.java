package net.rebornaddon.mount;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps explicit Armor Wolf summons separate from natural-mob spawn restrictions. */
public final class ArmorWolfCompatibilityHandler {
    public static final ArmorWolfCompatibilityHandler INSTANCE = new ArmorWolfCompatibilityHandler();
    private static final String ENTITY_ID = "armourwolfmod:skin_wolf";
    private static final String MARKER = "RebornAddonManagedCompanion";
    private static final long RETRY_DELAY_MS = 150L;
    private static final long VERIFY_WINDOW_MS = 2500L;
    private final Map<UUID, PendingSpawn> pending = new HashMap<UUID, PendingSpawn>();
    private final ThreadLocal<Deque<BukkitListenerProbe>> bukkitListenerProbes =
            new ThreadLocal<Deque<BukkitListenerProbe>>() {
                @Override
                protected Deque<BukkitListenerProbe> initialValue() {
                    return new ArrayDeque<BukkitListenerProbe>();
                }
            };
    private final ThreadLocal<Deque<ForgeListenerProbe>> forgeListenerProbes =
            new ThreadLocal<Deque<ForgeListenerProbe>>() {
                @Override
                protected Deque<ForgeListenerProbe> initialValue() {
                    return new ArrayDeque<ForgeListenerProbe>();
                }
            };

    private ArmorWolfCompatibilityHandler() {
    }

    public static boolean isArmorWolfCompanion(Entity entity) {
        if (entity == null) return false;
        ResourceLocation id = net.minecraft.entity.EntityList.getKey(entity);
        return id != null && ENTITY_ID.equals(id.toString());
    }

    /** Called around each Bukkit listener only while an Armor Wolf spawn is pending. */
    public static void beginBukkitSpawnListener(Object registeredListener, Object event) {
        if (INSTANCE.pending.isEmpty() || registeredListener == null || event == null) return;
        UUID entityId = bukkitSpawnEventEntityId(event);
        PendingSpawn spawn = entityId == null ? null : INSTANCE.pending.get(entityId);
        if (spawn == null) return;
        Boolean canceled = bukkitCanceled(event);
        if (canceled == null) return;
        INSTANCE.bukkitListenerProbes.get().push(new BukkitListenerProbe(
                registeredListener, event, spawn, canceled.booleanValue()));
    }

    /** Records the exact Bukkit plugin/listener that changed cancellation state. */
    public static void finishBukkitSpawnListener(Object registeredListener, Object event) {
        Deque<BukkitListenerProbe> probes = INSTANCE.bukkitListenerProbes.get();
        if (probes.isEmpty()) return;
        BukkitListenerProbe probe = probes.peek();
        if (probe.registeredListener != registeredListener || probe.event != event) return;
        probes.pop();
        Boolean canceled = bukkitCanceled(event);
        if (canceled == null || canceled.booleanValue() == probe.canceledBefore) return;
        String source = bukkitListenerSource(registeredListener, event);
        probe.spawn.bukkitCancellationChanges.add((probe.canceledBefore ? "canceled" : "allowed")
                + "->" + (canceled.booleanValue() ? "canceled" : "allowed")
                + " by " + source);
        if (!probe.canceledBefore && canceled.booleanValue()) {
            probe.spawn.bukkitCancellationSource = source;
        }
    }

    /** Called around Forge subscribers while a companion join event is being dispatched. */
    public static void beginForgeJoinListener(Object asmHandler, Object event) {
        if (INSTANCE.pending.isEmpty() || asmHandler == null
                || !(event instanceof EntityJoinWorldEvent)) return;
        Entity entity = ((EntityJoinWorldEvent) event).getEntity();
        PendingSpawn spawn = entity == null ? null : INSTANCE.pending.get(entity.getUniqueID());
        if (spawn == null) return;
        INSTANCE.forgeListenerProbes.get().push(new ForgeListenerProbe(
                asmHandler, event, spawn, ((EntityJoinWorldEvent) event).isCanceled()));
    }

    /** Records the exact Forge subscriber that changed the join event's cancellation state. */
    public static void finishForgeJoinListener(Object asmHandler, Object event) {
        Deque<ForgeListenerProbe> probes = INSTANCE.forgeListenerProbes.get();
        if (probes.isEmpty()) return;
        ForgeListenerProbe probe = probes.peek();
        if (probe.asmHandler != asmHandler || probe.event != event) return;
        probes.pop();
        boolean canceled = ((EntityJoinWorldEvent) event).isCanceled();
        if (canceled == probe.canceledBefore) return;
        String source = forgeListenerSource(asmHandler);
        probe.spawn.forgeCancellationChanges.add((probe.canceledBefore ? "canceled" : "allowed")
                + "->" + (canceled ? "canceled" : "allowed") + " by " + source);
        if (!probe.canceledBefore && canceled) probe.spawn.forgeCancellationSource = source;
    }

    /** Called from the transformed wrapper constructor before World.spawnEntity evaluates it. */
    public static void prepareExplicitSpawn(Entity entity) {
        if (entity == null) return;
        entity.forceSpawn = true;
        entity.getEntityData().setBoolean(MARKER, true);
        if (entity instanceof EntityLiving) ((EntityLiving) entity).enablePersistence();
        if (entity.world != null && !entity.world.isRemote) {
            INSTANCE.track(entity);
        }
    }

    /** Replaces Armor Wolf's unchecked World.spawnEntity calls and records the real result. */
    public static boolean spawnExplicit(World world, Entity entity) {
        if (world == null || entity == null) return false;
        prepareExplicitSpawn(entity);
        PendingSpawn spawn = INSTANCE.track(entity);
        Object[] customSpawn = mohistCustomSpawn(world);
        spawn.spawnPath = customSpawn == null ? "World.spawnEntity(DEFAULT)"
                : "WorldServer.addEntity(CUSTOM)";
        INSTANCE.captureAttempt(world, spawn, "before");
        try {
            boolean accepted;
            if (customSpawn == null) {
                accepted = world.spawnEntity(entity);
            } else {
                Object result = ((Method) customSpawn[0]).invoke(world, entity, customSpawn[1]);
                if (!(result instanceof Boolean)) {
                    throw new IllegalStateException("Mohist custom spawn returned a non-boolean result");
                }
                accepted = ((Boolean) result).booleanValue();
            }
            spawn.spawnReturned = Boolean.valueOf(accepted);
            if (accepted) spawn.acceptedOnce = true;
            INSTANCE.captureAttempt(world, spawn, "after:return=" + accepted);
            return accepted;
        } catch (Throwable failure) {
            if (failure instanceof InvocationTargetException
                    && ((InvocationTargetException) failure).getCause() != null) {
                failure = ((InvocationTargetException) failure).getCause();
            }
            spawn.lastFailure = "the world spawn call threw "
                    + failure.getClass().getSimpleName();
            spawn.exceptionType = failure.getClass().getName();
            spawn.exceptionMessage = failure.getMessage() == null ? "" : failure.getMessage();
            INSTANCE.captureAttempt(world, spawn, "after:exception");
            return false;
        }
    }

    /** Replaces Armor Wolf's eager self-removal when its owner lookup is briefly unavailable. */
    public static void handleUnresolvedOwner(Entity entity) {
        if (!(entity instanceof EntityTameable) || entity.world == null
                || entity.world.isRemote || !isArmorWolfCompanion(entity)) {
            if (entity != null) entity.setDead();
            return;
        }
        PendingSpawn spawn = INSTANCE.track(entity);
        UUID ownerId = ownerId(entity);
        if (ownerId != null) spawn.owner = ownerId;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayerMP owner = server == null || ownerId == null ? null
                : server.getPlayerList().getPlayerByUUID(ownerId);
        if (owner != null) {
            ((EntityTameable) entity).setOwnerId(owner.getUniqueID());
            spawn.ownerLookupRecovered = true;
            return;
        }
        spawn.lastFailure = ownerId == null
                ? "Armor Wolf created the wrapper without an owner UUID"
                : "the companion owner is no longer online";
        entity.setDead();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void observeJoinHighest(EntityJoinWorldEvent event) {
        checkpoint(event, "HIGHEST");
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public void observeJoinHigh(EntityJoinWorldEvent event) {
        checkpoint(event, "HIGH");
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void observeJoinNormal(EntityJoinWorldEvent event) {
        checkpoint(event, "NORMAL");
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public void observeJoinLow(EntityJoinWorldEvent event) {
        checkpoint(event, "LOW");
    }

    /** Captures who actually called Armor Wolf's removal method; invoked only on removal. */
    public static void recordRemoval(Entity entity) {
        if (entity == null || entity.world == null || entity.world.isRemote) return;
        PendingSpawn spawn = INSTANCE.track(entity);
        StackTraceElement source = removalSource(Thread.currentThread().getStackTrace());
        if (source == null) {
            spawn.removalSource = "unknown caller";
            return;
        }
        spawn.removalSource = source.getClassName() + "#" + source.getMethodName();
        if (spawn.lastFailure != null && !spawn.lastFailure.isEmpty()) return;
        if (source.getClassName().endsWith("CompanionDamageHandler")) {
            spawn.lastFailure = "Armor Wolf removed it after its owner took damage";
        } else if (source.getClassName().endsWith("PlayerCapabilityHandler")) {
            spawn.lastFailure = "Armor Wolf removed it during a player capability login/logout cleanup";
        } else if (source.getClassName().endsWith("MountAdministrationService")) {
            spawn.lastFailure = "the mount administration service revoked the active summon";
        } else if (source.getClassName().endsWith("EntitySkinWolf")
                && ("onLivingUpdate".equals(source.getMethodName())
                || "func_70636_d".equals(source.getMethodName()))) {
            spawn.lastFailure = "Armor Wolf removed it because its owner lookup returned null";
        } else {
            spawn.lastFailure = "it was removed by " + simpleSource(source);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (!isArmorWolfCompanion(entity)) return;

        boolean wasCanceled = event.isCanceled();
        prepareExplicitSpawn(entity);

        // This wrapper is only created by an explicit companion/mount summon. Natural spawn
        // controls and broad entity policies must not reject it.
        if (event.isCanceled()) event.setCanceled(false);
        if (event.getWorld().isRemote) return;

        PendingSpawn spawn = track(entity);
        spawn.checkpoints.add("LOWEST:canceled=" + wasCanceled);
        spawn.joinSeen = true;
        if (wasCanceled) {
            spawn.canceledJoinRecovered = true;
            if (spawn.firstCanceledCheckpoint.isEmpty()) {
                spawn.firstCanceledCheckpoint = "LOWEST";
            }
            spawn.cancellationCandidates = cancellationCandidates(event);
        }
        UUID owner = ownerId(entity);
        if (owner != null) spawn.owner = owner;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            pending.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingSpawn>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingSpawn> entry = iterator.next();
            PendingSpawn spawn = entry.getValue();
            if (spawn.owner == null) spawn.owner = ownerId(spawn.entity);
            Entity present = find(server, entry.getKey());
            if (present != null && !present.isDead) {
                spawn.acceptedOnce = true;
                if (!spawn.formReconciled) {
                    EntityPlayerMP owner = spawn.owner == null ? null
                            : server.getPlayerList().getPlayerByUUID(spawn.owner);
                    if (owner != null) {
                        spawn.formReconciled = MountAdministrationService.INSTANCE
                                .reconcileSpawnedForm(owner, present);
                    }
                }
                if (now - spawn.startedAt >= VERIFY_WINDOW_MS) {
                    captureAttempt(present.world, spawn, "verify:present");
                    iterator.remove();
                }
                continue;
            }

            if (spawn.attempts == 0 && now - spawn.startedAt >= RETRY_DELAY_MS
                    && !spawn.entity.isDead && spawn.entity.world instanceof WorldServer) {
                spawn.attempts++;
                spawnExplicit(spawn.entity.world, spawn.entity);
                continue;
            }

            if (now - spawn.startedAt < VERIFY_WINDOW_MS) continue;
            EntityPlayerMP owner = spawn.owner == null ? null
                    : server.getPlayerList().getPlayerByUUID(spawn.owner);
            if (owner != null) MountAdministrationService.INSTANCE
                    .clearMissingSummon(owner, entry.getKey());
            iterator.remove();
        }
    }

    private void checkpoint(EntityJoinWorldEvent event, String priority) {
        Entity entity = event.getEntity();
        if (!isArmorWolfCompanion(entity) || event.getWorld().isRemote) return;
        PendingSpawn spawn = track(entity);
        boolean canceled = event.isCanceled();
        spawn.checkpoints.add(priority + ":canceled=" + canceled);
        if (canceled && spawn.firstCanceledCheckpoint.isEmpty()) {
            spawn.firstCanceledCheckpoint = priority;
        }
    }

    private PendingSpawn track(Entity entity) {
        PendingSpawn spawn = pending.get(entity.getUniqueID());
        if (spawn == null) {
            spawn = new PendingSpawn(entity, ownerId(entity));
            pending.put(entity.getUniqueID(), spawn);
        } else if (ownerId(entity) != null) {
            spawn.owner = ownerId(entity);
        }
        return spawn;
    }

    private static String failureReason(PendingSpawn spawn, MinecraftServer server) {
        if (spawn == null) return "no spawn attempt was recorded";
        if (spawn.lastFailure != null && !spawn.lastFailure.isEmpty()) return spawn.lastFailure;
        Entity indexed = find(server, spawn.entity.getUniqueID());
        Entity identity = findIdentity(server, spawn.entity);
        if (indexed == spawn.entity && !spawn.entity.isDead) {
            return "the spawn was accepted and the same entity remains in the server UUID index";
        }
        if (indexed != null && indexed != spawn.entity) {
            return "its UUID was already indexed to a different entity";
        }
        if (identity != null && indexed == null) {
            return "the entity was loaded but missing from the server UUID index";
        }
        if (spawn.entity.isDead && spawn.acceptedOnce) {
            return "the entity entered the world and was removed afterward";
        }
        if (spawn.entity.isDead) return "the wrapper was marked dead before insertion";
        if (Boolean.FALSE.equals(spawn.spawnReturned)) {
            if (!spawn.bukkitCancellationSource.isEmpty()) {
                return "the Mohist Bukkit spawn event was canceled by "
                        + spawn.bukkitCancellationSource;
            }
            if (!spawn.forgeCancellationSource.isEmpty()) {
                return "the Forge join event was canceled by " + spawn.forgeCancellationSource;
            }
            if (!spawn.joinSeen) {
                return "World.spawnEntity returned false in the server's pre-join spawn path";
            }
            if (spawn.canceledJoinRecovered) {
                return "World.spawnEntity returned false after a Forge listener canceled the join event and RebornAddon cleared it";
            }
            return "World.spawnEntity returned false after an uncanceled Forge join event in the downstream server/Mohist spawn path";
        }
        if (spawn.canceledJoinRecovered) {
            return "another event handler canceled its join even after recovery";
        }
        if (!spawn.joinSeen) return "the entity never reached the world join event";
        return "the server stopped tracking the entity after its join event";
    }

    private void captureAttempt(World world, PendingSpawn spawn, String phase) {
        if (world == null || spawn == null) return;
        boolean before = "before".equals(phase);
        if (before) spawn.spawnCalls++;
        Entity entity = spawn.entity;
        BlockPos position = new BlockPos(entity.posX, entity.posY, entity.posZ);
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        Entity indexed = server == null ? null : find(server, entity.getUniqueID());
        Entity identity = server == null ? null : findIdentity(server, entity);
        UUID currentOwner = ownerId(entity);
        EntityPlayerMP onlineOwner = server == null || currentOwner == null ? null
                : server.getPlayerList().getPlayerByUUID(currentOwner);
        if (currentOwner != null) spawn.owner = currentOwner;

        boolean chunkLoaded = world.isBlockLoaded(position, false);
        boolean insideWorldBorder = world.getWorldBorder().contains(position);
        if (before) {
            spawn.worldClass = world.getClass().getName();
            spawn.dimension = world.provider.getDimension();
            spawn.position = position.toString();
            spawn.chunkLoadedBefore = chunkLoaded;
            spawn.insideWorldBorderBefore = insideWorldBorder;
            spawn.doMobSpawning = world.getGameRules().getBoolean("doMobSpawning");
            spawn.maxEntityCramming = world.getGameRules().getInt("maxEntityCramming");
            spawn.spawnRadius = world.getGameRules().getInt("spawnRadius");
            spawn.difficulty = world.getDifficulty().name();
            spawn.registryId = registryId(entity);
            spawn.policyDenied = ContentPolicyService.INSTANCE.denies(PolicyAction.SPAWN, ENTITY_ID);
            spawn.spawnPeacefulMobs = reflectedBoolean(world, "spawnPeacefulMobs");
            spawn.spawnHostileMobs = reflectedBoolean(world, "spawnHostileMobs");
        } else {
            spawn.chunkLoadedAfter = chunkLoaded;
            spawn.insideWorldBorderAfter = insideWorldBorder;
        }
        spawn.ownerOnline = onlineOwner != null;
        spawn.ownerDimension = onlineOwner == null ? Integer.MIN_VALUE
                : onlineOwner.world.provider.getDimension();
        spawn.ownerResolvedByEntity = entity instanceof EntityTameable
                && ((EntityTameable) entity).getOwner() != null;
        spawn.duplicateUuid = indexed != null && indexed != entity;
        spawn.identityLoaded = identity != null;
        spawn.uuidIndexed = indexed == entity;
        spawn.addedToWorld = addedToWorld(entity);
        TrackingEvidence tracking = trackingEvidence(world, entity, onlineOwner);
        spawn.serverTrackerIndexed = tracking.indexed;
        spawn.ownerTrackingEntity = tracking.ownerTracking;
        spawn.trackerInspection = tracking.detail;
        spawn.attemptEvidence.add("attempt=" + spawn.spawnCalls + ",phase=" + phase
                + ",force=" + entity.forceSpawn + ",dead=" + entity.isDead
                + ",chunkLoaded=" + chunkLoaded
                + ",insideWorldBorder=" + insideWorldBorder
                + ",joinSeen=" + spawn.joinSeen
                + ",uuidIndexed=" + spawn.uuidIndexed
                + ",identityLoaded=" + spawn.identityLoaded
                + ",serverTrackerIndexed=" + spawn.serverTrackerIndexed
                + ",ownerTracking=" + spawn.ownerTrackingEntity);
    }

    private static String diagnosticReport(PendingSpawn spawn, MinecraftServer server) {
        String reason = failureReason(spawn, server);
        Entity indexed = find(server, spawn.entity.getUniqueID());
        Entity identity = findIdentity(server, spawn.entity);
        EntityPlayerMP owner = spawn.owner == null ? null
                : server.getPlayerList().getPlayerByUUID(spawn.owner);
        String binding = owner == null ? "owner unavailable"
                : MountAdministrationService.INSTANCE.diagnoseSummonBinding(
                owner, spawn.entity.getUniqueID());
        StringBuilder report = new StringBuilder(1536);
        report.append("Armor Wolf summon diagnostic ").append(spawn.diagnosticId)
                .append('\n').append("reason=").append(reason)
                .append('\n').append("server.class=").append(server.getClass().getName())
                .append(", modName=").append(server.getServerModName())
                .append('\n').append("entity.registry=").append(spawn.registryId)
                .append(", uuid=").append(spawn.entity.getUniqueID())
                .append(", runtimeId=").append(spawn.entity.getEntityId())
                .append(", class=").append(spawn.entity.getClass().getName())
                .append('\n').append("entity.forceSpawn=").append(spawn.entity.forceSpawn)
                .append(", dead=").append(spawn.entity.isDead)
                .append(", addedToWorld=").append(spawn.addedToWorld)
                .append(", identityLoaded=").append(identity != null)
                .append(", uuidIndexedToSameEntity=").append(indexed == spawn.entity)
                .append(", duplicateUuid=").append(indexed != null && indexed != spawn.entity)
                .append(", serverTrackerIndexed=").append(spawn.serverTrackerIndexed)
                .append(", ownerTrackingEntity=").append(spawn.ownerTrackingEntity)
                .append(", trackerInspection=").append(spawn.trackerInspection)
                .append('\n').append("world.class=").append(spawn.worldClass)
                .append(", dimension=").append(spawn.dimension)
                .append(", position=").append(spawn.position)
                .append(", chunkLoadedBefore=").append(spawn.chunkLoadedBefore)
                .append(", chunkLoadedAfter=").append(spawn.chunkLoadedAfter)
                .append(", insideWorldBorderBefore=").append(spawn.insideWorldBorderBefore)
                .append(", insideWorldBorderAfter=").append(spawn.insideWorldBorderAfter)
                .append(", difficulty=").append(spawn.difficulty)
                .append('\n').append("gamerules.doMobSpawning=").append(spawn.doMobSpawning)
                .append(" (informational: direct World.spawnEntity does not consult this gamerule)")
                .append(", maxEntityCramming=").append(spawn.maxEntityCramming)
                .append(", spawnRadius=").append(spawn.spawnRadius)
                .append('\n').append("server.spawnPeacefulMobs=").append(spawn.spawnPeacefulMobs)
                .append(", server.spawnHostileMobs=").append(spawn.spawnHostileMobs)
                .append('\n').append("rebornContentSpawnPolicyDenied=").append(spawn.policyDenied)
                .append(" (Armor Wolf wrappers are explicitly exempted)")
                .append('\n').append("spawn.path=").append(spawn.spawnPath)
                .append(", spawn.calls=").append(spawn.spawnCalls)
                .append(", lastReturn=").append(spawn.spawnReturned)
                .append(", acceptedOnce=").append(spawn.acceptedOnce)
                .append(", joinSeen=").append(spawn.joinSeen)
                .append(", joinCanceledBeforeRecovery=").append(spawn.canceledJoinRecovered)
                .append(", firstCanceledCheckpoint=").append(empty(spawn.firstCanceledCheckpoint))
                .append('\n').append("join.checkpoints=").append(spawn.checkpoints)
                .append('\n').append("join.cancellationCandidates=")
                .append(spawn.cancellationCandidates)
                .append('\n').append("forge.exactCancellationSource=")
                .append(empty(spawn.forgeCancellationSource))
                .append('\n').append("forge.cancellationChanges=")
                .append(spawn.forgeCancellationChanges)
                .append('\n').append("mohist.bukkitSpawnListenerCandidates=")
                .append(bukkitSpawnListeners())
                .append('\n').append("bukkit.exactCancellationSource=")
                .append(empty(spawn.bukkitCancellationSource))
                .append('\n').append("bukkit.cancellationChanges=")
                .append(spawn.bukkitCancellationChanges)
                .append('\n').append("owner.uuid=").append(spawn.owner)
                .append(", online=").append(spawn.ownerOnline)
                .append(", entityOwnerResolved=").append(spawn.ownerResolvedByEntity)
                .append(", ownerDimension=").append(spawn.ownerDimension == Integer.MIN_VALUE
                ? "unknown" : Integer.toString(spawn.ownerDimension))
                .append(", ownerLookupRecovered=").append(spawn.ownerLookupRecovered)
                .append('\n').append("capability.binding=").append(binding)
                .append('\n').append("removal.source=").append(empty(spawn.removalSource))
                .append('\n').append("spawn.exception=").append(empty(spawn.exceptionType));
        if (spawn.exceptionMessage != null && !spawn.exceptionMessage.isEmpty()) {
            report.append(": ").append(spawn.exceptionMessage);
        }
        report.append('\n').append("attempt.evidence=").append(spawn.attemptEvidence);
        return report.toString();
    }

    private static List<String> bukkitSpawnListeners() {
        Set<String> listeners = new LinkedHashSet<String>();
        String[] eventTypes = new String[] {
                "org.bukkit.event.entity.CreatureSpawnEvent",
                "org.bukkit.event.entity.EntitySpawnEvent"
        };
        boolean bukkitAvailable = false;
        for (String eventType : eventTypes) {
            try {
                Class<?> type = Class.forName(eventType, false,
                        ArmorWolfCompatibilityHandler.class.getClassLoader());
                bukkitAvailable = true;
                Object handlerList = type.getMethod("getHandlerList").invoke(null);
                Object registered = handlerList.getClass()
                        .getMethod("getRegisteredListeners").invoke(handlerList);
                int count = Array.getLength(registered);
                for (int i = 0; i < count && listeners.size() < 32; i++) {
                    Object listener = Array.get(registered, i);
                    Object plugin = listener.getClass().getMethod("getPlugin").invoke(listener);
                    String pluginName = String.valueOf(plugin.getClass().getMethod("getName")
                            .invoke(plugin));
                    String priority = String.valueOf(listener.getClass().getMethod("getPriority")
                            .invoke(listener));
                    boolean ignoresCanceled = ((Boolean) listener.getClass()
                            .getMethod("isIgnoringCancelled").invoke(listener)).booleanValue();
                    listeners.add(type.getSimpleName() + ":" + priority + ":"
                            + pluginName + ":ignoreCanceled=" + ignoresCanceled);
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable failure) {
                listeners.add(eventType + ":inspection failed:"
                        + failure.getClass().getSimpleName());
            }
        }
        if (!bukkitAvailable) listeners.add("Bukkit API unavailable on this runtime");
        return Collections.unmodifiableList(new ArrayList<String>(listeners));
    }

    private static String forgeListenerSource(Object asmHandler) {
        String readable = String.valueOf(asmHandler).replace('\n', ' ').replace('\r', ' ');
        String owner = "unknown";
        try {
            Field field = asmHandler.getClass().getDeclaredField("owner");
            field.setAccessible(true);
            Object container = field.get(asmHandler);
            if (container != null) {
                Method getModId = container.getClass().getMethod("getModId");
                owner = String.valueOf(getModId.invoke(container));
            }
        } catch (Throwable ignored) {
        }
        String priority = "unknown";
        try {
            priority = String.valueOf(asmHandler.getClass().getMethod("getPriority")
                    .invoke(asmHandler));
        } catch (Throwable ignored) {
        }
        return "mod=" + owner + ", priority=" + priority + ", handler=" + readable;
    }

    private static UUID bukkitSpawnEventEntityId(Object event) {
        String type = event.getClass().getName();
        if (!type.endsWith("CreatureSpawnEvent") && !type.endsWith("EntitySpawnEvent")) {
            return null;
        }
        try {
            Object entity = event.getClass().getMethod("getEntity").invoke(event);
            Object uuid = entity.getClass().getMethod("getUniqueId").invoke(entity);
            return uuid instanceof UUID ? (UUID) uuid : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean bukkitCanceled(Object event) {
        try {
            Object value = event.getClass().getMethod("isCancelled").invoke(event);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String bukkitListenerSource(Object registeredListener, Object event) {
        try {
            Object plugin = registeredListener.getClass().getMethod("getPlugin")
                    .invoke(registeredListener);
            Object listener = registeredListener.getClass().getMethod("getListener")
                    .invoke(registeredListener);
            Object priority = registeredListener.getClass().getMethod("getPriority")
                    .invoke(registeredListener);
            String pluginName = String.valueOf(plugin.getClass().getMethod("getName")
                    .invoke(plugin));
            return "plugin=" + pluginName + ", listener=" + listener.getClass().getName()
                    + ", priority=" + priority + ", event=" + event.getClass().getName();
        } catch (Throwable failure) {
            return "listener=" + registeredListener.getClass().getName()
                    + ", event=" + event.getClass().getName()
                    + ", metadataError=" + failure.getClass().getSimpleName();
        }
    }

    private static StackTraceElement removalSource(StackTraceElement[] trace) {
        if (trace == null) return null;
        for (StackTraceElement element : trace) {
            String type = element.getClassName();
            if (Thread.class.getName().equals(type)
                    || ArmorWolfCompatibilityHandler.class.getName().equals(type)) continue;
            if (type.endsWith("EntitySkinWolf")
                    && ("setDead".equals(element.getMethodName())
                    || "func_70106_y".equals(element.getMethodName()))) continue;
            return element;
        }
        return null;
    }

    private static String simpleSource(StackTraceElement source) {
        String type = source.getClassName();
        int dot = type.lastIndexOf('.');
        if (dot >= 0) type = type.substring(dot + 1);
        return type + "." + source.getMethodName();
    }

    private static List<String> cancellationCandidates(EntityJoinWorldEvent event) {
        if (event == null) return Collections.emptyList();
        Set<String> candidates = new LinkedHashSet<String>();
        try {
            Field busIdField = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("busID");
            busIdField.setAccessible(true);
            int busId = busIdField.getInt(MinecraftForge.EVENT_BUS);
            IEventListener[] listeners = event.getListenerList().getListeners(busId);
            for (IEventListener listener : listeners) {
                String readable = String.valueOf(listener).replace('\n', ' ').replace('\r', ' ');
                if (readable.contains(ArmorWolfCompatibilityHandler.class.getName())
                        && readable.contains("onEntityJoin")) break;
                if (readable.contains(ArmorWolfCompatibilityHandler.class.getName())) continue;
                String priority = "unknown";
                try {
                    Method method = listener.getClass().getMethod("getPriority");
                    priority = String.valueOf(method.invoke(listener));
                } catch (Throwable ignored) {
                }
                if (readable.length() > 220) readable = readable.substring(0, 220);
                candidates.add(priority + ":" + readable);
                if (candidates.size() >= 32) break;
            }
        } catch (Throwable failure) {
            candidates.add("listener inspection unavailable: "
                    + failure.getClass().getSimpleName());
        }
        return Collections.unmodifiableList(new ArrayList<String>(candidates));
    }

    private static Entity find(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (WorldServer world : server.worlds) {
            Entity entity = world.getEntityFromUuid(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static Entity findIdentity(MinecraftServer server, Entity wanted) {
        if (server == null || wanted == null) return null;
        for (WorldServer world : server.worlds) {
            for (Entity entity : world.loadedEntityList) {
                if (entity == wanted) return entity;
            }
        }
        return null;
    }

    private static String registryId(Entity entity) {
        ResourceLocation id = entity == null ? null : EntityList.getKey(entity);
        return id == null ? "unregistered" : id.toString();
    }

    private static boolean addedToWorld(Entity entity) {
        if (entity == null) return false;
        try {
            Method method = entity.getClass().getMethod("isAddedToWorld");
            Object value = method.invoke(entity);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object[] mohistCustomSpawn(World world) {
        if (!(world instanceof WorldServer)) return null;
        try {
            Class<?> reasonType = Class.forName(
                    "org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason", false,
                    ArmorWolfCompatibilityHandler.class.getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object custom = Enum.valueOf((Class<? extends Enum>) reasonType.asSubclass(Enum.class),
                    "CUSTOM");
            Method method = world.getClass().getMethod("addEntity", Entity.class, reasonType);
            return new Object[] {method, custom};
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable failure) {
            return null;
        }
    }

    private static String reflectedBoolean(Object object, String fieldName) {
        if (object == null) return "unknown";
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Boolean.toString(field.getBoolean(object));
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable failure) {
                return "inspection failed:" + failure.getClass().getSimpleName();
            }
        }
        return "unknown";
    }

    private static TrackingEvidence trackingEvidence(World world, Entity entity,
                                                     EntityPlayerMP owner) {
        if (!(world instanceof WorldServer) || entity == null) {
            return new TrackingEvidence("not a server world", "unknown", "unknown");
        }
        try {
            Object tracker = ((WorldServer) world).getEntityTracker();
            Object entry = null;
            Class<?> type = tracker.getClass();
            while (type != null && entry == null) {
                for (Field field : type.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(tracker);
                    if (value == null) continue;
                    if (value instanceof Map) {
                        entry = ((Map<?, ?>) value).get(Integer.valueOf(entity.getEntityId()));
                    } else if (value.getClass().getName().endsWith("IntHashMap")) {
                        Method lookup = null;
                        for (Method candidate : value.getClass().getMethods()) {
                            if (candidate.getParameterTypes().length == 1
                                    && candidate.getParameterTypes()[0] == Integer.TYPE
                                    && ("lookup".equals(candidate.getName())
                                    || "func_76041_a".equals(candidate.getName()))) {
                                lookup = candidate;
                                break;
                            }
                        }
                        if (lookup != null) entry = lookup.invoke(value, entity.getEntityId());
                    }
                    if (entry != null) break;
                }
                type = type.getSuperclass();
            }
            if (entry == null) {
                return new TrackingEvidence("entity tracker has no entry for runtime ID "
                        + entity.getEntityId(), "false", "false");
            }
            if (owner == null) {
                return new TrackingEvidence("tracker entry=" + entry.getClass().getName()
                        + "; owner offline", "true", "unknown");
            }
            Boolean ownerTracked = collectionContains(entry, owner);
            return new TrackingEvidence("tracker entry=" + entry.getClass().getName(),
                    "true", ownerTracked == null ? "unknown" : ownerTracked.toString());
        } catch (Throwable failure) {
            return new TrackingEvidence("inspection failed: "
                    + failure.getClass().getSimpleName(), "unknown", "unknown");
        }
    }

    private static Boolean collectionContains(Object object, Object wanted) throws IllegalAccessException {
        Class<?> type = object.getClass();
        boolean collectionSeen = false;
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(object);
                if (!(value instanceof Iterable)) continue;
                collectionSeen = true;
                for (Object member : (Iterable<?>) value) {
                    if (member == wanted || wanted.equals(member)) return Boolean.TRUE;
                }
            }
            type = type.getSuperclass();
        }
        return collectionSeen ? Boolean.FALSE : null;
    }

    private static String empty(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static UUID ownerId(Entity entity) {
        return entity instanceof EntityTameable ? ((EntityTameable) entity).getOwnerId() : null;
    }

    private static final class PendingSpawn {
        private final Entity entity;
        private UUID owner;
        private final long startedAt = System.currentTimeMillis();
        private int attempts;
        private Boolean spawnReturned;
        private boolean joinSeen;
        private boolean canceledJoinRecovered;
        private boolean acceptedOnce;
        private boolean ownerLookupRecovered;
        private boolean formReconciled;
        private String removalSource;
        private String lastFailure;
        private final String diagnosticId;
        private int spawnCalls;
        private String exceptionType;
        private String exceptionMessage;
        private String worldClass = "unknown";
        private int dimension;
        private String position = "unknown";
        private boolean chunkLoadedBefore;
        private boolean chunkLoadedAfter;
        private boolean insideWorldBorderBefore;
        private boolean insideWorldBorderAfter;
        private boolean doMobSpawning;
        private String spawnPeacefulMobs = "unknown";
        private String spawnHostileMobs = "unknown";
        private int maxEntityCramming;
        private int spawnRadius;
        private String difficulty = "unknown";
        private String registryId = "unregistered";
        private boolean policyDenied;
        private boolean ownerOnline;
        private int ownerDimension = Integer.MIN_VALUE;
        private boolean ownerResolvedByEntity;
        private boolean duplicateUuid;
        private boolean identityLoaded;
        private boolean uuidIndexed;
        private boolean addedToWorld;
        private String serverTrackerIndexed = "unknown";
        private String ownerTrackingEntity = "unknown";
        private String trackerInspection = "not inspected";
        private String spawnPath = "not selected";
        private String firstCanceledCheckpoint = "";
        private List<String> cancellationCandidates = Collections.emptyList();
        private final List<String> checkpoints = new ArrayList<String>();
        private final List<String> attemptEvidence = new ArrayList<String>();
        private String forgeCancellationSource = "";
        private final List<String> forgeCancellationChanges = new ArrayList<String>();
        private String bukkitCancellationSource = "";
        private final List<String> bukkitCancellationChanges = new ArrayList<String>();

        private PendingSpawn(Entity entity, UUID owner) {
            this.entity = entity;
            this.owner = owner;
            String uuid = entity.getUniqueID().toString().replace("-", "");
            this.diagnosticId = "AW-" + uuid.substring(0, 8) + "-"
                    + Long.toHexString(startedAt).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static final class TrackingEvidence {
        private final String detail;
        private final String indexed;
        private final String ownerTracking;

        private TrackingEvidence(String detail, String indexed, String ownerTracking) {
            this.detail = detail;
            this.indexed = indexed;
            this.ownerTracking = ownerTracking;
        }
    }

    private static final class BukkitListenerProbe {
        private final Object registeredListener;
        private final Object event;
        private final PendingSpawn spawn;
        private final boolean canceledBefore;

        private BukkitListenerProbe(Object registeredListener, Object event,
                                    PendingSpawn spawn, boolean canceledBefore) {
            this.registeredListener = registeredListener;
            this.event = event;
            this.spawn = spawn;
            this.canceledBefore = canceledBefore;
        }
    }

    private static final class ForgeListenerProbe {
        private final Object asmHandler;
        private final Object event;
        private final PendingSpawn spawn;
        private final boolean canceledBefore;

        private ForgeListenerProbe(Object asmHandler, Object event,
                                   PendingSpawn spawn, boolean canceledBefore) {
            this.asmHandler = asmHandler;
            this.event = event;
            this.spawn = spawn;
            this.canceledBefore = canceledBefore;
        }
    }
}
