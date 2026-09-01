package net.rebornaddon.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.narutomod.entity.EntityBijuManager;
import net.narutomod.entity.EntityBijuManager2;

public final class BijuHuntCompatibility {
    private BijuHuntCompatibility() {
    }

    public static boolean isJinchuriki(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            if (EntityBijuManager.isJinchuriki(player)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return EntityBijuManager2.isJinchuriki(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
