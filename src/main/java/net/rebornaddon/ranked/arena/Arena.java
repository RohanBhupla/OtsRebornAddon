package net.rebornaddon.ranked.arena;

import net.rebornaddon.ranked.RankedLocation;

import java.util.ArrayList;
import java.util.List;

public class Arena {

    public String id;
    public RankedLocation corner1;
    public RankedLocation corner2;
    public List<RankedLocation> team0Spawns = new ArrayList<>();
    public List<RankedLocation> team1Spawns = new ArrayList<>();

    // Runtime-only, never persisted - resets to false on every server restart, same as before.
    public transient boolean inUse = false;

    // Gson needs a no-arg constructor
    public Arena() {}

    public Arena(String id) {
        this.id = id;
    }

    public List<RankedLocation> getTeamSpawns(int team) {
        return team == 0 ? team0Spawns : team1Spawns;
    }

    public void addSpawn(int team, RankedLocation loc) {
        getTeamSpawns(team).add(loc);
    }

    public void clearSpawns(int team) {
        getTeamSpawns(team).clear();
    }

    public void clearAllSpawns() {
        team0Spawns.clear();
        team1Spawns.clear();
    }

    public boolean isFullyConfigured() {
        return corner1 != null && corner2 != null;
    }

    public boolean canHost(int teamSize) {
        return team0Spawns.size() >= teamSize && team1Spawns.size() >= teamSize;
    }

    /**
     * Checks if a location is within the arena's bounding cuboid. Y bounds get a
     * generous automatic pad (same fix as the original plugin) so gravity jitter or
     * both corners being set at similar heights doesn't cause false boundary triggers.
     */
    public boolean contains(RankedLocation loc) {
        if (corner1 == null || corner2 == null) return false;
        if (loc.dimensionId != corner1.dimensionId) return false;

        double minX = Math.min(corner1.x, corner2.x);
        double maxX = Math.max(corner1.x, corner2.x);
        double minY = Math.min(corner1.y, corner2.y) - 5;
        double maxY = Math.max(corner1.y, corner2.y) + 15;
        double minZ = Math.min(corner1.z, corner2.z);
        double maxZ = Math.max(corner1.z, corner2.z);

        return loc.x >= minX && loc.x <= maxX
                && loc.y >= minY && loc.y <= maxY
                && loc.z >= minZ && loc.z <= maxZ;
    }

    /** Floor-level center point, used for spectator placement mid-match. */
    public RankedLocation getCenter() {
        double x = (corner1.x + corner2.x) / 2.0;
        double y = Math.min(corner1.y, corner2.y) + 1;
        double z = (corner1.z + corner2.z) / 2.0;
        return new RankedLocation(corner1.dimensionId, x, y, z);
    }
}
