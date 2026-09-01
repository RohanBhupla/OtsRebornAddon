package net.rebornaddon.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.content.JsonNbtConverter;
import net.rebornaddon.content.NativeContentPluginBridge;
import net.rebornaddon.content.NativeContentService;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NativeQuestService {
    private static final long REQUEST_DELAY_MILLIS = 750L;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<UUID, Long>();

    private NativeQuestService() {
    }

    public static int refresh() {
        return NativeContentService.INSTANCE.refreshRuntime() ? NativeContentService.INSTANCE.questCount() : 0;
    }

    public static void reset() {
        LAST_REQUEST.clear();
    }

    public static void removePlayer(UUID id) {
        if (id != null) {
            LAST_REQUEST.remove(id);
        }
    }

    public static void request(EntityPlayerMP player, int ignoredCatalogVersion) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = LAST_REQUEST.get(player.getUniqueID());
        if (previous != null && now - previous.longValue() < REQUEST_DELAY_MILLIS) {
            return;
        }
        LAST_REQUEST.put(player.getUniqueID(), Long.valueOf(now));
        send(player);
    }

    public static void send(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        String[] response = NativeContentPluginBridge.playerSnapshot(player);
        NBTTagCompound snapshot = snapshot(response);
        RebornAddonNetwork.sendQuestSnapshot(player, snapshot);
        NativeContentService.INSTANCE.updatePlayerRuntime(player, NativeContentPluginBridge.payload(response));
    }

    public static void sendProgress(EntityPlayerMP player) {
        send(player);
    }

    public static void sendAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    public static void claim(EntityPlayerMP player, String questId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("questId", questId == null ? "" : questId);
        action(player, "quest.claim", payload.toString());
    }

    public static void action(EntityPlayerMP player, String action, String payload) {
        if (player == null) {
            return;
        }
        String[] response = NativeContentPluginBridge.playerAction(player, action, payload);
        NativeContentService.INSTANCE.applyPlayerResponse(player, response);
        if (!NativeContentPluginBridge.successful(response)) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + NativeContentPluginBridge.message(response)), true);
        }
        send(player);
    }

    private static NBTTagCompound snapshot(String[] response) {
        String payload = NativeContentPluginBridge.payload(response);
        if (NativeContentPluginBridge.successful(response) && !payload.isEmpty()) {
            try {
                JsonObject object = new JsonParser().parse(payload).getAsJsonObject();
                NBTTagCompound result = JsonNbtConverter.compound(object);
                result.setBoolean("Available", true);
                result.setBoolean("Full", true);
                if (!result.hasKey("CatalogVersion")) {
                    result.setInteger("CatalogVersion", object.has("revision")
                            ? object.get("revision").getAsInt() : 0);
                }
                return result;
            } catch (Throwable ignored) {
            }
        }
        NBTTagCompound unavailable = new NBTTagCompound();
        unavailable.setBoolean("Available", false);
        unavailable.setBoolean("Full", true);
        unavailable.setInteger("CatalogVersion", 0);
        unavailable.setString("Message", NativeContentPluginBridge.message(response));
        return unavailable;
    }
}
