package net.rebornaddon.ranked.queue;

import net.rebornaddon.ranked.match.MatchMode;

import java.util.*;

/**
 * Matchmaking over QueuedGroups (a solo player or an intact 2-3 person party) rather
 * than individual players directly. Groups are never split across teams. Once a match
 * is actually formed, groups get flattened back into plain QueuedPlayer entries for
 * ProposedMatch - parties are purely a queueing-time concept, nothing downstream
 * (MatchManager, Match, the whole match lifecycle) needs to know they exist at all.
 */
public class QueueManager {

    private final Map<MatchMode, List<QueuedGroup>> queues = new EnumMap<>(MatchMode.class);

    private final int baseTolerance;
    private final int toleranceGrowth;
    private final int toleranceIntervalSeconds;
    private final int maxTolerance;

    public QueueManager(int baseTolerance, int toleranceGrowth, int toleranceIntervalSeconds, int maxTolerance) {
        this.baseTolerance = baseTolerance;
        this.toleranceGrowth = toleranceGrowth;
        this.toleranceIntervalSeconds = toleranceIntervalSeconds;
        this.maxTolerance = maxTolerance;
        for (MatchMode mode : MatchMode.values()) {
            queues.put(mode, new ArrayList<>());
        }
    }

    public boolean isQueued(UUID uuid) {
        for (List<QueuedGroup> queue : queues.values()) {
            for (QueuedGroup g : queue) {
                if (g.contains(uuid)) return true;
            }
        }
        return false;
    }

    public MatchMode getQueuedMode(UUID uuid) {
        for (Map.Entry<MatchMode, List<QueuedGroup>> e : queues.entrySet()) {
            for (QueuedGroup g : e.getValue()) {
                if (g.contains(uuid)) return e.getKey();
            }
        }
        return null;
    }

    public void joinSolo(MatchMode mode, UUID uuid, String name, int elo) {
        leaveAll(uuid);
        queues.get(mode).add(QueuedGroup.solo(uuid, name, elo));
    }

    /** Queues an entire pre-formed group (a party) as one atomic unit. Caller is
     *  responsible for having already validated group.size() <= mode.getTeamSize(). */
    public void joinGroup(MatchMode mode, QueuedGroup group) {
        for (UUID uuid : group.getMemberUuids()) {
            leaveAll(uuid);
        }
        queues.get(mode).add(group);
    }

    /** Removes whichever group (solo entry or party) contains this player from
     *  whatever queue they're in - a party can't be partially un-queued, so this
     *  always removes the whole group, not just this one member. */
    public void leaveAll(UUID uuid) {
        for (List<QueuedGroup> queue : queues.values()) {
            queue.removeIf(g -> g.contains(uuid));
        }
    }

    public int getQueueSize(MatchMode mode) {
        int total = 0;
        for (QueuedGroup g : queues.get(mode)) total += g.size();
        return total;
    }

    private int toleranceFor(QueuedGroup g) {
        long waited = g.getSecondsWaited();
        int grown = (int) (waited / toleranceIntervalSeconds) * toleranceGrowth;
        return Math.min(maxTolerance, baseTolerance + grown);
    }

    /**
     * Scans every mode's queue for a set of groups (solo entries and/or parties) that
     * together add up to exactly the players needed, fit within ELO tolerance, and can
     * be split into two teams of the right size without breaking any party apart.
     */
    public List<ProposedMatch> tick() {
        List<ProposedMatch> results = new ArrayList<>();

        for (MatchMode mode : MatchMode.values()) {
            int neededTotal = mode.getTotalPlayers();
            int teamSize = mode.getTeamSize();
            List<QueuedGroup> pool = queues.get(mode);

            boolean formed = true;
            while (formed) {
                formed = tryFormOneMatch(mode, pool, neededTotal, teamSize, results);
            }
        }

        return results;
    }

    private boolean tryFormOneMatch(MatchMode mode, List<QueuedGroup> pool, int neededTotal, int teamSize,
                                     List<ProposedMatch> results) {
        if (pool.isEmpty()) return false;

        List<QueuedGroup> sorted = new ArrayList<>(pool);
        sorted.sort(Comparator.comparingDouble(QueuedGroup::averageElo));

        List<QueuedGroup> combination = findCombination(sorted, neededTotal);
        if (combination == null) return false;

        List<QueuedGroup> team0Groups = new ArrayList<>();
        List<QueuedGroup> team1Groups = new ArrayList<>();
        if (!splitIntoBalancedTeams(combination, teamSize, team0Groups, team1Groups)) {
            // Shouldn't normally happen (findCombination already guarantees a valid split
            // exists in principle), but fail safe rather than crash if it ever does.
            return false;
        }

        pool.removeAll(combination);

        List<QueuedPlayer> team0 = flatten(team0Groups);
        List<QueuedPlayer> team1 = flatten(team1Groups);
        results.add(new ProposedMatch(mode, team0, team1, team0Groups, team1Groups));
        return true;
    }

    /**
     * Finds a subset of groups whose sizes sum to exactly neededTotal and whose ELO
     * spread fits within the most patient group's tolerance. Pool sizes in practice are
     * small (a handful of testers, not hundreds of concurrent queuers), so a
     * straightforward backtracking search is simpler to get right than anything cleverer
     * and costs nothing noticeable performance-wise at this scale.
     */
    private List<QueuedGroup> findCombination(List<QueuedGroup> sorted, int neededTotal) {
        // Try every possible starting point so we don't only ever consider windows
        // anchored at the very cheapest group.
        for (int start = 0; start < sorted.size(); start++) {
            List<QueuedGroup> found = new ArrayList<>();
            if (backtrack(sorted, start, neededTotal, 0, found)) {
                return found;
            }
        }
        return null;
    }

    private boolean backtrack(List<QueuedGroup> sorted, int index, int neededTotal, int currentSize, List<QueuedGroup> chosen) {
        if (currentSize == neededTotal) {
            return withinTolerance(chosen);
        }
        if (currentSize > neededTotal || index >= sorted.size()) {
            return false;
        }

        QueuedGroup candidate = sorted.get(index);

        // Try including this group, if it still fits.
        if (currentSize + candidate.size() <= neededTotal) {
            chosen.add(candidate);
            if (backtrack(sorted, index + 1, neededTotal, currentSize + candidate.size(), chosen)) {
                return true;
            }
            chosen.remove(chosen.size() - 1);
        }

        // Try skipping this group.
        return backtrack(sorted, index + 1, neededTotal, currentSize, chosen);
    }

    private boolean withinTolerance(List<QueuedGroup> chosen) {
        double minElo = chosen.stream().mapToDouble(QueuedGroup::averageElo).min().orElse(1000);
        double maxElo = chosen.stream().mapToDouble(QueuedGroup::averageElo).max().orElse(1000);
        int maxToleranceInGroup = chosen.stream().mapToInt(this::toleranceFor).max().orElse(baseTolerance);
        return (maxElo - minElo) <= maxToleranceInGroup;
    }

    /**
     * Splits the chosen groups into two teams of exactly teamSize each, without ever
     * breaking a group apart, picking whichever valid split minimizes the ELO gap
     * between the two sides. With at most a handful of groups in a combination,
     * enumerating every 2^n split is trivial.
     */
    private boolean splitIntoBalancedTeams(List<QueuedGroup> combination, int teamSize,
                                            List<QueuedGroup> outTeam0, List<QueuedGroup> outTeam1) {
        int n = combination.size();
        double bestDiff = Double.MAX_VALUE;
        List<QueuedGroup> bestTeam0 = null;
        List<QueuedGroup> bestTeam1 = null;

        for (int mask = 0; mask < (1 << n); mask++) {
            List<QueuedGroup> side0 = new ArrayList<>();
            List<QueuedGroup> side1 = new ArrayList<>();
            int size0 = 0;
            for (int i = 0; i < n; i++) {
                QueuedGroup g = combination.get(i);
                if ((mask & (1 << i)) != 0) {
                    side0.add(g);
                    size0 += g.size();
                } else {
                    side1.add(g);
                }
            }
            if (size0 != teamSize) continue;

            double avg0 = side0.stream().flatMap(g -> g.getMemberElos().stream()).mapToInt(Integer::intValue).average().orElse(1000);
            double avg1 = side1.stream().flatMap(g -> g.getMemberElos().stream()).mapToInt(Integer::intValue).average().orElse(1000);
            double diff = Math.abs(avg0 - avg1);

            if (diff < bestDiff) {
                bestDiff = diff;
                bestTeam0 = side0;
                bestTeam1 = side1;
            }
        }

        if (bestTeam0 == null) return false;
        outTeam0.addAll(bestTeam0);
        outTeam1.addAll(bestTeam1);
        return true;
    }

    private List<QueuedPlayer> flatten(List<QueuedGroup> groups) {
        List<QueuedPlayer> result = new ArrayList<>();
        for (QueuedGroup g : groups) {
            for (int i = 0; i < g.size(); i++) {
                result.add(new QueuedPlayer(g.getMemberUuids().get(i), g.getMemberNames().get(i), g.getMemberElos().get(i)));
            }
        }
        return result;
    }
}
