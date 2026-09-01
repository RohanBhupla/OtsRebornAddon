package net.rebornaddon.state;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Locale;

public final class PlayerStateLifecycleService {
    public static final PlayerStateLifecycleService INSTANCE = new PlayerStateLifecycleService();
    public static final int CLEAR_INVULNERABLE = 1;
    public static final int CLEAR_SLOWNESS = 2;
    public static final int CLEAR_MINING_FATIGUE = 4;
    public static final int CLEAR_WEAKNESS = 8;
    public static final int CLEAR_HURT_RESISTANCE = 16;
    public static final int CLEAR_ABSORPTION = 32;
    private static final String ROOT = "RebornManagedStates";
    private static final int MAX_STATES = 64;

    private PlayerStateLifecycleService() {
    }

    public synchronized void begin(EntityPlayerMP player, String state, String owner,
                                   long durationMillis, int cleanupMask,
                                   boolean clearOnDeath, boolean clearOnLogout,
                                   boolean clearOnDimension) {
        if (player == null) return;
        String cleanState = clean(state, 48);
        String cleanOwner = clean(owner, 96);
        if (cleanState.isEmpty() || cleanOwner.isEmpty()) return;
        NBTTagList current = states(player);
        NBTTagList next = new NBTTagList();
        long expiresAt = durationMillis <= 0L ? 0L : System.currentTimeMillis() + durationMillis;
        for (int i = 0; i < current.tagCount() && next.tagCount() < MAX_STATES - 1; i++) {
            NBTTagCompound value = current.getCompoundTagAt(i);
            if (cleanState.equals(value.getString("State")) && cleanOwner.equals(value.getString("Owner"))) continue;
            next.appendTag(value.copy());
        }
        NBTTagCompound value = new NBTTagCompound();
        value.setString("State", cleanState);
        value.setString("Owner", cleanOwner);
        value.setLong("StartedAt", System.currentTimeMillis());
        value.setLong("ExpiresAt", expiresAt);
        value.setInteger("Cleanup", cleanupMask);
        value.setBoolean("Death", clearOnDeath);
        value.setBoolean("Logout", clearOnLogout);
        value.setBoolean("Dimension", clearOnDimension);
        value.setInteger("SourceDimension", player.dimension);
        next.appendTag(value);
        persisted(player).setTag(ROOT, next);
    }

    public synchronized void end(EntityPlayerMP player, String state, String owner) {
        remove(player, clean(state, 48), clean(owner, 96), Removal.MATCH);
    }

    public synchronized void releaseOwner(EntityPlayerMP player, String owner) {
        remove(player, "", clean(owner, 96), Removal.OWNER);
    }

    public synchronized boolean active(EntityPlayer player, String state) {
        String expected = clean(state, 48);
        long now = System.currentTimeMillis();
        NBTTagList values = states(player);
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            long expires = value.getLong("ExpiresAt");
            if (expected.equals(value.getString("State")) && (expires == 0L || expires > now)) return true;
        }
        return false;
    }

    public synchronized int activeCount(MinecraftServer server) {
        if (server == null) return 0;
        int count = 0;
        long now = System.currentTimeMillis();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            NBTTagList values = states(player);
            for (int i = 0; i < values.tagCount(); i++) {
                long expiry = values.getCompoundTagAt(i).getLong("ExpiresAt");
                if (expiry == 0L || expiry > now) count++;
            }
        }
        return count;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 10 != 0) return;
        remove((EntityPlayerMP) event.player, "", "", Removal.EXPIRED);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            remove((EntityPlayerMP) event.getEntityLiving(), "", "", Removal.DEATH);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            remove((EntityPlayerMP) event.player, "", "", Removal.LOGOUT);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            remove((EntityPlayerMP) event.player, "", "", Removal.EXPIRED);
        }
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            remove((EntityPlayerMP) event.player, "", "", Removal.DIMENSION);
        }
    }

    private void remove(EntityPlayerMP player, String state, String owner, Removal removal) {
        if (player == null) return;
        NBTTagList current = states(player);
        if (current.tagCount() == 0) return;
        NBTTagList next = new NBTTagList();
        int removedMask = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < current.tagCount(); i++) {
            NBTTagCompound value = current.getCompoundTagAt(i);
            boolean remove;
            if (removal == Removal.MATCH) {
                remove = state.equals(value.getString("State")) && owner.equals(value.getString("Owner"));
            } else if (removal == Removal.OWNER) {
                remove = owner.equals(value.getString("Owner"));
            } else if (removal == Removal.EXPIRED) {
                remove = value.getLong("ExpiresAt") > 0L && value.getLong("ExpiresAt") <= now;
            } else if (removal == Removal.DEATH) {
                remove = value.getBoolean("Death");
            } else if (removal == Removal.LOGOUT) {
                remove = value.getBoolean("Logout");
            } else {
                remove = value.getBoolean("Dimension");
            }
            if (remove) removedMask |= value.getInteger("Cleanup");
            else if (next.tagCount() < MAX_STATES) next.appendTag(value.copy());
        }
        if (next.tagCount() == current.tagCount()) return;
        persisted(player).setTag(ROOT, next);
        cleanup(player, removedMask, activeMask(next));
    }

    private static int activeMask(NBTTagList values) {
        int result = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            if (value.getLong("ExpiresAt") == 0L || value.getLong("ExpiresAt") > now) {
                result |= value.getInteger("Cleanup");
            }
        }
        return result;
    }

    private static void cleanup(EntityPlayerMP player, int removed, int retained) {
        int mask = removed & ~retained;
        if ((mask & CLEAR_INVULNERABLE) != 0 && !player.isCreative() && !player.isSpectator()) {
            player.setEntityInvulnerable(false);
            player.capabilities.disableDamage = false;
            player.sendPlayerAbilities();
        }
        if ((mask & CLEAR_SLOWNESS) != 0) player.removePotionEffect(MobEffects.SLOWNESS);
        if ((mask & CLEAR_MINING_FATIGUE) != 0) player.removePotionEffect(MobEffects.MINING_FATIGUE);
        if ((mask & CLEAR_WEAKNESS) != 0) player.removePotionEffect(MobEffects.WEAKNESS);
        if ((mask & CLEAR_HURT_RESISTANCE) != 0) player.hurtResistantTime = 0;
        if ((mask & CLEAR_ABSORPTION) != 0) player.setAbsorptionAmount(0.0F);
    }

    private static NBTTagList states(EntityPlayer player) {
        NBTTagCompound data = persisted(player);
        return data.hasKey(ROOT, 9) ? data.getTagList(ROOT, 10) : new NBTTagList();
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            root.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private enum Removal { MATCH, OWNER, EXPIRED, DEATH, LOGOUT, DIMENSION }
}
