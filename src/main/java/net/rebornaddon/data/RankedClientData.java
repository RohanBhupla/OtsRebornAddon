package net.rebornaddon.data;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.ranked.network.RankedSyncMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of the last RankedSyncMessage received from the server. This is
 * what the hub GUI actually reads - replaces the old scoreboard-polling approach
 * entirely now that a real packet pushes this data directly.
 */
@SideOnly(Side.CLIENT)
public final class RankedClientData {

    private RankedClientData() {}

    private static volatile int elo = 1000;
    private static volatile int wins = 0;
    private static volatile int losses = 0;
    private static volatile int draws = 0;
    private static volatile int winStreak = 0;
    private static volatile int peakElo = 1000;
    private static volatile int matchState = 0; // 0 = idle, 1 = queued, 2 = in a match
    private static volatile int queuedModeNetId = -1;
    private static volatile int seasonNumber = 1;
    private static volatile int seasonDaysRemaining = 0;
    private static volatile String seasonDisplayName = "Season 1";
    private static volatile String seasonState = "running";
    private static volatile boolean leaderboardEnabled = true;
    private static volatile List<LeaderboardEntry> leaderboard = new ArrayList<>();
    private static volatile boolean inParty = false;
    private static volatile boolean isPartyLeader = false;
    private static volatile List<String> partyMemberNames = new ArrayList<>();
    private static volatile String pendingInviteFrom = "";
    private static volatile int partyTeleportCooldownSeconds = 0;
    private static volatile List<String> invitablePlayers = new ArrayList<>();
    private static volatile int revision;

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
        winStreak = msg.getWinStreak();
        peakElo = msg.getPeakElo();
        matchState = msg.getMatchState();
        queuedModeNetId = msg.getQueuedModeNetId();
        seasonNumber = msg.getSeasonNumber();
        seasonDaysRemaining = msg.getSeasonDaysRemaining();
        seasonDisplayName = msg.getSeasonDisplayName();
        seasonState = msg.getSeasonState();
        leaderboardEnabled = msg.isLeaderboardEnabled();
        inParty = msg.isInParty();
        isPartyLeader = msg.isPartyLeader();
        partyMemberNames = new ArrayList<>(msg.getPartyMemberNames());
        pendingInviteFrom = msg.getPendingInviteFrom();
        partyTeleportCooldownSeconds = msg.getPartyTeleportCooldownSeconds();
        invitablePlayers = new ArrayList<>(msg.getOnlinePlayerNames());

        List<LeaderboardEntry> entries = new ArrayList<>();
        List<String> names = msg.getLeaderboardNames();
        List<Integer> elos = msg.getLeaderboardElos();
        for (int i = 0; i < names.size(); i++) {
            entries.add(new LeaderboardEntry(names.get(i), elos.get(i)));
        }
        leaderboard = entries;
        revision++;
    }

    public static int getRevision() { return revision; }

    public static void reset() {
        elo = 1000;
        wins = 0;
        losses = 0;
        draws = 0;
        winStreak = 0;
        peakElo = 1000;
        matchState = 0;
        queuedModeNetId = -1;
        seasonNumber = 1;
        seasonDaysRemaining = 0;
        seasonDisplayName = "Season 1";
        seasonState = "running";
        leaderboardEnabled = true;
        leaderboard = new ArrayList<>();
        inParty = false;
        isPartyLeader = false;
        partyMemberNames = new ArrayList<>();
        pendingInviteFrom = "";
        partyTeleportCooldownSeconds = 0;
        invitablePlayers = new ArrayList<>();
        revision++;
    }

    public static int getElo() { return elo; }
    public static int getWins() { return wins; }
    public static int getLosses() { return losses; }
    public static int getDraws() { return draws; }
    public static int getWinStreak() { return winStreak; }
    public static int getPeakElo() { return peakElo; }
    public static int getSeasonNumber() { return seasonNumber; }
    public static int getSeasonDaysRemaining() { return seasonDaysRemaining; }
    public static String getSeasonDisplayName() { return seasonDisplayName; }
    public static String getSeasonState() { return seasonState; }
    public static boolean isSeasonRunning() { return "running".equals(seasonState); }
    public static boolean isLeaderboardEnabled() { return leaderboardEnabled; }
    public static boolean amIInMatch() { return matchState == 2; }
    public static boolean amIQueued() { return matchState == 1; }

    /** Human-readable label for the currently-queued mode, or null if not queued. */
    public static String getQueuedModeLabel() {
        if (matchState != 1) return null;
        switch (queuedModeNetId) {
            case 0: return "1v1";
            case 1: return "2v2";
            case 2: return "3v3";
            default: return null;
        }
    }

    public static List<LeaderboardEntry> getLeaderboard(int limit) {
        List<LeaderboardEntry> current = leaderboard;
        if (current.size() > limit) return current.subList(0, limit);
        return current;
    }

    public static boolean isInParty() { return inParty; }
    public static boolean isPartyLeader() { return isPartyLeader; }
    public static List<String> getPartyMemberNames() { return partyMemberNames; }

    /** Null if there's no pending invite waiting right now. */
    public static String getPendingInviteFrom() {
        return (pendingInviteFrom == null || pendingInviteFrom.isEmpty()) ? null : pendingInviteFrom;
    }

    public static int getPartyTeleportCooldownSeconds() { return partyTeleportCooldownSeconds; }
    public static List<String> getInvitablePlayers() { return invitablePlayers; }
}
