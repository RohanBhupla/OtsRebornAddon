package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.ranked.match.MatchMode;
import net.rebornaddon.ranked.network.QueueActionMessage;
import net.rebornaddon.ranked.network.RankedNetwork;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class RankedTab implements HubTab {

    private static final int BTN_QUEUE_1V1 = 100;
    private static final int BTN_QUEUE_2V2 = 101;
    private static final int BTN_QUEUE_3V3 = 102;
    private static final int BTN_LEAVE_QUEUE = 103;
    private static final int BTN_FORFEIT = 104;

    private static final int LEFT_COL_WIDTH = 170;
    private static final int COLUMN_GAP = 16;

    private String statusMessage = "";
    private long statusMessageExpireAt = 0;

    @Override
    public String getTabName() {
        return "Ranked";
    }

    @Override
    public int getTabColorActive() {
        return Theme.RANKED_RED;
    }

    @Override
    public int getTabColorHover() {
        return Theme.RANKED_RED_HOVER;
    }

    @Override
    public void buildButtons(List<GuiButton> buttonList, int left, int top, int width, int height) {
        boolean inMatch = RankedClientData.amIInMatch();

        int btnW = LEFT_COL_WIDTH;
        int btnH = 22;
        int gap = 8;
        int x = left;
        int y = top;

        buttonList.add(new ThemedButton(BTN_QUEUE_1V1, x, y, btnW, btnH, "Queue 1v1",
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
        y += btnH + gap;
        buttonList.add(new ThemedButton(BTN_QUEUE_2V2, x, y, btnW, btnH, "Queue 2v2",
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
        y += btnH + gap;
        buttonList.add(new ThemedButton(BTN_QUEUE_3V3, x, y, btnW, btnH, "Queue 3v3",
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
        y += btnH + gap * 2;

        buttonList.add(new ThemedButton(BTN_LEAVE_QUEUE, x, y, btnW, btnH, "Leave Queue",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
        y += btnH + gap;

        if (inMatch) {
            buttonList.add(new ThemedButton(BTN_FORFEIT, x, y, btnW, btnH, "Forfeit Match",
                    Theme.DANGER, 0xFFE05A4B, Theme.BUTTON_TEXT));
        }
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        switch (buttonId) {
            case BTN_QUEUE_1V1:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_QUEUE, MatchMode.ONE_V_ONE.getNetId()));
                showStatus("Queued for 1v1");
                return true;
            case BTN_QUEUE_2V2:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_QUEUE, MatchMode.TWO_V_TWO.getNetId()));
                showStatus("Queued for 2v2");
                return true;
            case BTN_QUEUE_3V3:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_QUEUE, MatchMode.THREE_V_THREE.getNetId()));
                showStatus("Queued for 3v3");
                return true;
            case BTN_LEAVE_QUEUE:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_LEAVE, 0));
                showStatus("Left queue");
                return true;
            case BTN_FORFEIT:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_FORFEIT, 0));
                showStatus("Forfeited match");
                return true;
            default:
                return false;
        }
    }

    private void showStatus(String message) {
        this.statusMessage = message;
        this.statusMessageExpireAt = System.currentTimeMillis() + 2500L;
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height, int mouseX, int mouseY) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        int rightColX = left + LEFT_COL_WIDTH + COLUMN_GAP;
        int rightColWidth = width - LEFT_COL_WIDTH - COLUMN_GAP;

        int dividerX = left + LEFT_COL_WIDTH + COLUMN_GAP / 2;
        Gui.drawRect(dividerX, top, dividerX + 1, top + height, Theme.GOLD_DARK);

        drawStatsAndLeaderboard(fr, rightColX, top, rightColWidth, height);

        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageExpireAt) {
            String msg = "\u00a77" + statusMessage;
            fr.drawString(msg, left, top + height - 12, Theme.TEXT_MUTED);
        } else if (System.currentTimeMillis() >= statusMessageExpireAt) {
            statusMessage = "";
        }
    }

    private void drawStatsAndLeaderboard(FontRenderer fr, int left, int top, int width, int height) {
        int elo = RankedClientData.getElo();
        int wins = RankedClientData.getWins();
        int losses = RankedClientData.getLosses();
        int draws = RankedClientData.getDraws();
        boolean inMatch = RankedClientData.amIInMatch();

        int centerX = left + width / 2;
        int y = top;

        drawCentered(fr, "\u00a77Your ELO", centerX, y, Theme.TEXT_MUTED);
        y += 12;
        drawCenteredScaled(fr, String.valueOf(elo), centerX, y, Theme.RANKED_RED_HOVER, 1.6f);
        y += 24;

        String record = wins + "W  -  " + losses + "L  -  " + draws + "D";
        drawCentered(fr, record, centerX, y, Theme.TEXT_LIGHT);
        y += 12;

        double totalGames = wins + losses + draws;
        String winRate = totalGames > 0 ? String.format("%.1f%% win rate", (wins / totalGames) * 100.0) : "No matches played yet";
        drawCentered(fr, "\u00a77" + winRate, centerX, y, Theme.TEXT_MUTED);
        y += 14;

        if (inMatch) {
            drawCentered(fr, "\u00a7aCurrently in a match", centerX, y, Theme.SUCCESS);
            y += 14;
        }

        y += 4;
        Gui.drawRect(left, y, left + width, y + 1, Theme.PURPLE_LIGHT);
        y += 10;

        drawCentered(fr, "\u00a7fLeaderboard", centerX, y, Theme.TEXT_LIGHT);
        y += 14;

        List<RankedClientData.LeaderboardEntry> entries = RankedClientData.getLeaderboard(8);
        if (entries.isEmpty()) {
            drawCentered(fr, "\u00a77No leaderboard data yet", centerX, y, Theme.TEXT_MUTED);
            return;
        }

        int rank = 1;
        for (RankedClientData.LeaderboardEntry entry : entries) {
            String line = "#" + rank + "  " + entry.playerName + "  -  " + entry.elo;
            fr.drawString(line, left, y, Theme.TEXT_LIGHT);
            y += 13;
            rank++;
        }
    }

    private void drawCentered(FontRenderer fr, String text, int centerX, int y, int color) {
        int w = fr.getStringWidth(text);
        fr.drawString(text, centerX - w / 2, y, color);
    }

    private void drawCenteredScaled(FontRenderer fr, String text, int centerX, int y, int color, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, y, 0);
        GL11.glScalef(scale, scale, 1.0f);
        int w = fr.getStringWidth(text);
        fr.drawString(text, -w / 2, 0, color);
        GL11.glPopMatrix();
    }
}
