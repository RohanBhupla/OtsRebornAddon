package net.rebornaddon.ranked;

/**
 * Forge doesn't have an equivalent to Bukkit's Location class, so this fills that gap:
 * a dimension id (which "world"/dimension) plus exact coordinates and facing. Used
 * everywhere a saved position is needed - arena corners, spawn points, a player's
 * pre-match position to restore them to afterward.
 */
public class RankedLocation {

    public final int dimensionId;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public RankedLocation(int dimensionId, double x, double y, double z, float yaw, float pitch) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public RankedLocation(int dimensionId, double x, double y, double z) {
        this(dimensionId, x, y, z, 0f, 0f);
    }
}
