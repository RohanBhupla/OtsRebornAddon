package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.ranked.elo.RankTier;
import net.rebornaddon.ranked.match.MatchMode;
import net.rebornaddon.ranked.network.QueueActionMessage;
import net.rebornaddon.ranked.network.RankedNetwork;

import java.util.List;
import java.util.Locale;

public final class RankedTab implements HubTab {
    private static final int BTN_QUEUE_1V1 = 110;
    private static final int BTN_QUEUE_2V2 = 111;
    private static final int BTN_QUEUE_3V3 = 112;
    private static final int BTN_LEAVE_QUEUE = 113;
    private static final int BTN_FORFEIT = 114;
    private static final int COLUMN_GAP = 10;

    private int leftColumnWidth;
    private int builtRevision = -1;
    private String statusMessage = "";
    private long statusMessageExpireAt;
    private long forfeitArmedUntil;

    @Override
    public String getTabName() {
        return ClientLocalization.format("gui.rebornaddon.tab.ranked", "Ranked");
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
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        builtRevision = RankedClientData.getRevision();
        leftColumnWidth = Math.max(86, Math.min(184, (width - COLUMN_GAP) / 2));
        int buttonWidth = Math.max(72, leftColumnWidth - 12);
        int buttonHeight = height < 160 ? 18 : 22;
        int gap = height < 160 ? 4 : 7;
        int x = left + 6;
        int y = top + 28;

        buttons.add(actionButton(BTN_QUEUE_1V1, x, y, buttonWidth, buttonHeight,
                ClientLocalization.format("gui.rebornaddon.ranked.queue_1v1", "Queue 1v1")));
        y += buttonHeight + gap;
        buttons.add(actionButton(BTN_QUEUE_2V2, x, y, buttonWidth, buttonHeight,
                ClientLocalization.format("gui.rebornaddon.ranked.queue_2v2", "Queue 2v2")));
        y += buttonHeight + gap;
        buttons.add(actionButton(BTN_QUEUE_3V3, x, y, buttonWidth, buttonHeight,
                ClientLocalization.format("gui.rebornaddon.ranked.queue_3v3", "Queue 3v3")));
        y += buttonHeight + gap + 3;

        ThemedButton leave = new ThemedButton(BTN_LEAVE_QUEUE, x, y, buttonWidth, buttonHeight,
                ClientLocalization.format("gui.rebornaddon.ranked.leave", "Leave Queue"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        leave.enabled = RankedClientData.amIQueued();
        buttons.add(leave);
        y += buttonHeight + gap;

        if (RankedClientData.amIInMatch() && y + buttonHeight <= top + height - 18) {
            boolean armed = forfeitArmedUntil > System.currentTimeMillis();
            ThemedButton forfeit = new ThemedButton(BTN_FORFEIT, x, y, buttonWidth, buttonHeight,
                    armed ? ClientLocalization.format("gui.rebornaddon.ranked.confirm_forfeit", "Confirm Forfeit")
                            : ClientLocalization.format("gui.rebornaddon.ranked.forfeit", "Forfeit Match"),
                    armed ? Theme.DANGER_BRIGHT : Theme.DANGER, Theme.DANGER_BRIGHT, Theme.BUTTON_TEXT);
            forfeit.setSelected(armed);
            buttons.add(forfeit);
        }
    }

    private ThemedButton actionButton(int id, int x, int y, int width, int height, String label) {
        ThemedButton button = new ThemedButton(id, x, y, width, height, label,
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT);
        button.enabled = RankedClientData.isSeasonRunning()
                && !RankedClientData.amIQueued() && !RankedClientData.amIInMatch();
        return button;
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        switch (buttonId) {
            case BTN_QUEUE_1V1:
                queue(MatchMode.ONE_V_ONE, ClientLocalization.format(
                        "gui.rebornaddon.ranked.status.queued_1v1", "Queued for 1v1"));
                return true;
            case BTN_QUEUE_2V2:
                queue(MatchMode.TWO_V_TWO, ClientLocalization.format(
                        "gui.rebornaddon.ranked.status.queued_2v2", "Queued for 2v2"));
                return true;
            case BTN_QUEUE_3V3:
                queue(MatchMode.THREE_V_THREE, ClientLocalization.format(
                        "gui.rebornaddon.ranked.status.queued_3v3", "Queued for 3v3"));
                return true;
            case BTN_LEAVE_QUEUE:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_LEAVE, 0));
                showStatus(ClientLocalization.format("gui.rebornaddon.ranked.status.left", "Left queue"));
                return true;
            case BTN_FORFEIT:
                long now = System.currentTimeMillis();
                if (forfeitArmedUntil > now) {
                    RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_FORFEIT, 0));
                    forfeitArmedUntil = 0L;
                    showStatus(ClientLocalization.format("gui.rebornaddon.ranked.status.forfeited",
                            "Forfeited match"));
                } else {
                    forfeitArmedUntil = now + 3000L;
                    showStatus(ClientLocalization.format("gui.rebornaddon.ranked.status.confirm",
                            "Press again to confirm"));
                }
                return true;
            default:
                return false;
        }
    }

    private void queue(MatchMode mode, String message) {
        RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_QUEUE, mode.getNetId()));
        showStatus(message);
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int rightX = left + leftColumnWidth + COLUMN_GAP;
        int rightWidth = width - leftColumnWidth - COLUMN_GAP;

        GuiChrome.section(left, top, leftColumnWidth, height, Theme.RANKED_RED);
        GuiChrome.section(rightX, top, rightWidth, height, Theme.GOLD);
        GuiChrome.caption(font, ClientLocalization.format("gui.rebornaddon.ranked.matchmaking", "Matchmaking"),
                left + 10, top + 9, Theme.RANKED_RED_HOVER);
        GuiChrome.caption(font, ClientLocalization.format("gui.rebornaddon.ranked.record", "Shinobi Record"),
                rightX + 10, top + 9, Theme.GOLD);
        GuiChrome.rule(left + 9, left + leftColumnWidth - 8, top + 21, Theme.RANKED_RED);
        GuiChrome.rule(rightX + 9, rightX + rightWidth - 8, top + 21, Theme.GOLD);

        drawStats(font, rightX + 7, top + 29, rightWidth - 14, height - 40);
        drawStatus(font, left + 8, top + height - 13, leftColumnWidth - 16);
    }

    private void drawStats(FontRenderer font, int left, int top, int width, int height) {
        int elo = RankedClientData.getElo();
        int center = left + width / 2;
        int y = top;
        drawCenteredTrimmed(font, ClientLocalization.format("gui.rebornaddon.ranked.season",
                "%s  |  %sd left", RankedClientData.getSeasonDisplayName(),
                RankedClientData.getSeasonDaysRemaining()), center, y, width, Theme.TEXT_MUTED);
        y += 15;
        drawCenteredTrimmed(font, RankTier.forElo(elo), center, y, width, RankTier.colorForElo(elo));
        y += 13;
        drawCenteredTrimmed(font, ClientLocalization.format("gui.rebornaddon.ranked.elo", "%s ELO", elo),
                center, y, width, Theme.TEXT_LIGHT);
        y += 15;
        drawCenteredTrimmed(font, RankedClientData.getWins() + "W  " + RankedClientData.getLosses()
                + "L  " + RankedClientData.getDraws() + "D", center, y, width, Theme.TEXT_LIGHT);
        y += 13;

        int games = RankedClientData.getWins() + RankedClientData.getLosses() + RankedClientData.getDraws();
        String winRate = games == 0
                ? ClientLocalization.format("gui.rebornaddon.ranked.no_matches", "No matches played")
                : ClientLocalization.format("gui.rebornaddon.ranked.win_rate", "%.1f%% win rate",
                        RankedClientData.getWins() * 100.0D / games);
        drawCenteredTrimmed(font, winRate, center, y, width, Theme.TEXT_MUTED);
        y += 13;
        drawCenteredTrimmed(font, ClientLocalization.format("gui.rebornaddon.ranked.peak", "Peak %s",
                RankedClientData.getPeakElo()), center, y, width, Theme.TEXT_MUTED);
        y += 13;

        if (RankedClientData.getWinStreak() >= 2 && y <= top + height - 8) {
            drawCenteredTrimmed(font, ClientLocalization.format("gui.rebornaddon.ranked.streak",
                    "%s win streak", RankedClientData.getWinStreak()), center, y, width, Theme.SUCCESS_BRIGHT);
            y += 13;
        }
        String state = !RankedClientData.isSeasonRunning()
                ? ClientLocalization.format("gui.rebornaddon.ranked.state.paused", "Season paused")
                : RankedClientData.amIInMatch()
                ? ClientLocalization.format("gui.rebornaddon.ranked.state.active", "Match active")
                : RankedClientData.amIQueued()
                        ? ClientLocalization.format("gui.rebornaddon.ranked.state.queued", "Queued for %s",
                                RankedClientData.getQueuedModeLabel())
                        : ClientLocalization.format("gui.rebornaddon.ranked.state.ready", "Ready");
        drawCenteredTrimmed(font, state, center, Math.min(y + 2, top + height - 8), width,
                !RankedClientData.isSeasonRunning() ? Theme.RANKED_RED_HOVER
                        : RankedClientData.amIQueued() ? Theme.GOLD : Theme.SUCCESS_BRIGHT);
    }

    private void drawStatus(FontRenderer font, int x, int y, int width) {
        if (System.currentTimeMillis() >= statusMessageExpireAt) {
            statusMessage = "";
        }
        if (!statusMessage.isEmpty()) {
            font.drawString(font.trimStringToWidth(statusMessage, Math.max(20, width)), x, y, Theme.TEXT_MUTED);
        }
    }

    private static void drawCenteredTrimmed(FontRenderer font, String value, int center, int y,
                                            int width, int color) {
        String text = font.trimStringToWidth(value == null ? "" : value, Math.max(20, width));
        font.drawString(text, center - font.getStringWidth(text) / 2, y, color);
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusMessageExpireAt = System.currentTimeMillis() + 2500L;
    }

    @Override
    public boolean consumeButtonRefresh() {
        return builtRevision != RankedClientData.getRevision();
    }
}
