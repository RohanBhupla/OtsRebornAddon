package net.rebornaddon.ranked.queue;

import net.rebornaddon.ranked.match.MatchMode;

import java.util.*;

public class QueueManager {

    private final Map<MatchMode, List<QueuedPlayer>> queues = new EnumMap<>(MatchMode.class);

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
        for (List<QueuedPlayer> queue : queues.values()) {
            for (QueuedPlayer qp : queue) {
                if (qp.getUuid().equals(uuid)) return true;
            }
        }
        return false;
    }

    public MatchMode getQueuedMode(UUID uuid) {
        for (Map.Entry<MatchMode, List<QueuedPlayer>> e : queues.entrySet()) {
            for (QueuedPlayer qp : e.getValue()) {
                if (qp.getUuid().equals(uuid)) return e.getKey();
            }
        }
        return null;
    }

    public void join(MatchMode mode, UUID uuid, String name, int elo) {
        leaveAll(uuid);
        queues.get(mode).add(new QueuedPlayer(uuid, name, elo));
    }

    public void leaveAll(UUID uuid) {
        for (List<QueuedPlayer> queue : queues.values()) {
            queue.removeIf(qp -> qp.getUuid().equals(uuid));
        }
    }

    public int getQueueSize(MatchMode mode) {
        return queues.get(mode).size();
    }

    private int toleranceFor(QueuedPlayer qp) {
        long waited = qp.getSecondsWaited();
        int grown = (int) (waited / toleranceIntervalSeconds) * toleranceGrowth;
        return Math.min(maxTolerance, baseTolerance + grown);
    }

    /**
     * Scans every mode's queue for a group of players close enough in ELO to form
     * a balanced match, removes them from the queue, and returns the proposals.
     * Call periodically (e.g. every few seconds) on the server thread.
     */
    public List<ProposedMatch> tick() {
        List<ProposedMatch> results = new ArrayList<>();

        for (MatchMode mode : MatchMode.values()) {
            int needed = mode.getTotalPlayers();
            List<QueuedPlayer> queue = queues.get(mode);
            if (queue.size() < needed) continue;

            List<QueuedPlayer> sorted = new ArrayList<>(queue);
            sorted.sort(Comparator.comparingInt(QueuedPlayer::getElo));

            boolean formed = true;
            while (formed && sorted.size() >= needed) {
                formed = false;
                for (int i = 0; i + needed <= sorted.size(); i++) {
                    List<QueuedPlayer> window = sorted.subList(i, i + needed);
                    int minElo = window.get(0).getElo();
                    int maxElo = window.get(window.size() - 1).getElo();
                    int diff = maxElo - minElo;

                    int maxToleranceInWindow = window.stream()
                            .mapToInt(this::toleranceFor)
                            .max().orElse(baseTolerance);

                    if (diff <= maxToleranceInWindow) {
                        List<QueuedPlayer> group = new ArrayList<>(window);
                        results.add(buildProposal(mode, group));

                        sorted.subList(i, i + needed).clear();
                        for (QueuedPlayer qp : group) {
                            queue.removeIf(q -> q.getUuid().equals(qp.getUuid()));
                        }
                        formed = true;
                        break;
                    }
                }
            }
        }

        return results;
    }

    /** Sorts by ELO descending and alternates picks between teams, keeping both teams'
     *  total ELO close regardless of team size. */
    private ProposedMatch buildProposal(MatchMode mode, List<QueuedPlayer> group) {
        group.sort(Comparator.comparingInt(QueuedPlayer::getElo).reversed());

        List<QueuedPlayer> team0 = new ArrayList<>();
        List<QueuedPlayer> team1 = new ArrayList<>();

        for (int i = 0; i < group.size(); i++) {
            if (i % 2 == 0) team0.add(group.get(i)); else team1.add(group.get(i));
        }

        return new ProposedMatch(mode, team0, team1);
    }
}
