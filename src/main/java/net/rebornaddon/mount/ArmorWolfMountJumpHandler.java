package net.rebornaddon.mount;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

import java.lang.reflect.Method;

/** Applies validated rider jumps to Armor Wolf's ground-mount wrapper. */
public final class ArmorWolfMountJumpHandler {
    private static final String LAST_JUMP_TICK = "RebornMountJumpTick";
    private static final String PENDING_JUMP_UNTIL_TICK = "RebornMountJumpPendingUntil";
    private static final int MINIMUM_JUMP_INTERVAL_TICKS = 4;
    private static final int JUMP_BUFFER_TICKS = 6;
    // Armor Wolf exposes no usable rider-jump implementation. A vanilla 0.42 impulse is
    // especially ineffective on its larger mount forms, so mounts use a deliberate high jump.
    private static final double BASE_JUMP_VELOCITY = 0.80D;

    private ArmorWolfMountJumpHandler() {
    }

    public static void tryJump(EntityPlayerMP player) {
        if (player == null || player.world == null || player.world.isRemote) return;
        Entity mount = controlledMount(player);
        if (mount == null || mount.isInLava()) return;
        NBTTagCompound data = mount.getEntityData();
        data.setInteger(PENDING_JUMP_UNTIL_TICK, mount.ticksExisted + JUMP_BUFFER_TICKS);
        if (applyValidatedJump(player)) data.removeTag(PENDING_JUMP_UNTIL_TICK);
    }

    public static void retryPendingJump(EntityPlayerMP player) {
        if (player == null || player.world == null || player.world.isRemote) return;
        Entity mount = controlledMount(player);
        if (mount == null) return;
        NBTTagCompound data = mount.getEntityData();
        if (!data.hasKey(PENDING_JUMP_UNTIL_TICK, 3)) return;
        if (mount.ticksExisted > data.getInteger(PENDING_JUMP_UNTIL_TICK)
                || mount.isInLava()) {
            data.removeTag(PENDING_JUMP_UNTIL_TICK);
            return;
        }
        if (applyValidatedJump(player)) data.removeTag(PENDING_JUMP_UNTIL_TICK);
    }

    /** Mirrors the validated impulse on the client that controls vehicle movement in 1.12. */
    public static boolean tryClientJump(EntityPlayer player) {
        return player != null && player.world != null && player.world.isRemote
                && applyValidatedJump(player);
    }

    private static boolean applyValidatedJump(EntityPlayer player) {
        Entity mount = controlledMount(player);
        if (mount == null || !hasGroundSupport(mount) || mount.isInLava()) {
            return false;
        }

        NBTTagCompound data = mount.getEntityData();
        int tick = mount.ticksExisted;
        if (data.hasKey(LAST_JUMP_TICK, 3)) {
            int elapsed = tick - data.getInteger(LAST_JUMP_TICK);
            if (elapsed >= 0 && elapsed < MINIMUM_JUMP_INTERVAL_TICKS) return false;
        }

        mount.motionY = jumpVelocity(customJumpHeight(mount));
        mount.fallDistance = 0.0F;
        mount.isAirBorne = true;
        mount.velocityChanged = true;
        data.setInteger(LAST_JUMP_TICK, tick);
        return true;
    }

    private static Entity controlledMount(EntityPlayer player) {
        if (player == null) return null;
        Entity mount = player.getRidingEntity();
        return ArmorWolfCompatibilityHandler.isArmorWolfCompanion(mount)
                && !mount.isDead && mount.getControllingPassenger() == player ? mount : null;
    }

    private static boolean hasGroundSupport(Entity mount) {
        if (mount.onGround) return true;
        AxisAlignedBB body = mount.getEntityBoundingBox();
        double inset = Math.min(0.05D, Math.min(body.maxX - body.minX,
                body.maxZ - body.minZ) * 0.2D);
        AxisAlignedBB feetProbe = new AxisAlignedBB(body.minX + inset, body.minY - 0.20D,
                body.minZ + inset, body.maxX - inset, body.minY + 0.02D,
                body.maxZ - inset);
        return !mount.world.getCollisionBoxes(mount, feetProbe).isEmpty();
    }

    static double jumpVelocity(float customJumpHeight) {
        return customJumpHeight > 0.0F
                ? BASE_JUMP_VELOCITY * (1.0D + customJumpHeight * 0.5D)
                : BASE_JUMP_VELOCITY;
    }

    private static float customJumpHeight(Entity mount) {
        try {
            Method method = mount.getClass().getMethod("getCustomJumpHeight");
            Object value = method.invoke(mount);
            return value instanceof Number ? ((Number) value).floatValue() : -1.0F;
        } catch (Throwable ignored) {
            return -1.0F;
        }
    }
}
