package net.rebornaddon.performance.client;

import net.minecraft.nbt.NBTTagCompound;

public final class ClientPerformanceData {
    private static NBTTagCompound snapshot = new NBTTagCompound();
    private static int revision;
    private static long requestedAt;

    private ClientPerformanceData() {
    }

    public static synchronized void update(NBTTagCompound value) {
        snapshot = value == null ? new NBTTagCompound() : value.copy();
        revision++;
    }

    public static synchronized NBTTagCompound snapshot() {
        return snapshot.copy();
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized boolean shouldRequest() {
        long now = System.currentTimeMillis();
        if (now - requestedAt < 2000L) return false;
        requestedAt = now;
        return true;
    }

    public static synchronized void reset() {
        snapshot = new NBTTagCompound();
        revision++;
        requestedAt = 0L;
    }
}
