package net.rebornaddon.store;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StoreQuotaService {
    public static final StoreQuotaService INSTANCE = new StoreQuotaService();
    public static final List<String> KINDS = Collections.unmodifiableList(Arrays.asList(
            StoreCatalog.QUOTA_ANBU_MASK, StoreCatalog.QUOTA_ANBU_CLOAK,
            StoreCatalog.QUOTA_KAGE_HAT, StoreCatalog.QUOTA_KAGE_ROBE,
            StoreCatalog.QUOTA_AKATSUKI_HEADBAND, StoreCatalog.QUOTA_AKATSUKI_HAT,
            StoreCatalog.QUOTA_AKATSUKI_ROBE));

    private static final String QUOTAS_KEY = "RebornStorePurchaseCounts";

    private StoreQuotaService() {
    }

    public Snapshot snapshot(EntityPlayerMP player) {
        if (player == null) return Snapshot.unavailable("Player is unavailable.");
        if (player.getServer() != null && player.getServer().isSinglePlayer()) {
            return localSnapshot(player);
        }
        return parse(StorePluginBridge.purchaseCounts(player.getUniqueID(), player.getName()));
    }

    public Snapshot snapshot(MinecraftServer server, UUID playerId, String playerName) {
        if (server != null && server.isSinglePlayer()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            return player == null ? Snapshot.unavailable("That single-player profile is not online.")
                    : localSnapshot(player);
        }
        return parse(StorePluginBridge.purchaseCounts(playerId, playerName));
    }

    public Result adjust(EntityPlayerMP player, String kind, int delta) {
        if (player == null || !validKind(kind) || delta == 0) {
            return Result.failure("Invalid store purchase count adjustment.");
        }
        if (player.getServer() != null && player.getServer().isSinglePlayer()) {
            Snapshot current = localSnapshot(player);
            int value = current.count(kind) + delta;
            if (value < 0 || value > limit(kind)) {
                return Result.failure(limitMessage(kind, current.count(kind)));
            }
            writeLocal(player, kind, value);
            return Result.success("Store purchase count updated.", localSnapshot(player));
        }
        String[] response = StorePluginBridge.adjustPurchaseCount(
                player.getUniqueID(), player.getName(), kind, delta);
        Snapshot snapshot = parse(response);
        return StorePluginBridge.successful(response) && snapshot.available()
                ? Result.success(StorePluginBridge.message(response), snapshot)
                : Result.failure(StorePluginBridge.message(response));
    }

    public Result adjust(ICommandSender sender, UUID playerId, String playerName,
                         String kind, int delta) {
        if (sender == null || playerId == null || !validKind(kind) || delta == 0) {
            return Result.failure("Invalid store purchase count adjustment.");
        }
        MinecraftServer server = sender.getServer();
        if (server != null && server.isSinglePlayer()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player == null) return Result.failure("That single-player profile is not online.");
            return adjust(player, kind, delta);
        }
        String[] response = StorePluginBridge.adjustPurchaseCount(playerId, playerName, kind, delta);
        Snapshot snapshot = parse(response);
        return StorePluginBridge.successful(response) && snapshot.available()
                ? Result.success(StorePluginBridge.message(response), snapshot)
                : Result.failure(StorePluginBridge.message(response));
    }

    public Result set(ICommandSender sender, UUID playerId, String playerName,
                      String kind, int value) {
        if (sender == null || playerId == null || !validKind(kind)
                || value < 0 || value > limit(kind)) {
            return Result.failure("The requested store purchase count is invalid.");
        }
        MinecraftServer server = sender.getServer();
        if (server != null && server.isSinglePlayer()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player == null) return Result.failure("That single-player profile is not online.");
            writeLocal(player, kind, value);
            return Result.success("Store purchase count updated.", localSnapshot(player));
        }
        String[] response = StorePluginBridge.setPurchaseCount(
                sender, playerId, playerName, kind, value);
        Snapshot snapshot = parse(response);
        return StorePluginBridge.successful(response) && snapshot.available()
                ? Result.success(StorePluginBridge.message(response), snapshot)
                : Result.failure(StorePluginBridge.message(response));
    }

    public static boolean validKind(String kind) {
        return kind != null && KINDS.contains(kind);
    }

    public static int limit(String kind) {
        return StoreCatalog.QUOTA_ANBU_MASK.equals(kind)
                || StoreCatalog.QUOTA_ANBU_CLOAK.equals(kind) ? 8 : 1;
    }

    private static Snapshot parse(String[] response) {
        if (!StorePluginBridge.successful(response)) {
            return Snapshot.unavailable(StorePluginBridge.message(response));
        }
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (String kind : KINDS) counts.put(kind, Integer.valueOf(0));
        for (int i = 2; response != null && i < response.length; i++) {
            String[] fields = response[i].split("\\t", -1);
            if (fields.length < 2 || !validKind(fields[0])) continue;
            try {
                int value = Integer.parseInt(fields[1]);
                if (value >= 0 && value <= limit(fields[0])) {
                    counts.put(fields[0], Integer.valueOf(value));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new Snapshot(true, StorePluginBridge.message(response), counts);
    }

    private static Snapshot localSnapshot(EntityPlayerMP player) {
        NBTTagCompound stored = persisted(player).getCompoundTag(QUOTAS_KEY);
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (String kind : KINDS) {
            counts.put(kind, Integer.valueOf(Math.max(0, Math.min(limit(kind), stored.getInteger(kind)))));
        }
        return new Snapshot(true, "Single-player preview counts loaded.", counts);
    }

    private static void writeLocal(EntityPlayerMP player, String kind, int value) {
        NBTTagCompound root = persisted(player);
        NBTTagCompound stored = root.getCompoundTag(QUOTAS_KEY);
        stored.setInteger(kind, value);
        root.setTag(QUOTAS_KEY, stored);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static String limitMessage(String kind, int current) {
        return current >= limit(kind) ? "The purchase limit for that equipment has been reached."
                : "That equipment count is already zero.";
    }

    public static final class Snapshot {
        private final boolean available;
        private final String message;
        private final Map<String, Integer> counts;

        private Snapshot(boolean available, String message, Map<String, Integer> counts) {
            this.available = available;
            this.message = message == null ? "" : message;
            this.counts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(counts));
        }

        private static Snapshot unavailable(String message) {
            return new Snapshot(false, message, Collections.<String, Integer>emptyMap());
        }

        public boolean available() { return available; }
        public String message() { return message; }
        public int count(String kind) {
            Integer value = counts.get(kind);
            return value == null ? 0 : value.intValue();
        }
        public boolean canPurchase(String kind) {
            return !validKind(kind) || available && count(kind) < limit(kind);
        }
    }

    public static final class Result {
        private final boolean success;
        private final String message;
        private final Snapshot snapshot;

        private Result(boolean success, String message, Snapshot snapshot) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.snapshot = snapshot;
        }

        public static Result success(String message, Snapshot snapshot) {
            return new Result(true, message, snapshot);
        }
        public static Result failure(String message) {
            return new Result(false, message, Snapshot.unavailable(message));
        }
        public boolean success() { return success; }
        public String message() { return message; }
        public Snapshot snapshot() { return snapshot; }
    }
}
