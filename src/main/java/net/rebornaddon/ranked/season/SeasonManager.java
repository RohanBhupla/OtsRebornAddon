package net.rebornaddon.ranked.season;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.rebornaddon.ranked.elo.EloManager;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.elo.RankTier;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Handles season lifecycle (start/end/timer), tier-based reward configuration, and
 * queuing rewards for players who are offline when a season actually ends. Items are
 * persisted as Base64-encoded binary NBT (the same format vanilla uses for player/chunk
 * data) rather than SNBT text, since that's the API surface I'm most confident is exactly
 * right in 1.12.2 without being able to compile-test this myself.
 */
public class SeasonManager {

    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private int seasonNumber = 1;
    private long seasonStartMillis = System.currentTimeMillis();
    private long seasonDurationMillis;

    // tierName -> Base64 NBT of the reward ItemStack for that tier
    private Map<String, String> tierRewards = new HashMap<>();

    // playerUUID.toString() -> list of Base64 NBT items waiting to be handed over
    private Map<String, List<String>> pendingRewards = new HashMap<>();

    private List<SeasonRecord> history = new ArrayList<>();

    public static class SeasonRecord {
        public int seasonNumber;
        public long endedAtMillis;
        public List<String> topPlayerNames = new ArrayList<>();
        public List<Integer> topPlayerElos = new ArrayList<>();
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
        this.seasonDurationMillis = defaultDurationMillis;
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "season.json");
        load();
    }

    public int getSeasonNumber() { return seasonNumber; }
    public long getSeasonStartMillis() { return seasonStartMillis; }
    public long getSeasonDurationMillis() { return seasonDurationMillis; }

    public long getTimeRemainingMillis() {
        long elapsed = System.currentTimeMillis() - seasonStartMillis;
        return Math.max(0, seasonDurationMillis - elapsed);
    }

    public boolean isSeasonExpired() {
        return getTimeRemainingMillis() <= 0;
    }

    /** Starts a fresh season clock. Doesn't touch ELO/rewards by itself - call
     *  endSeasonAndDistribute() first if a season is actually concluding. */
    public void startNewSeason(Long customDurationMillis) {
        seasonNumber++;
        seasonStartMillis = System.currentTimeMillis();
        if (customDurationMillis != null) seasonDurationMillis = customDurationMillis;
        save();
    }

    /** Registers the given item as the reward for a rank tier. Call this holding the
     *  item you want, so the exact stack (including any NBT/enchants) gets captured. */
    public void setReward(String tierName, ItemStack item) {
        tierRewards.put(tierName, itemToBase64(item));
        save();
    }

    public boolean hasReward(String tierName) {
        return tierRewards.containsKey(tierName);
    }

    public Map<String, String> getConfiguredTierNames() {
        return tierRewards;
    }

    /**
     * The full season-end flow: for every tracked player, determine their final rank
     * tier, queue (or directly grant, if online) the configured reward, record a
     * leaderboard snapshot into history, hard-reset every player's ELO/streak, then
     * start the next season's clock automatically.
     *
     * @param eloManager the live ELO manager, so this can both read final standings
     *                    and perform the reset
     * @param onlinePlayerGranter callback invoked for an ONLINE player with the reward
     *                            to give them directly - kept as a callback rather than
     *                            importing EntityPlayerMP handling here, so this class
     *                            doesn't need to know how to look up "is this player online"
     */
    public void endSeasonAndDistribute(EloManager eloManager, java.util.function.BiConsumer<UUID, ItemStack> onlinePlayerGranter,
                                        java.util.function.Predicate<UUID> isOnline, long nextSeasonDurationMillis) {
        List<PlayerStats> allStats = eloManager.getAllStats();

        // Snapshot top 10 for the history record before anything resets.
        List<PlayerStats> top = eloManager.getTop(10);
        SeasonRecord record = new SeasonRecord();
        record.seasonNumber = seasonNumber;
        record.endedAtMillis = System.currentTimeMillis();
        for (PlayerStats s : top) {
            record.topPlayerNames.add(s.name);
            record.topPlayerElos.add(s.elo);
        }
        history.add(record);

        // Distribute rewards based on final tier.
        for (PlayerStats s : allStats) {
            String tier = RankTier.forElo(s.elo);
            String rewardB64 = tierRewards.get(tier);
            if (rewardB64 == null) continue;

            ItemStack reward = itemFromBase64(rewardB64);
            if (reward == null || reward.isEmpty()) continue;

            if (isOnline.test(s.uuid)) {
                onlinePlayerGranter.accept(s.uuid, reward);
            } else {
                queuePendingReward(s.uuid, reward);
            }
        }

        eloManager.hardResetAllForNewSeason();
        startNewSeason(nextSeasonDurationMillis);
    }

    public void queuePendingReward(UUID uuid, ItemStack item) {
        pendingRewards.computeIfAbsent(uuid.toString(), k -> new ArrayList<>()).add(itemToBase64(item));
        save();
    }

    /** Call when a player logs in - returns and clears whatever's waiting for them. */
    public List<ItemStack> takePendingRewards(UUID uuid) {
        List<String> stored = pendingRewards.remove(uuid.toString());
        if (stored == null || stored.isEmpty()) return Collections.emptyList();
        save();

        List<ItemStack> result = new ArrayList<>();
        for (String b64 : stored) {
            ItemStack item = itemFromBase64(b64);
            if (item != null && !item.isEmpty()) result.add(item);
        }
        return result;
    }

    public List<SeasonRecord> getHistory() {
        return history;
    }

    // ------------------------------------------------------------------
    // ItemStack <-> Base64 NBT
    // ------------------------------------------------------------------

    private String itemToBase64(ItemStack item) {
        try {
            NBTTagCompound tag = new NBTTagCompound();
            item.writeToNBT(tag);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private ItemStack itemFromBase64(String b64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            NBTTagCompound tag = CompressedStreamTools.readCompressed(in);
            return new ItemStack(tag);
        } catch (Exception e) {
            e.printStackTrace();
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<SaveData>(){}.getType();
            SaveData data = gson.fromJson(reader, type);
            if (data != null) {
                seasonNumber = data.seasonNumber;
                seasonStartMillis = data.seasonStartMillis;
                if (data.seasonDurationMillis > 0) seasonDurationMillis = data.seasonDurationMillis;
                if (data.tierRewards != null) tierRewards = data.tierRewards;
                if (data.pendingRewards != null) pendingRewards = data.pendingRewards;
                if (data.history != null) history = data.history;
            }
        } catch (IOException e) {
            e.printStackTrace();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
