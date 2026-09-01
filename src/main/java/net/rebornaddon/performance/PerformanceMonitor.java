package net.rebornaddon.performance;

import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.server.timings.ForgeTimings;
import net.minecraftforge.server.timings.TimeTracker;
import net.rebornaddon.content.NativeContentService;
import net.rebornaddon.exam.ExamService;
import net.rebornaddon.trade.TradeService;
import net.rebornaddon.state.PlayerStateLifecycleService;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceMonitor {
    public static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    public static final long DEFAULT_PROFILE_MILLIS = 5000L;
    public static final long MIN_PROFILE_MILLIS = 1000L;
    public static final long MAX_PROFILE_MILLIS = 60L * 60L * 1000L;
    private static final int HISTORY = 1200;
    private static final long TIMING_SEGMENT_MILLIS = 3500L;
    private static final int MAX_STACK_SAMPLES = 5000;
    private static final int MAX_LOCATION_ROWS = 250;
    private static final int MAX_CHUNK_ROWS = 100;
    private static final long PROFILE_FRESH_MILLIS = 10L * 60L * 1000L;

    private final long[] tickDurations = new long[HISTORY];
    private final long[] tickEnds = new long[HISTORY];
    private final AtomicLong outboundBytes = new AtomicLong();
    private final AtomicLong inboundBytes = new AtomicLong();
    private final AtomicLong outboundPackets = new AtomicLong();
    private final AtomicLong inboundPackets = new AtomicLong();
    private final AtomicBoolean profiling = new AtomicBoolean();
    private final AtomicLong profileGeneration = new AtomicLong();
    private final long[] heapHistory = new long[60];
    private volatile ProfileResult latestProfile = ProfileResult.empty();
    private volatile Thread serverThread;
    private volatile MinecraftServer server;
    private volatile long profileStartedAt;
    private volatile long profileDurationMillis;
    private long tickStarted;
    private int cursor;
    private int count;
    private int heapCursor;
    private int heapCount;
    private long lifetimeTicks;
    private long startedAt = System.currentTimeMillis();

    private PerformanceMonitor() {
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    public void reset() {
        profileGeneration.incrementAndGet();
        profiling.set(false);
        stopForgeTimings();
        server = null;
        serverThread = null;
        cursor = 0;
        count = 0;
        tickStarted = 0L;
        latestProfile = ProfileResult.empty();
        profileStartedAt = 0L;
        profileDurationMillis = 0L;
        outboundBytes.set(0L);
        inboundBytes.set(0L);
        outboundPackets.set(0L);
        inboundPackets.set(0L);
        heapCursor = 0;
        heapCount = 0;
        lifetimeTicks = 0L;
        startedAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            serverThread = Thread.currentThread();
            tickStarted = System.nanoTime();
            return;
        }
        if (tickStarted == 0L) return;
        tickDurations[cursor] = Math.max(0L, System.nanoTime() - tickStarted);
        tickEnds[cursor] = System.nanoTime();
        cursor = (cursor + 1) % HISTORY;
        if (count < HISTORY) count++;
        lifetimeTicks++;
        if (lifetimeTicks % 200L == 0L) recordHeap();
    }

    public static void recordOutbound(int bytes) {
        INSTANCE.outboundPackets.incrementAndGet();
        if (bytes > 0) INSTANCE.outboundBytes.addAndGet(bytes);
    }

    public static void recordInbound(int bytes) {
        INSTANCE.inboundPackets.incrementAndGet();
        if (bytes > 0) INSTANCE.inboundBytes.addAndGet(bytes);
    }

    public void sendSnapshot(EntityPlayerMP player) {
        if (canInspect(player)) RebornAddonNetwork.sendPerformanceSnapshot(player, snapshot());
    }

    public NBTTagCompound snapshot() {
        NBTTagCompound data = new NBTTagCompound();
        data.setDouble("Tps", tps(100));
        data.setDouble("TickAverage", averageTickMillis(100));
        data.setDouble("TickP95", percentileTickMillis(100, 0.95D));
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        data.setLong("HeapUsed", used);
        data.setLong("HeapCommitted", runtime.totalMemory());
        data.setLong("HeapMax", runtime.maxMemory());
        data.setLong("HeapTrend", heapTrendPerMinute());
        data.setInteger("HeapRising", risingHeapSamples());
        data.setBoolean("LeakWarning", heapCount >= 12 && risingHeapSamples() >= heapCount - 2
                && heapTrendPerMinute() > 8L * 1024L * 1024L);
        data.setLong("GcCount", gcCount());
        data.setLong("GcMillis", gcMillis());
        data.setLong("OutboundBytes", outboundBytes.get());
        data.setLong("InboundBytes", inboundBytes.get());
        data.setLong("OutboundPackets", outboundPackets.get());
        data.setLong("InboundPackets", inboundPackets.get());
        long uptimeMillis = Math.max(1000L, System.currentTimeMillis() - startedAt);
        data.setLong("OutboundBytesPerSecond", outboundBytes.get() * 1000L / uptimeMillis);
        data.setLong("InboundBytesPerSecond", inboundBytes.get() * 1000L / uptimeMillis);
        data.setInteger("Threads", ManagementFactory.getThreadMXBean().getThreadCount());
        long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        data.setInteger("DeadlockedThreads", deadlocked == null ? 0 : deadlocked.length);
        data.setInteger("LoadedClasses", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        data.setBoolean("Profiling", profiling.get());
        data.setLong("ProfileStartedAt", profileStartedAt);
        data.setLong("ProfileDuration", profileDurationMillis);
        data.setLong("ProfileAge", latestProfile.finishedAt == 0L
                ? Long.MAX_VALUE : System.currentTimeMillis() - latestProfile.finishedAt);
        data.setLong("ProfileMeasured", latestProfile.measuredMillis);
        appendWorldCounts(data);
        appendPlayerLatency(data);
        NativeContentService.INSTANCE.appendPerformanceData(data);
        ExamService.INSTANCE.appendPerformanceData(data);
        TradeService.INSTANCE.appendPerformanceData(data);
        data.setInteger("RebornManagedPlayerStates",
                PlayerStateLifecycleService.INSTANCE.activeCount(server));
        data.setTag("Global", profileList(latestProfile.global));
        data.setTag("RebornAddon", profileList(latestProfile.reborn));
        data.setTag("Hotspots", locationList(latestProfile.locations));
        data.setTag("HotChunks", chunkList(latestProfile.chunks));
        return data;
    }

    public void requestProfile(EntityPlayerMP requester) {
        requestProfile(requester, DEFAULT_PROFILE_MILLIS);
    }

    public void requestProfile(final EntityPlayerMP requester, long requestedMillis) {
        if (!canInspect(requester)) return;
        final long durationMillis = Math.max(MIN_PROFILE_MILLIS,
                Math.min(MAX_PROFILE_MILLIS, requestedMillis));
        if (!profiling.compareAndSet(false, true)) {
            requester.sendMessage(new TextComponentString(TextFormatting.YELLOW
                    + "A RebornAddon performance sample is already running."));
            return;
        }
        final long generation = profileGeneration.incrementAndGet();
        profileStartedAt = System.currentTimeMillis();
        profileDurationMillis = durationMillis;
        requester.sendMessage(new TextComponentString(TextFormatting.AQUA
                + "Sampling server work for " + formatDuration(durationMillis)
                + ". Entity and block timing stops automatically."));
        final Thread target = serverThread;
        final MinecraftServer currentServer = server;
        final UUID requesterId = requester.getUniqueID();
        Thread sampler = new Thread(new Runnable() {
            @Override
            public void run() {
                ProfileResult result = ProfileResult.empty();
                try {
                    result = sample(currentServer, target, durationMillis, generation);
                } finally {
                    stopForgeTimings(currentServer);
                    if (profileGeneration.get() == generation) {
                        profiling.set(false);
                        profileStartedAt = 0L;
                    }
                }
                if (profileGeneration.get() != generation) return;
                latestProfile = result;
                if (currentServer != null) {
                    currentServer.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            EntityPlayerMP online = currentServer.getPlayerList().getPlayerByUUID(requesterId);
                            if (online != null) {
                                sendReport(online);
                                sendSnapshot(online);
                            }
                        }
                    });
                }
            }
        }, "RebornAddon performance sampler");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.MIN_PRIORITY);
        sampler.start();
    }

    public void teleportToHotspot(EntityPlayerMP player, int index, boolean chunk) {
        if (!canInspect(player)) return;
        List<? extends PositionedEntry> values = chunk ? latestProfile.chunks : latestProfile.locations;
        if (index < 0 || index >= values.size()) {
            player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "That performance result is no longer available."));
            return;
        }
        PositionedEntry entry = values.get(index);
        MinecraftServer current = player.getServer();
        WorldServer world = current == null ? null : current.getWorld(entry.dimension);
        if (world == null) {
            player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Dimension " + entry.dimension + " is not loaded."));
            return;
        }
        int x = chunk ? entry.x * 16 + 8 : entry.x;
        int z = chunk ? entry.z * 16 + 8 : entry.z;
        int y = chunk ? Math.max(2, world.getHeight(x, z) + 1) : Math.max(2, entry.y + 1);
        if (player.dimension != entry.dimension) player.changeDimension(entry.dimension);
        player.setPositionAndUpdate(x + 0.5D, y, z + 0.5D);
        player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Teleported to "
                + (chunk ? "chunk " : "hotspot ") + entry.label + "."));
    }

    public void sendReport(EntityPlayerMP player) {
        NBTTagCompound data = snapshot();
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "RebornAddon server performance"));
        player.sendMessage(new TextComponentString(TextFormatting.WHITE + String.format(Locale.ROOT,
                "TPS %.2f | tick avg %.2f ms | p95 %.2f ms | heap %s / %s",
                data.getDouble("Tps"), data.getDouble("TickAverage"), data.getDouble("TickP95"),
                formatBytes(data.getLong("HeapUsed")), formatBytes(data.getLong("HeapMax")))));
        sendTop(player, "Top overall sampled work", latestProfile.global);
        sendTop(player, "Top RebornAddon sampled work", latestProfile.reborn);
        sendLocations(player, latestProfile.locations);
    }

    public boolean hasFreshProfile() {
        return latestProfile.finishedAt > 0L
                && System.currentTimeMillis() - latestProfile.finishedAt <= PROFILE_FRESH_MILLIS;
    }

    public static long parseDuration(String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT_PROFILE_MILLIS;
        String text = value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        long total = 0L;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isDigit(current)) continue;
            if (i == start || current != 'h' && current != 'm' && current != 's') return -1L;
            long amount;
            try {
                amount = Long.parseLong(text.substring(start, i));
            } catch (NumberFormatException invalid) {
                return -1L;
            }
            long multiplier = current == 'h' ? 3600000L : current == 'm' ? 60000L : 1000L;
            if (amount > MAX_PROFILE_MILLIS / multiplier) return -1L;
            total += amount * multiplier;
            if (total > MAX_PROFILE_MILLIS) return -1L;
            start = i + 1;
        }
        if (start != text.length() || total < MIN_PROFILE_MILLIS) return -1L;
        return total;
    }

    public static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remainder = seconds % 60L;
        StringBuilder value = new StringBuilder();
        if (hours > 0L) value.append(hours).append('h');
        if (minutes > 0L) {
            if (value.length() > 0) value.append(' ');
            value.append(minutes).append('m');
        }
        if (remainder > 0L || value.length() == 0) {
            if (value.length() > 0) value.append(' ');
            value.append(remainder).append('s');
        }
        return value.toString();
    }

    private static ProfileResult sample(MinecraftServer server, Thread target, long durationMillis,
                                        long generation) {
        if (server == null || target == null) return ProfileResult.empty();
        Map<String, Integer> global = new HashMap<String, Integer>();
        Map<String, Integer> reborn = new HashMap<String, Integer>();
        Map<String, MutableLocation> locations = new HashMap<String, MutableLocation>();
        Map<String, MutableChunk> chunks = new HashMap<String, MutableChunk>();
        long started = System.currentTimeMillis();
        long deadline = started + durationMillis;
        int samples = 0;
        long sampleInterval = Math.max(2L, Math.min(100L,
                (durationMillis + MAX_STACK_SAMPLES - 1L) / MAX_STACK_SAMPLES));
        while (System.currentTimeMillis() < deadline
                && INSTANCE.profileGeneration.get() == generation) {
            long segmentEnd = Math.min(deadline, System.currentTimeMillis() + TIMING_SEGMENT_MILLIS);
            if (!startForgeTimings(server, segmentEnd - System.currentTimeMillis())) break;
            while (System.currentTimeMillis() < segmentEnd
                    && INSTANCE.profileGeneration.get() == generation) {
                StackTraceElement[] stack = target.getStackTrace();
                increment(global, firstUseful(stack, false));
                increment(reborn, firstUseful(stack, true));
                samples++;
                try {
                    Thread.sleep(sampleInterval);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            captureForgeTimings(server, locations, chunks);
        }
        long measured = Math.max(0L, System.currentTimeMillis() - started);
        List<LocationEntry> locationRows = topLocations(locations, MAX_LOCATION_ROWS);
        List<ChunkEntry> chunkRows = topChunks(chunks, MAX_CHUNK_ROWS);
        return new ProfileResult(top(global, samples, sampleInterval), top(reborn, samples, sampleInterval),
                locationRows, chunkRows, System.currentTimeMillis(), measured);
    }

    private static boolean startForgeTimings(MinecraftServer server, long segmentMillis) {
        final int seconds = (int) Math.max(1L, (segmentMillis + 999L) / 1000L + 1L);
        return onServerThread(server, new Runnable() {
            @Override
            public void run() {
                TimeTracker.ENTITY_UPDATE.reset();
                TimeTracker.TILE_ENTITY_UPDATE.reset();
                TimeTracker.ENTITY_UPDATE.enable(seconds);
                TimeTracker.TILE_ENTITY_UPDATE.enable(seconds);
            }
        });
    }

    private static void captureForgeTimings(MinecraftServer server,
                                            final Map<String, MutableLocation> locations,
                                            final Map<String, MutableChunk> chunks) {
        onServerThread(server, new Runnable() {
            @Override
            public void run() {
                for (ForgeTimings<Entity> timing : TimeTracker.ENTITY_UPDATE.getTimingData()) {
                    Entity entity = timing.getObject().get();
                    if (entity == null || entity.world == null || entity instanceof EntityPlayerMP) continue;
                    TimingTotal total = timingTotal(timing.getRawTimingData());
                    if (total.calls == 0) continue;
                    ResourceLocation key = EntityList.getKey(entity);
                    String name = key == null ? entity.getClass().getName() : key.toString();
                    addLocation(locations, chunks, "Entity", name, entity.dimension,
                            entity.getPosition(), total);
                }
                for (ForgeTimings<TileEntity> timing : TimeTracker.TILE_ENTITY_UPDATE.getTimingData()) {
                    TileEntity tile = timing.getObject().get();
                    if (tile == null || tile.getWorld() == null) continue;
                    BlockPos pos = tile.getPos();
                    if (!tile.getWorld().isBlockLoaded(pos, false)) continue;
                    TimingTotal total = timingTotal(timing.getRawTimingData());
                    if (total.calls == 0) continue;
                    Block block = tile.getWorld().getBlockState(pos).getBlock();
                    ResourceLocation key = block.getRegistryName();
                    String name = key == null ? tile.getClass().getName() : key.toString();
                    addLocation(locations, chunks, "Block", name,
                            tile.getWorld().provider.getDimension(), pos, total);
                }
                TimeTracker.ENTITY_UPDATE.reset();
                TimeTracker.TILE_ENTITY_UPDATE.reset();
            }
        });
    }

    private static void stopForgeTimings(MinecraftServer server) {
        if (server == null) return;
        onServerThread(server, new Runnable() {
            @Override
            public void run() {
                TimeTracker.ENTITY_UPDATE.reset();
                TimeTracker.TILE_ENTITY_UPDATE.reset();
            }
        });
    }

    private static void stopForgeTimings() {
        TimeTracker.ENTITY_UPDATE.reset();
        TimeTracker.TILE_ENTITY_UPDATE.reset();
    }

    private static boolean onServerThread(MinecraftServer server, Runnable task) {
        if (server == null || task == null) return false;
        if (server.isCallingFromMinecraftThread()) {
            task.run();
            return true;
        }
        try {
            ListenableFuture<?> future = server.addScheduledTask(task);
            future.get(15L, TimeUnit.SECONDS);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TimingTotal timingTotal(int[] values) {
        long nanos = 0L;
        int calls = 0;
        if (values != null) {
            for (int value : values) {
                if (value <= 0) continue;
                nanos += value;
                calls++;
            }
        }
        return new TimingTotal(nanos, calls);
    }

    private static void addLocation(Map<String, MutableLocation> locations,
                                    Map<String, MutableChunk> chunks, String kind, String name,
                                    int dimension, BlockPos pos, TimingTotal timing) {
        String key = kind + '|' + dimension + '|' + pos.getX() + '|' + pos.getY() + '|'
                + pos.getZ() + '|' + name;
        MutableLocation location = locations.get(key);
        if (location == null) {
            location = new MutableLocation(kind, name, dimension, pos.getX(), pos.getY(), pos.getZ());
            locations.put(key, location);
        }
        location.nanos += timing.nanos;
        location.calls += timing.calls;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String chunkKey = dimension + "|" + chunkX + "|" + chunkZ;
        MutableChunk chunk = chunks.get(chunkKey);
        if (chunk == null) {
            chunk = new MutableChunk(dimension, chunkX, chunkZ);
            chunks.put(chunkKey, chunk);
        }
        chunk.nanos += timing.nanos;
        chunk.calls += timing.calls;
        chunk.sources++;
    }

    private static List<LocationEntry> topLocations(Map<String, MutableLocation> source, int maximum) {
        List<MutableLocation> sorted = new ArrayList<MutableLocation>(source.values());
        Collections.sort(sorted, new Comparator<MutableLocation>() {
            @Override
            public int compare(MutableLocation first, MutableLocation second) {
                return Long.compare(second.nanos, first.nanos);
            }
        });
        long total = 0L;
        for (MutableLocation value : sorted) total += value.nanos;
        List<LocationEntry> result = new ArrayList<LocationEntry>();
        for (int i = 0; i < Math.min(maximum, sorted.size()); i++) {
            MutableLocation value = sorted.get(i);
            result.add(new LocationEntry(value.kind, value.name, value.dimension, value.x, value.y,
                    value.z, value.nanos / 1000L, value.calls,
                    total == 0L ? 0.0D : value.nanos * 100.0D / total));
        }
        return result;
    }

    private static List<ChunkEntry> topChunks(Map<String, MutableChunk> source, int maximum) {
        List<MutableChunk> sorted = new ArrayList<MutableChunk>(source.values());
        Collections.sort(sorted, new Comparator<MutableChunk>() {
            @Override
            public int compare(MutableChunk first, MutableChunk second) {
                return Long.compare(second.nanos, first.nanos);
            }
        });
        long total = 0L;
        for (MutableChunk value : sorted) total += value.nanos;
        List<ChunkEntry> result = new ArrayList<ChunkEntry>();
        for (int i = 0; i < Math.min(maximum, sorted.size()); i++) {
            MutableChunk value = sorted.get(i);
            result.add(new ChunkEntry(value.dimension, value.x, value.z, value.nanos / 1000L,
                    value.calls, value.sources, total == 0L ? 0.0D : value.nanos * 100.0D / total));
        }
        return result;
    }

    private static String firstUseful(StackTraceElement[] stack, boolean ownOnly) {
        if (stack == null) return "";
        for (StackTraceElement frame : stack) {
            String type = frame.getClassName();
            if (ownOnly && !type.startsWith("net.rebornaddon.")) continue;
            if (type.equals(Thread.class.getName()) || type.startsWith("sun.reflect.")
                    || type.startsWith("java.lang.reflect.")) continue;
            if (!ownOnly && (type.equals("net.minecraft.server.MinecraftServer")
                    || type.equals("net.minecraft.server.dedicated.DedicatedServer"))) continue;
            return type + "." + frame.getMethodName();
        }
        return "";
    }

    private static void increment(Map<String, Integer> counts, String key) {
        if (key == null || key.isEmpty()) return;
        Integer value = counts.get(key);
        counts.put(key, Integer.valueOf(value == null ? 1 : value.intValue() + 1));
    }

    private static List<ProfileEntry> top(Map<String, Integer> counts, int totalSamples,
                                          long sampleIntervalMillis) {
        List<Map.Entry<String, Integer>> values = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(values, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> first, Map.Entry<String, Integer> second) {
                return second.getValue().compareTo(first.getValue());
            }
        });
        List<ProfileEntry> result = new ArrayList<ProfileEntry>();
        for (int i = 0; i < Math.min(10, values.size()); i++) {
            Map.Entry<String, Integer> value = values.get(i);
            result.add(new ProfileEntry(value.getKey(),
                    (long) value.getValue().intValue() * sampleIntervalMillis * 1000L,
                    totalSamples == 0 ? 0.0D : value.getValue().doubleValue() * 100.0D / totalSamples));
        }
        return result;
    }

    private synchronized double tps(int window) {
        int size = Math.min(count, window);
        if (size < 2) return 20.0D;
        int newest = (cursor - 1 + HISTORY) % HISTORY;
        int oldest = (cursor - size + HISTORY) % HISTORY;
        long elapsed = tickEnds[newest] - tickEnds[oldest];
        if (elapsed <= 0L) return 20.0D;
        return Math.min(20.0D, (size - 1) * 1000000000.0D / elapsed);
    }

    private synchronized double averageTickMillis(int window) {
        int size = Math.min(count, window);
        if (size == 0) return 0.0D;
        long total = 0L;
        for (int i = 0; i < size; i++) total += tickDurations[(cursor - 1 - i + HISTORY) % HISTORY];
        return total / 1000000.0D / size;
    }

    private synchronized double percentileTickMillis(int window, double percentile) {
        int size = Math.min(count, window);
        if (size == 0) return 0.0D;
        List<Long> values = new ArrayList<Long>(size);
        for (int i = 0; i < size; i++) values.add(Long.valueOf(tickDurations[(cursor - 1 - i + HISTORY) % HISTORY]));
        Collections.sort(values);
        int index = Math.max(0, Math.min(size - 1, (int) Math.ceil(size * percentile) - 1));
        return values.get(index).longValue() / 1000000.0D;
    }

    private void appendWorldCounts(NBTTagCompound data) {
        MinecraftServer current = server;
        int chunks = 0;
        int entities = 0;
        int tiles = 0;
        if (current != null && current.worlds != null) {
            for (WorldServer world : current.worlds) {
                if (world == null) continue;
                chunks += world.getChunkProvider().getLoadedChunkCount();
                entities += world.loadedEntityList.size();
                tiles += world.loadedTileEntityList.size();
            }
        }
        data.setInteger("Chunks", chunks);
        data.setInteger("Entities", entities);
        data.setInteger("TileEntities", tiles);
    }

    private void appendPlayerLatency(NBTTagCompound data) {
        MinecraftServer current = server;
        int total = 0;
        int maximum = 0;
        int players = 0;
        if (current != null) {
            for (EntityPlayerMP player : current.getPlayerList().getPlayers()) {
                int ping = Math.max(0, player.ping);
                total += ping;
                maximum = Math.max(maximum, ping);
                players++;
            }
        }
        data.setInteger("Players", players);
        data.setInteger("AveragePing", players == 0 ? 0 : total / players);
        data.setInteger("MaximumPing", maximum);
    }

    private synchronized void recordHeap() {
        Runtime runtime = Runtime.getRuntime();
        heapHistory[heapCursor] = runtime.totalMemory() - runtime.freeMemory();
        heapCursor = (heapCursor + 1) % heapHistory.length;
        if (heapCount < heapHistory.length) heapCount++;
    }

    private synchronized long heapTrendPerMinute() {
        if (heapCount < 2) return 0L;
        int newest = (heapCursor - 1 + heapHistory.length) % heapHistory.length;
        int oldest = (heapCursor - heapCount + heapHistory.length) % heapHistory.length;
        long elapsedSeconds = (heapCount - 1L) * 10L;
        return elapsedSeconds <= 0L ? 0L
                : (heapHistory[newest] - heapHistory[oldest]) * 60L / elapsedSeconds;
    }

    private synchronized int risingHeapSamples() {
        int rising = 0;
        for (int i = 1; i < heapCount; i++) {
            int previous = (heapCursor - heapCount + i - 1 + heapHistory.length) % heapHistory.length;
            int current = (heapCursor - heapCount + i + heapHistory.length) % heapHistory.length;
            if (heapHistory[current] >= heapHistory[previous]) rising++;
        }
        return rising;
    }

    private static long gcCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionCount() > 0L) total += bean.getCollectionCount();
        }
        return total;
    }

    private static long gcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionTime() > 0L) total += bean.getCollectionTime();
        }
        return total;
    }

    private static NBTTagList profileList(List<ProfileEntry> values) {
        NBTTagList list = new NBTTagList();
        for (ProfileEntry value : values) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Name", value.name);
            row.setLong("Micros", value.micros);
            row.setDouble("Percent", value.percent);
            list.appendTag(row);
        }
        return list;
    }

    private static NBTTagList locationList(List<LocationEntry> values) {
        NBTTagList list = new NBTTagList();
        for (LocationEntry value : values) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Kind", value.kind);
            row.setString("Name", value.name);
            row.setInteger("Dimension", value.dimension);
            row.setInteger("X", value.x);
            row.setInteger("Y", value.y);
            row.setInteger("Z", value.z);
            row.setLong("Micros", value.micros);
            row.setInteger("Calls", value.calls);
            row.setDouble("Percent", value.percent);
            list.appendTag(row);
        }
        return list;
    }

    private static NBTTagList chunkList(List<ChunkEntry> values) {
        NBTTagList list = new NBTTagList();
        for (ChunkEntry value : values) {
            NBTTagCompound row = new NBTTagCompound();
            row.setInteger("Dimension", value.dimension);
            row.setInteger("ChunkX", value.x);
            row.setInteger("ChunkZ", value.z);
            row.setLong("Micros", value.micros);
            row.setInteger("Calls", value.calls);
            row.setInteger("Sources", value.sources);
            row.setDouble("Percent", value.percent);
            list.appendTag(row);
        }
        return list;
    }

    private static void sendTop(EntityPlayerMP player, String title, List<ProfileEntry> values) {
        player.sendMessage(new TextComponentString(TextFormatting.AQUA + title
                + TextFormatting.GRAY + " (estimated sampled microseconds):"));
        if (values.isEmpty()) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "  No samples captured."));
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            ProfileEntry value = values.get(i);
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "  " + (i + 1) + ". "
                    + TextFormatting.WHITE + value.name + TextFormatting.DARK_GRAY + " - "
                    + value.micros + " us (" + String.format(Locale.ROOT, "%.1f", value.percent) + "%)"));
        }
    }

    private static void sendLocations(EntityPlayerMP player, List<LocationEntry> values) {
        player.sendMessage(new TextComponentString(TextFormatting.AQUA
                + "Top exact entity and ticking-block locations:"));
        if (values.isEmpty()) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "  No timed locations captured."));
            return;
        }
        for (int i = 0; i < Math.min(10, values.size()); i++) {
            LocationEntry value = values.get(i);
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "  " + (i + 1) + ". "
                    + TextFormatting.WHITE + value.name + TextFormatting.DARK_GRAY + " @ "
                    + value.x + ", " + value.y + ", " + value.z + " dim " + value.dimension
                    + " - " + value.micros + " us"));
        }
    }

    private static boolean canInspect(EntityPlayerMP player) {
        return player != null && player.canUseCommand(2, "reborntps");
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024.0D;
            unit++;
        } while (value >= 1024.0D && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static class PositionedEntry {
        final String label;
        final int dimension;
        final int x;
        final int y;
        final int z;

        private PositionedEntry(String label, int dimension, int x, int y, int z) {
            this.label = label;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class LocationEntry extends PositionedEntry {
        private final String kind;
        private final String name;
        private final long micros;
        private final int calls;
        private final double percent;

        private LocationEntry(String kind, String name, int dimension, int x, int y, int z,
                              long micros, int calls, double percent) {
            super(name, dimension, x, y, z);
            this.kind = kind;
            this.name = name;
            this.micros = micros;
            this.calls = calls;
            this.percent = percent;
        }
    }

    private static final class ChunkEntry extends PositionedEntry {
        private final long micros;
        private final int calls;
        private final int sources;
        private final double percent;

        private ChunkEntry(int dimension, int x, int z, long micros, int calls, int sources,
                           double percent) {
            super("[" + x + ", " + z + "]", dimension, x, 0, z);
            this.micros = micros;
            this.calls = calls;
            this.sources = sources;
            this.percent = percent;
        }
    }

    private static final class MutableLocation {
        private final String kind;
        private final String name;
        private final int dimension;
        private final int x;
        private final int y;
        private final int z;
        private long nanos;
        private int calls;

        private MutableLocation(String kind, String name, int dimension, int x, int y, int z) {
            this.kind = kind;
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class MutableChunk {
        private final int dimension;
        private final int x;
        private final int z;
        private long nanos;
        private int calls;
        private int sources;

        private MutableChunk(int dimension, int x, int z) {
            this.dimension = dimension;
            this.x = x;
            this.z = z;
        }
    }

    private static final class TimingTotal {
        private final long nanos;
        private final int calls;

        private TimingTotal(long nanos, int calls) {
            this.nanos = nanos;
            this.calls = calls;
        }
    }

    private static final class ProfileResult {
        private final List<ProfileEntry> global;
        private final List<ProfileEntry> reborn;
        private final List<LocationEntry> locations;
        private final List<ChunkEntry> chunks;
        private final long finishedAt;
        private final long measuredMillis;

        private ProfileResult(List<ProfileEntry> global, List<ProfileEntry> reborn,
                              List<LocationEntry> locations, List<ChunkEntry> chunks,
                              long finishedAt, long measuredMillis) {
            this.global = global;
            this.reborn = reborn;
            this.locations = locations;
            this.chunks = chunks;
            this.finishedAt = finishedAt;
            this.measuredMillis = measuredMillis;
        }

        private static ProfileResult empty() {
            return new ProfileResult(Collections.<ProfileEntry>emptyList(),
                    Collections.<ProfileEntry>emptyList(), Collections.<LocationEntry>emptyList(),
                    Collections.<ChunkEntry>emptyList(), 0L, 0L);
        }
    }

    private static final class ProfileEntry {
        private final String name;
        private final long micros;
        private final double percent;

        private ProfileEntry(String name, long micros, double percent) {
            this.name = name;
            this.micros = micros;
            this.percent = percent;
        }
    }
}
