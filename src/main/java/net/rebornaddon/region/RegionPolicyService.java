package net.rebornaddon.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RegionPolicyService {
    public static final RegionPolicyService INSTANCE = new RegionPolicyService();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CELL_SIZE = 64;
    private static final int BROAD_REGION_CELL_LIMIT = 4096;
    private volatile Snapshot snapshot = Snapshot.empty();
    private final Map<UUID, PlayerCache> playerCache = new HashMap<UUID, PlayerCache>();
    private final List<RegionDefinition> persistedRegions = new ArrayList<RegionDefinition>();
    private final Map<String, List<RegionDefinition>> transientRegions =
            new LinkedHashMap<String, List<RegionDefinition>>();
    private File file;
    private long revision;

    private RegionPolicyService() {
    }

    public synchronized void initialize(File serverDirectory) {
        File directory = new File(serverDirectory, "rebornaddon");
        if (!directory.exists()) directory.mkdirs();
        file = new File(directory, "regions.json");
        reload();
    }

    public synchronized void reset() {
        snapshot = Snapshot.empty();
        persistedRegions.clear();
        transientRegions.clear();
        playerCache.clear();
        file = null;
        revision++;
    }

    public synchronized boolean reload() {
        RegionFile data = new RegionFile();
        if (file != null && file.isFile()) {
            try {
                FileReader reader = new FileReader(file);
                try {
                    RegionFile read = GSON.fromJson(reader, RegionFile.class);
                    if (read != null) data = read;
                } finally {
                    reader.close();
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        persistedRegions.clear();
        if (data.regions != null) {
            for (RegionDefinition region : data.regions) {
                RegionDefinition clean = sanitize(region);
                if (clean != null) persistedRegions.add(clean);
            }
        }
        rebuildSnapshot();
        if (file != null && !file.isFile()) save();
        return true;
    }

    public synchronized boolean put(RegionDefinition definition) {
        RegionDefinition clean = sanitize(definition);
        if (clean == null) return false;
        List<RegionDefinition> values = mutableRegions();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).id.equals(clean.id)) {
                values.set(i, clean);
                rebuild(values);
                return true;
            }
        }
        values.add(clean);
        rebuild(values);
        return true;
    }

    public synchronized boolean remove(String id) {
        List<RegionDefinition> values = mutableRegions();
        boolean changed = values.removeIf(region -> region.id.equals(normalizeId(id)));
        if (changed) rebuild(values);
        return changed;
    }

    public synchronized boolean setRule(String id, String rule, String value) {
        String target = normalizeId(id);
        String key = normalizeRule(rule);
        if (target.isEmpty() || key.isEmpty()) return false;
        List<RegionDefinition> values = mutableRegions();
        for (RegionDefinition region : values) {
            if (!region.id.equals(target)) continue;
            if (value == null || value.trim().isEmpty() || "default".equalsIgnoreCase(value)) {
                region.rules.remove(key);
            } else {
                region.rules.put(key, value.trim().toLowerCase(Locale.ROOT));
            }
            rebuild(values);
            return true;
        }
        return false;
    }

    public List<RegionDefinition> regions() {
        return snapshot.regions;
    }

    public synchronized void replaceTransient(String source, List<RegionDefinition> definitions) {
        String key = normalizeId(source);
        if (key.isEmpty()) return;
        List<RegionDefinition> clean = new ArrayList<RegionDefinition>();
        if (definitions != null) {
            for (RegionDefinition definition : definitions) {
                RegionDefinition value = sanitize(definition);
                if (value != null && clean.size() < 256) clean.add(value);
            }
        }
        if (clean.isEmpty()) transientRegions.remove(key);
        else transientRegions.put(key, clean);
        rebuildSnapshot();
    }

    public List<RegionDefinition> at(Entity entity) {
        if (entity == null) return Collections.emptyList();
        return at(entity.dimension, new BlockPos(entity));
    }

    public List<RegionDefinition> at(int dimension, BlockPos pos) {
        return snapshot.at(dimension, pos);
    }

    public boolean allows(String rule, Entity entity, boolean fallback) {
        if (entity == null) return fallback;
        List<RegionDefinition> active;
        if (entity instanceof EntityPlayer) {
            active = cached((EntityPlayer) entity);
        } else {
            active = at(entity.dimension, new BlockPos(entity));
        }
        return resolve(active, rule, fallback);
    }

    public boolean allows(String rule, int dimension, BlockPos pos, boolean fallback) {
        return resolve(at(dimension, pos), rule, fallback);
    }

    public String value(String rule, Entity entity, String fallback) {
        List<RegionDefinition> active = entity instanceof EntityPlayer
                ? cached((EntityPlayer) entity) : at(entity.dimension, new BlockPos(entity));
        String key = normalizeRule(rule);
        for (RegionDefinition region : active) {
            String value = region.rules.get(key);
            if (value != null && !value.isEmpty()) return value;
        }
        return fallback;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (event.getTarget() instanceof EntityPlayer && !allows("pvp", player, true)) {
            event.setCanceled(true);
            deny(player, "PvP is disabled in this region.");
            return;
        }
        ItemStack held = player.getHeldItemMainhand();
        if (isWeapon(held) && !allows("weapons", player, true)) {
            event.setCanceled(true);
            deny(player, "Weapons are disabled in this region.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player != null && !allows("break", player.dimension, event.getPos(), true)) {
            event.setCanceled(true);
            deny(player, "Blocks cannot be broken in this region.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player != null && !allows("place", player.dimension, event.getPos(), true)) {
            event.setCanceled(true);
            deny(player, "Blocks cannot be placed in this region.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!allows("interact", player.dimension, event.getPos(), true)) {
            event.setCanceled(true);
            deny(player, "Interaction is disabled in this region.");
            return;
        }
        IBlockState state = event.getWorld().getBlockState(event.getPos());
        if (state.getBlock().hasTileEntity(state)
                && !allows("containers", player.dimension, event.getPos(), true)) {
            event.setCanceled(true);
            deny(player, "Containers are disabled in this region.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTeleport(EnderTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!allows("teleport", entity, true)) {
            event.setCanceled(true);
            if (entity instanceof EntityPlayer) deny((EntityPlayer) entity, "Teleporting is disabled in this region.");
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || event.player.world.getTotalWorldTime() % 20L != 0L) return;
        if (allows("armor", event.player, true)) return;
        for (int i = 0; i < event.player.inventory.armorInventory.size(); i++) {
            ItemStack armor = event.player.inventory.armorInventory.get(i);
            if (armor.isEmpty() || !(armor.getItem() instanceof ItemArmor)) continue;
            ItemStack returned = armor.copy();
            event.player.inventory.armorInventory.set(i, ItemStack.EMPTY);
            if (!event.player.inventory.addItemStackToInventory(returned)) event.player.dropItem(returned, false);
        }
    }

    @SubscribeEvent
    public synchronized void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) playerCache.remove(event.player.getUniqueID());
    }

    public boolean allowsJutsu(EntityPlayer player) {
        return allows("jutsu", player, true);
    }

    public boolean allowsMode(EntityPlayer player) {
        return allows("modes", player, true);
    }

    private List<RegionDefinition> cached(EntityPlayer player) {
        BlockPos pos = new BlockPos(player);
        PlayerCache cached = playerCache.get(player.getUniqueID());
        if (cached != null && cached.revision == revision && cached.dimension == player.dimension
                && cached.x == pos.getX() && cached.y == pos.getY() && cached.z == pos.getZ()) {
            return cached.regions;
        }
        List<RegionDefinition> found = at(player.dimension, pos);
        playerCache.put(player.getUniqueID(), new PlayerCache(revision, player.dimension,
                pos.getX(), pos.getY(), pos.getZ(), found));
        return found;
    }

    private static boolean resolve(List<RegionDefinition> regions, String rule, boolean fallback) {
        String key = normalizeRule(rule);
        Boolean allowed = null;
        for (RegionDefinition region : regions) {
            String value = region.rules.get(key);
            if ("false".equals(value) || "deny".equals(value) || "disabled".equals(value)) return false;
            if ("true".equals(value) || "allow".equals(value) || "enabled".equals(value)) allowed = Boolean.TRUE;
        }
        return allowed == null ? fallback : allowed.booleanValue();
    }

    private synchronized List<RegionDefinition> mutableRegions() {
        List<RegionDefinition> values = new ArrayList<RegionDefinition>();
        for (RegionDefinition region : persistedRegions) values.add(region.copy());
        return values;
    }

    private synchronized void rebuild(List<RegionDefinition> values) {
        persistedRegions.clear();
        if (values != null) {
            for (RegionDefinition value : values) {
                RegionDefinition clean = sanitize(value);
                if (clean != null) persistedRegions.add(clean);
            }
        }
        rebuildSnapshot();
        save();
    }

    private synchronized void rebuildSnapshot() {
        List<RegionDefinition> combined = new ArrayList<RegionDefinition>();
        for (RegionDefinition region : persistedRegions) combined.add(region.copy());
        for (List<RegionDefinition> values : transientRegions.values()) {
            for (RegionDefinition region : values) combined.add(region.copy());
        }
        snapshot = Snapshot.build(combined);
        playerCache.clear();
        revision++;
    }

    private synchronized void save() {
        if (file == null) return;
        RegionFile data = new RegionFile();
        for (RegionDefinition region : persistedRegions) data.regions.add(region.copy());
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            FileWriter writer = new FileWriter(temporary);
            try {
                GSON.toJson(data, writer);
            } finally {
                writer.close();
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
        }
    }

    private static RegionDefinition sanitize(RegionDefinition value) {
        if (value == null) return null;
        RegionDefinition clean = value.copy();
        clean.id = normalizeId(clean.id);
        if (clean.id.isEmpty()) return null;
        if (clean.minX > clean.maxX) { int swap = clean.minX; clean.minX = clean.maxX; clean.maxX = swap; }
        if (clean.minY > clean.maxY) { int swap = clean.minY; clean.minY = clean.maxY; clean.maxY = swap; }
        if (clean.minZ > clean.maxZ) { int swap = clean.minZ; clean.minZ = clean.maxZ; clean.maxZ = swap; }
        clean.minY = Math.max(0, clean.minY);
        clean.maxY = Math.min(255, clean.maxY);
        Map<String, String> rules = new LinkedHashMap<String, String>();
        if (clean.rules != null) {
            for (Map.Entry<String, String> entry : clean.rules.entrySet()) {
                String key = normalizeRule(entry.getKey());
                if (!key.isEmpty() && entry.getValue() != null) rules.put(key,
                        entry.getValue().trim().toLowerCase(Locale.ROOT));
            }
        }
        clean.rules = rules;
        return clean;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String normalizeRule(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemBow || stack.getItem() instanceof ItemTool);
    }

    private static void deny(EntityPlayer player, String message) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED + message), true);
    }

    public static final class RegionDefinition {
        public String id = "";
        public int dimension;
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY = 255;
        public int maxZ;
        public int priority;
        public Map<String, String> rules = new LinkedHashMap<String, String>();

        public boolean contains(BlockPos pos) {
            return pos != null && pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        public RegionDefinition copy() {
            RegionDefinition value = new RegionDefinition();
            value.id = id;
            value.dimension = dimension;
            value.minX = minX;
            value.minY = minY;
            value.minZ = minZ;
            value.maxX = maxX;
            value.maxY = maxY;
            value.maxZ = maxZ;
            value.priority = priority;
            value.rules = rules == null ? new LinkedHashMap<String, String>()
                    : new LinkedHashMap<String, String>(rules);
            return value;
        }
    }

    private static final class RegionFile {
        private int version = 1;
        private List<RegionDefinition> regions = new ArrayList<RegionDefinition>();
    }

    private static final class Snapshot {
        private final List<RegionDefinition> regions;
        private final Map<Integer, DimensionIndex> dimensions;

        private Snapshot(List<RegionDefinition> regions, Map<Integer, DimensionIndex> dimensions) {
            this.regions = regions;
            this.dimensions = dimensions;
        }

        private List<RegionDefinition> at(int dimension, BlockPos pos) {
            DimensionIndex index = dimensions.get(Integer.valueOf(dimension));
            if (index == null || pos == null) return Collections.emptyList();
            return index.at(pos);
        }

        private static Snapshot build(List<RegionDefinition> source) {
            List<RegionDefinition> regions = new ArrayList<RegionDefinition>();
            Map<Integer, DimensionIndexBuilder> builders = new HashMap<Integer, DimensionIndexBuilder>();
            if (source != null) {
                for (RegionDefinition raw : source) {
                    RegionDefinition region = sanitize(raw);
                    if (region == null) continue;
                    regions.add(region);
                    DimensionIndexBuilder builder = builders.get(Integer.valueOf(region.dimension));
                    if (builder == null) {
                        builder = new DimensionIndexBuilder();
                        builders.put(Integer.valueOf(region.dimension), builder);
                    }
                    builder.add(region);
                }
            }
            Collections.sort(regions, Comparator.comparing(region -> region.id));
            Map<Integer, DimensionIndex> dimensions = new HashMap<Integer, DimensionIndex>();
            for (Map.Entry<Integer, DimensionIndexBuilder> entry : builders.entrySet()) {
                dimensions.put(entry.getKey(), entry.getValue().build());
            }
            return new Snapshot(Collections.unmodifiableList(regions), Collections.unmodifiableMap(dimensions));
        }

        private static Snapshot empty() {
            return build(Collections.<RegionDefinition>emptyList());
        }
    }

    private static final class DimensionIndexBuilder {
        private final Map<Long, List<RegionDefinition>> cells = new HashMap<Long, List<RegionDefinition>>();
        private final List<RegionDefinition> broad = new ArrayList<RegionDefinition>();

        private void add(RegionDefinition region) {
            int minCellX = Math.floorDiv(region.minX, CELL_SIZE);
            int maxCellX = Math.floorDiv(region.maxX, CELL_SIZE);
            int minCellZ = Math.floorDiv(region.minZ, CELL_SIZE);
            int maxCellZ = Math.floorDiv(region.maxZ, CELL_SIZE);
            long count = (long) (maxCellX - minCellX + 1) * (maxCellZ - minCellZ + 1);
            if (count > BROAD_REGION_CELL_LIMIT) {
                broad.add(region);
                return;
            }
            for (int x = minCellX; x <= maxCellX; x++) {
                for (int z = minCellZ; z <= maxCellZ; z++) {
                    long key = key(x, z);
                    List<RegionDefinition> values = cells.get(Long.valueOf(key));
                    if (values == null) {
                        values = new ArrayList<RegionDefinition>();
                        cells.put(Long.valueOf(key), values);
                    }
                    values.add(region);
                }
            }
        }

        private DimensionIndex build() {
            for (List<RegionDefinition> values : cells.values()) sort(values);
            sort(broad);
            return new DimensionIndex(cells, broad);
        }
    }

    private static final class DimensionIndex {
        private final Map<Long, List<RegionDefinition>> cells;
        private final List<RegionDefinition> broad;

        private DimensionIndex(Map<Long, List<RegionDefinition>> cells, List<RegionDefinition> broad) {
            Map<Long, List<RegionDefinition>> frozen = new HashMap<Long, List<RegionDefinition>>();
            for (Map.Entry<Long, List<RegionDefinition>> entry : cells.entrySet()) {
                frozen.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<RegionDefinition>(entry.getValue())));
            }
            this.cells = Collections.unmodifiableMap(frozen);
            this.broad = Collections.unmodifiableList(new ArrayList<RegionDefinition>(broad));
        }

        private List<RegionDefinition> at(BlockPos pos) {
            List<RegionDefinition> local = cells.get(Long.valueOf(key(Math.floorDiv(pos.getX(), CELL_SIZE),
                    Math.floorDiv(pos.getZ(), CELL_SIZE))));
            if ((local == null || local.isEmpty()) && broad.isEmpty()) return Collections.emptyList();
            List<RegionDefinition> result = new ArrayList<RegionDefinition>();
            if (local != null) for (RegionDefinition region : local) if (region.contains(pos)) result.add(region);
            for (RegionDefinition region : broad) if (region.contains(pos)) result.add(region);
            sort(result);
            return Collections.unmodifiableList(result);
        }
    }

    private static void sort(List<RegionDefinition> values) {
        Collections.sort(values, (first, second) -> {
            int priority = Integer.compare(second.priority, first.priority);
            return priority != 0 ? priority : first.id.compareTo(second.id);
        });
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static final class PlayerCache {
        private final long revision;
        private final int dimension;
        private final int x;
        private final int y;
        private final int z;
        private final List<RegionDefinition> regions;

        private PlayerCache(long revision, int dimension, int x, int y, int z,
                            List<RegionDefinition> regions) {
            this.revision = revision;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.regions = regions;
        }
    }
}
