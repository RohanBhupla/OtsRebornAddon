package net.rebornaddon.ranked.match;

import net.minecraft.item.ItemStack;
import net.rebornaddon.ranked.RankedLocation;
import net.rebornaddon.ranked.arena.Arena;

import java.util.*;

public class Match {

    public enum State { COUNTDOWN, ACTIVE, ENDED }

    private final UUID matchId = UUID.randomUUID();
    private final MatchMode mode;
    private final Arena arena;

    private final List<UUID> team0;
    private final List<UUID> team1;

    private final Set<UUID> alive = new HashSet<>();

    // Snapshot of pre-match state so we can restore it afterwards.
    private final Map<UUID, RankedLocation> originalLocations = new HashMap<>();
    private final Map<UUID, ItemStack[]> originalInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> originalArmor = new HashMap<>();
    private final Map<UUID, Float> originalHealth = new HashMap<>();
    private final Map<UUID, Integer> originalFood = new HashMap<>();
    private final Map<UUID, Float> originalSaturation = new HashMap<>();

    private State state = State.COUNTDOWN;
    private int timeRemainingSeconds;

    public Match(MatchMode mode, Arena arena, List<UUID> team0, List<UUID> team1, int timeLimitSeconds) {
        this.mode = mode;
        this.arena = arena;
        this.team0 = team0;
        this.team1 = team1;
        this.timeRemainingSeconds = timeLimitSeconds;
        alive.addAll(team0);
        alive.addAll(team1);
    }

    public UUID getMatchId() { return matchId; }
    public MatchMode getMode() { return mode; }
    public Arena getArena() { return arena; }
    public List<UUID> getTeam0() { return team0; }
    public List<UUID> getTeam1() { return team1; }
    public List<UUID> getTeam(int index) { return index == 0 ? team0 : team1; }

    public int getTeamOf(UUID uuid) {
        if (team0.contains(uuid)) return 0;
        if (team1.contains(uuid)) return 1;
        return -1;
    }

    public Set<UUID> getAlive() { return alive; }

    public void eliminate(UUID uuid) {
        alive.remove(uuid);
    }

    public boolean isAlive(UUID uuid) {
        return alive.contains(uuid);
    }

    public int countAlive(int team) {
        int count = 0;
        for (UUID u : getTeam(team)) {
            if (alive.contains(u)) count++;
        }
        return count;
    }

    public List<UUID> allPlayers() {
        List<UUID> all = new ArrayList<>(team0);
        all.addAll(team1);
        return all;
    }

    public void saveOriginalLocation(UUID uuid, RankedLocation loc) { originalLocations.put(uuid, loc); }
    public void saveOriginalInventory(UUID uuid, ItemStack[] inv) { originalInventories.put(uuid, inv); }
    public void saveOriginalArmor(UUID uuid, ItemStack[] armor) { originalArmor.put(uuid, armor); }
    public void saveOriginalHealth(UUID uuid, float health) { originalHealth.put(uuid, health); }
    public void saveOriginalFood(UUID uuid, int food) { originalFood.put(uuid, food); }
    public void saveOriginalSaturation(UUID uuid, float saturation) { originalSaturation.put(uuid, saturation); }

    public RankedLocation getOriginalLocation(UUID uuid) { return originalLocations.get(uuid); }
    public ItemStack[] getOriginalInventory(UUID uuid) { return originalInventories.get(uuid); }
    public ItemStack[] getOriginalArmor(UUID uuid) { return originalArmor.get(uuid); }
    public Float getOriginalHealth(UUID uuid) { return originalHealth.get(uuid); }
    public Integer getOriginalFood(UUID uuid) { return originalFood.get(uuid); }
    public Float getOriginalSaturation(UUID uuid) { return originalSaturation.get(uuid); }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public int getTimeRemainingSeconds() { return timeRemainingSeconds; }
    public void decrementTime() { timeRemainingSeconds--; }
}
