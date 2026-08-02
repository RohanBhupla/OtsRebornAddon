package net.rebornaddon.ranked.elo;

import java.util.UUID;

public class PlayerStats {
    public UUID uuid;
    public String name;
    public int elo;
    public int wins;
    public int losses;
    public int draws;
    public int currentWinStreak;
    public int peakElo;

    // Gson needs a no-arg constructor
    public PlayerStats() {}

    public PlayerStats(UUID uuid, String name, int elo, int wins, int losses, int draws) {
        this.uuid = uuid;
        this.name = name;
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.currentWinStreak = 0;
        this.peakElo = elo;
    }

    public void incrementWins() { wins++; }
    public void incrementLosses() { losses++; }
    public void incrementDraws() { draws++; }
}
