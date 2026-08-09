package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class QuestsTab implements HubTab {
    private static final int MODE_MISSIONS = 200;
    private static final int MODE_VILLAGE = 201;
    private static final int CUSTOM_TAB_BASE = 210;
    private static final int CUSTOM_PAGE_PREV = 218;
    private static final int CUSTOM_PAGE_NEXT = 219;
    private static final int RANK_BASE = 220;
    private static final int QUEST_BASE = 300;
    private static final int QUEST_PAGE_PREV = 390;
    private static final int QUEST_PAGE_NEXT = 391;
    private static final int DETAIL_UP = 392;
    private static final int DETAIL_DOWN = 393;
    private static final int CLAIM_REWARDS = 394;
    private static final int REFRESH = 399;
    private static final String[] RANKS = new String[]{"D", "C", "B", "A", "S"};
    private static final int QUESTS_PER_PAGE = 7;

    private int mode;
    private String selectedRank = "D";
    private String selectedCustomTab = "";
    private int customTabPage;
    private int questPage;
    private int selectedQuestId = -1;
    private int detailOffset;
    private int builtRevision = -1;
    private int customTabsPerPage = 1;
    private boolean requestSent;
    private boolean claimPending;
    private List<ClientQuestData.Tab> visibleCustomTabs = Collections.emptyList();
    private List<ClientQuestData.Quest> filteredQuests = Collections.emptyList();

    @Override
    public String getTabName() {
        return "Quests";
    }

    @Override
    public int getTabColorActive() {
        return Theme.SUCCESS;
    }

    @Override
    public int getTabColorHover() {
        return Theme.TAB_HOVER_BG;
    }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        requestIfNeeded();
        ClientQuestData.Snapshot data = ClientQuestData.get();
        int revision = ClientQuestData.getRevision();
        if (builtRevision != revision) {
            claimPending = false;
        }
        builtRevision = revision;
        if (mode == 2 && selectedTab(data) == null) {
            mode = 0;
            selectedCustomTab = "";
        }

        buttons.add(modeButton(MODE_MISSIONS, left, top, 72, "Missions", mode == 0));
        buttons.add(modeButton(MODE_VILLAGE, left + 76, top, 72, "Village", mode == 1));

        int customX = left + 152;
        int controlsX = left + width - 92;
        customTabsPerPage = Math.max(1, Math.min(3, Math.max(1, controlsX - customX - 4) / 72));
        int maxCustomPage = Math.max(0, (data.tabs.size() - 1) / customTabsPerPage);
        customTabPage = Math.min(customTabPage, maxCustomPage);
        int customStart = customTabPage * customTabsPerPage;
        int customEnd = Math.min(data.tabs.size(), customStart + customTabsPerPage);
        visibleCustomTabs = new ArrayList<ClientQuestData.Tab>(data.tabs.subList(customStart, customEnd));
        for (int i = 0; i < visibleCustomTabs.size(); i++) {
            ClientQuestData.Tab tab = visibleCustomTabs.get(i);
            boolean active = mode == 2 && tab.id.equals(selectedCustomTab);
            buttons.add(modeButton(CUSTOM_TAB_BASE + i, customX + i * 72, top, 68, tab.title, active));
        }

        ThemedButton previousTabs = new ThemedButton(CUSTOM_PAGE_PREV, controlsX, top, 18, 18, "<",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        previousTabs.enabled = customTabPage > 0;
        buttons.add(previousTabs);
        ThemedButton nextTabs = new ThemedButton(CUSTOM_PAGE_NEXT, controlsX + 20, top, 18, 18, ">",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        nextTabs.enabled = customTabPage < maxCustomPage;
        buttons.add(nextTabs);
        buttons.add(new ThemedButton(REFRESH, controlsX + 42, top, 50, 18, "Refresh",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));

        if (mode == 0) {
            for (int i = 0; i < RANKS.length; i++) {
                boolean active = RANKS[i].equals(selectedRank);
                buttons.add(new ThemedButton(RANK_BASE + i, left + i * 42, top + 24, 38, 18,
                        RANKS[i] + "-Rank", active ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                        active ? Theme.GOLD : Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT));
            }
        }

        filteredQuests = filter(data);
        int maxQuestPage = Math.max(0, (filteredQuests.size() - 1) / QUESTS_PER_PAGE);
        questPage = Math.min(questPage, maxQuestPage);
        normalizeSelectedQuest();

        int listTop = top + 50;
        int listWidth = Math.min(220, Math.max(170, width * 42 / 100));
        int start = questPage * QUESTS_PER_PAGE;
        int end = Math.min(filteredQuests.size(), start + QUESTS_PER_PAGE);
        for (int i = start; i < end; i++) {
            ClientQuestData.Quest quest = filteredQuests.get(i);
            int row = i - start;
            int color = questButtonColor(quest.status, quest.id == selectedQuestId);
            buttons.add(new ThemedButton(QUEST_BASE + row, left, listTop + row * 24, listWidth, 20,
                    quest.title, color, questHoverColor(quest.status), Theme.BUTTON_TEXT));
        }

        int pagerY = top + height - 18;
        ThemedButton previous = new ThemedButton(QUEST_PAGE_PREV, left, pagerY, 22, 18, "<",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        previous.enabled = questPage > 0;
        buttons.add(previous);
        ThemedButton next = new ThemedButton(QUEST_PAGE_NEXT, left + listWidth - 22, pagerY, 22, 18, ">",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        next.enabled = questPage < maxQuestPage;
        buttons.add(next);

        buttons.add(new ThemedButton(DETAIL_UP, left + width - 38, listTop, 18, 18, "^",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
        buttons.add(new ThemedButton(DETAIL_DOWN, left + width - 18, listTop, 18, 18, "v",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));

        ClientQuestData.Quest selected = selectedQuest();
        if (selected != null && selected.claimable) {
            ThemedButton claim = new ThemedButton(CLAIM_REWARDS, left + listWidth + 10, pagerY,
                    112, 18, "Claim Rewards", Theme.SUCCESS, 0xFF5B925E, Theme.BUTTON_TEXT);
            claim.enabled = !claimPending;
            buttons.add(claim);
        }
    }

    private ThemedButton modeButton(int id, int x, int y, int width, String label, boolean active) {
        return new ThemedButton(id, x, y, width, 18, label,
                active ? Theme.SUCCESS : Theme.TAB_INACTIVE_BG,
                active ? 0xFF94D391 : Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height, int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        ClientQuestData.Snapshot data = ClientQuestData.get();
        int listTop = top + 50;
        int listWidth = Math.min(220, Math.max(170, width * 42 / 100));
        int detailLeft = left + listWidth + 10;

        Gui.drawRect(left, top + 44, left + width, top + 45, Theme.GOLD_DARK);
        Gui.drawRect(left + listWidth + 4, listTop, left + listWidth + 5, top + height, Theme.GOLD_DARK);

        String heading = heading(data);
        font.drawString(heading, left + 218, top + 29, Theme.TEXT_MUTED);

        if (ClientQuestData.getRevision() == 0) {
            font.drawString("Loading CustomNPC missions...", left + 8, listTop + 10, Theme.TEXT_MUTED);
            return;
        }
        if (!data.available) {
            font.drawString("CustomNPCs is not available on this server.", left + 8, listTop + 10, Theme.DANGER);
            return;
        }
        if (mode == 1 && data.village.isEmpty()) {
            font.drawString("No village is assigned to this player.", left + 8, listTop + 10, Theme.TEXT_MUTED);
            return;
        }

        int pages = Math.max(1, (filteredQuests.size() + QUESTS_PER_PAGE - 1) / QUESTS_PER_PAGE);
        String pageText = (questPage + 1) + "/" + pages;
        font.drawString(pageText, left + (listWidth - font.getStringWidth(pageText)) / 2,
                top + height - 13, Theme.TEXT_MUTED);

        ClientQuestData.Quest selected = selectedQuest();
        if (selected == null) {
            font.drawString("No quests match this tab.", left + 8, listTop + 10, Theme.TEXT_MUTED);
            return;
        }

        int detailWidth = width - (detailLeft - left) - 4;
        List<DetailLine> lines = detailLines(font, selected, detailWidth - 8);
        int visibleLines = Math.max(1, (height - 94) / 10);
        int maxOffset = Math.max(0, lines.size() - visibleLines);
        detailOffset = Math.min(detailOffset, maxOffset);
        int y = listTop + 23;
        int end = Math.min(lines.size(), detailOffset + visibleLines);
        for (int i = detailOffset; i < end; i++) {
            DetailLine line = lines.get(i);
            font.drawString(line.text, detailLeft + 4, y, line.color);
            y += 10;
        }
    }

    private List<DetailLine> detailLines(FontRenderer font, ClientQuestData.Quest quest, int width) {
        List<DetailLine> lines = new ArrayList<DetailLine>();
        wrap(lines, font, quest.title, width - 40, Theme.TEXT_LIGHT);
        lines.add(new DetailLine(quest.statusLabel(), statusColor(quest.status)));
        String meta = quest.category + (quest.rank.isEmpty() ? "" : " | " + quest.rank + "-Rank");
        wrap(lines, font, meta, width, Theme.TEXT_MUTED);
        if (!quest.completer.isEmpty()) {
            wrap(lines, font, "Turn in to: " + quest.completer, width, Theme.TEXT_MUTED);
        }
        if (!quest.repeat.isEmpty()) {
            wrap(lines, font, quest.repeat, width, Theme.TEXT_MUTED);
        }
        lines.add(new DetailLine("", Theme.TEXT_MUTED));
        wrap(lines, font, quest.description, width, Theme.TEXT_LIGHT);

        if (quest.status >= 3 && !quest.completionText.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine("Completion", Theme.GOLD));
            wrap(lines, font, quest.completionText, width, Theme.TEXT_LIGHT);
        }

        if (quest.status == 3 && !quest.instantCompletion) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            wrap(lines, font, "Return to " + (quest.completer.isEmpty() ? "the assigned NPC" : quest.completer)
                    + " to claim these rewards.", width, Theme.NEUTRAL);
        }

        if (!quest.objectives.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine("Objectives", Theme.GOLD));
            for (ClientQuestData.Objective objective : quest.objectives) {
                String count = objective.progress + "/" + objective.maximum;
                String progress = objective.maximum > 0 && !objective.text.contains(count) ? " (" + count + ")" : "";
                wrap(lines, font, (objective.complete ? "[x] " : "[ ] ") + objective.text + progress,
                        width, objective.complete ? Theme.SUCCESS : Theme.TEXT_LIGHT);
            }
        }

        if (!quest.rules.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine("Requirements", Theme.GOLD));
            for (String rule : quest.rules) {
                wrap(lines, font, rule, width, Theme.TEXT_MUTED);
            }
        }

        if (!quest.rewards.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine("Rewards", Theme.GOLD));
            for (String reward : quest.rewards) {
                wrap(lines, font, reward, width, Theme.TEXT_LIGHT);
            }
        }
        return lines;
    }

    private void wrap(List<DetailLine> output, FontRenderer font, String value, int width, int color) {
        String[] paragraphs = value.replace("\\r", "").split("\\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                output.add(new DetailLine("", color));
                continue;
            }
            for (String line : font.listFormattedStringToWidth(paragraph, Math.max(20, width))) {
                output.add(new DetailLine(line, color));
            }
        }
    }

    private String heading(ClientQuestData.Snapshot data) {
        if (mode == 1) {
            return data.village.isEmpty() ? "Village Missions" : display(data.village) + " Missions";
        }
        if (mode == 2) {
            ClientQuestData.Tab tab = selectedTab(data);
            return tab == null ? "Custom Missions" : tab.title;
        }
        return selectedRank + "-Rank Missions";
    }

    private List<ClientQuestData.Quest> filter(ClientQuestData.Snapshot data) {
        List<ClientQuestData.Quest> result = new ArrayList<ClientQuestData.Quest>();
        ClientQuestData.Tab tab = mode == 2 ? selectedTab(data) : null;
        String tabFilter = tab == null ? "" : tab.filter.toLowerCase(Locale.ROOT);
        for (ClientQuestData.Quest quest : data.quests) {
            if (mode == 0 && quest.village.isEmpty() && selectedRank.equals(quest.rank)) {
                result.add(quest);
            } else if (mode == 1 && !data.village.isEmpty() && data.village.equals(quest.village)) {
                result.add(quest);
            } else if (mode == 2 && tab != null
                    && quest.category.toLowerCase(Locale.ROOT).equals(tabFilter)
                    && (tab.village.isEmpty() || tab.village.equals(quest.village))) {
                result.add(quest);
            }
        }
        return result;
    }

    private ClientQuestData.Tab selectedTab(ClientQuestData.Snapshot data) {
        for (ClientQuestData.Tab tab : data.tabs) {
            if (tab.id.equals(selectedCustomTab)) {
                return tab;
            }
        }
        return null;
    }

    private void normalizeSelectedQuest() {
        for (ClientQuestData.Quest quest : filteredQuests) {
            if (quest.id == selectedQuestId) {
                return;
            }
        }
        selectedQuestId = filteredQuests.isEmpty() ? -1 : filteredQuests.get(0).id;
        detailOffset = 0;
    }

    private ClientQuestData.Quest selectedQuest() {
        for (ClientQuestData.Quest quest : filteredQuests) {
            if (quest.id == selectedQuestId) {
                return quest;
            }
        }
        return null;
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        if (buttonId == MODE_MISSIONS || buttonId == MODE_VILLAGE) {
            mode = buttonId == MODE_MISSIONS ? 0 : 1;
            questPage = 0;
            detailOffset = 0;
            return true;
        }
        if (buttonId >= CUSTOM_TAB_BASE && buttonId < CUSTOM_TAB_BASE + visibleCustomTabs.size()) {
            mode = 2;
            selectedCustomTab = visibleCustomTabs.get(buttonId - CUSTOM_TAB_BASE).id;
            questPage = 0;
            detailOffset = 0;
            return true;
        }
        if (buttonId == CUSTOM_PAGE_PREV || buttonId == CUSTOM_PAGE_NEXT) {
            customTabPage += buttonId == CUSTOM_PAGE_PREV ? -1 : 1;
            return true;
        }
        if (buttonId >= RANK_BASE && buttonId < RANK_BASE + RANKS.length) {
            selectedRank = RANKS[buttonId - RANK_BASE];
            questPage = 0;
            detailOffset = 0;
            return true;
        }
        if (buttonId >= QUEST_BASE && buttonId < QUEST_BASE + QUESTS_PER_PAGE) {
            int index = questPage * QUESTS_PER_PAGE + buttonId - QUEST_BASE;
            if (index < filteredQuests.size()) {
                selectedQuestId = filteredQuests.get(index).id;
                detailOffset = 0;
            }
            return true;
        }
        if (buttonId == QUEST_PAGE_PREV || buttonId == QUEST_PAGE_NEXT) {
            questPage += buttonId == QUEST_PAGE_PREV ? -1 : 1;
            return true;
        }
        if (buttonId == DETAIL_UP || buttonId == DETAIL_DOWN) {
            detailOffset = Math.max(0, detailOffset + (buttonId == DETAIL_UP ? -4 : 4));
            return false;
        }
        if (buttonId == CLAIM_REWARDS) {
            ClientQuestData.Quest selected = selectedQuest();
            if (selected != null && selected.claimable && !claimPending) {
                claimPending = true;
                RebornAddonNetwork.claimQuest(selected.id);
            }
            return false;
        }
        if (buttonId == REFRESH) {
            RebornAddonNetwork.requestQuestSync();
            return false;
        }
        return false;
    }

    @Override
    public boolean consumeButtonRefresh() {
        return builtRevision != ClientQuestData.getRevision();
    }

    private void requestIfNeeded() {
        if (!requestSent) {
            requestSent = true;
            RebornAddonNetwork.requestQuestSync();
        }
    }

    private int questButtonColor(int status, boolean selected) {
        if (selected) return Theme.GOLD_DARK;
        if (status == 3) return 0xFF3E6E42;
        if (status == 2) return Theme.PURPLE_DARK;
        if (status == 1) return 0xFF3E586E;
        return status == 4 ? 0xFF39463A : Theme.TAB_INACTIVE_BG;
    }

    private int questHoverColor(int status) {
        if (status == 3 || status == 4) return Theme.SUCCESS;
        if (status == 2) return Theme.PURPLE_MID;
        if (status == 1) return Theme.NEUTRAL;
        return Theme.TAB_HOVER_BG;
    }

    private int statusColor(int status) {
        if (status == 3 || status == 4) return Theme.SUCCESS;
        if (status == 2) return Theme.PURPLE_LIGHT;
        if (status == 1) return Theme.NEUTRAL;
        return Theme.TEXT_MUTED;
    }

    private static String display(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class DetailLine {
        private final String text;
        private final int color;

        private DetailLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}
