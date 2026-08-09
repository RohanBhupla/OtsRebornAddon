package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.ranked.elo.RankTier;
import net.rebornaddon.ranked.match.MatchMode;
import net.rebornaddon.ranked.network.QueueActionMessage;
import net.rebornaddon.ranked.network.RankedNetwork;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;

/**
 * Has its own internal sub-tab bar (Battle / Party, Leaderboard to follow later) -
 * same nested-tab pattern as the outer hub, just one level deeper.
 */
public class RankedTab implements HubTab {

    // Sub-tab switch buttons: 100-109
    private static final int SUBTAB_BATTLE = 100;
    private static final int SUBTAB_PARTY = 101;

    // Battle sub-tab actions: 110-119
    private static final int BTN_QUEUE_1V1 = 110;
    private static final int BTN_QUEUE_2V2 = 111;
    private static final int BTN_QUEUE_3V3 = 112;
    private static final int BTN_LEAVE_QUEUE = 113;
    private static final int BTN_FORFEIT = 114;

    // Party sub-tab actions: 120-129
    private static final int BTN_PARTY_INVITE = 120;
    private static final int BTN_PARTY_ACCEPT = 121;
    private static final int BTN_PARTY_LEAVE = 122;

    // Per-member-row action buttons, one ID per party slot (max 3 members)
    private static final int BTN_PARTY_TP_BASE = 130;       // 130-132
    private static final int BTN_PARTY_KICK_BASE = 140;      // 140-142
    private static final int BTN_PARTY_PROMOTE_BASE = 150;   // 150-152

    // Clickable online-player invite shortcuts, one ID per visible slot
    private static final int BTN_INVITE_ONLINE_BASE = 160;   // 160-163
    private static final int MAX_VISIBLE_INVITE_CANDIDATES = 4;

    private static final int LEFT_COL_WIDTH = 170;
    private static final int COLUMN_GAP = 16;
    private static final int SUBTAB_BAR_HEIGHT = 24;
    private static final int MEMBER_ROW_HEIGHT = 20;

    private int selectedSubTab = 0; // 0 = Battle, 1 = Party

    private String statusMessage = "";
    private long statusMessageExpireAt = 0;
    private long forfeitArmedUntil = 0; // 0 = not armed; otherwise a click-again window

    // Created once and kept alive across rebuilds so partial typing never gets lost.
    private GuiTextField inviteNameField;

    // Snapshots taken at the moment buttons were last built, so handleButtonClick can
    // reliably map "which slot was clicked" back to a name even if the underlying
    // data changes a moment later (next sync tick, etc).
    private List<String> lastBuiltMemberNames = Collections.emptyList();
    private List<String> lastBuiltInviteCandidates = Collections.emptyList();

    // Where the member list starts, in both buildButtons and drawContent - kept as a
    // field so the two stay in sync without duplicating the layout math twice.
    private int memberListStartY = 0;

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
        int subTabW = 90;
        int subTabH = 18;
        buttonList.add(new ThemedButton(SUBTAB_BATTLE, left, top, subTabW, subTabH, "Battle",
                subTabColor(0), subTabHoverColor(0), Theme.BUTTON_TEXT));
        buttonList.add(new ThemedButton(SUBTAB_PARTY, left + subTabW + 6, top, subTabW, subTabH, "Party",
                subTabColor(1), subTabHoverColor(1), Theme.BUTTON_TEXT));

        int contentTop = top + SUBTAB_BAR_HEIGHT;
        int contentHeight = height - SUBTAB_BAR_HEIGHT;

        if (selectedSubTab == 0) {
            buildBattleButtons(buttonList, left, contentTop, width, contentHeight);
        } else {
            buildPartyButtons(buttonList, left, contentTop, width, contentHeight);
        }
    }

    private int subTabColor(int index) {
        return selectedSubTab == index ? Theme.RANKED_RED : Theme.PURPLE_DARK;
    }

    private int subTabHoverColor(int index) {
        return selectedSubTab == index ? Theme.RANKED_RED_HOVER : Theme.PURPLE_MID;
    }

    private void buildBattleButtons(List<GuiButton> buttonList, int left, int top, int width, int height) {
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
            boolean armed = forfeitArmedUntil > System.currentTimeMillis();
            String label = armed ? "Confirm Forfeit?" : "Forfeit Match";
            int bg = armed ? 0xFFE05A4B : Theme.DANGER;
            int hover = armed ? 0xFFFF7A6B : 0xFFE05A4B;
            buttonList.add(new ThemedButton(BTN_FORFEIT, x, y, btnW, btnH, label, bg, hover, Theme.BUTTON_TEXT));
        }
    }

    private void buildPartyButtons(List<GuiButton> buttonList, int left, int top, int width, int height) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int rightColX = left + LEFT_COL_WIDTH + COLUMN_GAP;
        int rightColWidth = width - LEFT_COL_WIDTH - COLUMN_GAP;
        int rowRight = rightColX + rightColWidth;

        // ---- LEFT COLUMN: invite ----
        int y = top;
        List<String> candidates = RankedClientData.getInvitablePlayers();
        lastBuiltInviteCandidates = candidates;
        int shownCandidates = Math.min(candidates.size(), MAX_VISIBLE_INVITE_CANDIDATES);
        for (int i = 0; i < shownCandidates; i++) {
            buttonList.add(new ThemedButton(BTN_INVITE_ONLINE_BASE + i, left, y, LEFT_COL_WIDTH, 20,
                    candidates.get(i), Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
            y += 24;
        }
        if (shownCandidates == 0) {
            y += 14; // room for the "no one else online" text drawn in drawContent
        }

        if (inviteNameField == null) {
            inviteNameField = new GuiTextField(0, fr, left, y, LEFT_COL_WIDTH, 18);
            inviteNameField.setMaxStringLength(16); // Minecraft usernames are never longer than this
        }
        y += 22;
        buttonList.add(new ThemedButton(BTN_PARTY_INVITE, left, y, LEFT_COL_WIDTH, 22, "Invite by Name",
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
        y += 30;

        if (RankedClientData.isInParty()) {
            buttonList.add(new ThemedButton(BTN_PARTY_LEAVE, left, y, LEFT_COL_WIDTH, 22, "Leave Party",
                    Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
        }

        // ---- RIGHT COLUMN: pending invite + member list ----
        int ry = top;
        String pendingFrom = RankedClientData.getPendingInviteFrom();
        if (pendingFrom != null) {
            buttonList.add(new ThemedButton(BTN_PARTY_ACCEPT, rightColX, ry, rightColWidth, 22,
                    "Accept " + pendingFrom + "'s Invite", Theme.SUCCESS, 0xFF66BB6A, Theme.BUTTON_TEXT));
            ry += 28;
        }

        if (!RankedClientData.isInParty()) {
            lastBuiltMemberNames = Collections.emptyList();
            return;
        }

        ry += 14; // "Party Members" header, drawn in drawContent
        memberListStartY = ry;

        List<String> members = RankedClientData.getPartyMemberNames();
        lastBuiltMemberNames = members;
        boolean amLeader = RankedClientData.isPartyLeader();
        String myName = Minecraft.getMinecraft().player.getName();
        int cooldownSeconds = RankedClientData.getPartyTeleportCooldownSeconds();

        int btnW = 42;
        int btnH = 16;
        int btnGap = 2;

        for (int i = 0; i < members.size(); i++) {
            String name = members.get(i);
            boolean isSelf = name.equalsIgnoreCase(myName);
            int rowY = ry + i * MEMBER_ROW_HEIGHT;
            int cursorX = rowRight;

            if (!isSelf && amLeader) {
                cursorX -= btnW;
                buttonList.add(new ThemedButton(BTN_PARTY_PROMOTE_BASE + i, cursorX, rowY, btnW, btnH, "Lead",
                        Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
                cursorX -= btnGap;

                cursorX -= btnW;
                buttonList.add(new ThemedButton(BTN_PARTY_KICK_BASE + i, cursorX, rowY, btnW, btnH, "Kick",
                        Theme.DANGER, 0xFFE05A4B, Theme.BUTTON_TEXT));
                cursorX -= btnGap;
            }

            if (!isSelf) {
                cursorX -= btnW;
                String tpLabel = cooldownSeconds > 0 ? cooldownSeconds + "s" : "TP";
                ThemedButton tpBtn = new ThemedButton(BTN_PARTY_TP_BASE + i, cursorX, rowY, btnW, btnH, tpLabel,
                        Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT);
                if (cooldownSeconds > 0) tpBtn.enabled = false;
                buttonList.add(tpBtn);
            }
        }
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        if (buttonId >= BTN_INVITE_ONLINE_BASE && buttonId < BTN_INVITE_ONLINE_BASE + MAX_VISIBLE_INVITE_CANDIDATES) {
            int slot = buttonId - BTN_INVITE_ONLINE_BASE;
            if (slot < lastBuiltInviteCandidates.size()) {
                String target = lastBuiltInviteCandidates.get(slot);
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_PARTY_INVITE, 0, target));
                showStatus("Invite sent to " + target);
            }
            return true;
        }
        if (buttonId >= BTN_PARTY_TP_BASE && buttonId < BTN_PARTY_TP_BASE + 3) {
            sendPartyMemberAction(buttonId - BTN_PARTY_TP_BASE, QueueActionMessage.ACTION_PARTY_TELEPORT, "Teleporting to ");
            return true;
        }
        if (buttonId >= BTN_PARTY_KICK_BASE && buttonId < BTN_PARTY_KICK_BASE + 3) {
            sendPartyMemberAction(buttonId - BTN_PARTY_KICK_BASE, QueueActionMessage.ACTION_PARTY_KICK, "Kicking ");
            return true;
        }
        if (buttonId >= BTN_PARTY_PROMOTE_BASE && buttonId < BTN_PARTY_PROMOTE_BASE + 3) {
            sendPartyMemberAction(buttonId - BTN_PARTY_PROMOTE_BASE, QueueActionMessage.ACTION_PARTY_PROMOTE, "Promoting ");
            return true;
        }

        switch (buttonId) {
            case SUBTAB_BATTLE:
                selectedSubTab = 0;
                return true;
            case SUBTAB_PARTY:
                selectedSubTab = 1;
                return true;

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
                long now = System.currentTimeMillis();
                if (forfeitArmedUntil > now) {
                    RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_FORFEIT, 0));
                    forfeitArmedUntil = 0;
                    showStatus("Forfeited match");
                } else {
                    forfeitArmedUntil = now + 3000L;
                    showStatus("Click Forfeit again within 3s to confirm");
                }
                return true;

            case BTN_PARTY_INVITE:
                String target = inviteNameField != null ? inviteNameField.getText().trim() : "";
                if (target.isEmpty()) {
                    showStatus("Type a player name first");
                    return true;
                }
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_PARTY_INVITE, 0, target));
                if (inviteNameField != null) inviteNameField.setText("");
                showStatus("Invite sent to " + target);
                return true;
            case BTN_PARTY_ACCEPT:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_PARTY_ACCEPT, 0));
                showStatus("Joined party");
                return true;
            case BTN_PARTY_LEAVE:
                RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(QueueActionMessage.ACTION_PARTY_LEAVE, 0));
                showStatus("Left party");
                return true;

            default:
                return false;
        }
    }

    private void sendPartyMemberAction(int slot, int action, String statusPrefix) {
        if (slot < 0 || slot >= lastBuiltMemberNames.size()) return;
        String targetName = lastBuiltMemberNames.get(slot);
        RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(action, 0, targetName));
        showStatus(statusPrefix + targetName);
    }

    @Override
    public void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (selectedSubTab == 1 && inviteNameField != null) {
            inviteNameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void handleKeyTyped(char typedChar, int keyCode) {
        if (selectedSubTab == 1 && inviteNameField != null) {
            inviteNameField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void onTick() {
        if (inviteNameField != null) {
            inviteNameField.updateCursorCounter();
        }
    }

    private void showStatus(String message) {
        this.statusMessage = message;
        this.statusMessageExpireAt = System.currentTimeMillis() + 2500L;
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height, int mouseX, int mouseY) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        int contentTop = top + SUBTAB_BAR_HEIGHT;
        int contentHeight = height - SUBTAB_BAR_HEIGHT;

        int rightColX = left + LEFT_COL_WIDTH + COLUMN_GAP;
        int rightColWidth = width - LEFT_COL_WIDTH - COLUMN_GAP;

        int dividerX = left + LEFT_COL_WIDTH + COLUMN_GAP / 2;
        Gui.drawRect(dividerX, contentTop, dividerX + 1, contentTop + contentHeight, Theme.PURPLE_LIGHT);

        if (selectedSubTab == 0) {
            drawStats(fr, rightColX, contentTop, rightColWidth, contentHeight);
        } else {
            drawPartyContent(fr, left, rightColX, contentTop, rightColWidth, contentHeight);
        }

        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageExpireAt) {
            String msg = "\u00a77" + statusMessage;
            fr.drawString(msg, left, top + height - 12, Theme.TEXT_MUTED);
        } else if (System.currentTimeMillis() >= statusMessageExpireAt) {
            statusMessage = "";
        }
    }

    private void drawStats(FontRenderer fr, int left, int top, int width, int height) {
        int elo = RankedClientData.getElo();
        int wins = RankedClientData.getWins();
        int losses = RankedClientData.getLosses();
        int draws = RankedClientData.getDraws();
        int winStreak = RankedClientData.getWinStreak();
        int peakElo = RankedClientData.getPeakElo();
        boolean inMatch = RankedClientData.amIInMatch();

        int centerX = left + width / 2;
        int y = top;

        drawCentered(fr, "\u00a77Season " + RankedClientData.getSeasonNumber() + " - "
                + RankedClientData.getSeasonDaysRemaining() + "d left", centerX, y, Theme.TEXT_MUTED);
        y += 12;

        drawCentered(fr, "\u00a77Your ELO", centerX, y, Theme.TEXT_MUTED);
        y += 12;
        drawCenteredScaled(fr, String.valueOf(elo), centerX, y, Theme.RANKED_RED_HOVER, 1.6f);
        y += 22;

        drawCentered(fr, RankTier.forElo(elo), centerX, y, RankTier.colorForElo(elo));
        y += 14;

        String record = wins + "W  -  " + losses + "L  -  " + draws + "D";
        drawCentered(fr, record, centerX, y, Theme.TEXT_LIGHT);
        y += 12;

        double totalGames = wins + losses + draws;
        String winRate = totalGames > 0 ? String.format("%.1f%% win rate", (wins / totalGames) * 100.0) : "No matches played yet";
        drawCentered(fr, "\u00a77" + winRate, centerX, y, Theme.TEXT_MUTED);
        y += 12;

        if (winStreak >= 2) {
            drawCentered(fr, "\u00a7c\u00a7l" + winStreak + " win streak!", centerX, y, Theme.SUCCESS);
            y += 12;
        }
        drawCentered(fr, "\u00a77Peak ELO: " + peakElo, centerX, y, Theme.TEXT_MUTED);
        y += 14;

        if (inMatch) {
            drawCentered(fr, "\u00a7aCurrently in a match", centerX, y, Theme.SUCCESS);
            y += 14;
        }

        String queuedLabel = RankedClientData.getQueuedModeLabel();
        if (queuedLabel != null) {
            drawCentered(fr, "\u00a7eIn queue for " + queuedLabel, centerX, y, Theme.RANKED_RED_HOVER);
            y += 14;
        }
    }

    private void drawPartyContent(FontRenderer fr, int leftColX, int rightColX, int top, int rightColWidth, int height) {
        // ---- LEFT COLUMN ----
        int ly = top;
        List<String> candidates = lastBuiltInviteCandidates;
        int shownCandidates = Math.min(candidates.size(), MAX_VISIBLE_INVITE_CANDIDATES);
        if (shownCandidates == 0) {
            fr.drawString("\u00a77No one else online", leftColX, ly, Theme.TEXT_MUTED);
        }
        // (candidate buttons render themselves - nothing extra to draw here for them)

        if (inviteNameField != null) {
            inviteNameField.drawTextBox();
        }

        // ---- RIGHT COLUMN ----
        int centerX = rightColX + rightColWidth / 2;
        int y = top;

        String pendingFrom = RankedClientData.getPendingInviteFrom();
        if (pendingFrom != null) {
            y += 28; // the Accept button itself carries the message - just reserve its row
        }

        if (!RankedClientData.isInParty()) {
            drawCentered(fr, "\u00a77You're not in a party", centerX, y, Theme.TEXT_MUTED);
            drawCentered(fr, "\u00a77Click a name or type one to invite", centerX, y + 12, Theme.TEXT_MUTED);
            return;
        }

        drawCentered(fr, "\u00a7fParty Members", centerX, y, Theme.TEXT_LIGHT);

        List<String> members = lastBuiltMemberNames;
        boolean amLeader = RankedClientData.isPartyLeader();
        for (int i = 0; i < members.size(); i++) {
            String name = members.get(i);
            String label = (i == 0 ? "\u00a76\u2605 " : "\u00a7f  ") + name; // star marks the leader
            int rowY = memberListStartY + i * MEMBER_ROW_HEIGHT;
            fr.drawString(label, rightColX, rowY + 4, i == 0 ? Theme.GOLD : Theme.TEXT_LIGHT);
        }

        int afterRows = memberListStartY + members.size() * MEMBER_ROW_HEIGHT + 6;
        if (amLeader) {
            drawCentered(fr, "\u00a77You're the party leader", centerX, afterRows, Theme.TEXT_MUTED);
        } else {
            drawCentered(fr, "\u00a77Only the leader can start the queue", centerX, afterRows, Theme.TEXT_MUTED);
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
