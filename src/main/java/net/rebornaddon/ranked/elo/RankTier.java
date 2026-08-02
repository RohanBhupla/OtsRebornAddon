package net.rebornaddon.ranked.elo;

/**
 * Maps ELO to a themed rank name. Pure/stateless, so it's safe to call from both
 * the server (no real use there currently) and the client GUI directly, without
 * needing to send the label itself over the network - just the raw ELO number.
 */
public final class RankTier {

    private RankTier() {}

    public static String forElo(int elo) {
        if (elo < 900) return "Academy Student";
        if (elo < 1100) return "Genin";
        if (elo < 1300) return "Chunin";
        if (elo < 1500) return "Jonin";
        if (elo < 1700) return "Elite Jonin";
        return "Kage";
    }

    /** A distinct color per tier so it's visually obvious at a glance, not just text. */
    public static int colorForElo(int elo) {
        if (elo < 900) return 0xFF9E9E9E;   // grey
        if (elo < 1100) return 0xFF8BC34A;  // green
        if (elo < 1300) return 0xFF03A9F4;  // blue
        if (elo < 1500) return 0xFF9C27B0;  // purple
        if (elo < 1700) return 0xFFFF9800;  // orange
        return 0xFFFFD700;                  // gold
    }
}
