package net.rebornaddon.ranked.queue;

import net.rebornaddon.ranked.match.MatchMode;

import java.util.List;

public class ProposedMatch {
    private final MatchMode mode;
    private final List<QueuedPlayer> team0;
    private final List<QueuedPlayer> team1;

    public ProposedMatch(MatchMode mode, List<QueuedPlayer> team0, List<QueuedPlayer> team1) {
        this.mode = mode;
        this.team0 = team0;
        this.team1 = team1;
    }

    public MatchMode getMode() { return mode; }
    public List<QueuedPlayer> getTeam0() { return team0; }
    public List<QueuedPlayer> getTeam1() { return team1; }
}
