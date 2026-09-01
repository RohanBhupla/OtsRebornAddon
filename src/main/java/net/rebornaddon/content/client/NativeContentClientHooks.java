package net.rebornaddon.content.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.content.network.NativeContentSyncMessage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class NativeContentClientHooks {
    public static final NativeContentClientHooks INSTANCE = new NativeContentClientHooks();
    private static final int MAX_PARTS = 512;
    private static final int MAX_TRANSFER_BYTES = 8 * 1024 * 1024;
    private static final long TRANSFER_TTL_MILLIS = 30000L;

    private BossHud boss;
    private final Map<String, PendingTransfer> pending = new HashMap<String, PendingTransfer>();

    private NativeContentClientHooks() {
    }

    public void reset() {
        pending.clear();
        boss = null;
        ClientNativeContentData.clear();
    }

    public void handle(NativeContentSyncMessage message) {
        if (message == null || message.partCount() < 1 || message.partCount() > MAX_PARTS
                || message.partIndex() < 0 || message.partIndex() >= message.partCount()) {
            return;
        }
        expireTransfers();
        String key = message.kind() + '\n' + message.transferId();
        PendingTransfer transfer = pending.get(key);
        if (transfer == null || transfer.partCount != message.partCount()) {
            transfer = new PendingTransfer(message.kind(), message.partCount());
            pending.put(key, transfer);
        }
        if (!transfer.add(message.partIndex(), message.payload())) {
            pending.remove(key);
            return;
        }
        if (!transfer.complete()) return;
        pending.remove(key);
        handleComplete(transfer.kind, transfer.text());
    }

    private void handleComplete(String kind, String json) {
        JsonObject data;
        try {
            data = json == null || json.isEmpty() ? new JsonObject()
                    : new JsonParser().parse(json).getAsJsonObject();
        } catch (Throwable ignored) {
            return;
        }
        if ("admin".equals(kind)) {
            ClientNativeContentData.updateAdmin(data);
        } else if ("boss".equals(kind)) {
            boss = new BossHud(data);
        } else if ("boss_clear".equals(kind)) {
            boss = null;
        } else if ("interaction".equals(kind) || "action".equals(kind)) {
            handleInteraction(data);
        }
    }

    private void expireTransfers() {
        long cutoff = System.currentTimeMillis() - TRANSFER_TTL_MILLIS;
        Iterator<PendingTransfer> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().updatedAt < cutoff) iterator.remove();
        }
    }

    private void handleInteraction(JsonObject data) {
        String route = string(data, "route");
        if (data.has("pages") && data.get("pages").isJsonArray()) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiNativeDialogue(data));
        } else if ("mail".equals(route)) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiMailbox());
        } else if ("quests".equals(route) || "store".equals(route)
                || "trade".equals(route) || "world".equals(route)) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiHub(route));
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.gameSettings.hideGUI) {
            return;
        }
        int y = 9;
        if (boss != null && System.currentTimeMillis() - boss.updatedAt <= 3000L) {
            drawBoss(event.getResolution().getScaledWidth(), y, boss);
            y += 53;
        } else {
            boss = null;
        }
        drawTracker(event.getResolution().getScaledWidth(), y, ClientQuestData.get().tracker);
    }

    private void drawBoss(int screenWidth, int y, BossHud value) {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int width = Math.min(330, Math.max(210, screenWidth / 3));
        int left = (screenWidth - width) / 2;
        int healthWidth = (int) Math.round((width - 4) * ratio(value.health, value.maximum));
        int contributionWidth = (int) Math.round((width - 4) * ratio(value.contribution, 100.0D));
        Gui.drawRect(left - 2, y - 2, left + width + 2, y + 45, 0xD8101114);
        Gui.drawRect(left, y + 20, left + width, y + 31, Theme.EDGE_DARK);
        Gui.drawRect(left + 2, y + 22, left + 2 + healthWidth, y + 29, 0xFFB33A48);
        Gui.drawRect(left, y + 34, left + width, y + 42, Theme.EDGE_DARK);
        Gui.drawRect(left + 2, y + 36, left + 2 + contributionWidth, y + 40, Theme.GOLD);
        String title = trim(font, value.name + "  " + whole(value.health) + "/" + whole(value.maximum)
                + "  " + Math.round(ratio(value.health, value.maximum) * 100.0D) + "%", width);
        font.drawStringWithShadow(title, left + (width - font.getStringWidth(title)) / 2, y, 0xFFF5EEF0);
        String phase = (value.phase.isEmpty() ? "" : value.phase)
                + (value.enraged ? (value.phase.isEmpty() ? "Enraged" : "  |  Enraged") : "")
                + (value.scaledPlayers > 1 ? "  |  " + value.scaledPlayers + " shinobi" : "");
        phase = trim(font, phase, width - 8);
        if (!phase.isEmpty()) font.drawStringWithShadow(phase,
                left + (width - font.getStringWidth(phase)) / 2, y + 10,
                value.enraged ? Theme.RANKED_RED : Theme.GOLD);
        String contribution = ClientLocalization.format("gui.rebornaddon.boss.contribution",
                "Contribution  %s%%", Math.round(value.contribution));
        font.drawStringWithShadow(contribution,
                left + (width - font.getStringWidth(contribution)) / 2, y + 33, 0xFFFFD981);
    }

    private void drawTracker(int screenWidth, int y, ClientQuestData.Tracker tracker) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tracker == null || !tracker.active || minecraft.player == null) {
            return;
        }
        double dx = tracker.x - minecraft.player.posX;
        double dz = tracker.z - minecraft.player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = tracker.y - minecraft.player.posY;
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = wrap(targetYaw - minecraft.player.rotationYaw);
        String arrow = Math.abs(relative) < 22.5D ? "^" : relative > 0.0D
                ? (relative < 157.5D ? ">" : "v") : (relative > -157.5D ? "<" : "v");
        boolean wrongLevel = Math.abs(dy) > Math.max(3.0D, tracker.radius * 0.5D);
        FontRenderer font = minecraft.fontRenderer;
        int width = Math.min(300, Math.max(190, screenWidth / 4));
        int left = (screenWidth - width) / 2;
        Gui.drawRect(left, y, left + width, y + (wrongLevel ? 31 : 22), 0xC9101114);
        Gui.drawRect(left, y, left + 3, y + (wrongLevel ? 31 : 22), Theme.SUCCESS);
        String heading = trim(font, arrow + "  " + tracker.title + "  " + Math.round(horizontal) + "m",
                width - 16);
        font.drawStringWithShadow(heading, left + 9, y + 4, Theme.TEXT_LIGHT);
        if (wrongLevel) {
            String level = dy > 0.0D
                    ? ClientLocalization.format("gui.rebornaddon.tracker.above",
                    "Target is %sm above", Math.round(Math.abs(dy)))
                    : ClientLocalization.format("gui.rebornaddon.tracker.below",
                    "Target is %sm below", Math.round(Math.abs(dy)));
            font.drawStringWithShadow(level, left + 9, y + 16, Theme.GOLD);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static String trim(FontRenderer font, String value, int width) {
        return font.trimStringToWidth(value, Math.max(20, width));
    }

    private static double ratio(double value, double maximum) {
        return maximum <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, value / maximum));
    }

    private static String whole(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static double wrap(double value) {
        while (value <= -180.0D) value += 360.0D;
        while (value > 180.0D) value -= 360.0D;
        return value;
    }

    private static final class BossHud {
        private final String name;
        private final double health;
        private final double maximum;
        private final double contribution;
        private final String phase;
        private final boolean enraged;
        private final int scaledPlayers;
        private final long updatedAt = System.currentTimeMillis();

        private BossHud(JsonObject data) {
            name = string(data, "name");
            health = data.has("health") ? data.get("health").getAsDouble() : 0.0D;
            maximum = data.has("maximum") ? data.get("maximum").getAsDouble() : 1.0D;
            contribution = data.has("contribution") ? data.get("contribution").getAsDouble() : 0.0D;
            phase = data.has("phase") ? data.get("phase").getAsString() : "";
            enraged = data.has("enraged") && data.get("enraged").getAsBoolean();
            scaledPlayers = data.has("scaledPlayers") ? data.get("scaledPlayers").getAsInt() : 1;
        }
    }

    private static final class PendingTransfer {
        private final String kind;
        private final byte[][] parts;
        private final int partCount;
        private int received;
        private int totalBytes;
        private long updatedAt = System.currentTimeMillis();

        private PendingTransfer(String kind, int partCount) {
            this.kind = kind;
            this.partCount = partCount;
            this.parts = new byte[partCount][];
        }

        private boolean add(int index, byte[] value) {
            byte[] bytes = value == null ? new byte[0] : value;
            if (parts[index] != null) return true;
            if (totalBytes + bytes.length > MAX_TRANSFER_BYTES) return false;
            parts[index] = bytes;
            totalBytes += bytes.length;
            received++;
            updatedAt = System.currentTimeMillis();
            return true;
        }

        private boolean complete() {
            return received == partCount;
        }

        private String text() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            for (byte[] part : parts) output.write(part, 0, part.length);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
