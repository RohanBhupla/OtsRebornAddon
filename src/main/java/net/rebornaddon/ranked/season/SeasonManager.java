package net.rebornaddon.ranked.season;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.rebornaddon.ranked.RankedPluginBridge;
import net.rebornaddon.ranked.elo.EloManager;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.elo.RankTier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class SeasonManager {
    public static final int ACTION_LIFECYCLE = 60;
    public static final int ACTION_SET_NAME = 61;
    public static final int ACTION_SET_DURATION = 62;
    public static final int ACTION_SET_LEADERBOARD = 63;
    public static final int ACTION_SET_PLACEMENT = 64;
    public static final int ACTION_ADD_ITEM = 65;
    public static final int ACTION_CLEAR_ITEMS = 66;
    public static final int ACTION_ADD_COMMAND = 67;
    public static final int ACTION_CLEAR_COMMANDS = 68;
    public static final int ACTION_SET_GROUP = 69;
    public static final int ACTION_BLOCK_PLAYER = 70;
    public static final int ACTION_UNBLOCK_PLAYER = 71;
    public static final int ACTION_RELOAD = 72;

    private static final long RUNTIME_CACHE_MILLIS = 2000L;
    private static final long PODIUM_RETRY_MILLIS = 5000L;
    private static final long PODIUM_HEARTBEAT_MILLIS = 30000L;

    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private int seasonNumber = 1;
    private long seasonStartMillis = System.currentTimeMillis();
    private long seasonDurationMillis;
    private Map<String, String> tierRewards = new HashMap<String, String>();
    private Map<String, List<String>> pendingRewards = new HashMap<String, List<String>>();
    private List<SeasonRecord> history = new ArrayList<SeasonRecord>();
    private RuntimeState runtime = new RuntimeState();
    private long lastRuntimeRefresh;
    private boolean pluginActive;
    private String lastPodiumPayload = "";
    private long lastPodiumSync;
    private long lastPodiumAttempt;

    public static class SeasonRecord {
        public int seasonNumber;
        public long endedAtMillis;
        public List<String> topPlayerNames = new ArrayList<String>();
        public List<Integer> topPlayerElos = new ArrayList<Integer>();
    }

    private static class SaveData {
        int seasonNumber;
        long seasonStartMillis;
        long seasonDurationMillis;
        Map<String, String> tierRewards;
        Map<String, List<String>> pendingRewards;
        List<SeasonRecord> history;
    }

    public SeasonManager(File dataFolder, long defaultDurationMillis) {
        seasonDurationMillis = defaultDurationMillis;
        if (!dataFolder.exists()) dataFolder.mkdirs();
        dataFile = new File(dataFolder, "season.json");
        load();
        runtime.seasonNumber = seasonNumber;
        runtime.displayName = "Season " + seasonNumber;
        runtime.durationMillis = seasonDurationMillis;
        runtime.remainingMillis = fallbackRemaining();
        refreshRuntime(true);
    }

    public int getSeasonNumber() {
        refreshRuntime(false);
        return pluginActive ? runtime.seasonNumber : seasonNumber;
    }

    public String getSeasonDisplayName() {
        refreshRuntime(false);
        return pluginActive ? runtime.displayName : "Season " + seasonNumber;
    }

    public String getState() {
        refreshRuntime(false);
        return pluginActive ? runtime.state : "running";
    }

    public long getSeasonStartMillis() {
        return seasonStartMillis;
    }

    public long getSeasonDurationMillis() {
        refreshRuntime(false);
        return pluginActive ? runtime.durationMillis : seasonDurationMillis;
    }

    public long getTimeRemainingMillis() {
        refreshRuntime(false);
        return pluginActive ? runtime.remainingMillis : fallbackRemaining();
    }

    public boolean isSeasonExpired() {
        refreshRuntime(true);
        return pluginActive ? "running".equals(runtime.state) && runtime.remainingMillis <= 0L
                : fallbackRemaining() <= 0L;
    }

    public boolean isAcceptingMatches() {
        refreshRuntime(false);
        return !pluginActive || "running".equals(runtime.state) && !runtime.resetPending;
    }

    public boolean isLeaderboardEnabled() {
        refreshRuntime(false);
        return !pluginActive || runtime.leaderboardEnabled;
    }

    public boolean isBlocked(UUID playerId) {
        refreshRuntime(false);
        return pluginActive && playerId != null && runtime.blockedPlayers.contains(playerId.toString());
    }

    public List<PlayerStats> visibleTop(EloManager eloManager, int limit) {
        if (!isLeaderboardEnabled() || eloManager == null || limit <= 0) return Collections.emptyList();
        return eligibleTop(eloManager, limit);
    }

    public List<PlayerStats> eligibleTop(EloManager eloManager, int limit) {
        if (eloManager == null || limit <= 0) return Collections.emptyList();
        List<PlayerStats> values = new ArrayList<PlayerStats>(eloManager.getAllStats());
        Collections.sort(values, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats left, PlayerStats right) {
                int elo = Integer.compare(right.elo, left.elo);
                return elo != 0 ? elo : left.name.compareToIgnoreCase(right.name);
            }
        });
        List<PlayerStats> result = new ArrayList<PlayerStats>();
        for (PlayerStats stats : values) {
            if (!isBlocked(stats.uuid)) result.add(stats);
            if (result.size() >= limit) break;
        }
        return result;
    }

    public void syncPodium(EloManager eloManager, boolean force) {
        if (eloManager == null || !RankedPluginBridge.isAvailable()) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastPodiumAttempt < PODIUM_RETRY_MILLIS) return;

        JsonArray standings = new JsonArray();
        List<PlayerStats> top = eligibleTop(eloManager, 3);
        for (int index = 0; index < top.size(); index++) {
            PlayerStats stats = top.get(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("placement", index + 1);
            entry.addProperty("uuid", stats.uuid.toString());
            entry.addProperty("name", stats.name);
            entry.addProperty("elo", stats.elo);
            standings.add(entry);
        }
        String payload = gson.toJson(standings);
        if (!force && payload.equals(lastPodiumPayload)
                && now - lastPodiumSync < PODIUM_HEARTBEAT_MILLIS) return;

        lastPodiumAttempt = now;
        String[] response = RankedPluginBridge.syncPodium(payload);
        if (RankedPluginBridge.successful(response)) {
            lastPodiumPayload = payload;
            lastPodiumSync = now;
        }
    }

    public void refreshRuntime(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRuntimeRefresh < RUNTIME_CACHE_MILLIS) return;
        lastRuntimeRefresh = now;
        if (!RankedPluginBridge.isAvailable()) {
            pluginActive = false;
            return;
        }
        String[] response = RankedPluginBridge.runtimeSnapshot();
        RuntimeState parsed = parseRuntime(RankedPluginBridge.payload(response));
        if (RankedPluginBridge.successful(response) && parsed != null) {
            runtime = parsed;
            pluginActive = true;
        }
    }

    public NBTTagCompound adminSnapshot(ICommandSender sender) {
        NBTTagCompound result = new NBTTagCompound();
        String[] response = RankedPluginBridge.adminSnapshot(sender);
        result.setBoolean("Available", RankedPluginBridge.successful(response));
        result.setString("Message", RankedPluginBridge.message(response));
        if (!RankedPluginBridge.successful(response)) return result;
        try {
            JsonObject root = new JsonParser().parse(RankedPluginBridge.payload(response)).getAsJsonObject();
            result.setInteger("SeasonNumber", integer(root, "seasonNumber", 1));
            result.setString("SeasonName", string(root, "seasonName"));
            result.setString("DisplayName", string(root, "displayName"));
            result.setString("State", string(root, "state"));
            result.setLong("DurationMillis", number(root, "durationMillis", 0L));
            result.setLong("RemainingMillis", number(root, "remainingMillis", 0L));
            result.setBoolean("LeaderboardEnabled", bool(root, "leaderboardEnabled", true));
            result.setBoolean("ResetPending", bool(root, "resetPending", false));
            result.setLong("Revision", number(root, "revision", 0L));
            NBTTagList placements = new NBTTagList();
            JsonArray placementValues = array(root, "placements");
            for (JsonElement element : placementValues) {
                if (!element.isJsonObject()) continue;
                JsonObject source = element.getAsJsonObject();
                NBTTagCompound placement = new NBTTagCompound();
                placement.setInteger("Placement", integer(source, "placement", 0));
                placement.setBoolean("Enabled", bool(source, "enabled", false));
                placement.setString("Group", string(source, "group"));
                placement.setTag("Items", strings(array(source, "itemLabels")));
                placement.setTag("Commands", strings(array(source, "commands")));
                placements.appendTag(placement);
            }
            result.setTag("Placements", placements);
            NBTTagList blocked = new NBTTagList();
            for (JsonElement element : array(root, "blockedPlayers")) {
                if (!element.isJsonObject()) continue;
                JsonObject source = element.getAsJsonObject();
                NBTTagCompound player = new NBTTagCompound();
                player.setString("Uuid", string(source, "uuid"));
                player.setString("Name", string(source, "name"));
                blocked.appendTag(player);
            }
            result.setTag("BlockedPlayers", blocked);
        } catch (RuntimeException exception) {
            result.setBoolean("Available", false);
            result.setString("Message", "The server returned invalid ranked season data.");
        }
        return result;
    }

    public String[] handleAdminAction(EntityPlayerMP player, int action, String id,
                                      String property, String value) {
        if (player == null || !player.canUseCommand(2, "rebornadmin")) {
            return new String[] {"false", "Operator access is required.", ""};
        }
        JsonObject payload = new JsonObject();
        String pluginAction;
        if (action == ACTION_LIFECYCLE) {
            if ("end".equals(id)) return endSeason(player, player.getServer());
            pluginAction = id;
        } else if (action == ACTION_SET_NAME) {
            pluginAction = "set-name";
            payload.addProperty("name", value);
        } else if (action == ACTION_SET_DURATION) {
            pluginAction = "set-duration";
            Long duration = whole(value);
            if (duration == null) return failure("Enter a valid season duration.");
            payload.addProperty("milliseconds", duration.longValue());
        } else if (action == ACTION_SET_LEADERBOARD) {
            pluginAction = "set-leaderboard";
            payload.addProperty("enabled", Boolean.parseBoolean(value));
        } else if (action == ACTION_SET_PLACEMENT) {
            pluginAction = "set-placement-enabled";
            payload.addProperty("placement", placement(id));
            payload.addProperty("enabled", Boolean.parseBoolean(value));
        } else if (action == ACTION_ADD_ITEM) {
            ItemStack held = player.getHeldItemMainhand();
            if (held == null || held.isEmpty()) return failure("Hold the reward item in your main hand first.");
            pluginAction = "add-item";
            payload.addProperty("placement", placement(id));
            payload.addProperty("item", itemToBase64(held.copy()));
            payload.addProperty("label", held.getCount() + "x " + held.getDisplayName());
        } else if (action == ACTION_CLEAR_ITEMS) {
            pluginAction = "clear-items";
            payload.addProperty("placement", placement(id));
        } else if (action == ACTION_ADD_COMMAND) {
            pluginAction = "add-command";
            payload.addProperty("placement", placement(id));
            payload.addProperty("command", value);
        } else if (action == ACTION_CLEAR_COMMANDS) {
            pluginAction = "clear-commands";
            payload.addProperty("placement", placement(id));
        } else if (action == ACTION_SET_GROUP) {
            pluginAction = "set-podium-group";
            payload.addProperty("placement", placement(id));
            payload.addProperty("group", value);
        } else if (action == ACTION_BLOCK_PLAYER) {
            GameProfile profile = resolveProfile(player.getServer(), value);
            if (profile == null || profile.getId() == null) return failure("That player has not joined this server before.");
            pluginAction = "block-player";
            payload.addProperty("uuid", profile.getId().toString());
            payload.addProperty("name", profile.getName());
        } else if (action == ACTION_UNBLOCK_PLAYER) {
            pluginAction = "unblock-player";
            payload.addProperty("uuid", id);
        } else if (action == ACTION_RELOAD) {
            pluginAction = "reload";
        } else {
            return failure("Unknown ranked season administration action.");
        }
        String[] response = RankedPluginBridge.applyAction(player, pluginAction, gson.toJson(payload));
        refreshRuntime(true);
        if (RankedPluginBridge.successful(response)
                && ("block-player".equals(pluginAction) || "unblock-player".equals(pluginAction))) {
            syncPodium(net.rebornaddon.ranked.RankedSystem.eloManager, true);
        }
        return response;
    }

    public String[] endSeason(ICommandSender sender, MinecraftServer server) {
        if (server == null) return failure("The Minecraft server is unavailable.");
        JsonArray standings = new JsonArray();
        List<PlayerStats> all = new ArrayList<PlayerStats>(net.rebornaddon.ranked.RankedSystem.eloManager.getAllStats());
        Collections.sort(all, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats left, PlayerStats right) {
                return Integer.compare(right.elo, left.elo);
            }
        });
        for (PlayerStats stats : all) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", stats.uuid.toString());
            entry.addProperty("name", stats.name);
            entry.addProperty("elo", stats.elo);
            standings.add(entry);
        }
        String[] response = RankedPluginBridge.endSeason(sender, gson.toJson(standings));
        if (!RankedPluginBridge.successful(response)) return response;
        String token = "";
        try {
            token = new JsonParser().parse(RankedPluginBridge.payload(response)).getAsJsonObject()
                    .get("resetToken").getAsString();
        } catch (RuntimeException ignored) {
        }
        net.rebornaddon.ranked.RankedSystem.eloManager.hardResetAllForNewSeason();
        if (!token.isEmpty()) RankedPluginBridge.confirmReset(token);
        refreshRuntime(true);
        syncPodium(net.rebornaddon.ranked.RankedSystem.eloManager, true);
        for (EntityPlayerMP online : server.getPlayerList().getPlayers()) deliverPendingRewards(online);
        return response;
    }

    public void reconcilePendingReset(EloManager eloManager) {
        refreshRuntime(true);
        if (!pluginActive || !runtime.resetPending || runtime.resetToken.isEmpty() || eloManager == null) return;
        eloManager.hardResetAllForNewSeason();
        RankedPluginBridge.confirmReset(runtime.resetToken);
        refreshRuntime(true);
        syncPodium(eloManager, true);
    }

    public void deliverPendingRewards(EntityPlayerMP player) {
        if (player == null || !RankedPluginBridge.isAvailable()) return;
        String[] response = RankedPluginBridge.pendingRewards(player.getUniqueID());
        if (!RankedPluginBridge.successful(response)) return;
        try {
            JsonArray values = new JsonParser().parse(RankedPluginBridge.payload(response)).getAsJsonArray();
            int delivered = 0;
            for (JsonElement element : values) {
                if (!element.isJsonObject()) continue;
                JsonObject reward = element.getAsJsonObject();
                String rewardId = string(reward, "id");
                if (rewardId.isEmpty()) continue;
                if (hasDeliveredReward(player, rewardId)) {
                    RankedPluginBridge.acknowledgeReward(player.getUniqueID(), rewardId);
                    continue;
                }
                ItemStack stack = itemFromBase64(string(reward, "item"));
                if (stack.isEmpty()) continue;
                stack.getOrCreateSubCompound("RebornAddon").setString("RankedRewardId", rewardId);
                if (!canFit(player, stack)) continue;
                ItemStack remaining = stack.copy();
                player.inventory.addItemStackToInventory(remaining);
                if (!remaining.isEmpty()) continue;
                player.getServerWorld().getSaveHandler().getPlayerNBTManager().writePlayerData(player);
                if (RankedPluginBridge.successful(RankedPluginBridge.acknowledgeReward(
                        player.getUniqueID(), rewardId))) delivered++;
            }
            if (delivered > 0) {
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.GOLD + "Received " + delivered
                                + " ranked season reward" + (delivered == 1 ? "." : "s.")));
            }
        } catch (RuntimeException ignored) {
        }
    }

    public void startNewSeason(Long customDurationMillis) {
        if (pluginActive) return;
        seasonNumber++;
        seasonStartMillis = System.currentTimeMillis();
        if (customDurationMillis != null) seasonDurationMillis = customDurationMillis.longValue();
        save();
    }

    public void setReward(String tierName, ItemStack item) {
        if (pluginActive || tierName == null || item == null || item.isEmpty()) return;
        tierRewards.put(tierName, itemToBase64(item));
        save();
    }

    public boolean hasReward(String tierName) {
        return tierRewards.containsKey(tierName);
    }

    public Map<String, String> getConfiguredTierNames() {
        return tierRewards;
    }

    public void endSeasonAndDistribute(EloManager eloManager, BiConsumer<UUID, ItemStack> granter,
                                       Predicate<UUID> isOnline, long nextDuration) {
        if (pluginActive) return;
        List<PlayerStats> top = eloManager.getTop(10);
        SeasonRecord record = new SeasonRecord();
        record.seasonNumber = seasonNumber;
        record.endedAtMillis = System.currentTimeMillis();
        for (PlayerStats stats : top) {
            record.topPlayerNames.add(stats.name);
            record.topPlayerElos.add(Integer.valueOf(stats.elo));
        }
        history.add(record);
        for (PlayerStats stats : eloManager.getAllStats()) {
            String encoded = tierRewards.get(RankTier.forElo(stats.elo));
            ItemStack reward = itemFromBase64(encoded);
            if (reward.isEmpty()) continue;
            if (isOnline.test(stats.uuid)) granter.accept(stats.uuid, reward);
            else queuePendingReward(stats.uuid, reward);
        }
        eloManager.hardResetAllForNewSeason();
        startNewSeason(Long.valueOf(nextDuration));
    }

    public void queuePendingReward(UUID uuid, ItemStack item) {
        if (pluginActive || uuid == null || item == null || item.isEmpty()) return;
        List<String> values = pendingRewards.get(uuid.toString());
        if (values == null) {
            values = new ArrayList<String>();
            pendingRewards.put(uuid.toString(), values);
        }
        values.add(itemToBase64(item));
        save();
    }

    public List<ItemStack> takePendingRewards(UUID uuid) {
        if (pluginActive || uuid == null) return Collections.emptyList();
        List<String> stored = pendingRewards.remove(uuid.toString());
        if (stored == null) return Collections.emptyList();
        save();
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (String encoded : stored) {
            ItemStack item = itemFromBase64(encoded);
            if (!item.isEmpty()) result.add(item);
        }
        return result;
    }

    public List<SeasonRecord> getHistory() {
        return history;
    }

    private long fallbackRemaining() {
        return Math.max(0L, seasonDurationMillis - (System.currentTimeMillis() - seasonStartMillis));
    }

    private RuntimeState parseRuntime(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            RuntimeState value = new RuntimeState();
            value.seasonNumber = integer(root, "seasonNumber", 1);
            value.displayName = string(root, "displayName");
            value.state = string(root, "state");
            value.durationMillis = number(root, "durationMillis", seasonDurationMillis);
            value.remainingMillis = number(root, "remainingMillis", 0L);
            value.leaderboardEnabled = bool(root, "leaderboardEnabled", true);
            value.resetPending = bool(root, "resetPending", false);
            value.resetToken = string(root, "resetToken");
            for (JsonElement element : array(root, "blockedPlayers")) {
                if (element.isJsonObject()) value.blockedPlayers.add(string(element.getAsJsonObject(), "uuid"));
            }
            return value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static GameProfile resolveProfile(MinecraftServer server, String name) {
        if (server == null || name == null || name.trim().isEmpty()) return null;
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name.trim());
        if (online != null) return online.getGameProfile();
        return server.getPlayerProfileCache().getGameProfileForUsername(name.trim());
    }

    private static boolean canFit(EntityPlayerMP player, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack slot : player.inventory.mainInventory) {
            if (slot.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            } else if (ItemStack.areItemsEqual(slot, stack) && ItemStack.areItemStackTagsEqual(slot, stack)) {
                remaining -= Math.max(0, Math.min(slot.getMaxStackSize(), player.inventory.getInventoryStackLimit())
                        - slot.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean hasDeliveredReward(EntityPlayerMP player, String rewardId) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.isEmpty() || !stack.hasTagCompound()) continue;
            NBTTagCompound root = stack.getTagCompound();
            if (root != null && root.hasKey("RebornAddon", 10)
                    && rewardId.equals(root.getCompoundTag("RebornAddon").getString("RankedRewardId"))) {
                return true;
            }
        }
        return false;
    }

    private static int placement(String value) {
        try {
            int result = Integer.parseInt(value == null ? "" : value.trim());
            return result >= 1 && result <= 100 ? result : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static Long whole(String value) {
        try { return Long.valueOf(value == null ? "" : value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String itemToBase64(ItemStack item) {
        try {
            NBTTagCompound tag = new NBTTagCompound();
            item.writeToNBT(tag);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            return "";
        }
    }

    private static ItemStack itemFromBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) return ItemStack.EMPTY;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return new ItemStack(CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes)));
        } catch (Exception exception) {
            return ItemStack.EMPTY;
        }
    }

    private static NBTTagList strings(JsonArray values) {
        NBTTagList list = new NBTTagList();
        for (JsonElement value : values) if (value.isJsonPrimitive()) list.appendTag(new NBTTagString(value.getAsString()));
        return list;
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try { return object.get(name).getAsInt(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static long number(JsonObject object, String name, long fallback) {
        try { return object.get(name).getAsLong(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        try { return object.get(name).getAsBoolean(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String[] failure(String message) {
        return new String[] {"false", message, ""};
    }

    private void load() {
        if (!dataFile.isFile()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<SaveData>() { }.getType();
            SaveData data = gson.fromJson(reader, type);
            if (data == null) return;
            seasonNumber = Math.max(1, data.seasonNumber);
            seasonStartMillis = data.seasonStartMillis;
            if (data.seasonDurationMillis > 0L) seasonDurationMillis = data.seasonDurationMillis;
            if (data.tierRewards != null) tierRewards = data.tierRewards;
            if (data.pendingRewards != null) pendingRewards = data.pendingRewards;
            if (data.history != null) history = data.history;
        } catch (IOException ignored) {
        }
    }

    public synchronized void save() {
        SaveData data = new SaveData();
        data.seasonNumber = seasonNumber;
        data.seasonStartMillis = seasonStartMillis;
        data.seasonDurationMillis = seasonDurationMillis;
        data.tierRewards = tierRewards;
        data.pendingRewards = pendingRewards;
        data.history = history;
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(data, writer);
        } catch (IOException ignored) {
        }
    }

    private static final class RuntimeState {
        private int seasonNumber = 1;
        private String displayName = "Season 1";
        private String state = "running";
        private long durationMillis;
        private long remainingMillis;
        private boolean leaderboardEnabled = true;
        private boolean resetPending;
        private String resetToken = "";
        private final Set<String> blockedPlayers = new HashSet<String>();
    }
}
