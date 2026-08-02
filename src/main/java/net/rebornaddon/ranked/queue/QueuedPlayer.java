package net.rebornaddon.ranked.queue;

import java.util.UUID;

public class QueuedPlayer {
    private final UUID uuid;
    private final String name;
    private final int elo;
    private final long queuedAtMillis;

    public QueuedPlayer(UUID uuid, String name, int elo) {
        this.uuid = uuid;
        this.name = name;
        this.elo = elo;
        this.queuedAtMillis = System.currentTimeMillis();
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getElo() { return elo; }

    public long getSecondsWaited() {
        return (System.currentTimeMillis() - queuedAtMillis) / 1000L;
    }
}
