package net.rebornaddon.ranked.elo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Persists ELO/W-L-D to a JSON file (Gson ships with Minecraft itself, so this needs
 * no extra dependency or shading - unlike the original Bukkit plugin, which used SQLite).
 * Simpler is safer here given this whole rewrite can't be compile-tested locally.
 */
public class EloManager {

    private final File dataFile;
    private final int startingElo;
    private final int kFactor;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Keyed by UUID.toString() - Gson doesn't handle a raw UUID map key cleanly, so this
    // sidesteps that entirely rather than writing a custom TypeAdapter.
    private Map<String, PlayerStats> stats = new HashMap<>();

    public EloManager(File dataFolder, int startingElo, int kFactor) {
        this.startingElo = startingElo;
        this.kFactor = kFactor;
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "elo.json");
        load();
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, PlayerStats>>(){}.getType();
            Map<String, PlayerStats> loaded = gson.fromJson(reader, type);
            if (loaded != null) this.stats = loaded;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void saveAll() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(stats, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized PlayerStats getStats(UUID uuid, String currentName) {
        PlayerStats s = stats.get(uuid.toString());
        if (s == null) {
            s = new PlayerStats(uuid, currentName, startingElo, 0, 0, 0);
            stats.put(uuid.toString(), s);
            saveAll();
        } else {
            s.name = currentName;
            // Defensive: if this record predates peakElo tracking, it'll have
            // deserialized to 0 - self-correct rather than showing a wrong peak.
            if (s.peakElo < s.elo) s.peakElo = s.elo;
        }
        return s;
    }

    public synchronized List<PlayerStats> getTop(int limit) {
        List<PlayerStats> all = new ArrayList<>(stats.values());
        all.sort((a, b) -> Integer.compare(b.elo, a.elo));
        if (all.size() > limit) return all.subList(0, limit);
        return all;
    }

    /**
     * Applies ELO changes for a completed match between two teams, same formula as the
     * original plugin: each team's average ELO acts as its rating, standard logistic
     * expected-score formula, delta split equally across each team's members.
     *
     * @param team0Result 1.0 = team0 won, 0.0 = team0 lost, 0.5 = draw
     */
    public synchronized void applyMatchResult(List<PlayerStats> team0, List<PlayerStats> team1, double team0Result) {
        double avg0 = average(team0);
        double avg1 = average(team1);

        double expected0 = 1.0 / (1.0 + Math.pow(10, (avg1 - avg0) / 400.0));
        double expected1 = 1.0 - expected0;
        double team1Result = 1.0 - team0Result;

        int delta0 = (int) Math.round(kFactor * (team0Result - expected0));
        int delta1 = (int) Math.round(kFactor * (team1Result - expected1));

        applyDelta(team0, delta0, team0Result);
        applyDelta(team1, delta1, team1Result);
        saveAll();
    }

    private void applyDelta(List<PlayerStats> team, int delta, double result) {
        for (PlayerStats s : team) {
            s.elo = Math.max(0, s.elo + delta);
            if (result == 1.0) {
                s.incrementWins();
                s.currentWinStreak++;
            } else if (result == 0.0) {
                s.incrementLosses();
                s.currentWinStreak = 0;
            } else {
                s.incrementDraws();
                // Draws don't extend a streak, but don't reset it either - a
                // common convention, and gentler than punishing a close match.
            }
            if (s.elo > s.peakElo) s.peakElo = s.elo;
        }
    }

    private double average(List<PlayerStats> team) {
        return team.stream().mapToInt(s -> s.elo).average().orElse(startingElo);
    }
}
