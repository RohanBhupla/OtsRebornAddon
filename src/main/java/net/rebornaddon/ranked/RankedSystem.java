package net.rebornaddon.ranked;

import net.rebornaddon.ranked.arena.ArenaManager;
import net.rebornaddon.ranked.elo.EloManager;
import net.rebornaddon.ranked.match.MatchManager;
import net.rebornaddon.ranked.party.PartyManager;
import net.rebornaddon.ranked.queue.QueueManager;
import net.rebornaddon.ranked.season.SeasonManager;


public final class RankedSystem {

    private RankedSystem() {}

    public static ArenaManager arenaManager;
    public static EloManager eloManager;
    public static QueueManager queueManager;
    public static MatchManager matchManager;
    public static SeasonManager seasonManager;
    public static PartyManager partyManager;

    public static boolean isReady() {
        return arenaManager != null && eloManager != null && queueManager != null
                && matchManager != null && seasonManager != null && partyManager != null;
    }
}
