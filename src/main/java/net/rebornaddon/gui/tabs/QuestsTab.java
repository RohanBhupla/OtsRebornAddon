package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.client.ClientLocalization;
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
    private static final int QUEST_ACTION = 395;
    private static final int QUEST_RESET = 396;
    private static final int REFRESH = 399;
    private static final String[] RANKS = new String[]{"D", "C", "B", "A", "S"};
    private static final int MAX_QUESTS_PER_PAGE = 7;

    private int mode;
    private String selectedRank = "D";
    private String selectedCustomTab = "";
    private int customTabPage;
    private int questPage;
    private String selectedQuestId = "";
    private int detailOffset;
    private int builtRevision = -1;
    private int customTabsPerPage = 1;
    private int questsPerPage = MAX_QUESTS_PER_PAGE;
    private boolean claimPending;
    private boolean resetArmed;
    private int detailRevision = -1;
    private String detailQuestId = "";
    private int detailWidth = -1;
    private int controlsHeight;
    private int listTop;
    private int listWidth;
    private List<DetailLine> cachedDetailLines = Collections.emptyList();
    private List<ClientQuestData.Tab> visibleCustomTabs = Collections.emptyList();
    private List<ClientQuestData.Quest> filteredQuests = Collections.emptyList();

    @Override
    public String getTabName() {
        return ClientLocalization.format("gui.rebornaddon.tab.quests", "Quests");
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

        int modeWidth = Math.max(58, Math.min(76, (width - 64) / 3));
        buttons.add(modeButton(MODE_MISSIONS, left, top, modeWidth,
                ClientLocalization.format("gui.rebornaddon.quests.missions", "Missions"), mode == 0));
        buttons.add(modeButton(MODE_VILLAGE, left + modeWidth + 4, top, modeWidth,
                ClientLocalization.format("gui.rebornaddon.quests.village", "Village"), mode == 1));
        buttons.add(new ThemedButton(REFRESH, left + width - 56, top, 56, 20,
                ClientLocalization.format("gui.rebornaddon.quests.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT));

        int customY = top + 25;
        int customX = left;
        int controlsX = left + width - 40;
        int customSpace = Math.max(1, controlsX - customX - 4);
        int targetTabWidth = 78;
        customTabsPerPage = Math.max(1, Math.min(3, Math.max(1, customSpace) / targetTabWidth));
        int maxCustomPage = Math.max(0, (data.tabs.size() - 1) / customTabsPerPage);
        customTabPage = Math.min(customTabPage, maxCustomPage);
        int customStart = customTabPage * customTabsPerPage;
        int customEnd = Math.min(data.tabs.size(), customStart + customTabsPerPage);
        visibleCustomTabs = new ArrayList<ClientQuestData.Tab>(data.tabs.subList(customStart, customEnd));
        int customSlotWidth = visibleCustomTabs.isEmpty() ? targetTabWidth
                : Math.max(1, customSpace / visibleCustomTabs.size());
        for (int i = 0; i < visibleCustomTabs.size(); i++) {
            ClientQuestData.Tab tab = visibleCustomTabs.get(i);
            boolean active = mode == 2 && tab.id.equals(selectedCustomTab);
            int buttonWidth = Math.max(18, customSlotWidth - 4);
            buttons.add(modeButton(CUSTOM_TAB_BASE + i, customX + i * customSlotWidth,
                    customY, buttonWidth, tab.title, active));
        }

        ThemedButton previousTabs = new ThemedButton(CUSTOM_PAGE_PREV, controlsX, customY, 18, 18, "<",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        previousTabs.enabled = customTabPage > 0;
        buttons.add(previousTabs);
        ThemedButton nextTabs = new ThemedButton(CUSTOM_PAGE_NEXT, controlsX + 20, customY, 18, 18, ">",
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        nextTabs.enabled = customTabPage < maxCustomPage;
        buttons.add(nextTabs);
        controlsHeight = 48;
        if (mode == 0) {
            int rankGap = 4;
            int rankWidth = Math.max(42, (width - rankGap * (RANKS.length - 1)) / RANKS.length);
            for (int i = 0; i < RANKS.length; i++) {
                boolean active = RANKS[i].equals(selectedRank);
                ThemedButton rank = new ThemedButton(RANK_BASE + i, left + i * (rankWidth + rankGap),
                        top + 48, rankWidth, 20,
                        ClientLocalization.format("gui.rebornaddon.quests.rank", "%s-Rank", RANKS[i]),
                        active ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                        active ? Theme.GOLD : Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
                rank.setSelected(active);
                buttons.add(rank);
            }
            controlsHeight = 72;
        }

        filteredQuests = filter(data);
        listTop = top + controlsHeight + 18;
        int pagerY = top + height - 20;
        questsPerPage = Math.max(1, Math.min(MAX_QUESTS_PER_PAGE,
                Math.max(1, pagerY - listTop - 2) / 24));
        int maxQuestPage = Math.max(0, (filteredQuests.size() - 1) / questsPerPage);
        questPage = Math.min(questPage, maxQuestPage);
        normalizeSelectedQuest();

        listWidth = Math.min(230, Math.max(104, width * 42 / 100));
        int start = questPage * questsPerPage;
        int end = Math.min(filteredQuests.size(), start + questsPerPage);
        for (int i = start; i < end; i++) {
            ClientQuestData.Quest quest = filteredQuests.get(i);
            int row = i - start;
            int color = questButtonColor(quest.status, quest.id.equals(selectedQuestId));
            ThemedButton questButton = new ThemedButton(QUEST_BASE + row, left, listTop + row * 24,
                    listWidth, 20, quest.title, color, questHoverColor(quest.status), Theme.BUTTON_TEXT);
            questButton.setSelected(quest.id.equals(selectedQuestId));
            questButton.setTextAlignment(ThemedButton.Alignment.LEFT);
            questButton.setTextInset(9);
            buttons.add(questButton);
        }

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
        if (selected != null) {
            int availableWidth = Math.max(72, width - listWidth - 14);
            if (selected.claimable) {
                ThemedButton claim = new ThemedButton(CLAIM_REWARDS, left + listWidth + 10, pagerY,
                        availableWidth, 18, ClientLocalization.format("gui.rebornaddon.quests.claim", "Claim Rewards"),
                        Theme.SUCCESS, Theme.SUCCESS_BRIGHT, Theme.BUTTON_TEXT);
                claim.enabled = !claimPending;
                buttons.add(claim);
            } else if (selected.status == 1 || selected.status == 2 || selected.status == 5) {
                boolean canReset = selected.status == 2 || selected.status == 5;
                int resetWidth = canReset ? Math.min(68, availableWidth / 2) : 0;
                int actionWidth = availableWidth - (canReset ? resetWidth + 4 : 0);
                String label = selected.status == 1
                        ? ClientLocalization.format("gui.rebornaddon.quests.accept", "Accept")
                        : selected.status == 5
                        ? ClientLocalization.format("gui.rebornaddon.quests.resume", "Resume")
                        : ClientLocalization.format("gui.rebornaddon.quests.abandon", "Abandon");
                buttons.add(new ThemedButton(QUEST_ACTION, left + listWidth + 10, pagerY,
                        actionWidth, 18, label, Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT));
                if (canReset) {
                    buttons.add(new ThemedButton(QUEST_RESET,
                            left + listWidth + 14 + actionWidth, pagerY, resetWidth, 18,
                            resetArmed ? ClientLocalization.format("gui.rebornaddon.quests.confirm", "Confirm")
                                    : ClientLocalization.format("gui.rebornaddon.quests.reset", "Reset"),
                            Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
                }
            }
        }
    }

    private ThemedButton modeButton(int id, int x, int y, int width, String label, boolean active) {
        ThemedButton button = new ThemedButton(id, x, y, width, 20, label,
                active ? Theme.SUCCESS : Theme.TAB_INACTIVE_BG,
                active ? 0xFF94D391 : Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
        button.setSelected(active);
        return button;
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height, int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        ClientQuestData.Snapshot data = ClientQuestData.get();
        int detailLeft = left + listWidth + 10;
        int bodyTop = top + controlsHeight;
        int bottom = top + height - 21;
        int detailPanelWidth = width - (detailLeft - left);

        GuiChrome.section(left - 3, listTop - 6, listWidth + 6,
                Math.max(24, bottom - listTop + 7), Theme.SUCCESS);
        GuiChrome.section(detailLeft - 3, bodyTop, detailPanelWidth + 3,
                Math.max(28, bottom - bodyTop), Theme.GOLD);
        GuiChrome.rule(detailLeft + 4, left + width - 8, bodyTop + 18, Theme.GOLD);

        String heading = heading(data);
        font.drawString(font.trimStringToWidth(heading, Math.max(20, detailPanelWidth - 18)),
                detailLeft + 5, bodyTop + 7, Theme.GOLD);

        if (ClientQuestData.getRevision() == 0) {
            font.drawString(ClientLocalization.format("gui.rebornaddon.quests.loading",
                    "Loading missions..."), left + 8, listTop + 10, Theme.TEXT_MUTED);
            return;
        }
        if (!data.available) {
            font.drawString(ClientLocalization.format("gui.rebornaddon.quests.unavailable",
                    "The server mission service is not available."), left + 8, listTop + 10, Theme.DANGER);
            return;
        }
        if (mode == 1 && data.village.isEmpty()) {
            font.drawString(ClientLocalization.format("gui.rebornaddon.quests.no_village",
                    "No village is assigned to this player."), left + 8, listTop + 10, Theme.TEXT_MUTED);
            return;
        }

        int pages = Math.max(1, (filteredQuests.size() + questsPerPage - 1) / questsPerPage);
        String pageText = (questPage + 1) + "/" + pages;
        font.drawString(pageText, left + (listWidth - font.getStringWidth(pageText)) / 2,
                top + height - 13, Theme.TEXT_MUTED);

        ClientQuestData.Quest selected = selectedQuest();
        if (selected == null) {
            font.drawString(ClientLocalization.format("gui.rebornaddon.quests.empty",
                    "No quests match this tab."), left + 8, listTop + 10, Theme.TEXT_MUTED);
            return;
        }

        int detailWidth = width - (detailLeft - left) - 4;
        List<DetailLine> lines = cachedDetailLines(font, selected, detailWidth - 8);
        int visibleLines = Math.max(1, (top + height - 22 - (listTop + 23)) / 10);
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
            wrap(lines, font, ClientLocalization.format("gui.rebornaddon.quests.turn_in", "Turn in to: %s",
                    quest.completer), width, Theme.TEXT_MUTED);
        }
        if (!quest.repeat.isEmpty()) {
            wrap(lines, font, quest.repeat, width, Theme.TEXT_MUTED);
        }
        lines.add(new DetailLine("", Theme.TEXT_MUTED));
        wrap(lines, font, quest.description, width, Theme.TEXT_LIGHT);

        if (quest.status >= 3 && !quest.completionText.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine(ClientLocalization.format("gui.rebornaddon.quests.completion", "Completion"),
                    Theme.GOLD));
            wrap(lines, font, quest.completionText, width, Theme.TEXT_LIGHT);
        }

        if (quest.status == 3 && !quest.instantCompletion) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            String completer = quest.completer.isEmpty()
                    ? ClientLocalization.format("gui.rebornaddon.quests.assigned_npc", "the assigned NPC")
                    : quest.completer;
            wrap(lines, font, ClientLocalization.format("gui.rebornaddon.quests.return_to",
                    "Return to %s to claim these rewards.", completer), width, Theme.NEUTRAL);
        }

        if (!quest.objectives.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine(ClientLocalization.format("gui.rebornaddon.quests.objectives", "Objectives"),
                    Theme.GOLD));
            for (ClientQuestData.Objective objective : quest.objectives) {
                String count = objective.progress + "/" + objective.maximum;
                String progress = objective.maximum > 0 && !objective.text.contains(count) ? " (" + count + ")" : "";
                wrap(lines, font, (objective.complete ? "[x] " : "[ ] ") + objective.text + progress,
                        width, objective.complete ? Theme.SUCCESS : Theme.TEXT_LIGHT);
            }
        }

        if (!quest.rules.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine(ClientLocalization.format("gui.rebornaddon.quests.requirements", "Requirements"),
                    Theme.GOLD));
            for (String rule : quest.rules) {
                wrap(lines, font, rule, width, Theme.TEXT_MUTED);
            }
        }

        if (!quest.rewards.isEmpty()) {
            lines.add(new DetailLine("", Theme.TEXT_MUTED));
            lines.add(new DetailLine(ClientLocalization.format("gui.rebornaddon.quests.rewards", "Rewards"),
                    Theme.GOLD));
            for (String reward : quest.rewards) {
                wrap(lines, font, reward, width, Theme.TEXT_LIGHT);
            }
        }
        return lines;
    }

    private List<DetailLine> cachedDetailLines(FontRenderer font, ClientQuestData.Quest quest, int width) {
        int revision = ClientQuestData.getRevision();
        if (detailRevision != revision || !detailQuestId.equals(quest.id) || detailWidth != width) {
            cachedDetailLines = detailLines(font, quest, width);
            detailRevision = revision;
            detailQuestId = quest.id;
            detailWidth = width;
        }
        return cachedDetailLines;
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
            return data.village.isEmpty()
                    ? ClientLocalization.format("gui.rebornaddon.quests.village_missions", "Village Missions")
                    : ClientLocalization.format("gui.rebornaddon.quests.named_village_missions", "%s Missions",
                            display(data.village));
        }
        if (mode == 2) {
            ClientQuestData.Tab tab = selectedTab(data);
            return tab == null
                    ? ClientLocalization.format("gui.rebornaddon.quests.custom_missions", "Custom Missions")
                    : tab.title;
        }
        return ClientLocalization.format("gui.rebornaddon.quests.rank_missions", "%s-Rank Missions", selectedRank);
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
            if (quest.id.equals(selectedQuestId)) {
                return;
            }
        }
        selectedQuestId = filteredQuests.isEmpty() ? "" : filteredQuests.get(0).id;
        detailOffset = 0;
    }

    private ClientQuestData.Quest selectedQuest() {
        for (ClientQuestData.Quest quest : filteredQuests) {
            if (quest.id.equals(selectedQuestId)) {
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
        if (buttonId >= QUEST_BASE && buttonId < QUEST_BASE + questsPerPage) {
            int index = questPage * questsPerPage + buttonId - QUEST_BASE;
            if (index < filteredQuests.size()) {
                selectedQuestId = filteredQuests.get(index).id;
                detailOffset = 0;
                resetArmed = false;
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
        if (buttonId == QUEST_ACTION) {
            ClientQuestData.Quest selected = selectedQuest();
            if (selected != null) {
                String action = selected.status == 1 ? "quest.accept"
                        : selected.status == 5 ? "quest.resume" : "quest.abandon";
                com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
                payload.addProperty("questId", selected.id);
                RebornAddonNetwork.sendNativeContentAction(action, payload.toString());
                resetArmed = false;
            }
            return false;
        }
        if (buttonId == QUEST_RESET) {
            ClientQuestData.Quest selected = selectedQuest();
            if (selected != null && resetArmed) {
                com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
                payload.addProperty("questId", selected.id);
                RebornAddonNetwork.sendNativeContentAction("quest.reset", payload.toString());
                resetArmed = false;
            } else {
                resetArmed = true;
            }
            return true;
        }
        if (buttonId == REFRESH) {
            RebornAddonNetwork.requestQuestSync(ClientQuestData.get().catalogVersion);
            return false;
        }
        return false;
    }

    @Override
    public boolean consumeButtonRefresh() {
        return builtRevision != ClientQuestData.getRevision();
    }

    private int questButtonColor(int status, boolean selected) {
        if (selected) return Theme.GOLD_DARK;
        if (status == 3) return 0xFF3E6E42;
        if (status == 2) return Theme.PURPLE_DARK;
        if (status == 5) return 0xFF5C5038;
        if (status == 1) return 0xFF3E586E;
        return status == 4 ? 0xFF39463A : Theme.TAB_INACTIVE_BG;
    }

    private int questHoverColor(int status) {
        if (status == 3 || status == 4) return Theme.SUCCESS;
        if (status == 2) return Theme.PURPLE_MID;
        if (status == 5) return Theme.GOLD_DARK;
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
