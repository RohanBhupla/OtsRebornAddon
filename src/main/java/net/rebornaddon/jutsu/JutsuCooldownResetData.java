package net.rebornaddon.jutsu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class JutsuCooldownResetData extends WorldSavedData {
    private static final String DATA_NAME = "rebornaddon_jutsu_cooldown_resets";
    private static final String APPLIED_KEY = "RebornAddonJutsuCooldownReset";

    private long globalResetAt;
    private final Map<UUID, Long> playerResets = new HashMap<UUID, Long>();

    public JutsuCooldownResetData() {
        super(DATA_NAME);
    }

    public JutsuCooldownResetData(String name) {
        super(name);
    }

    public static JutsuCooldownResetData get(MinecraftServer server) {
        if (server == null) return null;
        WorldServer world = server.getWorld(0);
        if (world == null) return null;
        MapStorage storage = world.getMapStorage();
        JutsuCooldownResetData data = (JutsuCooldownResetData) storage.getOrLoadData(
                JutsuCooldownResetData.class, DATA_NAME);
        if (data == null) {
            data = new JutsuCooldownResetData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void resetAll(MinecraftServer server) {
        globalResetAt = System.currentTimeMillis();
        playerResets.clear();
        markDirty();
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) apply(player);
    }

    public void resetPlayer(EntityPlayerMP player) {
        if (player == null) return;
        resetPlayer(player.getServer(), player.getUniqueID());
    }

    public void resetPlayer(MinecraftServer server, UUID playerId) {
        if (playerId == null) return;
        playerResets.put(playerId, Long.valueOf(System.currentTimeMillis()));
        markDirty();
        EntityPlayerMP online = server == null ? null
                : server.getPlayerList().getPlayerByUUID(playerId);
        if (online != null) apply(online);
    }

    public void apply(EntityPlayerMP player) {
        if (player == null) return;
        Long personal = playerResets.get(player.getUniqueID());
        long resetAt = Math.max(globalResetAt, personal == null ? 0L : personal.longValue());
        if (resetAt <= 0L) return;
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getLong(APPLIED_KEY) >= resetAt) return;
        JutsuRuntimeHooks.clearAllCooldowns(player);
        persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setLong(APPLIED_KEY, resetAt);
        entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        if (personal != null && personal.longValue() <= resetAt) {
            playerResets.remove(player.getUniqueID());
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        globalResetAt = compound.getLong("GlobalResetAt");
        playerResets.clear();
        NBTTagList values = compound.getTagList("PlayerResets", 10);
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            try {
                playerResets.put(UUID.fromString(value.getString("Uuid")),
                        Long.valueOf(value.getLong("ResetAt")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setLong("GlobalResetAt", globalResetAt);
        NBTTagList values = new NBTTagList();
        Iterator<Map.Entry<UUID, Long>> iterator = playerResets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Uuid", entry.getKey().toString());
            value.setLong("ResetAt", entry.getValue().longValue());
            values.appendTag(value);
        }
        compound.setTag("PlayerResets", values);
        return compound;
    }
}
