package net.rebornaddon.data;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.ranked.network.RankedSyncMessage;

import java.util.ArrayList;
import java.util.List;


@SideOnly(Side.CLIENT)
public final class RankedClientData {

    private RankedClientData() {}

    private static volatile int elo = 1000;
    private static volatile int wins = 0;
    private static volatile int losses = 0;
    private static volatile int draws = 0;
    private static volatile int matchState = 0;
    private static volatile List<LeaderboardEntry> leaderboard = new ArrayList<>();

    public static class LeaderboardEntry {
        public final String playerName;
        public final int elo;

        public LeaderboardEntry(String playerName, int elo) {
            this.playerName = playerName;
            this.elo = elo;
        }
    }

    public static void update(RankedSyncMessage msg) {
        elo = msg.getElo();
        wins = msg.getWins();
        losses = msg.getLosses();
        draws = msg.getDraws();
        matchState = msg.getMatchState();

        List<LeaderboardEntry> entries = new ArrayList<>();
        List<String> names = msg.getLeaderboardNames();
        List<Integer> elos = msg.getLeaderboardElos();
        for (int i = 0; i < names.size(); i++) {
            entries.add(new LeaderboardEntry(names.get(i), elos.get(i)));
        }
        leaderboard = entries;
    }

    public static int getElo() { return elo; }
    public static int getWins() { return wins; }
    public static int getLosses() { return losses; }
    public static int getDraws() { return draws; }
    public static boolean amIInMatch() { return matchState == 2; }
    public static boolean amIQueued() { return matchState == 1; }
    public static List<LeaderboardEntry> getLeaderboard(int limit) {
        List<LeaderboardEntry> current = leaderboard;
        if (current.size() > limit) return current.subList(0, limit);
        return current;
    }
}
