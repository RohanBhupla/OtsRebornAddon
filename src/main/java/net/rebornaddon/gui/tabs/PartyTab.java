package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.ranked.network.QueueActionMessage;
import net.rebornaddon.ranked.network.RankedNetwork;

import java.util.Collections;
import java.util.List;

public final class PartyTab implements HubTab {
    private static final int INVITE = 500;
    private static final int ACCEPT = 501;
    private static final int LEAVE = 502;
    private static final int INVITE_ONLINE_BASE = 510;
    private static final int TELEPORT_BASE = 520;
    private static final int KICK_BASE = 530;
    private static final int PROMOTE_BASE = 540;
    private static final int MAX_CANDIDATES = 4;
    private static final int MEMBER_ACTION_ID_CAPACITY = 10;
    private static final int COLUMN_GAP = 10;

    private GuiTextField inviteField;
    private List<String> candidates = Collections.emptyList();
    private List<String> members = Collections.emptyList();
    private int visibleCandidates;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int memberTop;
    private int memberRowHeight;
    private int builtRevision = -1;
    private String status = "";
    private long statusExpires;

    @Override
    public String getTabName() {
        return ClientLocalization.format("gui.rebornaddon.tab.party", "Party");
    }

    @Override
    public int getTabColorActive() {
        return Theme.TEAL;
    }

    @Override
    public int getTabColorHover() {
        return Theme.TEAL_LIGHT;
    }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        builtRevision = RankedClientData.getRevision();
        leftWidth = Math.max(88, Math.min(190, (width - COLUMN_GAP) / 2));
        rightX = left + leftWidth + COLUMN_GAP;
        rightWidth = width - leftWidth - COLUMN_GAP;

        candidates = RankedClientData.getInvitablePlayers();
        members = RankedClientData.getPartyMemberNames();
        int listTop = top + 27;
        int reserved = RankedClientData.isInParty() ? 69 : 43;
        visibleCandidates = Math.min(MAX_CANDIDATES,
                Math.min(candidates.size(), Math.max(0, (height - reserved - 27) / 22)));
        int y = listTop;
        for (int i = 0; i < visibleCandidates; i++) {
            buttons.add(new ThemedButton(INVITE_ONLINE_BASE + i, left + 6, y, leftWidth - 12, 18,
                    candidates.get(i), Theme.TAB_INACTIVE_BG, Theme.TEAL, Theme.BUTTON_TEXT));
            y += 22;
        }
        if (visibleCandidates == 0) {
            y += 13;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (inviteField == null) {
            inviteField = new GuiTextField(0, font, left + 6, y, leftWidth - 12, 18);
            inviteField.setMaxStringLength(16);
        } else {
            inviteField.x = left + 6;
            inviteField.y = y;
            inviteField.width = leftWidth - 12;
        }
        y += 22;
        buttons.add(new ThemedButton(INVITE, left + 6, y, leftWidth - 12, 19,
                ClientLocalization.format("gui.rebornaddon.party.invite", "Invite by Name"),
                Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT));
        y += 24;
        if (RankedClientData.isInParty() && y + 19 <= top + height - 5) {
            buttons.add(new ThemedButton(LEAVE, left + 6, y, leftWidth - 12, 19,
                    ClientLocalization.format("gui.rebornaddon.party.leave", "Leave Party"),
                    Theme.TAB_INACTIVE_BG, Theme.DANGER, Theme.BUTTON_TEXT));
        }

        int ry = listTop;
        String pending = RankedClientData.getPendingInviteFrom();
        if (pending != null) {
            buttons.add(new ThemedButton(ACCEPT, rightX + 6, ry, rightWidth - 12, 20,
                    ClientLocalization.format("gui.rebornaddon.party.accept", "Accept %s", pending),
                    Theme.SUCCESS, Theme.SUCCESS_BRIGHT, Theme.BUTTON_TEXT));
            ry += 25;
        }
        memberTop = ry;
        memberRowHeight = Math.max(28, Math.min(38, Math.max(1, height - (memberTop - top) - 19)
                / Math.max(1, members.size())));

        boolean leader = RankedClientData.isPartyLeader();
        String self = Minecraft.getMinecraft().player == null ? "" : Minecraft.getMinecraft().player.getName();
        int actionGap = 2;
        int actionWidth = Math.max(24, Math.min(34, (rightWidth - 16 - actionGap * 2) / 3));
        for (int i = 0; i < members.size(); i++) {
            String name = members.get(i);
            if (name.equalsIgnoreCase(self)) {
                continue;
            }
            int rowY = memberTop + i * memberRowHeight;
            int buttonY = rowY + 13;
            int cursor = rightX + rightWidth - 6;
            if (leader) {
                cursor -= actionWidth;
                buttons.add(new ThemedButton(PROMOTE_BASE + i, cursor, buttonY, actionWidth, 16,
                        ClientLocalization.format("gui.rebornaddon.party.lead", "Lead"),
                        Theme.TAB_INACTIVE_BG, Theme.GOLD_DARK, Theme.BUTTON_TEXT));
                cursor -= actionGap + actionWidth;
                buttons.add(new ThemedButton(KICK_BASE + i, cursor, buttonY, actionWidth, 16,
                        ClientLocalization.format("gui.rebornaddon.party.kick", "Kick"),
                        Theme.TAB_INACTIVE_BG, Theme.DANGER, Theme.BUTTON_TEXT));
                cursor -= actionGap;
            }
            cursor -= actionWidth;
            ThemedButton teleport = new ThemedButton(TELEPORT_BASE + i, cursor, buttonY, actionWidth, 16,
                    RankedClientData.getPartyTeleportCooldownSeconds() > 0
                            ? RankedClientData.getPartyTeleportCooldownSeconds() + "s" : "TP",
                    Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT);
            teleport.enabled = RankedClientData.getPartyTeleportCooldownSeconds() <= 0;
            buttons.add(teleport);
        }
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        if (buttonId >= INVITE_ONLINE_BASE && buttonId < INVITE_ONLINE_BASE + MAX_CANDIDATES) {
            int index = buttonId - INVITE_ONLINE_BASE;
            if (index < candidates.size()) {
                send(QueueActionMessage.ACTION_PARTY_INVITE, candidates.get(index));
                showStatus(ClientLocalization.format("gui.rebornaddon.party.status.invited",
                        "Invite sent to %s", candidates.get(index)));
            }
            return true;
        }
        if (buttonId >= TELEPORT_BASE && buttonId < TELEPORT_BASE + MEMBER_ACTION_ID_CAPACITY) {
            return memberAction(buttonId - TELEPORT_BASE, QueueActionMessage.ACTION_PARTY_TELEPORT,
                    "gui.rebornaddon.party.status.teleporting", "Teleporting to %s");
        }
        if (buttonId >= KICK_BASE && buttonId < KICK_BASE + MEMBER_ACTION_ID_CAPACITY) {
            return memberAction(buttonId - KICK_BASE, QueueActionMessage.ACTION_PARTY_KICK,
                    "gui.rebornaddon.party.status.removing", "Removing %s");
        }
        if (buttonId >= PROMOTE_BASE && buttonId < PROMOTE_BASE + MEMBER_ACTION_ID_CAPACITY) {
            return memberAction(buttonId - PROMOTE_BASE, QueueActionMessage.ACTION_PARTY_PROMOTE,
                    "gui.rebornaddon.party.status.promoting", "Promoting %s");
        }
        switch (buttonId) {
            case INVITE:
                String target = inviteField == null ? "" : inviteField.getText().trim();
                if (target.isEmpty()) {
                    showStatus(ClientLocalization.format("gui.rebornaddon.party.status.enter_name",
                            "Enter a player name"));
                } else {
                    send(QueueActionMessage.ACTION_PARTY_INVITE, target);
                    inviteField.setText("");
                    showStatus(ClientLocalization.format("gui.rebornaddon.party.status.invited",
                            "Invite sent to %s", target));
                }
                return true;
            case ACCEPT:
                send(QueueActionMessage.ACTION_PARTY_ACCEPT, "");
                showStatus(ClientLocalization.format("gui.rebornaddon.party.status.accepted",
                        "Party invite accepted"));
                return true;
            case LEAVE:
                send(QueueActionMessage.ACTION_PARTY_LEAVE, "");
                showStatus(ClientLocalization.format("gui.rebornaddon.party.status.left", "Left party"));
                return true;
            default:
                return false;
        }
    }

    private boolean memberAction(int index, int action, String key, String fallback) {
        if (index >= 0 && index < members.size()) {
            send(action, members.get(index));
            showStatus(ClientLocalization.format(key, fallback, members.get(index)));
        }
        return true;
    }

    private void send(int action, String target) {
        RankedNetwork.CHANNEL.sendToServer(new QueueActionMessage(action, 0, target));
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GuiChrome.section(left, top, leftWidth, height, Theme.TEAL);
        GuiChrome.section(rightX, top, rightWidth, height, Theme.GOLD);
        GuiChrome.caption(font, ClientLocalization.format("gui.rebornaddon.party.online", "Online Shinobi"),
                left + 10, top + 9, Theme.TEAL_LIGHT);
        GuiChrome.caption(font, ClientLocalization.format("gui.rebornaddon.party.roster", "Party Roster"),
                rightX + 10, top + 9, Theme.GOLD);
        GuiChrome.rule(left + 9, left + leftWidth - 8, top + 21, Theme.TEAL);
        GuiChrome.rule(rightX + 9, rightX + rightWidth - 8, top + 21, Theme.GOLD);

        if (visibleCandidates == 0) {
            font.drawString(ClientLocalization.format("gui.rebornaddon.party.none_online", "No one else online"),
                    left + 7, top + 30, Theme.TEXT_MUTED);
        }
        if (inviteField != null) {
            inviteField.drawTextBox();
        }

        if (!RankedClientData.isInParty()) {
            drawWrappedCentered(font, ClientLocalization.format("gui.rebornaddon.party.empty", "No active party"),
                    rightX + 7, top + 34, rightWidth - 14, Theme.TEXT_MUTED);
            drawWrappedCentered(font, ClientLocalization.format("gui.rebornaddon.party.empty_hint",
                            "Invite a player to begin"), rightX + 7, top + 48, rightWidth - 14, Theme.TEXT_MUTED);
        } else {
            for (int i = 0; i < members.size(); i++) {
                String name = members.get(i);
                int rowY = memberTop + i * memberRowHeight;
                GuiChrome.playerHead(name, rightX + 6, rowY + 1, 10);
                String label = i == 0
                        ? ClientLocalization.format("gui.rebornaddon.party.leader", "Leader  %s", name) : name;
                int color = i == 0 ? Theme.GOLD : Theme.TEXT_LIGHT;
                font.drawString(font.trimStringToWidth(label, Math.max(20, rightWidth - 20)),
                        rightX + 19, rowY + 2, color);
            }
        }

        if (System.currentTimeMillis() >= statusExpires) {
            status = "";
        }
        if (!status.isEmpty()) {
            font.drawString(font.trimStringToWidth(status, Math.max(20, width - 16)),
                    left + 7, top + height - 12, Theme.TEXT_MUTED);
        }
    }

    private static void drawWrappedCentered(FontRenderer font, String value, int left, int y, int width, int color) {
        String text = font.trimStringToWidth(value, Math.max(20, width));
        font.drawString(text, left + (width - font.getStringWidth(text)) / 2, y, color);
    }

    private void showStatus(String message) {
        status = message;
        statusExpires = System.currentTimeMillis() + 2500L;
    }

    @Override
    public void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (inviteField != null) {
            inviteField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void handleKeyTyped(char typedChar, int keyCode) {
        if (inviteField != null) {
            inviteField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void onTick() {
        if (inviteField != null) {
            inviteField.updateCursorCounter();
        }
    }

    @Override
    public boolean consumeButtonRefresh() {
        return builtRevision != RankedClientData.getRevision();
    }
}
