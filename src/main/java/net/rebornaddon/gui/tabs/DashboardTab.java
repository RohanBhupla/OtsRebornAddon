package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.ranked.elo.RankTier;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.client.ClientVillageLeadershipData;

import java.util.List;

public final class DashboardTab implements HubTab {
    private int missionRevision = -1;
    private int activeMissions;
    private int readyMissions;
    private int completedMissions;

    @Override
    public String getTabName() {
        return ClientLocalization.format("gui.rebornaddon.tab.overview", "Overview");
    }

    @Override
    public int getTabColorActive() {
        return Theme.GOLD;
    }

    @Override
    public int getTabColorHover() {
        return Theme.GOLD_DARK;
    }

    @Override
    public void buildButtons(List<GuiButton> buttonList, int contentLeft, int contentTop,
                             int contentWidth, int contentHeight) {
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GuiChrome.header(left, top, width, 34, Theme.GOLD);
        String playerName = Minecraft.getMinecraft().player == null
                ? "Shinobi" : Minecraft.getMinecraft().player.getName();
        GuiChrome.playerHead(playerName, left + 10, top + 8, 18);
        font.drawString(ClientLocalization.format("gui.rebornaddon.overview.record", "Shinobi Record"),
                left + 36, top + 8, Theme.GOLD);
        font.drawString(font.trimStringToWidth(playerName, Math.max(20, width - 50)),
                left + 36, top + 20, Theme.TEXT_MUTED);

        updateMissionCounts();
        ClientVillageLeadershipData.Snapshot leadership = ClientVillageLeadershipData.get();
        String villageName = display(ClientQuestData.get().village);
        if (villageName.isEmpty()) {
            Village village = Village.byId(leadership.villageId);
            villageName = village == null
                    ? ClientLocalization.format("gui.rebornaddon.overview.unassigned", "Unassigned")
                    : village.displayName();
        }
        String role = leadership.operator
                ? ClientLocalization.format("gui.rebornaddon.overview.role.operator", "Operator")
                : leadership.kage ? ClientLocalization.format("gui.rebornaddon.overview.role.kage", "Kage")
                : leadership.advisor ? ClientLocalization.format("gui.rebornaddon.overview.role.advisor", "Advisor")
                : leadership.captain ? ClientLocalization.format("gui.rebornaddon.overview.role.captain", "ANBU Captain")
                : ClientLocalization.format("gui.rebornaddon.overview.role.shinobi", "Shinobi");
        Village village = Village.byId(leadership.villageId);
        int villageAccent = village == null ? Theme.BLUE : village.accentColor();

        int gap = 5;
        int rowTop = top + 41;
        int rowHeight = Math.max(29, (height - 41 - gap * 4) / 5);
        drawRow(font, left, rowTop, width, rowHeight, Theme.SUCCESS, "Q",
                ClientLocalization.format("gui.rebornaddon.overview.missions", "Missions"),
                ClientLocalization.format("gui.rebornaddon.overview.mission_active", "%s active", activeMissions),
                ClientLocalization.format("gui.rebornaddon.overview.mission_ready", "%s ready  |  %s complete",
                        readyMissions, completedMissions));
        rowTop += rowHeight + gap;
        drawRow(font, left, rowTop, width, rowHeight, villageAccent, "V",
                ClientLocalization.format("gui.rebornaddon.overview.village", "Village"),
                villageName, role);
        rowTop += rowHeight + gap;

        String partyMain = RankedClientData.isInParty()
                ? ClientLocalization.format("gui.rebornaddon.overview.party_members", "%s members",
                        RankedClientData.getPartyMemberNames().size())
                : ClientLocalization.format("gui.rebornaddon.overview.party_empty", "No active party");
        String partyDetail = RankedClientData.isInParty()
                ? RankedClientData.isPartyLeader()
                        ? ClientLocalization.format("gui.rebornaddon.overview.party_leader", "Party leader")
                        : ClientLocalization.format("gui.rebornaddon.overview.party_member", "Party member")
                : ClientLocalization.format("gui.rebornaddon.overview.party_ready", "Ready for invitations");
        drawRow(font, left, rowTop, width, rowHeight, Theme.TEAL, "P",
                ClientLocalization.format("gui.rebornaddon.overview.party", "Party"), partyMain, partyDetail);
        rowTop += rowHeight + gap;

        int elo = RankedClientData.getElo();
        String rankedState = RankedClientData.amIInMatch()
                ? ClientLocalization.format("gui.rebornaddon.ranked.state.active", "Match active")
                : RankedClientData.amIQueued()
                        ? ClientLocalization.format("gui.rebornaddon.overview.ranked_queued", "Queued %s",
                                RankedClientData.getQueuedModeLabel())
                        : ClientLocalization.format("gui.rebornaddon.ranked.state.ready", "Ready");
        drawRow(font, left, rowTop, width, rowHeight, Theme.RANKED_RED, "R",
                ClientLocalization.format("gui.rebornaddon.overview.ranked", "Ranked"),
                RankTier.forElo(elo) + "  |  " + elo + " ELO", rankedState);
        rowTop += rowHeight + gap;
        drawRow(font, left, rowTop, width, rowHeight, Theme.COPPER, "S",
                ClientLocalization.format("gui.rebornaddon.overview.store", "Store"),
                ClientLocalization.format("gui.rebornaddon.overview.store_catalog", "Vendor catalog"),
                ClientLocalization.format("gui.rebornaddon.overview.store_categories",
                        "Wardrobe  |  Music  |  Scrolls"));
    }

    private void updateMissionCounts() {
        int revision = ClientQuestData.getRevision();
        if (missionRevision == revision) {
            return;
        }
        activeMissions = 0;
        readyMissions = 0;
        completedMissions = 0;
        for (ClientQuestData.Quest quest : ClientQuestData.get().quests) {
            if (quest.status == 2) {
                activeMissions++;
            } else if (quest.status == 3) {
                readyMissions++;
            } else if (quest.status == 4) {
                completedMissions++;
            }
        }
        missionRevision = revision;
    }

    private static void drawRow(FontRenderer font, int x, int y, int width, int height, int accent,
                                String mark, String title, String primary, String secondary) {
        Gui.drawRect(x + 2, y + 2, x + width + 2, y + height + 2, 0x50000000);
        Gui.drawRect(x, y, x + width, y + height, Theme.EDGE_DARK);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, Theme.BUTTON_BORDER);
        Gui.drawRect(x + 2, y + 2, x + width - 2, y + height - 2, Theme.PARCHMENT_DARK);
        Gui.drawRect(x + 2, y + 2, x + width - 2, y + 5, Theme.PARCHMENT_LIGHT);
        Gui.drawRect(x + 2, y + 2, x + 6, y + height - 2, accent);
        Gui.drawRect(x + width - 18, y + height - 6, x + width - 7, y + height - 5, accent);
        int sealSize = Math.max(18, Math.min(24, height - 8));
        int sealX = x + 10;
        int sealY = y + (height - sealSize) / 2;
        Gui.drawRect(sealX + 1, sealY + 2, sealX + sealSize + 2, sealY + sealSize + 2, 0x65000000);
        Gui.drawRect(sealX, sealY, sealX + sealSize, sealY + sealSize, Theme.EDGE_DARK);
        Gui.drawRect(sealX + 1, sealY + 1, sealX + sealSize - 1, sealY + sealSize - 1, accent);
        Gui.drawRect(sealX + 3, sealY + 3, sealX + sealSize - 3, sealY + sealSize - 3, Theme.INK);
        font.drawString(mark, sealX + (sealSize - font.getStringWidth(mark)) / 2,
                sealY + (sealSize - 8) / 2, accent);

        int textX = sealX + sealSize + 9;
        int textWidth = width - (textX - x) - 8;
        font.drawString(font.trimStringToWidth(title, Math.max(20, textWidth)), textX, y + 5, Theme.TEXT_LIGHT);
        if (height >= 30) {
            int half = Math.max(20, (textWidth - 8) / 2);
            String first = font.trimStringToWidth(primary, half);
            String second = font.trimStringToWidth(secondary, half);
            int lineY = y + height - 13;
            font.drawString(first, textX, lineY, accent);
            font.drawString(second, textX + textWidth - font.getStringWidth(second), lineY, Theme.TEXT_MUTED);
        }
    }

    private static String display(String value) {
        return value == null || value.isEmpty() ? ""
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        return false;
    }
}
