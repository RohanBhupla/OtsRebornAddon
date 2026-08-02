package net.rebornaddon.ranked;

import net.rebornaddon.ranked.arena.ArenaManager;
import net.rebornaddon.ranked.elo.EloManager;
import net.rebornaddon.ranked.match.MatchManager;
import net.rebornaddon.ranked.queue.QueueManager;

/**
 * Central static holder for the ranked system's manager instances. Set up once in
 * CommonProxy when the server starts, torn down on server stop. Both the network
 * message handlers and the tick/event listener classes reach the managers through
 * this rather than each needing their own reference threaded through constructors.
 */
public final class RankedSystem {

    private RankedSystem() {}

    public static ArenaManager arenaManager;
    public static EloManager eloManager;
    public static QueueManager queueManager;
    public static MatchManager matchManager;

    public static boolean isReady() {
        return arenaManager != null && eloManager != null && queueManager != null && matchManager != null;
    }
}
