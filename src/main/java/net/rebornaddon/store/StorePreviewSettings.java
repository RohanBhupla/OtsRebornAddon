package net.rebornaddon.store;

import net.minecraft.server.MinecraftServer;

public final class StorePreviewSettings {
    private static int buildingLockOverride = -1;

    private StorePreviewSettings() {
    }

    public static boolean buildingLocked(MinecraftServer server, boolean operator) {
        if (server != null && server.isSinglePlayer() && buildingLockOverride >= 0) {
            return buildingLockOverride == 1;
        }
        return !operator;
    }

    public static void setBuildingLock(MinecraftServer server, int value) {
        if (server != null && server.isSinglePlayer()) {
            buildingLockOverride = value < 0 ? -1 : value > 0 ? 1 : 0;
        }
    }

    public static String buildingLockState() {
        return buildingLockOverride < 0 ? "default" : buildingLockOverride > 0 ? "locked" : "unlocked";
    }

    public static void reset() {
        buildingLockOverride = -1;
    }
}
