package net.rebornaddon.trade.client;

import net.minecraft.nbt.NBTTagCompound;

public final class ClientTradeData {
    private static NBTTagCompound data = new NBTTagCompound();
    private static int revision;
    private static long requestedAt;

    private ClientTradeData() {
    }

    public static synchronized void update(NBTTagCompound snapshot) {
        data = snapshot == null ? new NBTTagCompound() : snapshot.copy();
        revision++;
        requestedAt = System.currentTimeMillis();
    }

    public static synchronized NBTTagCompound snapshot() {
        return data.copy();
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized boolean shouldRequest() {
        long now = System.currentTimeMillis();
        if (now - requestedAt < 3000L) return false;
        requestedAt = now;
        return true;
    }

    public static synchronized void reset() {
        data = new NBTTagCompound();
        revision++;
        requestedAt = 0L;
    }
}
