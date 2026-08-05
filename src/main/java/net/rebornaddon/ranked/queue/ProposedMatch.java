package net.rebornaddon.ranked.queue;

import net.rebornaddon.ranked.match.MatchMode;

import java.util.List;

public class ProposedMatch {
    private final MatchMode mode;
    private final List<QueuedPlayer> team0;
    private final List<QueuedPlayer> team1;
    private final List<QueuedGroup> team0Groups;
    private final List<QueuedGroup> team1Groups;

    public ProposedMatch(MatchMode mode, List<QueuedPlayer> team0, List<QueuedPlayer> team1,
                          List<QueuedGroup> team0Groups, List<QueuedGroup> team1Groups) {
        this.mode = mode;
        this.team0 = team0;
        this.team1 = team1;
        this.team0Groups = team0Groups;
        this.team1Groups = team1Groups;
    }

    public MatchMode getMode() { return mode; }
    public List<QueuedPlayer> getTeam0() { return team0; }
    public List<QueuedPlayer> getTeam1() { return team1; }

    /** The original groups (solo entries and/or intact parties) that made up each team -
     *  used when a match fails to actually start (e.g. no free arena) so re-queueing can
     *  put parties back together atomically instead of flattening them to solo entries. */
    public List<QueuedGroup> getTeam0Groups() { return team0Groups; }
    public List<QueuedGroup> getTeam1Groups() { return team1Groups; }
}
