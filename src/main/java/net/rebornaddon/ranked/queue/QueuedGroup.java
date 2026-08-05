package net.rebornaddon.ranked.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One entry in a mode's queue - either a single solo player (size 1) or an intact
 * party (size 2-3) that must land on the same team together. The matchmaker treats
 * both uniformly; it never needs to know which case it's looking at.
 */
public class QueuedGroup {

    private final List<UUID> memberUuids;
    private final List<String> memberNames;
    private final List<Integer> memberElos;
    private final long queuedAtMillis = System.currentTimeMillis();

    public QueuedGroup(List<UUID> memberUuids, List<String> memberNames, List<Integer> memberElos) {
        this.memberUuids = new ArrayList<>(memberUuids);
        this.memberNames = new ArrayList<>(memberNames);
        this.memberElos = new ArrayList<>(memberElos);
    }

    public static QueuedGroup solo(UUID uuid, String name, int elo) {
        return new QueuedGroup(
                java.util.Collections.singletonList(uuid),
                java.util.Collections.singletonList(name),
                java.util.Collections.singletonList(elo));
    }

    public int size() {
        return memberUuids.size();
    }

    public List<UUID> getMemberUuids() { return memberUuids; }
    public List<String> getMemberNames() { return memberNames; }
    public List<Integer> getMemberElos() { return memberElos; }

    public boolean contains(UUID uuid) {
        return memberUuids.contains(uuid);
    }

    public double averageElo() {
        return memberElos.stream().mapToInt(Integer::intValue).average().orElse(1000);
    }

    public long getSecondsWaited() {
        return (System.currentTimeMillis() - queuedAtMillis) / 1000L;
    }
}
