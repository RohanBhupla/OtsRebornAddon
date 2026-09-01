package net.rebornaddon.exam;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.narutomod.NarutomodModVariables;
import net.narutomod.PlayerTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RankService {
    public static final RankService INSTANCE = new RankService();
    private static final String RANK_KEY = "RebornAddonNinjaRank";
    private static final String CAP_KEY = "RebornAddonNinjaRankCap";

    private final Map<String, Rank> ranks = new LinkedHashMap<String, Rank>();

    private RankService() {
        installDefaults();
    }

    public synchronized void updateCatalog(JsonArray values) {
        if (values == null || values.size() == 0) return;
        Map<String, Rank> next = new LinkedHashMap<String, Rank>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String id = clean(string(value, "id"));
            String name = string(value, "name");
            long cap = value.has("xpCap") ? Math.max(0L, value.get("xpCap").getAsLong()) : 0L;
            if (!id.isEmpty() && !name.isEmpty() && cap > 0L) {
                next.put(id, new Rank(id, name, cap, string(value, "lpGroup")));
            }
        }
        if (!next.isEmpty()) {
            ranks.clear();
            ranks.putAll(next);
        }
    }

    public synchronized List<Rank> ranks() {
        return Collections.unmodifiableList(new ArrayList<Rank>(ranks.values()));
    }

    public synchronized JsonArray catalog() {
        JsonArray values = new JsonArray();
        for (Rank rank : ranks.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", rank.id);
            value.addProperty("name", rank.name);
            value.addProperty("xpCap", rank.xpCap);
            value.addProperty("lpGroup", rank.lpGroup);
            values.add(value);
        }
        return values;
    }

    public synchronized Rank rank(String id) {
        return ranks.get(clean(id));
    }

    public void applyRank(EntityPlayerMP player, String id, long suppliedCap) {
        if (player == null) return;
        Rank configured = rank(id);
        long cap = suppliedCap > 0L ? suppliedCap : configured == null ? 0L : configured.xpCap;
        NBTTagCompound persisted = persisted(player);
        persisted.setString(RANK_KEY, configured == null ? clean(id) : configured.id);
        persisted.setLong(CAP_KEY, Math.max(0L, cap));
        syncLegacyRankCapability(player, configured == null ? id : configured.name, cap);
    }

    public String rankId(EntityPlayer player) {
        return persisted(player).getString(RANK_KEY);
    }

    public long cap(EntityPlayer player) {
        NBTTagCompound data = persisted(player);
        long stored = data.getLong(CAP_KEY);
        if (stored > 0L) return stored;
        Rank rank = rank(data.getString(RANK_KEY));
        return rank == null ? 0L : rank.xpCap;
    }

    public void sync(EntityPlayerMP player) {
        if (player == null || (!rankId(player).isEmpty() && cap(player) > 0L)) return;
        ExamService.INSTANCE.syncRank(player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || event.player.ticksExisted % 20 != 0) return;
        long cap = cap(event.player);
        if (cap <= 0L) return;
        double current = PlayerTracker.getBattleXp(event.player);
        if (current > cap) {
            event.player.getEntityData().setDouble(NarutomodModVariables.BATTLEXP, cap);
        }
    }

    @SubscribeEvent
    public void onLogin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) sync((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        NBTTagCompound oldData = persisted(event.getOriginal());
        NBTTagCompound newData = persisted(event.getEntityPlayer());
        newData.setString(RANK_KEY, oldData.getString(RANK_KEY));
        newData.setLong(CAP_KEY, oldData.getLong(CAP_KEY));
    }

    public void notifyAssigned(EntityPlayerMP player, String rankId) {
        Rank rank = rank(rankId);
        String name = rank == null ? rankId : rank.name;
        player.sendMessage(new TextComponentString(TextFormatting.GOLD
                + "Your ninja rank is now " + name + "."));
    }

    private void installDefaults() {
        ranks.put("academy", new Rank("academy", "Academy Student", 20000L, "academy"));
        ranks.put("genin", new Rank("genin", "Genin", 50000L, "genin"));
        ranks.put("chunin", new Rank("chunin", "Chunin", 80000L, "chunin"));
        ranks.put("jonin", new Rank("jonin", "Jonin", 120000L, "jonin"));
        ranks.put("kage", new Rank("kage", "Kage", 160000L, "kage"));
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsString() : "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static void syncLegacyRankCapability(EntityPlayerMP player, String rankName, long cap) {
        try {
            Class<?> provider = Class.forName("Jarno.coremod.ranks.RankProvider");
            Object value = provider.getField("RANK_CAP").get(null);
            if (!(value instanceof Capability)) return;
            Object rank = player.getCapability((Capability<Object>) value, null);
            if (rank == null) return;
            rank.getClass().getMethod("setRank", String.class).invoke(rank, rankName == null ? "" : rankName);
            rank.getClass().getMethod("setValue", int.class).invoke(rank,
                    Integer.valueOf((int) Math.min(Integer.MAX_VALUE, Math.max(0L, cap))));
        } catch (Throwable ignored) {
        }
    }

    public static final class Rank {
        public final String id;
        public final String name;
        public final long xpCap;
        public final String lpGroup;

        private Rank(String id, String name, long xpCap, String lpGroup) {
            this.id = id;
            this.name = name;
            this.xpCap = xpCap;
            this.lpGroup = lpGroup == null ? "" : lpGroup;
        }
    }
}
