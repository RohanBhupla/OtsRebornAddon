package net.rebornaddon.jutsu.client;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

public final class ClientAdminData {
    private static NBTTagCompound snapshot = new NBTTagCompound();
    private static int revision;
    private static long requestedAt;
    private static boolean loaded;

    private ClientAdminData() {
    }

    public static synchronized void update(NBTTagCompound data) {
        if (data == null) {
            snapshot = new NBTTagCompound();
        } else if (data.getBoolean("Partial")) {
            NBTTagCompound merged = snapshot.copy();
            for (String key : data.getKeySet()) {
                NBTBase value = data.getTag(key);
                if (value != null) merged.setTag(key, value.copy());
            }
            snapshot = merged;
        } else {
            snapshot = data.copy();
        }
        revision++;
        requestedAt = 0L;
        loaded = true;
    }

    public static synchronized NBTTagCompound snapshot() {
        return snapshot.copy();
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized boolean loaded() {
        return loaded;
    }

    public static synchronized boolean shouldRequest() {
        long now = System.currentTimeMillis();
        if (requestedAt != 0L && now - requestedAt < 2000L) return false;
        requestedAt = now;
        return true;
    }

    public static synchronized void invalidate() {
        requestedAt = 0L;
        loaded = false;
    }

    public static synchronized void reset() {
        snapshot = new NBTTagCompound();
        revision++;
        requestedAt = 0L;
        loaded = false;
    }
}
