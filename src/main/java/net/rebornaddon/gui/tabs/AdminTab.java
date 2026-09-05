package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.chakra.ChakraControlConfigurationService;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.jutsu.JutsuConfigurationService;
import net.rebornaddon.jutsu.JutsuValueParser;
import net.rebornaddon.jutsu.client.ClientAdminData;
import net.rebornaddon.jutsu.client.GuiJutsuEffectEditor;
import net.rebornaddon.mode.ModeConfigurationService;
import net.rebornaddon.mode.ModeDefinition;
import net.rebornaddon.mode.client.GuiModeCombinationEditor;
import net.rebornaddon.ranked.season.SeasonManager;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.exam.client.ClientExamData;
import net.rebornaddon.performance.PerformanceMonitor;
import net.rebornaddon.performance.client.ClientPerformanceData;
import net.rebornaddon.performance.client.ClientPerformanceOverlay;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminTab implements HubTab {
    private static final int SECTION_BASE = 6000;
    private static final int PREVIOUS_PAGE = 910;
    private static final int NEXT_PAGE = 911;
    private static final int PROPERTY = 912;
    private static final int BOOLEAN_VALUE = 913;
    private static final int SAVE = 914;
    private static final int RESET_PROPERTY = 915;
    private static final int RESET_ENTRY = 916;
    private static final int RESET_ALL = 917;
    private static final int RELOAD = 918;
    private static final int QUOTA_LOAD = 919;
    private static final int QUOTA_KIND = 920;
    private static final int QUOTA_SET = 921;
    private static final int QUOTA_ADD = 922;
    private static final int QUOTA_RESET = 923;
    private static final int QUEST_ACTION = 924;
    private static final int QUEST_APPLY = 925;
    private static final int JUTSU_PROPERTY_BASE = 6100;
    private static final int JUTSU_SCOPE = 940;
    private static final int JUTSU_CATEGORY = 941;
    private static final int OPEN_EFFECT_RULES = 942;
    private static final int MODE_PROPERTY_BASE = 6200;
    private static final int OPEN_MODE_COMBINATIONS = 990;
    private static final int RANKED_VIEW_BASE = 960;
    private static final int RANKED_START = 963;
    private static final int RANKED_PAUSE = 964;
    private static final int RANKED_END = 965;
    private static final int RANKED_LEADERBOARD = 966;
    private static final int RANKED_SAVE_NAME = 967;
    private static final int RANKED_SAVE_DURATION = 968;
    private static final int RANKED_PLACEMENT_PREVIOUS = 969;
    private static final int RANKED_PLACEMENT_NEXT = 970;
    private static final int RANKED_PLACEMENT_ENABLED = 971;
    private static final int RANKED_ADD_ITEM = 972;
    private static final int RANKED_CLEAR_ITEMS = 973;
    private static final int RANKED_ADD_COMMAND = 974;
    private static final int RANKED_CLEAR_COMMANDS = 975;
    private static final int RANKED_SAVE_GROUP = 976;
    private static final int RANKED_BLOCK_PLAYER = 977;
    private static final int RANKED_UNBLOCK_PLAYER = 978;
    private static final int RANKED_RELOAD = 979;
    private static final int RANKED_BLOCKED_BASE = 1800;
    private static final int EXAM_RANK_PREVIOUS = 1980;
    private static final int EXAM_RANK_NEXT = 1981;
    private static final int EXAM_RANK_APPLY = 1982;
    private static final int COOLDOWN_CLEAR_PLAYER = 1983;
    private static final int COOLDOWN_CLEAR_ALL = 1984;
    private static final int PERFORMANCE_REFRESH = 1985;
    private static final int PERFORMANCE_PROFILE = 1986;
    private static final int PERFORMANCE_VIEW_OVERVIEW = 1987;
    private static final int PERFORMANCE_VIEW_HOTSPOTS = 1988;
    private static final int PERFORMANCE_VIEW_CHUNKS = 1989;
    private static final int PERFORMANCE_OVERLAY = 1990;
    private static final int PERFORMANCE_PREVIOUS = 1991;
    private static final int PERFORMANCE_NEXT = 1992;
    private static final int PERFORMANCE_TELEPORT = 1993;
    private static final int CHAKRA_SETTING_BASE = 2300;
    private static final int CHAKRA_SAVE = 2310;
    private static final int CHAKRA_RESET = 2311;
    private static final int CHAKRA_RELOAD = 2312;
    private static final int PERFORMANCE_ROW_BASE = 2200;
    private static final int ROW_BASE = 1000;
    private static final String[] TAB_IDS = {"jutsus", "store", "sections", "limits", "quests",
            "modes", "ranked", "ranks", "cooldowns", "performance", "moderation",
            "chakra-control", "mounts", "luckperms"};
    private static final String[] CHAKRA_SETTING_IDS = {
            ChakraControlConfigurationService.ENABLED,
            ChakraControlConfigurationService.WATER_WALKING_ENABLED,
            ChakraControlConfigurationService.WALL_CLIMBING_ENABLED,
            ChakraControlConfigurationService.WALL_CLIMB_SPEED_MULTIPLIER
    };
    private static final String[] QUEST_ACTIONS = {"reset", "start", "complete", "abandon", "resume"};
    private static final List<String> QUOTA_KINDS = Collections.unmodifiableList(Arrays.asList(
            "anbu_mask", "anbu_cloak", "kage_hat", "kage_robe",
            "akatsuki_headband", "akatsuki_hat", "akatsuki_robe"));

    private final List<JutsuEntry> jutsus = new ArrayList<JutsuEntry>();
    private final List<StoreEntry> storeEntries = new ArrayList<StoreEntry>();
    private final List<ScopeEntry> scopes = new ArrayList<ScopeEntry>();
    private final List<ModeEntry> modes = new ArrayList<ModeEntry>();
    private final ModerationAdminPanel moderation = new ModerationAdminPanel();
    private final MountAdminPanel mounts = new MountAdminPanel();
    private final LuckPermsAdminPanel luckPerms = new LuckPermsAdminPanel();
    private GuiTextField searchField;
    private GuiTextField valueField;
    private GuiTextField playerField;
    private GuiTextField hoursField;
    private GuiTextField minutesField;
    private GuiTextField secondsField;
    private GuiTextField rankedNameField;
    private GuiTextField rankedCommandField;
    private GuiTextField rankedGroupField;
    private GuiTextField rankedPlayerField;
    private GuiTextField examRankPlayerField;
    private GuiTextField cooldownPlayerField;
    private GuiTextField performanceHoursField;
    private GuiTextField performanceMinutesField;
    private GuiTextField performanceSecondsField;
    private NBTTagCompound snapshot = new NBTTagCompound();
    private NBTTagCompound storeData = new NBTTagCompound();
    private NBTTagCompound rankedData = new NBTTagCompound();
    private NBTTagCompound chakraData = new NBTTagCompound();
    private JutsuEntry selectedJutsu;
    private StoreEntry selectedStore;
    private ScopeEntry selectedScope;
    private ModeEntry selectedMode;
    private int selected;
    private int propertyIndex;
    private int quotaIndex;
    private int questActionIndex;
    private int jutsuCategoryIndex;
    private int rankedView;
    private int rankedPlacement = 1;
    private int rankedBlockedPage;
    private int rankedBlockedSelection = -1;
    private int examRankIndex;
    private int examRevision = -1;
    private int performanceRevision = -1;
    private int performanceView;
    private int performancePage;
    private int performanceSelection = -1;
    private int chakraSettingIndex;
    private int page;
    private int observedRevision = -1;
    private int left;
    private int top;
    private int width;
    private int height;
    private int bodyTop;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int listTop;
    private int rowsPerPage;
    private boolean booleanValue;
    private boolean resetAllArmed;
    private boolean refreshButtons;
    private boolean restrictedJutsus;
    private String valueContext = "";
    private long lastBuildAt;

    @Override
    public String getTabName() {
        return text("gui.rebornaddon.tab.admin", "Admin");
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
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        if (selected != 7 && selected != 9 && selected != 10 && selected != 12 && selected != 13) {
            syncSnapshot();
            requestSnapshot(true);
        }
        layout();
        prepareFields();
        addSectionButtons(buttons);
        if (selected == 7) {
            requestRankCatalog(false);
            buildExamRankButtons(buttons);
            return;
        }
        if (selected == 9) {
            requestPerformance(false);
            buildPerformanceButtons(buttons);
            return;
        }
        if (selected == 10) {
            moderation.build(buttons, left, bodyTop, leftWidth, rightX, rightWidth, top + height);
            return;
        }
        if (selected == 12) {
            mounts.build(buttons, left, bodyTop, leftWidth, rightX, rightWidth, top + height);
            return;
        }
        if (selected == 13) {
            luckPerms.build(buttons, left, bodyTop, leftWidth, rightX, rightWidth, top + height);
            return;
        }
        if (!ClientAdminData.loaded()) return;
        if (selected == 0) buildJutsuButtons(buttons);
        else if (selected == 1) buildStoreButtons(buttons);
        else if (selected == 2) buildSectionButtons(buttons);
        else if (selected == 3) buildLimitButtons(buttons);
        else if (selected == 4) buildQuestButtons(buttons);
        else if (selected == 5) buildModeButtons(buttons);
        else if (selected == 6) buildRankedButtons(buttons);
        else if (selected == 8) buildCooldownButtons(buttons);
        else buildChakraControlButtons(buttons);
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (selected != 7 && selected != 9 && selected != 10 && selected != 12 && selected != 13
                && !ClientAdminData.loaded()) {
            GuiChrome.header(left, bodyTop, width, 38, Theme.RANKED_RED);
            font.drawString(text("gui.rebornaddon.admin.loading", "Loading server controls..."),
                    left + 13, bodyTop + 14, Theme.TEXT_LIGHT);
            return;
        }
        int accent = sectionAccent(selected);
        int bodyHeight = Math.max(80, top + height - bodyTop);
        GuiChrome.section(left, bodyTop, leftWidth, bodyHeight, accent);
        GuiChrome.section(rightX, bodyTop, rightWidth, bodyHeight, accent);
        if (selected != 9 && selected != 10 && selected != 11 && selected != 12 && selected != 13) {
            drawNotice(font, accent);
        }
        if (selected == 3) drawLimits(font, accent);
        else if (selected == 4) drawQuests(font, accent);
        else if (selected == 6) drawRanked(font, accent);
        else if (selected == 7) drawExamRanks(font, accent);
        else if (selected == 8) drawCooldowns(font, accent);
        else if (selected == 9) drawPerformance(font, accent);
        else if (selected == 10) moderation.draw(font);
        else if (selected == 11) drawChakraControl(font, accent);
        else if (selected == 12) mounts.draw(font);
        else if (selected == 13) luckPerms.draw(font);
        else {
            font.drawString(selected == 5
                            ? text("gui.rebornaddon.admin.search_modes", "Search modes")
                            : selected == 2
                            ? text("gui.rebornaddon.admin.search_sections", "Search sections")
                            : selected == 1 ? text("gui.rebornaddon.admin.search_items", "Search items")
                            : text("gui.rebornaddon.jutsu.search", "Search"),
                    left + 8, bodyTop + 6, Theme.TEXT_MUTED);
            searchField.drawTextBox();
            if (selected == 0) drawJutsu(font, accent);
            else if (selected == 1) drawStore(font, accent);
            else if (selected == 2) drawScope(font, accent);
            else drawMode(font, accent);
        }
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        if (isSectionButtonId(buttonId)) {
            int next = buttonId - SECTION_BASE;
            if (next != selected && usesAdminSnapshot(next)) ClientAdminData.invalidate();
            selected = next;
            page = 0;
            propertyIndex = 0;
            resetAllArmed = false;
            valueContext = "";
            return true;
        }
        if (buttonId == PREVIOUS_PAGE || buttonId == NEXT_PAGE) {
            if (selected == 6 && rankedView == 2) {
                rankedBlockedPage += buttonId == PREVIOUS_PAGE ? -1 : 1;
            } else {
                page += buttonId == PREVIOUS_PAGE ? -1 : 1;
            }
            return true;
        }
        if (buttonId >= ROW_BASE && buttonId < ROW_BASE + rowsPerPage) {
            selectRow(page * rowsPerPage + buttonId - ROW_BASE);
            return true;
        }
        if (selected == 0) return handleJutsuButton(buttonId);
        if (selected == 1) return handleStoreButton(buttonId);
        if (selected == 2) return handleSectionButton(buttonId);
        if (selected == 3) return handleLimitButton(buttonId);
        if (selected == 4) return handleQuestButton(buttonId);
        if (selected == 5) return handleModeButton(buttonId);
        if (selected == 6) return handleRankedButton(buttonId);
        if (selected == 7) return handleExamRankButton(buttonId);
        if (selected == 9) return handlePerformanceButton(buttonId);
        if (selected == 10) return moderation.handleButton(buttonId);
        if (selected == 11) return handleChakraControlButton(buttonId);
        if (selected == 12) return mounts.handleButton(buttonId);
        if (selected == 13) return luckPerms.handleButton(buttonId);
        return handleCooldownButton(buttonId);
    }

    static boolean isSectionButtonId(int buttonId) {
        return buttonId >= SECTION_BASE && buttonId < SECTION_BASE + TAB_IDS.length;
    }

    @Override
    public void handleKeyTyped(char typedChar, int keyCode) {
        if (selected == 10) {
            moderation.keyTyped(typedChar, keyCode);
        } else if (selected == 12) {
            mounts.keyTyped(typedChar, keyCode);
        } else if (selected == 13) {
            luckPerms.keyTyped(typedChar, keyCode);
        } else if (selected == 11) {
            if (chakraSettingIndex == 3 && valueField != null) {
                valueField.textboxKeyTyped(typedChar, keyCode);
            }
        } else if (selected == 8) {
            if (cooldownPlayerField != null) cooldownPlayerField.textboxKeyTyped(typedChar, keyCode);
        } else if (selected == 9) {
            if (performanceHoursField != null) performanceHoursField.textboxKeyTyped(typedChar, keyCode);
            if (performanceMinutesField != null) performanceMinutesField.textboxKeyTyped(typedChar, keyCode);
            if (performanceSecondsField != null) performanceSecondsField.textboxKeyTyped(typedChar, keyCode);
        } else if (selected == 7) {
            if (examRankPlayerField != null) examRankPlayerField.textboxKeyTyped(typedChar, keyCode);
        } else if (selected == 6) {
            if (rankedNameField.textboxKeyTyped(typedChar, keyCode)
                    || rankedCommandField.textboxKeyTyped(typedChar, keyCode)
                    || rankedGroupField.textboxKeyTyped(typedChar, keyCode)
                    || rankedPlayerField.textboxKeyTyped(typedChar, keyCode)
                    || hoursField.textboxKeyTyped(typedChar, keyCode)
                    || minutesField.textboxKeyTyped(typedChar, keyCode)
                    || secondsField.textboxKeyTyped(typedChar, keyCode)) return;
        } else if (selected == 3 || selected == 4) {
            if (playerField.textboxKeyTyped(typedChar, keyCode)
                    || valueField.textboxKeyTyped(typedChar, keyCode)) return;
        } else {
            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                page = 0;
                refreshButtons = true;
                return;
            }
            if (selected == 0 && usesDurationValue()) {
                if (hoursField.textboxKeyTyped(typedChar, keyCode)
                        || minutesField.textboxKeyTyped(typedChar, keyCode)
                        || secondsField.textboxKeyTyped(typedChar, keyCode)) return;
            } else if (selected != 2 && usesTextValue()
                    && valueField.textboxKeyTyped(typedChar, keyCode)) return;
        }
    }

    @Override
    public void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (selected == 10) {
            moderation.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 12) {
            mounts.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 13) {
            luckPerms.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 11) {
            if (chakraSettingIndex == 3 && valueField != null) {
                valueField.mouseClicked(mouseX, mouseY, mouseButton);
            }
        } else if (selected == 8) {
            if (cooldownPlayerField != null) cooldownPlayerField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 9) {
            if (performanceHoursField != null) performanceHoursField.mouseClicked(mouseX, mouseY, mouseButton);
            if (performanceMinutesField != null) performanceMinutesField.mouseClicked(mouseX, mouseY, mouseButton);
            if (performanceSecondsField != null) performanceSecondsField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 7) {
            if (examRankPlayerField != null) examRankPlayerField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 6) {
            rankedNameField.mouseClicked(mouseX, mouseY, mouseButton);
            rankedCommandField.mouseClicked(mouseX, mouseY, mouseButton);
            rankedGroupField.mouseClicked(mouseX, mouseY, mouseButton);
            rankedPlayerField.mouseClicked(mouseX, mouseY, mouseButton);
            hoursField.mouseClicked(mouseX, mouseY, mouseButton);
            minutesField.mouseClicked(mouseX, mouseY, mouseButton);
            secondsField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (selected == 3 || selected == 4) {
            playerField.mouseClicked(mouseX, mouseY, mouseButton);
            valueField.mouseClicked(mouseX, mouseY, mouseButton);
        } else {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            if (selected == 0 && usesDurationValue()) {
                hoursField.mouseClicked(mouseX, mouseY, mouseButton);
                minutesField.mouseClicked(mouseX, mouseY, mouseButton);
                secondsField.mouseClicked(mouseX, mouseY, mouseButton);
            } else if (selected != 2 && usesTextValue()) valueField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void onTick() {
        if (searchField != null) searchField.updateCursorCounter();
        if (valueField != null) valueField.updateCursorCounter();
        if (playerField != null) playerField.updateCursorCounter();
        if (hoursField != null) hoursField.updateCursorCounter();
        if (minutesField != null) minutesField.updateCursorCounter();
        if (secondsField != null) secondsField.updateCursorCounter();
        if (rankedNameField != null) rankedNameField.updateCursorCounter();
        if (rankedCommandField != null) rankedCommandField.updateCursorCounter();
        if (rankedGroupField != null) rankedGroupField.updateCursorCounter();
        if (rankedPlayerField != null) rankedPlayerField.updateCursorCounter();
        if (examRankPlayerField != null) examRankPlayerField.updateCursorCounter();
        if (cooldownPlayerField != null) cooldownPlayerField.updateCursorCounter();
        if (performanceHoursField != null) performanceHoursField.updateCursorCounter();
        if (performanceMinutesField != null) performanceMinutesField.updateCursorCounter();
        if (performanceSecondsField != null) performanceSecondsField.updateCursorCounter();
        if (examRevision != ClientExamData.rankRevision()) {
            examRevision = ClientExamData.rankRevision();
            refreshButtons = true;
        }
        if (selected == 10) {
            if (moderation.tick()) refreshButtons = true;
        } else if (selected == 12) {
            if (mounts.tick()) refreshButtons = true;
        } else if (selected == 13) {
            if (luckPerms.tick()) refreshButtons = true;
        } else if (selected == 7) {
            requestRankCatalog(false);
        } else if (selected == 9) {
            if (performanceRevision != ClientPerformanceData.revision()) {
                performanceRevision = ClientPerformanceData.revision();
                refreshButtons = true;
            }
            requestPerformance(false);
        } else {
            if (observedRevision != ClientAdminData.revision()) {
                syncSnapshot();
                refreshButtons = true;
            }
            requestSnapshot(false);
        }
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean result = refreshButtons;
        refreshButtons = false;
        return result;
    }

    private void layout() {
        int columns = width >= 500 ? 7 : width >= 380 ? 4 : width >= 260 ? 3 : 2;
        int headerRows = (TAB_IDS.length + columns - 1) / columns;
        bodyTop = top + headerRows * 24 + 5;
        leftWidth = Math.max(112, Math.min(220, width * 41 / 100));
        rightX = left + leftWidth + 6;
        rightWidth = Math.max(120, width - leftWidth - 6);
        listTop = bodyTop + (selected == 0 ? 62 : 34);
        int pagerY = top + height - 23;
        rowsPerPage = Math.max(2, (pagerY - listTop - 3) / 22);
    }

    private void prepareFields() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean hadDurationFields = hoursField != null;
        String search = searchField == null ? "" : searchField.getText();
        String player = playerField == null ? storeData.getString("QuotaPlayer") : playerField.getText();
        String value = valueField == null ? "" : valueField.getText();
        String hours = hoursField == null ? "0" : hoursField.getText();
        String minutes = minutesField == null ? "0" : minutesField.getText();
        String seconds = secondsField == null ? "0" : secondsField.getText();
        boolean searchFocused = searchField != null && searchField.isFocused();
        boolean playerFocused = playerField != null && playerField.isFocused();
        boolean valueFocused = valueField != null && valueField.isFocused();
        boolean hoursFocused = hoursField != null && hoursField.isFocused();
        boolean minutesFocused = minutesField != null && minutesField.isFocused();
        boolean secondsFocused = secondsField != null && secondsField.isFocused();
        boolean rankedNameFocused = rankedNameField != null && rankedNameField.isFocused();
        boolean rankedCommandFocused = rankedCommandField != null && rankedCommandField.isFocused();
        boolean rankedGroupFocused = rankedGroupField != null && rankedGroupField.isFocused();
        boolean rankedPlayerFocused = rankedPlayerField != null && rankedPlayerField.isFocused();
        boolean examRankFocused = examRankPlayerField != null && examRankPlayerField.isFocused();
        boolean cooldownPlayerFocused = cooldownPlayerField != null && cooldownPlayerField.isFocused();
        boolean performanceHoursFocused = performanceHoursField != null && performanceHoursField.isFocused();
        boolean performanceMinutesFocused = performanceMinutesField != null && performanceMinutesField.isFocused();
        boolean performanceSecondsFocused = performanceSecondsField != null && performanceSecondsField.isFocused();
        String examRankPlayer = examRankPlayerField == null ? "" : examRankPlayerField.getText();
        String cooldownPlayer = cooldownPlayerField == null ? "" : cooldownPlayerField.getText();
        String performanceHours = performanceHoursField == null ? "0" : performanceHoursField.getText();
        String performanceMinutes = performanceMinutesField == null ? "0" : performanceMinutesField.getText();
        String performanceSeconds = performanceSecondsField == null ? "5" : performanceSecondsField.getText();
        int searchCursor = searchField == null ? 0 : searchField.getCursorPosition();
        int playerCursor = playerField == null ? 0 : playerField.getCursorPosition();
        int valueCursor = valueField == null ? 0 : valueField.getCursorPosition();
        searchField = field(font, 2000, left + 7, bodyTop + 17, leftWidth - 14, 16, search);
        playerField = field(font, 2001, left + 7, bodyTop + 21, leftWidth - 14, 18, player);
        valueField = field(font, 2002, rightX + 10,
                selected == 0 ? jutsuValueY() : selected == 5 ? modeValueY() : bodyTop + 105,
                rightWidth - 20, 18, value);
        restoreField(searchField, searchFocused, searchCursor);
        restoreField(playerField, playerFocused, playerCursor);
        restoreField(valueField, valueFocused, valueCursor);
        cooldownPlayerField = field(font, 2014, rightX + 10, bodyTop + 76,
                rightWidth - 20, 18, cooldownPlayer);
        restoreField(cooldownPlayerField, cooldownPlayerFocused,
                cooldownPlayerField.getText().length());
        int performanceFieldWidth = Math.max(34, (rightWidth - 28) / 3);
        performanceHoursField = field(font, 2015, rightX + 10, bodyTop + 42,
                performanceFieldWidth, 18, performanceHours);
        performanceMinutesField = field(font, 2016, rightX + 14 + performanceFieldWidth,
                bodyTop + 42, performanceFieldWidth, 18, performanceMinutes);
        performanceSecondsField = field(font, 2017, rightX + 18 + performanceFieldWidth * 2,
                bodyTop + 42, rightWidth - 28 - performanceFieldWidth * 2, 18, performanceSeconds);
        restoreField(performanceHoursField, performanceHoursFocused, performanceHoursField.getText().length());
        restoreField(performanceMinutesField, performanceMinutesFocused, performanceMinutesField.getText().length());
        restoreField(performanceSecondsField, performanceSecondsFocused, performanceSecondsField.getText().length());
        int gap = 4;
        int durationAreaWidth = selected == 6 ? rightWidth - 96 : rightWidth - 20;
        int durationWidth = Math.max(34, (durationAreaWidth - gap * 2) / 3);
        int durationY = selected == 6 ? bodyTop + 149 : jutsuValueY() + 10;
        hoursField = field(font, 2003, rightX + 10, durationY, durationWidth, 18, hours);
        minutesField = field(font, 2004, rightX + 10 + durationWidth + gap,
                durationY, durationWidth, 18, minutes);
        secondsField = field(font, 2005, rightX + 10 + (durationWidth + gap) * 2,
                durationY, durationAreaWidth - durationWidth * 2 - gap * 2, 18, seconds);
        restoreField(hoursField, hoursFocused, hoursField.getText().length());
        restoreField(minutesField, minutesFocused, minutesField.getText().length());
        restoreField(secondsField, secondsFocused, secondsField.getText().length());
        String rankedName = rankedNameField == null ? rankedData.getString("SeasonName") : rankedNameField.getText();
        String rankedCommand = rankedCommandField == null ? "" : rankedCommandField.getText();
        String rankedGroup = rankedGroupField == null ? placementData(rankedPlacement).getString("Group")
                : rankedGroupField.getText();
        String rankedPlayer = rankedPlayerField == null ? "" : rankedPlayerField.getText();
        rankedNameField = field(font, 2010, rightX + 10, bodyTop + 97,
                rightWidth - 88, 18, rankedName);
        rankedCommandField = field(font, 2011, rightX + 10, bodyTop + 89,
                rightWidth - 20, 18, rankedCommand);
        rankedCommandField.setMaxStringLength(512);
        rankedGroupField = field(font, 2012, rightX + 10, bodyTop + 135,
                rightWidth - 20, 18, rankedGroup);
        rankedPlayerField = field(font, 2013, left + 7, bodyTop + 58,
                leftWidth - 14, 18, rankedPlayer);
        restoreField(rankedNameField, rankedNameFocused, rankedNameField.getText().length());
        restoreField(rankedCommandField, rankedCommandFocused, rankedCommandField.getText().length());
        restoreField(rankedGroupField, rankedGroupFocused, rankedGroupField.getText().length());
        restoreField(rankedPlayerField, rankedPlayerFocused, rankedPlayerField.getText().length());
        examRankPlayerField = field(font, 2014, rightX + 10, bodyTop + 59,
                rightWidth - 20, 18, examRankPlayer);
        restoreField(examRankPlayerField, examRankFocused, examRankPlayerField.getText().length());
        if (selected == 6 && !hadDurationFields) {
            setRankedDurationFields(rankedData.getLong("DurationMillis"));
        }
        syncValueField();
    }

    private static void restoreField(GuiTextField field, boolean focused, int cursor) {
        field.setFocused(focused);
        field.setCursorPosition(Math.min(cursor, field.getText().length()));
    }

    private static GuiTextField field(FontRenderer font, int id, int x, int y,
                                      int width, int height, String value) {
        GuiTextField field = new GuiTextField(id, font, x, y, Math.max(54, width), height);
        field.setMaxStringLength(80);
        field.setEnableBackgroundDrawing(true);
        field.setText(value == null ? "" : value);
        return field;
    }

    private void addSectionButtons(List<GuiButton> buttons) {
        int gap = 3;
        int columns = width >= 500 ? 7 : width >= 380 ? 4 : width >= 260 ? 3 : 2;
        int buttonWidth = Math.max(44, (width - gap * (columns - 1)) / columns);
        int buttonHeight = 22;
        for (int i = 0; i < TAB_IDS.length; i++) {
            int y = top + i / columns * (buttonHeight + 2);
            ThemedButton button = button(SECTION_BASE + i,
                    left + i % columns * (buttonWidth + gap), y, buttonWidth, buttonHeight,
                    sectionName(i), i == selected ? sectionAccent(i) : Theme.TAB_INACTIVE_BG,
                    sectionAccent(i));
            button.setSelected(i == selected);
            buttons.add(button);
        }
    }

    private void buildJutsuButtons(List<GuiButton> buttons) {
        List<String> categories = jutsuCategories();
        if (jutsuCategoryIndex >= categories.size()) jutsuCategoryIndex = 0;
        int filterWidth = Math.max(48, (leftWidth - 17) / 2);
        buttons.add(button(JUTSU_SCOPE, left + 7, bodyTop + 38, filterWidth, 19,
                restrictedJutsus
                        ? text("gui.rebornaddon.jutsu.restricted", "Restricted")
                        : text("gui.rebornaddon.jutsu.available", "Available"),
                restrictedJutsus ? Theme.RANKED_RED_DARK : Theme.TEAL_DARK,
                restrictedJutsus ? Theme.RANKED_RED : Theme.TEAL));
        buttons.add(button(JUTSU_CATEGORY, left + 10 + filterWidth, bodyTop + 38,
                leftWidth - 17 - filterWidth, 19, categories.get(jutsuCategoryIndex),
                Theme.TAB_INACTIVE_BG, Theme.PURPLE_LIGHT));
        List<JutsuEntry> filtered = filteredJutsus();
        selectedJutsu = preserve(selectedJutsu, filtered);
        addRowsAndPager(buttons, jutsuNames(filtered), filtered.indexOf(selectedJutsu));
        if (selectedJutsu == null || selectedJutsu.properties.isEmpty()) return;
        String property = jutsuProperty();
        int columns = jutsuPropertyColumns();
        int gap = 3;
        int propertyWidth = Math.max(44, (rightWidth - 20 - gap * (columns - 1)) / columns);
        for (int i = 0; i < selectedJutsu.properties.size(); i++) {
            int row = i / columns;
            int column = i % columns;
            ThemedButton propertyButton = button(JUTSU_PROPERTY_BASE + i,
                    rightX + 10 + column * (propertyWidth + gap), bodyTop + 78 + row * 21,
                    propertyWidth, 18, propertyLabel(selectedJutsu.properties.get(i)),
                    i == propertyIndex ? Theme.PURPLE_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.PURPLE_LIGHT);
            propertyButton.setSelected(i == propertyIndex);
            buttons.add(propertyButton);
        }
        if (isEffectEditorProperty(property)) {
            buttons.add(button(OPEN_EFFECT_RULES, rightX + 10, jutsuValueY(), rightWidth - 20, 20,
                    text("gui.rebornaddon.effects.open", "Open Effect Rules"),
                    Theme.PURPLE_DARK, Theme.PURPLE_LIGHT));
            return;
        }
        if (isBooleanJutsuProperty(property)) {
            booleanValue = Boolean.parseBoolean(selectedJutsu.effective(property));
            buttons.add(booleanButton(BOOLEAN_VALUE, booleanValue,
                    text("gui.rebornaddon.jutsu.enabled", "Enabled"),
                    text("gui.rebornaddon.jutsu.disabled", "Disabled")));
        }
        addEditorActions(buttons, true);
    }

    private void buildExamRankButtons(List<GuiButton> buttons) {
        JsonArray ranks = examRanks();
        examRankIndex = ranks.size() == 0 ? 0 : Math.max(0, Math.min(examRankIndex, ranks.size() - 1));
        int y = bodyTop + 91;
        int third = Math.max(44, (rightWidth - 28) / 3);
        ThemedButton previous = button(EXAM_RANK_PREVIOUS, rightX + 10, y, third, 20,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = ranks.size() > 1;
        buttons.add(previous);
        ThemedButton next = button(EXAM_RANK_NEXT, rightX + 14 + third, y, third, 20,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = ranks.size() > 1;
        buttons.add(next);
        ThemedButton apply = button(EXAM_RANK_APPLY, rightX + 18 + third * 2, y,
                rightWidth - 28 - third * 2, 20,
                text("gui.rebornaddon.admin.ranks.apply", "Set Rank"), Theme.TEAL_DARK, Theme.TEAL);
        apply.enabled = ranks.size() > 0 && examRankPlayerField != null
                && !examRankPlayerField.getText().trim().isEmpty();
        buttons.add(apply);
    }

    private boolean handleExamRankButton(int id) {
        JsonArray ranks = examRanks();
        if (id == EXAM_RANK_PREVIOUS || id == EXAM_RANK_NEXT) {
            if (ranks.size() > 0) examRankIndex = (examRankIndex
                    + (id == EXAM_RANK_PREVIOUS ? -1 : 1) + ranks.size()) % ranks.size();
            return true;
        }
        if (id == EXAM_RANK_APPLY && ranks.size() > 0 && examRankPlayerField != null) {
            JsonObject rank = ranks.get(examRankIndex).getAsJsonObject();
            JsonObject payload = new JsonObject();
            payload.addProperty("player", examRankPlayerField.getText().trim());
            payload.addProperty("rankId", jsonString(rank, "id"));
            RebornAddonNetwork.sendExamAction("rank.set", payload.toString());
            return false;
        }
        return false;
    }

    private void buildCooldownButtons(List<GuiButton> buttons) {
        ThemedButton player = button(COOLDOWN_CLEAR_PLAYER, rightX + 10, bodyTop + 103,
                rightWidth - 20, 20,
                text("gui.rebornaddon.admin.cooldowns.clear_player", "Clear Player Cooldowns"),
                Theme.TEAL_DARK, Theme.TEAL);
        player.enabled = cooldownPlayerField != null
                && !cooldownPlayerField.getText().trim().isEmpty();
        buttons.add(player);
        buttons.add(button(COOLDOWN_CLEAR_ALL, rightX + 10, bodyTop + 130,
                rightWidth - 20, 20,
                text("gui.rebornaddon.admin.cooldowns.clear_all", "Clear All Players"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    private boolean handleCooldownButton(int id) {
        if (id == COOLDOWN_CLEAR_PLAYER) {
            send(JutsuConfigurationService.COOLDOWN_CLEAR_PLAYER, "", "",
                    cooldownPlayerField == null ? "" : cooldownPlayerField.getText().trim());
            return false;
        }
        if (id == COOLDOWN_CLEAR_ALL) {
            send(JutsuConfigurationService.COOLDOWN_CLEAR_ALL, "", "", "");
            return false;
        }
        return false;
    }

    private void buildPerformanceButtons(List<GuiButton> buttons) {
        int buttonWidth = Math.max(56, (rightWidth - 23) / 2);
        buttons.add(button(PERFORMANCE_PROFILE, rightX + 10, bodyTop + 64, buttonWidth, 20,
                text("gui.rebornaddon.admin.performance.profile", "Start Profile"),
                Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(PERFORMANCE_REFRESH, rightX + 13 + buttonWidth, bodyTop + 64,
                rightWidth - 23 - buttonWidth, 20,
                text("gui.rebornaddon.admin.performance.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.TEAL));
        int viewWidth = Math.max(46, (rightWidth - 26) / 3);
        String[] views = {text("gui.rebornaddon.admin.performance.overview", "Overview"),
                text("gui.rebornaddon.admin.performance.hotspots", "Hotspots"),
                text("gui.rebornaddon.admin.performance.chunks", "Chunks")};
        for (int i = 0; i < 3; i++) {
            ThemedButton view = button(PERFORMANCE_VIEW_OVERVIEW + i,
                    rightX + 10 + i * (viewWidth + 3), bodyTop + 89,
                    i == 2 ? rightWidth - 20 - i * (viewWidth + 3) : viewWidth, 19,
                    views[i], performanceView == i ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.TEAL);
            view.setSelected(performanceView == i);
            buttons.add(view);
        }
        buttons.add(button(PERFORMANCE_OVERLAY, rightX + 10, bodyTop + 113,
                rightWidth - 20, 19,
                ClientPerformanceOverlay.INSTANCE.isEnabled()
                        ? text("gui.rebornaddon.admin.performance.overlay_on", "Nearby Overlay: On")
                        : text("gui.rebornaddon.admin.performance.overlay_off", "Nearby Overlay: Off"),
                ClientPerformanceOverlay.INSTANCE.isEnabled() ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                Theme.GOLD));
        if (performanceView == 0) return;
        NBTTagList rows = performanceRows();
        int rowsPerPage = performanceRowsPerPage();
        int start = performancePage * rowsPerPage;
        if (start >= rows.tagCount() && performancePage > 0) {
            performancePage = Math.max(0, (rows.tagCount() - 1) / rowsPerPage);
            start = performancePage * rowsPerPage;
        }
        for (int i = 0; i < rowsPerPage && start + i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(start + i);
            String label = performanceView == 1
                    ? shortRegistryName(row.getString("Name")) + " @ " + row.getInteger("X")
                    + ", " + row.getInteger("Y") + ", " + row.getInteger("Z")
                    : "Chunk " + row.getInteger("ChunkX") + ", " + row.getInteger("ChunkZ")
                    + " (dim " + row.getInteger("Dimension") + ")";
            ThemedButton result = button(PERFORMANCE_ROW_BASE + i, rightX + 10,
                    bodyTop + 137 + i * 21, rightWidth - 20, 18, label,
                    start + i == performanceSelection ? Theme.PURPLE_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.PURPLE_LIGHT);
            result.setSelected(start + i == performanceSelection);
            result.setTextAlignment(ThemedButton.Alignment.LEFT);
            result.setTextInset(7);
            buttons.add(result);
        }
        int bottom = top + height - 22;
        buttons.add(button(PERFORMANCE_PREVIOUS, rightX + 10, bottom, 28, 18, "<",
                Theme.TAB_INACTIVE_BG, Theme.TEAL));
        buttons.add(button(PERFORMANCE_NEXT, rightX + 42, bottom, 28, 18, ">",
                Theme.TAB_INACTIVE_BG, Theme.TEAL));
        ThemedButton teleport = button(PERFORMANCE_TELEPORT, rightX + 76, bottom,
                rightWidth - 86, 18,
                text("gui.rebornaddon.admin.performance.teleport", "Teleport to Selected"),
                Theme.TEAL_DARK, Theme.TEAL);
        teleport.enabled = performanceSelection >= 0 && performanceSelection < rows.tagCount();
        buttons.add(teleport);
    }

    private boolean handlePerformanceButton(int id) {
        if (id == PERFORMANCE_REFRESH) {
            requestPerformance(true);
            return false;
        }
        if (id == PERFORMANCE_PROFILE) {
            long duration = performanceDurationMillis();
            if (duration > 0L) RebornAddonNetwork.requestPerformanceProfile(duration);
            return false;
        }
        if (id >= PERFORMANCE_VIEW_OVERVIEW && id <= PERFORMANCE_VIEW_CHUNKS) {
            performanceView = id - PERFORMANCE_VIEW_OVERVIEW;
            performancePage = 0;
            performanceSelection = -1;
            return true;
        }
        if (id == PERFORMANCE_OVERLAY) {
            ClientPerformanceOverlay.INSTANCE.toggle();
            return true;
        }
        if (id == PERFORMANCE_PREVIOUS || id == PERFORMANCE_NEXT) {
            performancePage = Math.max(0, performancePage + (id == PERFORMANCE_PREVIOUS ? -1 : 1));
            return true;
        }
        if (id >= PERFORMANCE_ROW_BASE && id < PERFORMANCE_ROW_BASE + performanceRowsPerPage()) {
            performanceSelection = performancePage * performanceRowsPerPage()
                    + id - PERFORMANCE_ROW_BASE;
            return true;
        }
        if (id == PERFORMANCE_TELEPORT && performanceSelection >= 0) {
            RebornAddonNetwork.teleportPerformanceResult(performanceView == 2, performanceSelection);
            return false;
        }
        return false;
    }

    private void buildStoreButtons(List<GuiButton> buttons) {
        List<StoreEntry> filtered = filteredStore();
        selectedStore = preserve(selectedStore, filtered);
        addRowsAndPager(buttons, storeNames(filtered), filtered.indexOf(selectedStore));
        if (selectedStore == null) return;
        buttons.add(button(PROPERTY, rightX + 10, bodyTop + 74, rightWidth - 20, 20,
                propertyIndex == 0 ? text("gui.rebornaddon.admin.price", "Price (Ryo)")
                        : text("gui.rebornaddon.admin.availability", "Availability"),
                Theme.TAB_INACTIVE_BG, Theme.COPPER));
        if (propertyIndex == 1) {
            booleanValue = selectedStore.purchasable;
            buttons.add(booleanButton(BOOLEAN_VALUE, booleanValue,
                    text("gui.rebornaddon.admin.purchasable", "Purchasable"),
                    text("gui.rebornaddon.admin.blocked", "Blocked")));
        }
        addEditorActions(buttons, false);
        buttons.add(button(RELOAD, rightX + rightWidth - 62, bodyTop + 158, 52, 19,
                text("gui.rebornaddon.admin.reload", "Reload"), Theme.TAB_INACTIVE_BG, Theme.COPPER));
    }

    private void buildSectionButtons(List<GuiButton> buttons) {
        List<ScopeEntry> filtered = filteredScopes();
        selectedScope = preserve(selectedScope, filtered);
        addRowsAndPager(buttons, scopeNames(filtered), filtered.indexOf(selectedScope));
        if (selectedScope == null) return;
        booleanValue = selectedScope.enabled;
        buttons.add(booleanButton(BOOLEAN_VALUE, booleanValue,
                text("gui.rebornaddon.admin.enabled", "Enabled"),
                text("gui.rebornaddon.admin.disabled", "Disabled")));
        int buttonWidth = Math.max(54, (rightWidth - 24) / 2);
        buttons.add(button(SAVE, rightX + 10, bodyTop + 135, buttonWidth, 20,
                text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RESET_PROPERTY, rightX + 14 + buttonWidth, bodyTop + 135,
                rightWidth - 24 - buttonWidth, 20,
                text("gui.rebornaddon.jutsu.reset_property", "Reset"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void buildLimitButtons(List<GuiButton> buttons) {
        buttons.add(button(QUOTA_LOAD, left + 7, bodyTop + 45, leftWidth - 14, 20,
                text("gui.rebornaddon.admin.load_player", "Load Player"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(QUOTA_KIND, rightX + 10, bodyTop + 42, rightWidth - 20, 20,
                quotaLabel(quotaKind()), Theme.TAB_INACTIVE_BG, Theme.GOLD));
        int buttonWidth = Math.max(44, (rightWidth - 28) / 3);
        buttons.add(button(QUOTA_SET, rightX + 10, bodyTop + 132, buttonWidth, 20,
                text("gui.rebornaddon.admin.set_count", "Set"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(QUOTA_ADD, rightX + 14 + buttonWidth, bodyTop + 132, buttonWidth, 20,
                text("gui.rebornaddon.admin.adjust_count", "Adjust"), Theme.TAB_INACTIVE_BG, Theme.GOLD));
        buttons.add(button(QUOTA_RESET, rightX + 18 + buttonWidth * 2, bodyTop + 132,
                rightWidth - 28 - buttonWidth * 2, 20,
                text("gui.rebornaddon.admin.reset_count", "Reset"), Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        buttons.add(button(RESET_ALL, rightX + 10, bodyTop + 159, rightWidth - 20, 19,
                resetAllArmed ? text("gui.rebornaddon.admin.confirm_reset_counts", "Confirm Reset All")
                        : text("gui.rebornaddon.admin.reset_all_counts", "Reset All Counts"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    private void buildQuestButtons(List<GuiButton> buttons) {
        buttons.add(button(QUEST_ACTION, rightX + 10, bodyTop + 42, rightWidth - 20, 20,
                text("gui.rebornaddon.admin.quest_action", "Action") + ": "
                        + title(QUEST_ACTIONS[questActionIndex]),
                Theme.TAB_INACTIVE_BG, Theme.GOLD));
        buttons.add(button(QUEST_APPLY, rightX + 10, bodyTop + 132, rightWidth - 20, 20,
                text("gui.rebornaddon.admin.apply_quest_action", "Apply Quest Action"),
                Theme.TEAL_DARK, Theme.TEAL));
    }

    private void buildModeButtons(List<GuiButton> buttons) {
        List<ModeEntry> filtered = filteredModes();
        selectedMode = preserve(selectedMode, filtered);
        addRowsAndPager(buttons, modeNames(filtered), filtered.indexOf(selectedMode));
        if (selectedMode == null || selectedMode.properties.isEmpty()) return;
        int columns = modePropertyColumns();
        int gap = 3;
        int propertyWidth = Math.max(44, (rightWidth - 20 - gap * (columns - 1)) / columns);
        for (int i = 0; i < selectedMode.properties.size(); i++) {
            int row = i / columns;
            int column = i % columns;
            ThemedButton propertyButton = button(MODE_PROPERTY_BASE + i,
                    rightX + 10 + column * (propertyWidth + gap), modePropertyTop() + row * 21,
                    propertyWidth, 18, modePropertyLabel(selectedMode.properties.get(i)),
                    i == propertyIndex ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG, Theme.TEAL);
            propertyButton.setSelected(i == propertyIndex);
            buttons.add(propertyButton);
        }
        if ("incompatible-modes".equals(modeProperty())) {
            buttons.add(button(OPEN_MODE_COMBINATIONS, rightX + 10, modeValueY(), rightWidth - 20, 20,
                    text("gui.rebornaddon.mode.choose_combinations", "Choose Blocked Modes"),
                    Theme.TEAL_DARK, Theme.TEAL));
        }
        if ("enabled".equals(modeProperty())) {
            booleanValue = Boolean.parseBoolean(selectedMode.effective(modeProperty()));
            buttons.add(booleanButton(BOOLEAN_VALUE, booleanValue,
                    text("gui.rebornaddon.admin.enabled", "Enabled"),
                    text("gui.rebornaddon.admin.disabled", "Disabled")));
        }
        addModeEditorActions(buttons);
    }

    private void buildChakraControlButtons(List<GuiButton> buttons) {
        chakraSettingIndex = Math.max(0, Math.min(chakraSettingIndex,
                CHAKRA_SETTING_IDS.length - 1));
        for (int i = 0; i < CHAKRA_SETTING_IDS.length; i++) {
            ThemedButton setting = button(CHAKRA_SETTING_BASE + i, left + 7,
                    bodyTop + 32 + i * 25, leftWidth - 14, 21,
                    chakraSettingLabel(i), i == chakraSettingIndex
                            ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG, Theme.TEAL);
            setting.setSelected(i == chakraSettingIndex);
            buttons.add(setting);
        }
        boolean available = chakraData.getBoolean("Available");
        if (chakraSettingIndex < 3) {
            booleanValue = chakraBoolean(CHAKRA_SETTING_IDS[chakraSettingIndex]);
            ThemedButton toggle = button(BOOLEAN_VALUE, rightX + 10, bodyTop + 104,
                    rightWidth - 20, 20,
                    booleanValue ? text("gui.rebornaddon.admin.enabled", "Enabled")
                            : text("gui.rebornaddon.admin.disabled", "Disabled"),
                    booleanValue ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                    booleanValue ? Theme.TEAL : Theme.RANKED_RED);
            toggle.enabled = available;
            buttons.add(toggle);
        }
        int actionWidth = Math.max(48, (rightWidth - 28) / 3);
        ThemedButton save = button(CHAKRA_SAVE, rightX + 10, bodyTop + 145,
                actionWidth, 20, text("gui.rebornaddon.jutsu.save", "Save"),
                Theme.TEAL_DARK, Theme.TEAL);
        save.enabled = available;
        buttons.add(save);
        ThemedButton reset = button(CHAKRA_RESET, rightX + 14 + actionWidth,
                bodyTop + 145, actionWidth, 20,
                text("gui.rebornaddon.chakra_control.reset", "Reset Setting"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        reset.enabled = available;
        buttons.add(reset);
        ThemedButton reload = button(CHAKRA_RELOAD, rightX + 18 + actionWidth * 2,
                bodyTop + 145, rightWidth - 28 - actionWidth * 2, 20,
                text("gui.rebornaddon.admin.reload", "Reload"),
                Theme.TAB_INACTIVE_BG, Theme.TEAL);
        reload.enabled = available;
        buttons.add(reload);
    }

    private boolean handleChakraControlButton(int id) {
        if (id >= CHAKRA_SETTING_BASE
                && id < CHAKRA_SETTING_BASE + CHAKRA_SETTING_IDS.length) {
            chakraSettingIndex = id - CHAKRA_SETTING_BASE;
            valueContext = "";
            return true;
        }
        if (id == BOOLEAN_VALUE && chakraSettingIndex < 3) {
            booleanValue = !booleanValue;
            return true;
        }
        if (id == CHAKRA_SAVE) {
            String value = chakraSettingIndex < 3 ? Boolean.toString(booleanValue)
                    : valueField == null ? "" : valueField.getText();
            send(ChakraControlConfigurationService.UPDATE, "", chakraSetting(), value);
            return false;
        }
        if (id == CHAKRA_RESET) {
            send(ChakraControlConfigurationService.RESET, "", chakraSetting(), "");
            return false;
        }
        if (id == CHAKRA_RELOAD) {
            send(ChakraControlConfigurationService.RELOAD, "", "", "");
            return false;
        }
        return false;
    }

    private void buildRankedButtons(List<GuiButton> buttons) {
        int gap = 3;
        int viewWidth = Math.max(34, (leftWidth - 20) / 3);
        String[] labels = {
                text("gui.rebornaddon.admin.ranked.season", "Season"),
                text("gui.rebornaddon.admin.ranked.rewards", "Rewards"),
                text("gui.rebornaddon.admin.ranked.exclusions", "Exclusions")
        };
        for (int i = 0; i < labels.length; i++) {
            ThemedButton view = button(RANKED_VIEW_BASE + i,
                    left + 7 + i * (viewWidth + gap), bodyTop + 27,
                    i == 2 ? leftWidth - 14 - i * (viewWidth + gap) : viewWidth, 20,
                    labels[i], i == rankedView ? Theme.RANKED_RED_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.RANKED_RED);
            view.setSelected(i == rankedView);
            buttons.add(view);
        }
        if (!rankedData.getBoolean("Available")) return;
        if (rankedView == 0) buildRankedSeasonButtons(buttons);
        else if (rankedView == 1) buildRankedRewardButtons(buttons);
        else buildRankedExclusionButtons(buttons);
    }

    private void buildRankedSeasonButtons(List<GuiButton> buttons) {
        String state = rankedData.getString("State");
        String startLabel = "paused".equals(state)
                ? text("gui.rebornaddon.admin.ranked.resume", "Resume")
                : text("gui.rebornaddon.admin.ranked.start", "Start");
        int lifecycleWidth = Math.max(46, (rightWidth - 28) / 3);
        ThemedButton start = button(RANKED_START, rightX + 10, bodyTop + 36,
                lifecycleWidth, 20, startLabel, Theme.TEAL_DARK, Theme.TEAL);
        start.enabled = !"running".equals(state) && !rankedData.getBoolean("ResetPending");
        buttons.add(start);
        ThemedButton pause = button(RANKED_PAUSE, rightX + 14 + lifecycleWidth, bodyTop + 36,
                lifecycleWidth, 20, text("gui.rebornaddon.admin.ranked.pause", "Pause"),
                Theme.TAB_INACTIVE_BG, Theme.GOLD);
        pause.enabled = "running".equals(state);
        buttons.add(pause);
        buttons.add(button(RANKED_END, rightX + 18 + lifecycleWidth * 2, bodyTop + 36,
                rightWidth - 28 - lifecycleWidth * 2, 20,
                resetAllArmed ? text("gui.rebornaddon.admin.ranked.confirm_end", "Confirm End")
                        : text("gui.rebornaddon.admin.ranked.end", "End"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        boolean leaderboard = rankedData.getBoolean("LeaderboardEnabled");
        buttons.add(button(RANKED_LEADERBOARD, rightX + 10, bodyTop + 61, rightWidth - 20, 19,
                leaderboard ? text("gui.rebornaddon.admin.ranked.leaderboard_on", "Leaderboard Enabled")
                        : text("gui.rebornaddon.admin.ranked.leaderboard_off", "Leaderboard Disabled"),
                leaderboard ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                leaderboard ? Theme.TEAL : Theme.RANKED_RED));
        buttons.add(button(RANKED_SAVE_NAME, rightX + rightWidth - 70, bodyTop + 97,
                60, 19, text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RANKED_SAVE_DURATION, rightX + rightWidth - 70, bodyTop + 149,
                60, 19, text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RANKED_RELOAD, left + 7, top + height - 23, leftWidth - 14, 19,
                text("gui.rebornaddon.admin.reload", "Reload"), Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void buildRankedRewardButtons(List<GuiButton> buttons) {
        int selectorY = bodyTop + 56;
        buttons.add(button(RANKED_PLACEMENT_PREVIOUS, left + 7, selectorY, 28, 20,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        ThemedButton placement = button(-1, left + 39, selectorY, leftWidth - 78, 20,
                ClientLocalization.format("gui.rebornaddon.admin.ranked.placement", "Place %s", rankedPlacement),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        placement.enabled = false;
        buttons.add(placement);
        buttons.add(button(RANKED_PLACEMENT_NEXT, left + leftWidth - 35, selectorY, 28, 20,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        NBTTagCompound placementData = placementData(rankedPlacement);
        boolean enabled = placementData.getBoolean("Enabled");
        buttons.add(button(RANKED_PLACEMENT_ENABLED, rightX + 10, bodyTop + 36,
                rightWidth - 20, 19,
                enabled ? text("gui.rebornaddon.admin.ranked.reward_on", "Placement Rewards Enabled")
                        : text("gui.rebornaddon.admin.ranked.reward_off", "Placement Rewards Disabled"),
                enabled ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                enabled ? Theme.TEAL : Theme.RANKED_RED));
        int half = Math.max(52, (rightWidth - 24) / 2);
        buttons.add(button(RANKED_ADD_ITEM, rightX + 10, bodyTop + 61, half, 19,
                text("gui.rebornaddon.admin.ranked.add_held", "Add Held Item"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RANKED_CLEAR_ITEMS, rightX + 14 + half, bodyTop + 61,
                rightWidth - 24 - half, 19,
                text("gui.rebornaddon.admin.ranked.clear_items", "Clear Items"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttons.add(button(RANKED_ADD_COMMAND, rightX + 10, bodyTop + 110, half, 19,
                text("gui.rebornaddon.admin.ranked.add_command", "Add Command"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RANKED_CLEAR_COMMANDS, rightX + 14 + half, bodyTop + 110,
                rightWidth - 24 - half, 19,
                text("gui.rebornaddon.admin.ranked.clear_commands", "Clear Commands"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        if (rankedPlacement <= 3) {
            buttons.add(button(RANKED_SAVE_GROUP, rightX + rightWidth - 70, bodyTop + 156,
                    60, 19, text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        }
    }

    private void buildRankedExclusionButtons(List<GuiButton> buttons) {
        buttons.add(button(RANKED_BLOCK_PLAYER, left + 7, bodyTop + 79, leftWidth - 14, 19,
                text("gui.rebornaddon.admin.ranked.exclude", "Exclude Player"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        NBTTagList blocked = rankedData.getTagList("BlockedPlayers", 10);
        int rows = Math.max(1, (top + height - 25 - (bodyTop + 105)) / 22);
        int pages = Math.max(1, (blocked.tagCount() + rows - 1) / rows);
        rankedBlockedPage = Math.max(0, Math.min(rankedBlockedPage, pages - 1));
        int start = rankedBlockedPage * rows;
        int end = Math.min(blocked.tagCount(), start + rows);
        for (int i = start; i < end; i++) {
            NBTTagCompound player = blocked.getCompoundTagAt(i);
            ThemedButton row = button(RANKED_BLOCKED_BASE + i - start, left + 7,
                    bodyTop + 105 + (i - start) * 22, leftWidth - 14, 19,
                    player.getString("Name"), Theme.TAB_INACTIVE_BG, Theme.RANKED_RED);
            row.setSelected(i == rankedBlockedSelection);
            buttons.add(row);
        }
        ThemedButton previous = button(PREVIOUS_PAGE, left + 7, top + height - 23, 30, 19,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = rankedBlockedPage > 0;
        buttons.add(previous);
        ThemedButton next = button(NEXT_PAGE, left + leftWidth - 37, top + height - 23, 30, 19,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = rankedBlockedPage + 1 < pages;
        buttons.add(next);
        ThemedButton remove = button(RANKED_UNBLOCK_PLAYER, rightX + 10, bodyTop + 85,
                rightWidth - 20, 20,
                text("gui.rebornaddon.admin.ranked.remove_exclusion", "Remove Exclusion"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        remove.enabled = rankedBlockedSelection >= 0 && rankedBlockedSelection < blocked.tagCount();
        buttons.add(remove);
    }

    private void addModeEditorActions(List<GuiButton> buttons) {
        int buttonWidth = Math.max(43, (rightWidth - 28) / 3);
        int y = modeActionY();
        buttons.add(button(SAVE, rightX + 10, y, buttonWidth, 20,
                text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RESET_PROPERTY, rightX + 14 + buttonWidth, y, buttonWidth, 20,
                text("gui.rebornaddon.jutsu.reset_property", "Reset Value"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttons.add(button(RESET_ENTRY, rightX + 18 + buttonWidth * 2, y,
                rightWidth - 28 - buttonWidth * 2, 20, "Reset Mode",
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        buttons.add(button(RESET_ALL, rightX + 10, y + 26, rightWidth - 76, 19,
                resetAllArmed ? text("gui.rebornaddon.jutsu.confirm_reset_all", "Confirm Reset All")
                        : text("gui.rebornaddon.jutsu.reset_all", "Reset All"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        buttons.add(button(RELOAD, rightX + rightWidth - 62, y + 26, 52, 19,
                text("gui.rebornaddon.admin.reload", "Reload"), Theme.TAB_INACTIVE_BG, Theme.TEAL));
    }

    private void addRowsAndPager(List<GuiButton> buttons, List<String> labels, int selectedIndex) {
        int totalPages = Math.max(1, (labels.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(labels.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            ThemedButton row = button(ROW_BASE + i - start, left + 7,
                    listTop + (i - start) * 22, leftWidth - 14, 19,
                    labels.get(i), Theme.TAB_INACTIVE_BG, sectionAccent(selected));
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(6);
            row.setSelected(i == selectedIndex);
            buttons.add(row);
        }
        int y = top + height - 23;
        ThemedButton previous = button(PREVIOUS_PAGE, left + 7, y, 30, 19,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttons.add(previous);
        ThemedButton next = button(NEXT_PAGE, left + leftWidth - 37, y, 30, 19,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < totalPages;
        buttons.add(next);
    }

    private void addEditorActions(List<GuiButton> buttons, boolean jutsu) {
        int buttonWidth = Math.max(43, (rightWidth - 28) / 3);
        int y = jutsu ? jutsuActionY() : bodyTop + 132;
        buttons.add(button(SAVE, rightX + 10, y, buttonWidth, 20,
                text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(RESET_PROPERTY, rightX + 14 + buttonWidth, y, buttonWidth, 20,
                text("gui.rebornaddon.jutsu.reset_property", "Reset Value"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttons.add(button(RESET_ENTRY, rightX + 18 + buttonWidth * 2, y,
                rightWidth - 28 - buttonWidth * 2, 20,
                jutsu ? text("gui.rebornaddon.jutsu.reset_jutsu", "Reset Jutsu")
                        : text("gui.rebornaddon.admin.reset_item", "Reset Item"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        int resetWidth = Math.max(78, rightWidth - (jutsu ? 20 : 76));
        buttons.add(button(RESET_ALL, rightX + 10, y + 26, resetWidth, 19,
                resetAllArmed ? text("gui.rebornaddon.jutsu.confirm_reset_all", "Confirm Reset All")
                        : jutsu ? text("gui.rebornaddon.jutsu.reset_all", "Reset All")
                        : text("gui.rebornaddon.admin.reset_store", "Reset Store"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    private ThemedButton booleanButton(int id, boolean value, String on, String off) {
        return button(id, rightX + 10, selected == 0 ? jutsuValueY()
                        : selected == 5 ? modeValueY() : bodyTop + 103,
                rightWidth - 20, 20,
                value ? on : off, value ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                value ? Theme.TEAL : Theme.RANKED_RED);
    }

    private boolean handleJutsuButton(int id) {
        if (id == JUTSU_SCOPE) {
            restrictedJutsus = !restrictedJutsus;
            page = 0;
            selectedJutsu = null;
            return true;
        }
        if (id == JUTSU_CATEGORY) {
            jutsuCategoryIndex = (jutsuCategoryIndex + 1) % Math.max(1, jutsuCategories().size());
            page = 0;
            selectedJutsu = null;
            return true;
        }
        if (selectedJutsu == null) return false;
        if (id >= JUTSU_PROPERTY_BASE
                && id < JUTSU_PROPERTY_BASE + selectedJutsu.properties.size()) {
            propertyIndex = id - JUTSU_PROPERTY_BASE;
            valueContext = "";
            if (isEffectEditorProperty(jutsuProperty())) openEffectEditor();
        } else if (id == OPEN_EFFECT_RULES) {
            openEffectEditor();
        } else if (id == BOOLEAN_VALUE) {
            booleanValue = !booleanValue;
        } else if (id == SAVE) {
            String property = jutsuProperty();
            send(0, selectedJutsu.id, property,
                    isBooleanJutsuProperty(property) ? Boolean.toString(booleanValue)
                            : usesDurationValue() ? durationInput() : valueField.getText());
        } else if (id == RESET_PROPERTY) {
            send(1, selectedJutsu.id, jutsuProperty(), "");
        } else if (id == RESET_ENTRY) {
            send(1, selectedJutsu.id, "all", "");
        } else if (id == RESET_ALL) {
            if (resetAllArmed) send(2, "", "", "");
            resetAllArmed = !resetAllArmed;
        } else return false;
        return true;
    }

    private boolean handleModeButton(int id) {
        if (selectedMode == null) return false;
        if (id >= MODE_PROPERTY_BASE && id < MODE_PROPERTY_BASE + selectedMode.properties.size()) {
            propertyIndex = id - MODE_PROPERTY_BASE;
            valueContext = "";
            if ("incompatible-modes".equals(modeProperty())) openModeCombinationEditor();
        } else if (id == OPEN_MODE_COMBINATIONS) {
            openModeCombinationEditor();
        } else if (id == BOOLEAN_VALUE) {
            booleanValue = !booleanValue;
        } else if (id == SAVE && "incompatible-modes".equals(modeProperty())) {
            openModeCombinationEditor();
        } else if (id == SAVE) {
            send(ModeConfigurationService.UPDATE, selectedMode.id, modeProperty(),
                    "enabled".equals(modeProperty()) ? Boolean.toString(booleanValue)
                            : modeInputValue());
        } else if (id == RESET_PROPERTY) {
            send(ModeConfigurationService.RESET, selectedMode.id, modeProperty(), "");
        } else if (id == RESET_ENTRY) {
            send(ModeConfigurationService.RESET, selectedMode.id, "all", "");
        } else if (id == RESET_ALL) {
            if (resetAllArmed) send(ModeConfigurationService.RESET_ALL, "", "", "");
            resetAllArmed = !resetAllArmed;
        } else if (id == RELOAD) {
            send(ModeConfigurationService.RELOAD, "", "", "");
        } else return false;
        return true;
    }

    private boolean handleRankedButton(int id) {
        if (id >= RANKED_VIEW_BASE && id < RANKED_VIEW_BASE + 3) {
            rankedView = id - RANKED_VIEW_BASE;
            resetAllArmed = false;
            rankedBlockedSelection = -1;
            return true;
        }
        int rows = Math.max(1, (top + height - 25 - (bodyTop + 105)) / 22);
        if (id >= RANKED_BLOCKED_BASE && id < RANKED_BLOCKED_BASE + rows) {
            rankedBlockedSelection = rankedBlockedPage * rows + id - RANKED_BLOCKED_BASE;
            return true;
        }
        if (!rankedData.getBoolean("Available")) return false;
        if (id == RANKED_START) {
            send(SeasonManager.ACTION_LIFECYCLE,
                    "paused".equals(rankedData.getString("State")) ? "resume" : "start", "", "");
        } else if (id == RANKED_PAUSE) {
            send(SeasonManager.ACTION_LIFECYCLE, "pause", "", "");
        } else if (id == RANKED_END) {
            if (resetAllArmed) send(SeasonManager.ACTION_LIFECYCLE, "end", "", "");
            resetAllArmed = !resetAllArmed;
        } else if (id == RANKED_LEADERBOARD) {
            send(SeasonManager.ACTION_SET_LEADERBOARD, "", "",
                    Boolean.toString(!rankedData.getBoolean("LeaderboardEnabled")));
        } else if (id == RANKED_SAVE_NAME) {
            send(SeasonManager.ACTION_SET_NAME, "", "", rankedNameField.getText());
        } else if (id == RANKED_SAVE_DURATION) {
            long seconds = durationPart(hoursField) * 3600L
                    + durationPart(minutesField) * 60L + durationPart(secondsField);
            send(SeasonManager.ACTION_SET_DURATION, "", "", Long.toString(seconds * 1000L));
        } else if (id == RANKED_PLACEMENT_PREVIOUS || id == RANKED_PLACEMENT_NEXT) {
            rankedPlacement += id == RANKED_PLACEMENT_PREVIOUS ? -1 : 1;
            rankedPlacement = Math.max(1, Math.min(100, rankedPlacement));
            rankedGroupField.setText(placementData(rankedPlacement).getString("Group"));
        } else if (id == RANKED_PLACEMENT_ENABLED) {
            send(SeasonManager.ACTION_SET_PLACEMENT, Integer.toString(rankedPlacement), "",
                    Boolean.toString(!placementData(rankedPlacement).getBoolean("Enabled")));
        } else if (id == RANKED_ADD_ITEM) {
            send(SeasonManager.ACTION_ADD_ITEM, Integer.toString(rankedPlacement), "", "");
        } else if (id == RANKED_CLEAR_ITEMS) {
            send(SeasonManager.ACTION_CLEAR_ITEMS, Integer.toString(rankedPlacement), "", "");
        } else if (id == RANKED_ADD_COMMAND) {
            send(SeasonManager.ACTION_ADD_COMMAND, Integer.toString(rankedPlacement), "",
                    rankedCommandField.getText());
        } else if (id == RANKED_CLEAR_COMMANDS) {
            send(SeasonManager.ACTION_CLEAR_COMMANDS, Integer.toString(rankedPlacement), "", "");
        } else if (id == RANKED_SAVE_GROUP) {
            send(SeasonManager.ACTION_SET_GROUP, Integer.toString(rankedPlacement), "",
                    rankedGroupField.getText());
        } else if (id == RANKED_BLOCK_PLAYER) {
            send(SeasonManager.ACTION_BLOCK_PLAYER, "", "", rankedPlayerField.getText());
        } else if (id == RANKED_UNBLOCK_PLAYER) {
            NBTTagList blocked = rankedData.getTagList("BlockedPlayers", 10);
            if (rankedBlockedSelection >= 0 && rankedBlockedSelection < blocked.tagCount()) {
                send(SeasonManager.ACTION_UNBLOCK_PLAYER,
                        blocked.getCompoundTagAt(rankedBlockedSelection).getString("Uuid"), "", "");
            }
        } else if (id == RANKED_RELOAD) {
            send(SeasonManager.ACTION_RELOAD, "", "", "");
        } else return false;
        return true;
    }

    private boolean handleStoreButton(int id) {
        if (id == RELOAD) {
            send(JutsuConfigurationService.STORE_RELOAD, "", "", "");
            return true;
        }
        if (selectedStore == null) return false;
        if (id == PROPERTY) {
            propertyIndex = (propertyIndex + 1) % 2;
            valueContext = "";
        } else if (id == BOOLEAN_VALUE) {
            booleanValue = !booleanValue;
        } else if (id == SAVE) {
            String property = propertyIndex == 0 ? "price" : "purchasable";
            send(JutsuConfigurationService.STORE_UPDATE, selectedStore.key, property,
                    propertyIndex == 0 ? valueField.getText() : Boolean.toString(booleanValue));
        } else if (id == RESET_PROPERTY) {
            send(JutsuConfigurationService.STORE_RESET, selectedStore.key,
                    propertyIndex == 0 ? "price" : "purchasable", "");
        } else if (id == RESET_ENTRY) {
            send(JutsuConfigurationService.STORE_RESET, selectedStore.key, "all", "");
        } else if (id == RESET_ALL) {
            if (resetAllArmed) send(JutsuConfigurationService.STORE_RESET_ALL, "", "", "");
            resetAllArmed = !resetAllArmed;
        } else return false;
        return true;
    }

    private boolean handleSectionButton(int id) {
        if (selectedScope == null) return false;
        if (id == BOOLEAN_VALUE) booleanValue = !booleanValue;
        else if (id == SAVE) send(JutsuConfigurationService.STORE_SCOPE_UPDATE,
                selectedScope.key, "", Boolean.toString(booleanValue));
        else if (id == RESET_PROPERTY) send(JutsuConfigurationService.STORE_SCOPE_RESET,
                selectedScope.key, "", "");
        else return false;
        return true;
    }

    private boolean handleLimitButton(int id) {
        String player = playerField.getText().trim();
        if (id == QUOTA_LOAD) send(JutsuConfigurationService.QUOTA_LOAD, player, "", "");
        else if (id == QUOTA_KIND) quotaIndex = (quotaIndex + 1) % QUOTA_KINDS.size();
        else if (id == QUOTA_SET) send(JutsuConfigurationService.QUOTA_SET,
                player, quotaKind(), valueField.getText());
        else if (id == QUOTA_ADD) send(JutsuConfigurationService.QUOTA_ADD,
                player, quotaKind(), valueField.getText());
        else if (id == QUOTA_RESET) send(JutsuConfigurationService.QUOTA_RESET,
                player, quotaKind(), "");
        else if (id == RESET_ALL) {
            if (resetAllArmed) send(JutsuConfigurationService.QUOTA_RESET, player, "all", "");
            resetAllArmed = !resetAllArmed;
        } else return false;
        return true;
    }

    private boolean handleQuestButton(int id) {
        if (id == QUEST_ACTION) {
            questActionIndex = (questActionIndex + 1) % QUEST_ACTIONS.length;
            return true;
        }
        if (id == QUEST_APPLY) {
            com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
            payload.addProperty("player", playerField.getText().trim());
            payload.addProperty("questId", valueField.getText().trim());
            payload.addProperty("action", QUEST_ACTIONS[questActionIndex]);
            RebornAddonNetwork.sendNativeContentAction("admin.quest-player", payload.toString());
            return false;
        }
        return false;
    }

    private void selectRow(int index) {
        if (selected == 0) {
            List<JutsuEntry> list = filteredJutsus();
            selectedJutsu = index >= 0 && index < list.size() ? list.get(index) : selectedJutsu;
        } else if (selected == 1) {
            List<StoreEntry> list = filteredStore();
            selectedStore = index >= 0 && index < list.size() ? list.get(index) : selectedStore;
        } else if (selected == 2) {
            List<ScopeEntry> list = filteredScopes();
            selectedScope = index >= 0 && index < list.size() ? list.get(index) : selectedScope;
        } else if (selected == 5) {
            List<ModeEntry> list = filteredModes();
            selectedMode = index >= 0 && index < list.size() ? list.get(index) : selectedMode;
        }
        propertyIndex = 0;
        resetAllArmed = false;
        valueContext = "";
    }

    private void drawNotice(FontRenderer font, int accent) {
        String notice = snapshot.getString("Message");
        if (selected == 5 && notice.startsWith("Loaded ")
                && notice.contains(" configurable jutsu entries")) {
            notice = ClientLocalization.format("gui.rebornaddon.admin.modes_loaded",
                    "Loaded %s configurable modes.", modes.size());
        }
        if (!notice.isEmpty()) {
            font.drawString(trim(font, notice, rightWidth - 20), rightX + 10,
                    bodyTop + 6, snapshot.getBoolean("Success") ? Theme.SUCCESS_BRIGHT : Theme.GOLD);
        }
        GuiChrome.rule(rightX + 10, rightX + rightWidth - 10, bodyTop + 20, accent);
    }

    private void drawJutsu(FontRenderer font, int accent) {
        if (selectedJutsu == null) {
            drawEmpty(font, text("gui.rebornaddon.jutsu.empty", "No configurable jutsus match."));
            return;
        }
        drawTitle(font, selectedJutsu.displayName, selectedJutsu.category, accent);
        String property = jutsuProperty();
        String current = selectedJutsu.effective(property);
        String valueState = selectedJutsu.hasOverride(property)
                ? text("gui.rebornaddon.admin.saved_override", "Saved override")
                : text("gui.rebornaddon.admin.mod_default", "Mod default");
        String currentText = isEffectEditorProperty(property)
                ? effectRuleSummary(property, current)
                : JutsuValueParser.display(property, current);
        font.drawString(trim(font, valueState + ": " + currentText, rightWidth - 20),
                rightX + 10, bodyTop + 61, Theme.TEXT_LIGHT);
        String help = propertyHelp(property);
        font.drawString(trim(font, help, rightWidth - 20), rightX + 10,
                jutsuValueY() - 12, Theme.TEXT_MUTED);
        if (isEffectEditorProperty(property)) {
            font.drawSplitString(text("gui.rebornaddon.effects.summary",
                            "Choose individual effects, their behavior, duration, and strength."),
                    rightX + 10, jutsuValueY() + 25, rightWidth - 20, Theme.TEXT_MUTED);
        } else if (usesDurationValue()) {
            drawDurationFields(font);
        } else if (!isBooleanJutsuProperty(property)) valueField.drawTextBox();
    }

    private void drawStore(FontRenderer font, int accent) {
        if (selectedStore == null) {
            drawEmpty(font, text("gui.rebornaddon.admin.no_items", "No store items match."));
            return;
        }
        drawTitle(font, selectedStore.name, selectedStore.key, accent);
        String current = propertyIndex == 0 ? selectedStore.price + " Ryo"
                : selectedStore.purchasable ? text("gui.rebornaddon.admin.purchasable", "Purchasable")
                : text("gui.rebornaddon.admin.blocked", "Blocked");
        font.drawString(trim(font, text("gui.rebornaddon.jutsu.current", "Current") + ": " + current,
                rightWidth - 20), rightX + 10, bodyTop + 61, Theme.TEXT_LIGHT);
        if (propertyIndex == 0) valueField.drawTextBox();
    }

    private void drawScope(FontRenderer font, int accent) {
        if (selectedScope == null) {
            drawEmpty(font, text("gui.rebornaddon.admin.no_sections", "No store sections match."));
            return;
        }
        drawTitle(font, scopeLabel(selectedScope), selectedScope.key, accent);
        font.drawString(text("gui.rebornaddon.jutsu.current", "Current") + ": "
                        + (selectedScope.enabled ? text("gui.rebornaddon.admin.enabled", "Enabled")
                        : text("gui.rebornaddon.admin.disabled", "Disabled")),
                rightX + 10, bodyTop + 61,
                selectedScope.enabled ? Theme.SUCCESS_BRIGHT : Theme.DANGER_BRIGHT);
    }

    private void drawMode(FontRenderer font, int accent) {
        if (selectedMode == null) {
            drawEmpty(font, text("gui.rebornaddon.admin.no_modes", "No configurable modes match."));
            return;
        }
        drawTitle(font, selectedMode.displayName, title(selectedMode.source), accent);
        String property = modeProperty();
        boolean movement = "movement-speed-multiplier".equals(property);
        String valueState = movement
                ? ModeDefinition.hasFixedNativeMovement(selectedMode.id)
                ? selectedMode.hasOverride(property)
                ? text("gui.rebornaddon.admin.configured_speed", "Configured movement speed")
                : text("gui.rebornaddon.admin.native_speed", "Source movement speed")
                : text("gui.rebornaddon.admin.additional_speed", "Additional movement multiplier")
                : selectedMode.hasOverride(property)
                ? text("gui.rebornaddon.admin.saved_adjustment", "Saved adjustment")
                : text("gui.rebornaddon.admin.default_adjustment", "Default adjustment");
        String displayValue = modeEditableValue(selectedMode, property,
                selectedMode.effective(property));
        font.drawString(trim(font, valueState + ": " + modeDisplay(property,
                        displayValue), rightWidth - 20),
                rightX + 10, bodyTop + 61, Theme.TEXT_LIGHT);
        font.drawString(trim(font, nativeModeDetail(selectedMode, property), rightWidth - 20),
                rightX + 10, bodyTop + 74, Theme.TEXT_MUTED);
        font.drawString(trim(font, modePropertyHelp(property, selectedMode), rightWidth - 20),
                rightX + 10, modeValueY() - 12, Theme.TEXT_MUTED);
        if (!("enabled".equals(property) || "incompatible-modes".equals(property))) valueField.drawTextBox();
    }

    private void drawChakraControl(FontRenderer font, int accent) {
        font.drawString(text("gui.rebornaddon.chakra_control.title", "Chakra Control"),
                left + 8, bodyTop + 8, Theme.TEXT_LIGHT);
        GuiChrome.rule(left + 8, left + leftWidth - 8, bodyTop + 22, accent);
        chakraSettingIndex = Math.max(0, Math.min(chakraSettingIndex,
                CHAKRA_SETTING_IDS.length - 1));
        font.drawString(trim(font, chakraSettingLabel(chakraSettingIndex), rightWidth - 20),
                rightX + 10, bodyTop + 8, Theme.TEXT_LIGHT);
        GuiChrome.rule(rightX + 10, rightX + rightWidth - 10, bodyTop + 22, accent);
        if (!chakraData.getBoolean("Available")) {
            font.drawSplitString(chakraData.getString("Message"), rightX + 10,
                    bodyTop + 32, rightWidth - 20, Theme.DANGER_BRIGHT);
            return;
        }
        String current = chakraSettingIndex < 3
                ? chakraBoolean(chakraSetting())
                ? text("gui.rebornaddon.admin.enabled", "Enabled")
                : text("gui.rebornaddon.admin.disabled", "Disabled")
                : formatMultiplier(chakraData.getDouble("WallClimbSpeedMultiplier"));
        font.drawString(text("gui.rebornaddon.jutsu.current", "Current") + ": " + current,
                rightX + 10, bodyTop + 32, Theme.TEXT_LIGHT);
        font.drawSplitString(chakraSettingHelp(chakraSettingIndex), rightX + 10,
                bodyTop + 49, rightWidth - 20, Theme.TEXT_MUTED);
        if (chakraSettingIndex == 3) {
            font.drawString(text("gui.rebornaddon.chakra_control.multiplier", "Speed multiplier"),
                    rightX + 10, bodyTop + 91, Theme.TEXT_MUTED);
            valueField.drawTextBox();
        }
    }

    private void drawLimits(FontRenderer font, int accent) {
        font.drawString(text("gui.rebornaddon.admin.player", "Player"),
                left + 8, bodyTop + 7, Theme.TEXT_MUTED);
        playerField.drawTextBox();
        String quotaPlayer = storeData.getString("QuotaPlayer");
        String message = storeData.getString("QuotaMessage");
        font.drawString(trim(font, quotaPlayer.isEmpty() ? message : quotaPlayer, leftWidth - 16),
                left + 8, bodyTop + 73, Theme.TEXT_LIGHT);
        int y = bodyTop + 89;
        for (String kind : QUOTA_KINDS) {
            if (y > top + height - 10) break;
            font.drawString(trim(font, quotaLabel(kind) + ": " + quotaCount(kind) + "/" + quotaLimit(kind),
                    leftWidth - 16), left + 8, y,
                    kind.equals(quotaKind()) ? Theme.GOLD : Theme.TEXT_MUTED);
            y += 12;
        }
        font.drawString(text("gui.rebornaddon.admin.purchase_limit", "Purchase limit"),
                rightX + 10, bodyTop + 28, Theme.TEXT_LIGHT);
        font.drawString(text("gui.rebornaddon.admin.count_or_adjustment", "Count or adjustment"),
                rightX + 10, bodyTop + 82, Theme.TEXT_MUTED);
        valueField.drawTextBox();
    }

    private void drawQuests(FontRenderer font, int accent) {
        font.drawString(text("gui.rebornaddon.admin.player", "Player"),
                left + 8, bodyTop + 7, Theme.TEXT_MUTED);
        playerField.drawTextBox();
        font.drawSplitString(text("gui.rebornaddon.admin.quest_help",
                        "Reset, start, complete, pause, or resume a player's native quest progress."),
                left + 8, bodyTop + 70, leftWidth - 16, Theme.TEXT_LIGHT);
        font.drawString(text("gui.rebornaddon.admin.quest_id", "Quest ID"),
                rightX + 10, bodyTop + 82, Theme.TEXT_MUTED);
        valueField.drawTextBox();
    }

    private void drawExamRanks(FontRenderer font, int accent) {
        JsonObject root = ClientExamData.rankCatalogSnapshot();
        font.drawString(text("gui.rebornaddon.admin.ranks.title", "Ninja Ranks"),
                left + 8, bodyTop + 8, Theme.TEXT_LIGHT);
        GuiChrome.rule(left + 8, left + leftWidth - 8, bodyTop + 21, accent);
        font.drawSplitString(text("gui.rebornaddon.admin.ranks.summary",
                        "Assign server ninja ranks and their NarutoMod XP caps. LuckPerms is updated by the hosted plugin."),
                left + 8, bodyTop + 36, leftWidth - 16, Theme.TEXT_MUTED);
        if (!root.has("available") || !root.get("available").getAsBoolean()) {
            drawEmpty(font, root.has("message") ? root.get("message").getAsString()
                    : text("gui.rebornaddon.admin.ranks.loading", "Loading rank controls..."));
            return;
        }
        font.drawString(text("gui.rebornaddon.admin.ranks.player", "Online player"),
                rightX + 10, bodyTop + 45, Theme.TEXT_MUTED);
        examRankPlayerField.drawTextBox();
        JsonArray ranks = examRanks();
        if (ranks.size() == 0) {
            font.drawString(text("gui.rebornaddon.admin.ranks.empty", "No ranks are configured."),
                    rightX + 10, bodyTop + 124, Theme.GOLD);
            return;
        }
        examRankIndex = Math.max(0, Math.min(examRankIndex, ranks.size() - 1));
        JsonObject rank = ranks.get(examRankIndex).getAsJsonObject();
        font.drawString(text("gui.rebornaddon.admin.ranks.selected", "Selected") + ": "
                + jsonString(rank, "name"), rightX + 10, bodyTop + 119, Theme.GOLD);
        font.drawString(text("gui.rebornaddon.admin.ranks.cap", "Ninja XP cap") + ": "
                        + (rank.has("xpCap") ? rank.get("xpCap").getAsLong() : 0L),
                rightX + 10, bodyTop + 136, Theme.TEXT_LIGHT);
        font.drawString(text("gui.rebornaddon.admin.ranks.group", "LuckPerms group") + ": "
                        + jsonString(rank, "lpGroup"),
                rightX + 10, bodyTop + 151, Theme.TEXT_MUTED);
        font.drawSplitString(text("gui.rebornaddon.admin.ranks.exam_note",
                        "Passing an approved exam can assign the same rank automatically."),
                rightX + 10, bodyTop + 178, rightWidth - 20, Theme.TEXT_MUTED);
    }

    private void drawCooldowns(FontRenderer font, int accent) {
        font.drawString(text("gui.rebornaddon.admin.cooldowns.title", "Current Cooldowns"),
                left + 8, bodyTop + 8, Theme.TEXT_LIGHT);
        GuiChrome.rule(left + 8, left + leftWidth - 8, bodyTop + 21, accent);
        font.drawSplitString(text("gui.rebornaddon.admin.cooldowns.summary",
                        "Clear active jutsu cooldowns without changing configured durations."),
                left + 8, bodyTop + 36, leftWidth - 16, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.admin.cooldowns.player", "Online player"),
                rightX + 10, bodyTop + 58, Theme.TEXT_MUTED);
        if (cooldownPlayerField != null) cooldownPlayerField.drawTextBox();
        font.drawSplitString(text("gui.rebornaddon.admin.cooldowns.help",
                        "Clear All also applies when offline players next join. New uses keep the saved server durations."),
                 rightX + 10, bodyTop + 162, rightWidth - 20, Theme.TEXT_MUTED);
    }

    private void drawPerformance(FontRenderer font, int accent) {
        NBTTagCompound data = ClientPerformanceData.snapshot();
        font.drawString(text("gui.rebornaddon.admin.performance.title", "Server Performance"),
                left + 8, bodyTop + 8, Theme.TEXT_LIGHT);
        GuiChrome.rule(left + 8, left + leftWidth - 8, bodyTop + 21, accent);
        if (!data.hasKey("Tps")) {
            font.drawSplitString(text("gui.rebornaddon.admin.performance.loading",
                            "Waiting for server performance metrics..."),
                    left + 8, bodyTop + 37, leftWidth - 16, Theme.TEXT_MUTED);
            return;
        }
        int y = bodyTop + 34;
        font.drawString(String.format(Locale.ROOT, "TPS: %.2f", data.getDouble("Tps")),
                left + 8, y, data.getDouble("Tps") >= 18.0D ? Theme.SUCCESS_BRIGHT : Theme.GOLD);
        font.drawString(String.format(Locale.ROOT, "Tick: %.2f ms average", data.getDouble("TickAverage")),
                left + 8, y + 14, Theme.TEXT_LIGHT);
        font.drawString(String.format(Locale.ROOT, "Tick p95: %.2f ms", data.getDouble("TickP95")),
                left + 8, y + 28, Theme.TEXT_MUTED);
        font.drawString("Heap: " + PerformanceMonitor.formatBytes(data.getLong("HeapUsed")) + " / "
                        + PerformanceMonitor.formatBytes(data.getLong("HeapMax")),
                left + 8, y + 47, Theme.TEXT_LIGHT);
        font.drawString("Chunks: " + data.getInteger("Chunks"), left + 8, y + 65, Theme.TEXT_MUTED);
        font.drawString("Entities: " + data.getInteger("Entities"), left + 8, y + 79, Theme.TEXT_MUTED);
        font.drawString("Tile entities: " + data.getInteger("TileEntities"), left + 8, y + 93, Theme.TEXT_MUTED);
        font.drawString("GC: " + data.getLong("GcCount") + " runs / " + data.getLong("GcMillis") + " ms",
                left + 8, y + 111, Theme.TEXT_MUTED);
        long trend = data.getLong("HeapTrend");
        font.drawString("Heap trend: " + (trend >= 0L ? "+" : "") + PerformanceMonitor.formatBytes(Math.abs(trend))
                        + "/min", left + 8, y + 129,
                data.getBoolean("LeakWarning") ? Theme.RANKED_RED : Theme.TEXT_MUTED);
        font.drawString("Latency: " + data.getInteger("AveragePing") + " ms avg / "
                        + data.getInteger("MaximumPing") + " ms max", left + 8, y + 143, Theme.TEXT_MUTED);
        font.drawString("Reborn net: " + PerformanceMonitor.formatBytes(data.getLong("OutboundBytesPerSecond"))
                        + "/s out / " + PerformanceMonitor.formatBytes(data.getLong("InboundBytesPerSecond"))
                        + "/s in", left + 8, y + 157, Theme.TEXT_MUTED);
        font.drawString("Own runtime: " + data.getInteger("RebornActiveSpawns") + " spawns, "
                        + data.getInteger("RebornBosses") + " bosses, " + data.getInteger("RebornDungeons")
                        + " dungeons", left + 8, y + 171, Theme.TEXT_MUTED);
        font.drawString("Managed states: " + data.getInteger("RebornManagedPlayerStates")
                        + " | Exam jobs: " + data.getInteger("RebornExamPending"),
                left + 8, y + 185, Theme.TEXT_MUTED);

        font.drawString(text("gui.rebornaddon.admin.performance.window", "Sampling window"),
                rightX + 10, bodyTop + 8, Theme.TEXT_LIGHT);
        int performanceFieldWidth = Math.max(34, (rightWidth - 28) / 3);
        font.drawString(text("gui.rebornaddon.time.hours", "Hours"), rightX + 10,
                bodyTop + 29, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.minutes", "Minutes"),
                rightX + 14 + performanceFieldWidth, bodyTop + 29, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.seconds", "Seconds"),
                rightX + 18 + performanceFieldWidth * 2, bodyTop + 29, Theme.TEXT_MUTED);
        if (performanceHoursField != null) performanceHoursField.drawTextBox();
        if (performanceMinutesField != null) performanceMinutesField.drawTextBox();
        if (performanceSecondsField != null) performanceSecondsField.drawTextBox();
        if (performanceView == 0) {
            int listY = bodyTop + 143;
            int gap = 16;
            int columnWidth = Math.max(80, (rightWidth - 20 - gap) / 2);
            font.drawString(text("gui.rebornaddon.admin.performance.global", "Top overall sampled work"),
                    rightX + 10, listY, Theme.GOLD);
            int rebornX = rightX + 10 + columnWidth + gap;
            font.drawString(text("gui.rebornaddon.admin.performance.reborn", "Top RebornAddon sampled work"),
                    rebornX, listY, Theme.TEAL);
            drawProfile(font, data.getTagList("Global", 10), rightX + 10, listY + 15,
                    columnWidth, 10);
            drawProfile(font, data.getTagList("RebornAddon", 10), rebornX, listY + 15,
                    columnWidth, 10);
        } else {
            NBTTagList rows = performanceRows();
            int index = performanceSelection;
            if (index >= 0 && index < rows.tagCount()) {
                NBTTagCompound row = rows.getCompoundTagAt(index);
                String detail = performanceView == 1
                        ? row.getString("Kind") + "  " + row.getString("Name") + "  dim "
                        + row.getInteger("Dimension")
                        : "Dimension " + row.getInteger("Dimension") + "  "
                        + row.getInteger("Sources") + " captured sources";
                font.drawString(trim(font, detail, rightWidth - 20), rightX + 10,
                        top + height - 38, Theme.TEXT_MUTED);
            } else if (rows.tagCount() == 0) {
                font.drawString(text("gui.rebornaddon.admin.performance.no_locations",
                                "Run a profile to capture timed locations."),
                        rightX + 10, bodyTop + 143, Theme.TEXT_MUTED);
            }
        }
        if (data.getBoolean("Profiling")) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - data.getLong("ProfileStartedAt"));
            long total = Math.max(1000L, data.getLong("ProfileDuration"));
            font.drawString(text("gui.rebornaddon.admin.performance.running", "Profile in progress...")
                            + " " + Math.min(100L, elapsed * 100L / total) + "%",
                    left + 8, y + 204, Theme.SUCCESS_BRIGHT);
        } else if (data.getLong("ProfileMeasured") > 0L) {
            font.drawString("Last sample: " + PerformanceMonitor.formatDuration(data.getLong("ProfileMeasured")),
                    left + 8, y + 204, Theme.TEXT_MUTED);
        }
    }

    private static int drawProfile(FontRenderer font, NBTTagList rows, int x, int y,
                                   int availableWidth, int maximum) {
        if (rows == null || rows.tagCount() == 0) {
            font.drawString("No recent sample. Run a profile.", x, y, Theme.TEXT_MUTED);
            return y + 13;
        }
        int count = Math.min(maximum, rows.tagCount());
        for (int i = 0; i < count; i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            String suffix = "  " + row.getLong("Micros") + " us";
            String name = trim(font, (i + 1) + ". " + row.getString("Name"),
                    Math.max(20, availableWidth - font.getStringWidth(suffix)));
            font.drawString(name, x, y, Theme.TEXT_LIGHT);
            font.drawString(suffix, x + availableWidth - font.getStringWidth(suffix), y, Theme.TEXT_MUTED);
            y += 12;
        }
        return y;
    }

    private void drawRanked(FontRenderer font, int accent) {
        font.drawString(text("gui.rebornaddon.admin.ranked.title", "Ranked Seasons"),
                left + 8, bodyTop + 8, Theme.TEXT_LIGHT);
        GuiChrome.rule(left + 8, left + leftWidth - 8, bodyTop + 21, accent);
        if (!rankedData.getBoolean("Available")) {
            font.drawSplitString(rankedData.getString("Message"), left + 8, bodyTop + 58,
                    leftWidth - 16, Theme.GOLD);
            drawEmpty(font, text("gui.rebornaddon.admin.ranked.plugin_required",
                    "Install and enable the RebornDiscordRoleSync plugin to manage ranked seasons."));
            return;
        }
        if (rankedView == 0) drawRankedSeason(font, accent);
        else if (rankedView == 1) drawRankedRewards(font, accent);
        else drawRankedExclusions(font, accent);
    }

    private void drawRankedSeason(FontRenderer font, int accent) {
        String state = title(rankedData.getString("State"));
        font.drawString(trim(font, rankedData.getString("DisplayName"), leftWidth - 16),
                left + 8, bodyTop + 59, Theme.TEXT_LIGHT);
        font.drawString(text("gui.rebornaddon.admin.ranked.state", "State") + ": " + state,
                left + 8, bodyTop + 75, state.equals("Running") ? Theme.SUCCESS_BRIGHT : Theme.GOLD);
        font.drawString(text("gui.rebornaddon.admin.ranked.remaining", "Remaining") + ": "
                        + durationLabel(rankedData.getLong("RemainingMillis")),
                left + 8, bodyTop + 90, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.admin.ranked.number", "Season number") + ": "
                        + rankedData.getInteger("SeasonNumber"),
                left + 8, bodyTop + 105, Theme.TEXT_MUTED);
        if (rankedData.getBoolean("ResetPending")) {
            font.drawSplitString(text("gui.rebornaddon.admin.ranked.reset_pending",
                            "The previous season's ELO reset is being reconciled."),
                    left + 8, bodyTop + 124, leftWidth - 16, Theme.GOLD);
        }
        font.drawString(text("gui.rebornaddon.admin.ranked.name", "Optional season name"),
                rightX + 10, bodyTop + 86, Theme.TEXT_MUTED);
        rankedNameField.drawTextBox();
        font.drawString(text("gui.rebornaddon.admin.ranked.duration", "Season duration"),
                rightX + 10, bodyTop + 123, Theme.TEXT_LIGHT);
        drawRankedDurationFields(font);
    }

    private void drawRankedRewards(FontRenderer font, int accent) {
        NBTTagCompound placement = placementData(rankedPlacement);
        font.drawString(text("gui.rebornaddon.admin.ranked.placement_summary", "Placement Setup"),
                left + 8, bodyTop + 91, Theme.TEXT_LIGHT);
        font.drawString(text("gui.rebornaddon.admin.ranked.items", "Items") + ": "
                        + placement.getTagList("Items", 8).tagCount(),
                left + 8, bodyTop + 108, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.admin.ranked.commands", "Commands") + ": "
                        + placement.getTagList("Commands", 8).tagCount(),
                left + 8, bodyTop + 123, Theme.TEXT_MUTED);
        String firstItem = firstString(placement.getTagList("Items", 8));
        if (!firstItem.isEmpty()) {
            font.drawString(trim(font, firstItem, leftWidth - 16), left + 8, bodyTop + 142, Theme.GOLD);
        }
        font.drawString(text("gui.rebornaddon.admin.ranked.command", "Reward command"),
                rightX + 10, bodyTop + 80, Theme.TEXT_MUTED);
        rankedCommandField.drawTextBox();
        if (rankedPlacement <= 3) {
            font.drawString(text("gui.rebornaddon.admin.ranked.lp_group", "LuckPerms title group"),
                    rightX + 10, bodyTop + 126, Theme.TEXT_MUTED);
            rankedGroupField.drawTextBox();
        } else {
            font.drawString(text("gui.rebornaddon.admin.ranked.podium_only",
                            "LuckPerms title groups are available for places 1 through 3."),
                    rightX + 10, bodyTop + 137, Theme.TEXT_MUTED);
        }
    }

    private void drawRankedExclusions(FontRenderer font, int accent) {
        font.drawString(text("gui.rebornaddon.admin.player", "Player"),
                left + 8, bodyTop + 48, Theme.TEXT_MUTED);
        rankedPlayerField.drawTextBox();
        NBTTagList blocked = rankedData.getTagList("BlockedPlayers", 10);
        font.drawString(text("gui.rebornaddon.admin.ranked.excluded_count", "Excluded players")
                        + ": " + blocked.tagCount(),
                rightX + 10, bodyTop + 32, Theme.TEXT_LIGHT);
        if (rankedBlockedSelection >= 0 && rankedBlockedSelection < blocked.tagCount()) {
            NBTTagCompound player = blocked.getCompoundTagAt(rankedBlockedSelection);
            font.drawString(trim(font, player.getString("Name"), rightWidth - 20),
                    rightX + 10, bodyTop + 54, Theme.GOLD);
            font.drawString(trim(font, player.getString("Uuid"), rightWidth - 20),
                    rightX + 10, bodyTop + 69, Theme.TEXT_MUTED);
        } else {
            font.drawSplitString(text("gui.rebornaddon.admin.ranked.exclusion_help",
                            "Excluded players do not appear publicly and cannot receive placement rewards."),
                    rightX + 10, bodyTop + 54, rightWidth - 20, Theme.TEXT_MUTED);
        }
    }

    private void drawRankedDurationFields(FontRenderer font) {
        font.drawString(text("gui.rebornaddon.time.hours", "Hours"), hoursField.x,
                bodyTop + 137, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.minutes", "Minutes"), minutesField.x,
                bodyTop + 137, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.seconds", "Seconds"), secondsField.x,
                bodyTop + 137, Theme.TEXT_MUTED);
        hoursField.drawTextBox();
        minutesField.drawTextBox();
        secondsField.drawTextBox();
    }

    private void drawTitle(FontRenderer font, String title, String subtitle, int accent) {
        font.drawString(trim(font, title, rightWidth - 20), rightX + 10, bodyTop + 28, Theme.TEXT_LIGHT);
        font.drawString(trim(font, subtitle, rightWidth - 20), rightX + 10, bodyTop + 43, Theme.TEXT_MUTED);
        GuiChrome.rule(rightX + 10, rightX + rightWidth - 10, bodyTop + 56, accent);
    }

    private void drawEmpty(FontRenderer font, String message) {
        font.drawSplitString(message, rightX + 10, bodyTop + 31, rightWidth - 20, Theme.TEXT_MUTED);
    }

    private void syncSnapshot() {
        int revision = ClientAdminData.revision();
        if (observedRevision == revision) return;
        String jutsuId = selectedJutsu == null ? "" : selectedJutsu.id;
        String storeKey = selectedStore == null ? "" : selectedStore.key;
        String scopeKey = selectedScope == null ? "" : selectedScope.key;
        String modeId = selectedMode == null ? "" : selectedMode.id;
        snapshot = ClientAdminData.snapshot();
        String openTab = snapshot.getString("OpenTab");
        for (int i = 0; i < TAB_IDS.length; i++) {
            if (TAB_IDS[i].equals(openTab)) {
                selected = i;
                break;
            }
        }
        storeData = snapshot.getCompoundTag("Store");
        rankedData = snapshot.getCompoundTag("Ranked");
        chakraData = snapshot.getCompoundTag("ChakraControl");
        parseJutsus();
        parseStore();
        parseModes();
        selectedJutsu = findJutsu(jutsuId);
        selectedStore = findStore(storeKey);
        selectedScope = findScope(scopeKey);
        selectedMode = findMode(modeId);
        if (selectedJutsu == null && !jutsus.isEmpty()) selectedJutsu = jutsus.get(0);
        if (selectedStore == null && !storeEntries.isEmpty()) selectedStore = storeEntries.get(0);
        if (selectedScope == null && !scopes.isEmpty()) selectedScope = scopes.get(0);
        if (selectedMode == null && !modes.isEmpty()) selectedMode = modes.get(0);
        if (rankedNameField != null && !rankedNameField.isFocused()) {
            rankedNameField.setText(rankedData.getString("SeasonName"));
        }
        if (rankedGroupField != null && !rankedGroupField.isFocused()) {
            rankedGroupField.setText(placementData(rankedPlacement).getString("Group"));
        }
        setRankedDurationFields(rankedData.getLong("DurationMillis"));
        observedRevision = revision;
        resetAllArmed = false;
        valueContext = "";
    }

    private void requestSnapshot(boolean fromBuild) {
        long now = System.currentTimeMillis();
        boolean returnedToTab = fromBuild && now - lastBuildAt > 1000L;
        if (fromBuild) lastBuildAt = now;
        if ((!ClientAdminData.loaded() || returnedToTab) && ClientAdminData.shouldRequest()) {
            RebornAddonNetwork.sendJutsuAdminAction(JutsuConfigurationService.ADMIN_OPEN,
                    TAB_IDS[selected], "", "");
        }
    }

    private static boolean usesAdminSnapshot(int tab) {
        return tab != 7 && tab != 9 && tab != 10 && tab != 12 && tab != 13;
    }

    private static void requestPerformance(boolean force) {
        if (force || ClientPerformanceData.shouldRequest()) {
            RebornAddonNetwork.requestPerformanceSnapshot(false);
        }
    }

    private static void requestRankCatalog(boolean force) {
        if (ClientExamData.requestRankCatalog(force)) RebornAddonNetwork.requestExamRankCatalog();
    }

    private void parseJutsus() {
        jutsus.clear();
        Map<String, JutsuEntry> unique = new LinkedHashMap<String, JutsuEntry>();
        NBTTagList records = snapshot.getTagList("Records", 8);
        for (int i = 0; i < records.tagCount(); i++) {
            JutsuEntry entry = JutsuEntry.parse(records.getStringTagAt(i));
            if (entry == null) continue;
            String key = canonicalJutsuName(entry.displayName);
            JutsuEntry existing = unique.get(key);
            if (existing == null || !"narutomod".equals(existing.source)
                    && "narutomod".equals(entry.source)) unique.put(key, entry);
        }
        jutsus.addAll(unique.values());
        Collections.sort(jutsus, Comparator.comparing(JutsuEntry::sortKey));
    }

    private void parseStore() {
        storeEntries.clear();
        NBTTagList records = storeData.getTagList("Records", 10);
        for (int i = 0; i < records.tagCount(); i++) {
            StoreEntry entry = StoreEntry.parse(records.getCompoundTagAt(i));
            if (entry != null) storeEntries.add(entry);
        }
        Collections.sort(storeEntries, Comparator.comparing(StoreEntry::sortKey));
        scopes.clear();
        NBTTagList recordsScopes = storeData.getTagList("Scopes", 10);
        for (int i = 0; i < recordsScopes.tagCount(); i++) {
            ScopeEntry entry = ScopeEntry.parse(recordsScopes.getCompoundTagAt(i));
            if (entry != null) scopes.add(entry);
        }
        Collections.sort(scopes, Comparator.comparing(ScopeEntry::sortKey));
    }

    private void parseModes() {
        modes.clear();
        NBTTagList records = snapshot.getTagList("ModeRecords", 8);
        for (int i = 0; i < records.tagCount(); i++) {
            ModeEntry entry = ModeEntry.parse(records.getStringTagAt(i));
            if (entry != null && ModeDefinition.isKnownMode(entry.id)
                    && entry.properties.contains("incompatible-modes")) modes.add(entry);
        }
        Collections.sort(modes, Comparator.comparing(ModeEntry::sortKey));
    }

    private List<JutsuEntry> filteredJutsus() {
        String query = query();
        List<String> categories = jutsuCategories();
        String category = categories.get(Math.max(0, Math.min(jutsuCategoryIndex, categories.size() - 1)));
        List<JutsuEntry> result = new ArrayList<JutsuEntry>();
        for (JutsuEntry entry : jutsus) {
            if (entry.restricted != restrictedJutsus) continue;
            if (!"All".equals(category) && !category.equals(entry.category)) continue;
            if (query.isEmpty() || entry.id.toLowerCase(Locale.ROOT).contains(query)
                    || entry.displayName.toLowerCase(Locale.ROOT).contains(query)
                    || entry.category.toLowerCase(Locale.ROOT).contains(query)) result.add(entry);
        }
        return result;
    }

    private List<String> jutsuCategories() {
        List<String> values = new ArrayList<String>();
        values.add("All");
        for (JutsuEntry entry : jutsus) {
            if (entry.restricted == restrictedJutsus && !values.contains(entry.category)) {
                values.add(entry.category);
            }
        }
        if (values.size() > 1) Collections.sort(values.subList(1, values.size()));
        return values;
    }

    private List<StoreEntry> filteredStore() {
        String query = query();
        List<StoreEntry> result = new ArrayList<StoreEntry>();
        for (StoreEntry entry : storeEntries) {
            if (query.isEmpty() || entry.key.toLowerCase(Locale.ROOT).contains(query)
                    || entry.name.toLowerCase(Locale.ROOT).contains(query)) result.add(entry);
        }
        return result;
    }

    private List<ScopeEntry> filteredScopes() {
        String query = query();
        List<ScopeEntry> result = new ArrayList<ScopeEntry>();
        for (ScopeEntry entry : scopes) {
            if (query.isEmpty() || entry.key.toLowerCase(Locale.ROOT).contains(query)
                    || scopeLabel(entry).toLowerCase(Locale.ROOT).contains(query)) result.add(entry);
        }
        return result;
    }

    private List<ModeEntry> filteredModes() {
        String query = query();
        List<ModeEntry> result = new ArrayList<ModeEntry>();
        for (ModeEntry entry : modes) {
            if (query.isEmpty() || entry.id.toLowerCase(Locale.ROOT).contains(query)
                    || entry.displayName.toLowerCase(Locale.ROOT).contains(query)
                    || entry.source.toLowerCase(Locale.ROOT).contains(query)) result.add(entry);
        }
        return result;
    }

    private String query() {
        return searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private void syncValueField() {
        if (valueField == null) return;
        String context = "";
        String value = "";
        if (selected == 0 && selectedJutsu != null && !isBooleanJutsuProperty(jutsuProperty())
                && !isEffectEditorProperty(jutsuProperty())) {
            context = "j:" + selectedJutsu.id + ":" + jutsuProperty();
            value = JutsuValueParser.editable(jutsuProperty(), selectedJutsu.effective(jutsuProperty()));
        } else if (selected == 1 && selectedStore != null && propertyIndex == 0) {
            context = "s:" + selectedStore.key;
            value = Integer.toString(selectedStore.price);
        } else if (selected == 5 && selectedMode != null && !"enabled".equals(modeProperty())) {
            context = "m:" + selectedMode.id + ":" + modeProperty();
            value = modeEditableValue(selectedMode, modeProperty(),
                    selectedMode.effective(modeProperty()));
        } else if (selected == 11 && chakraSettingIndex == 3) {
            context = "c:" + chakraData.getDouble("WallClimbSpeedMultiplier");
            value = BigDecimal.valueOf(chakraData.getDouble("WallClimbSpeedMultiplier"))
                    .stripTrailingZeros().toPlainString();
        }
        if (!context.isEmpty() && !context.equals(valueContext)) {
            valueField.setText(value);
            if (selected == 0 && usesDurationValue()) setDurationFields(selectedJutsu.effective(jutsuProperty()));
            valueContext = context;
        }
    }

    private boolean usesTextValue() {
        return selected == 0 && selectedJutsu != null && !isBooleanJutsuProperty(jutsuProperty())
                && !usesDurationValue() && !isEffectEditorProperty(jutsuProperty())
                || selected == 1 && propertyIndex == 0
                || selected == 5 && selectedMode != null && !"enabled".equals(modeProperty())
                && !"incompatible-modes".equals(modeProperty())
                || selected == 11 && chakraSettingIndex == 3;
    }

    private boolean usesDurationValue() {
        if (selected != 0 || selectedJutsu == null) return false;
        String property = jutsuProperty();
        return "cooldown".equals(property) || "charge-time".equals(property)
                || "max-duration".equals(property);
    }

    private String jutsuProperty() {
        if (selectedJutsu == null || selectedJutsu.properties.isEmpty()) return "";
        propertyIndex = Math.max(0, Math.min(propertyIndex, selectedJutsu.properties.size() - 1));
        return selectedJutsu.properties.get(propertyIndex);
    }

    private static boolean isBooleanJutsuProperty(String property) {
        return "enabled".equals(property) || "negative-effects-enabled".equals(property);
    }

    private String modeProperty() {
        if (selectedMode == null || selectedMode.properties.isEmpty()) return "";
        propertyIndex = Math.max(0, Math.min(propertyIndex, selectedMode.properties.size() - 1));
        return selectedMode.properties.get(propertyIndex);
    }

    private String modeInputValue() {
        String value = valueField == null ? "" : valueField.getText().trim();
        if (!"movement-speed-multiplier".equals(modeProperty())) return value;
        try {
            double target = Double.parseDouble(value);
            if (!Double.isFinite(target) || target < 0.0D) return value;
            return BigDecimal.valueOf(ModeDefinition.movementAdjustment(
                    selectedMode.id, target)).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String modeEditableValue(ModeEntry mode, String property, String value) {
        if (mode == null || !"movement-speed-multiplier".equals(property)
                || !ModeDefinition.hasFixedNativeMovement(mode.id)) return value;
        try {
            double adjustment = Double.parseDouble(value);
            if (!Double.isFinite(adjustment) || adjustment < 0.0D) return value;
            return BigDecimal.valueOf(ModeDefinition.movementTarget(mode.id, adjustment))
                    .stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private int modePropertyColumns() {
        return rightWidth >= 700 ? 3 : 2;
    }

    private int modePropertyRows() {
        int count = selectedMode == null ? 1 : Math.max(1, selectedMode.properties.size());
        return (count + modePropertyColumns() - 1) / modePropertyColumns();
    }

    private int modeValueY() {
        return modePropertyTop() + modePropertyRows() * 21 + 15;
    }

    private int modePropertyTop() { return bodyTop + 92; }

    private int modeActionY() {
        return modeValueY() + 26;
    }

    private int jutsuPropertyColumns() {
        return rightWidth >= 700 ? 3 : 2;
    }

    private int jutsuPropertyRows() {
        int count = selectedJutsu == null ? 1 : Math.max(1, selectedJutsu.properties.size());
        return (count + jutsuPropertyColumns() - 1) / jutsuPropertyColumns();
    }

    private int jutsuValueY() {
        return bodyTop + 82 + jutsuPropertyRows() * 21 + 15;
    }

    private int jutsuActionY() {
        return jutsuValueY() + (usesDurationValue() ? 36 : 26);
    }

    private void drawDurationFields(FontRenderer font) {
        font.drawString(text("gui.rebornaddon.time.hours", "Hours"), hoursField.x,
                jutsuValueY(), Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.minutes", "Minutes"), minutesField.x,
                jutsuValueY(), Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.seconds", "Seconds"), secondsField.x,
                jutsuValueY(), Theme.TEXT_MUTED);
        hoursField.drawTextBox();
        minutesField.drawTextBox();
        secondsField.drawTextBox();
    }

    private void setDurationFields(String ticksValue) {
        long ticks;
        try { ticks = Math.max(0L, Long.parseLong(ticksValue == null ? "0" : ticksValue)); }
        catch (NumberFormatException ignored) { ticks = 0L; }
        long totalSeconds = Math.round(ticks / 20.0D);
        hoursField.setText(Long.toString(totalSeconds / 3600L));
        minutesField.setText(Long.toString(totalSeconds % 3600L / 60L));
        secondsField.setText(Long.toString(totalSeconds % 60L));
    }

    private String durationInput() {
        return durationPart(hoursField) + "h" + durationPart(minutesField) + "m"
                + durationPart(secondsField) + "s";
    }

    private static long durationPart(GuiTextField field) {
        try { return Math.max(0L, Long.parseLong(field == null ? "0" : field.getText().trim())); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private long performanceDurationMillis() {
        long hours = durationPart(performanceHoursField);
        long minutes = durationPart(performanceMinutesField);
        long seconds = durationPart(performanceSecondsField);
        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        if (totalSeconds < 1L) {
            if (performanceSecondsField != null) performanceSecondsField.setText("5");
            return PerformanceMonitor.DEFAULT_PROFILE_MILLIS;
        }
        return Math.min(PerformanceMonitor.MAX_PROFILE_MILLIS, totalSeconds * 1000L);
    }

    private NBTTagList performanceRows() {
        NBTTagCompound data = ClientPerformanceData.snapshot();
        return data.getTagList(performanceView == 2 ? "HotChunks" : "Hotspots", 10);
    }

    private int performanceRowsPerPage() {
        return Math.max(2, (top + height - 166 - bodyTop) / 21);
    }

    private static String shortRegistryName(String value) {
        if (value == null || value.isEmpty()) return "Unknown";
        int split = value.indexOf(':');
        String name = split >= 0 && split + 1 < value.length() ? value.substring(split + 1) : value;
        return title(name.replace('_', ' '));
    }

    private void setRankedDurationFields(long milliseconds) {
        if (hoursField == null || minutesField == null || secondsField == null) return;
        long totalSeconds = Math.max(0L, milliseconds / 1000L);
        hoursField.setText(Long.toString(totalSeconds / 3600L));
        minutesField.setText(Long.toString(totalSeconds % 3600L / 60L));
        secondsField.setText(Long.toString(totalSeconds % 60L));
    }

    private NBTTagCompound placementData(int placement) {
        NBTTagList values = rankedData.getTagList("Placements", 10);
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            if (value.getInteger("Placement") == placement) return value;
        }
        NBTTagCompound empty = new NBTTagCompound();
        empty.setInteger("Placement", placement);
        empty.setTag("Items", new NBTTagList());
        empty.setTag("Commands", new NBTTagList());
        return empty;
    }

    private static String firstString(NBTTagList values) {
        return values == null || values.tagCount() == 0 ? "" : values.getStringTagAt(0);
    }

    private static String durationLabel(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long days = seconds / 86400L;
        long hours = seconds % 86400L / 3600L;
        long minutes = seconds % 3600L / 60L;
        if (days > 0L) return days + "d " + hours + "h";
        if (hours > 0L) return hours + "h " + minutes + "m";
        return minutes + "m " + seconds % 60L + "s";
    }

    private String quotaKind() {
        quotaIndex = Math.max(0, Math.min(quotaIndex, QUOTA_KINDS.size() - 1));
        return QUOTA_KINDS.get(quotaIndex);
    }

    private int quotaCount(String kind) {
        return storeData.getInteger("Quota_" + kind);
    }

    private int quotaLimit(String kind) {
        return storeData.getInteger("Limit_" + kind);
    }

    private void send(int action, String id, String property, String value) {
        RebornAddonNetwork.sendJutsuAdminAction(action, id, property, value);
    }

    private static String sectionName(int index) {
        String[] keys = {"gui.rebornaddon.admin.jutsus", "gui.rebornaddon.admin.store",
                "gui.rebornaddon.admin.sections", "gui.rebornaddon.admin.limits",
                "gui.rebornaddon.admin.quests", "gui.rebornaddon.admin.modes",
                "gui.rebornaddon.admin.ranked", "gui.rebornaddon.admin.ranks",
                "gui.rebornaddon.admin.cooldowns", "gui.rebornaddon.admin.performance",
                "gui.rebornaddon.admin.moderation", "gui.rebornaddon.admin.chakra_control",
                "gui.rebornaddon.admin.mounts", "gui.rebornaddon.admin.luckperms"};
        String[] fallback = {"Jutsus", "Store", "Sections", "Limits", "Quests", "Modes",
                "Ranked", "Ranks", "Cooldowns", "Performance", "Moderation", "Chakra Control",
                "Mounts", "LuckPerms"};
        return text(keys[index], fallback[index]);
    }

    private String chakraSetting() {
        chakraSettingIndex = Math.max(0, Math.min(chakraSettingIndex,
                CHAKRA_SETTING_IDS.length - 1));
        return CHAKRA_SETTING_IDS[chakraSettingIndex];
    }

    private boolean chakraBoolean(String property) {
        if (ChakraControlConfigurationService.ENABLED.equals(property)) {
            return chakraData.getBoolean("Enabled");
        }
        if (ChakraControlConfigurationService.WATER_WALKING_ENABLED.equals(property)) {
            return chakraData.getBoolean("WaterWalkingEnabled");
        }
        return chakraData.getBoolean("WallClimbingEnabled");
    }

    private static String chakraSettingLabel(int index) {
        String[] keys = {
                "gui.rebornaddon.chakra_control.master",
                "gui.rebornaddon.chakra_control.water_walking",
                "gui.rebornaddon.chakra_control.wall_climbing",
                "gui.rebornaddon.chakra_control.wall_speed"
        };
        String[] fallback = {"Master Control", "Water Walking", "Wall Climbing",
                "Wall Climb Speed"};
        int safe = Math.max(0, Math.min(index, keys.length - 1));
        return text(keys[safe], fallback[safe]);
    }

    private static String chakraSettingHelp(int index) {
        String[] keys = {
                "gui.rebornaddon.chakra_control.help.master",
                "gui.rebornaddon.chakra_control.help.water_walking",
                "gui.rebornaddon.chakra_control.help.wall_climbing",
                "gui.rebornaddon.chakra_control.help.wall_speed"
        };
        String[] fallback = {
                "Controls the complete RebornAddon water-walking and wall-climbing system.",
                "Controls stable movement on the top surface of water.",
                "Controls automatic climbing while moving into a wall.",
                "Scales normal and ledge climbing. 1 is current speed; 1.5 is 50% faster."
        };
        int safe = Math.max(0, Math.min(index, keys.length - 1));
        return text(keys[safe], fallback[safe]);
    }

    private static String formatMultiplier(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + "x";
    }

    private static String propertyLabel(String property) {
        if (property == null || property.isEmpty()) return "";
        String fallback = property.replace('-', ' ').replace('_', ' ');
        fallback = Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
        return text("gui.rebornaddon.jutsu.property." + property.replace('-', '_'), fallback);
    }

    private static String propertyHelp(String property) {
        if ("enabled".equals(property)) return text("gui.rebornaddon.jutsu.help.enabled", "Controls whether players can activate this jutsu.");
        if ("chakra".equals(property)) return text("gui.rebornaddon.jutsu.help.chakra", "Chakra consumed by one activation.");
        if ("cooldown".equals(property)) return text("gui.rebornaddon.jutsu.help.cooldown", "Reuse delay. Enter values such as 12s, 5m, or 1h.");
        if ("damage".equals(property)) return text("gui.rebornaddon.jutsu.help.damage", "Damage per registered hit; multi-hit jutsus apply it to each hit.");
        if ("explosion-damage".equals(property)) return text("gui.rebornaddon.jutsu.help.explosion_damage", "Final explosion damage for this stage.");
        if ("charge-time".equals(property)) return text("gui.rebornaddon.jutsu.help.charge_time", "Time required to fully charge; use instant or a duration.");
        if ("charge-speed".equals(property)) return text("gui.rebornaddon.jutsu.help.charge_speed", "Charge multiplier: 1 is normal, 2 is twice as fast.");
        if ("max-duration".equals(property)) return text("gui.rebornaddon.jutsu.help.max_duration", "Maximum active duration, entered as seconds, minutes, or hours.");
        if ("negative-effects-enabled".equals(property)) return text("gui.rebornaddon.jutsu.help.negative_effects_enabled", "Allows this jutsu to apply harmful status effects.");
        if ("effect-duration-multiplier".equals(property)) return text("gui.rebornaddon.jutsu.help.effect_duration_multiplier", "Multiplies harmful effect duration; 1 is unchanged.");
        if ("effect-amplifier-adjustment".equals(property)) return text("gui.rebornaddon.jutsu.help.effect_amplifier_adjustment", "Adds to each harmful effect level.");
        if ("negative-effect-rules".equals(property)) return text("gui.rebornaddon.jutsu.help.negative_effect_rules", "Per-effect behavior, duration, and strength overrides.");
        if ("additional-negative-effects".equals(property)) return text("gui.rebornaddon.jutsu.help.additional_negative_effects", "Extra effects: namespace:id|amplifier|duration, separated with commas.");
        return "Server-authoritative value used by this jutsu.";
    }

    private static String modePropertyLabel(String property) {
        if (property == null || property.isEmpty()) return "";
        String fallback = title(property.replace('-', '_'));
        return text("gui.rebornaddon.mode.property." + property.replace('-', '_'), fallback);
    }

    private static String modePropertyHelp(String property, ModeEntry entry) {
        if ("movement-speed-multiplier".equals(property)) {
            return ModeDefinition.hasFixedNativeMovement(entry == null ? "" : entry.id)
                    ? text("gui.rebornaddon.mode.help.movement_speed_multiplier",
                    "Final movement speed while active; 1 is normal player speed.")
                    : text("gui.rebornaddon.mode.help.movement_speed_dynamic",
                    "Multiplies the source mod's current speed; 1 leaves its dynamic value unchanged.");
        }
        String fallback = "Server-authoritative value applied while this mode is active.";
        return text("gui.rebornaddon.mode.help." + property.replace('-', '_'), fallback);
    }

    private void openEffectEditor() {
        if (selectedJutsu == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayGuiScreen(new GuiJutsuEffectEditor(minecraft.currentScreen,
                selectedJutsu.id, selectedJutsu.displayName,
                selectedJutsu.effective("negative-effect-rules"),
                selectedJutsu.effective("additional-negative-effects")));
    }

    private void openModeCombinationEditor() {
        if (selectedMode == null) return;
        List<String> ids = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        for (ModeEntry mode : modes) {
            if (mode.id.equals(selectedMode.id) || "server:global".equals(mode.id)) continue;
            ids.add(mode.id);
            names.add(mode.displayName);
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayGuiScreen(new GuiModeCombinationEditor(minecraft.currentScreen,
                selectedMode.id, selectedMode.displayName, ids, names,
                selectedMode.effective("incompatible-modes")));
    }

    private static boolean isEffectEditorProperty(String property) {
        return "negative-effect-rules".equals(property)
                || "additional-negative-effects".equals(property);
    }

    private static String effectRuleSummary(String property, String value) {
        if (value == null || value.trim().isEmpty()) {
            return text("gui.rebornaddon.effects.none", "No individual effect overrides");
        }
        String separator = "negative-effect-rules".equals(property) ? ";" : ",";
        int count = 0;
        for (String entry : value.split(separator)) if (!entry.trim().isEmpty()) count++;
        return ClientLocalization.format("gui.rebornaddon.effects.configured_count",
                "%s configured effects", count);
    }

    private static String nativeModeDetail(ModeEntry entry, String property) {
        return ModeDefinition.nativeDetail(entry.id, property, entry.nativeSummary);
    }

    private static String modeDisplay(String property, String value) {
        if ("melee-damage-multiplier".equals(property)
                || "movement-speed-multiplier".equals(property)
                || "damage-taken-multiplier".equals(property)) return value + "x";
        if ("regeneration-per-second".equals(property)) return value + " health / second";
        if ("knockback-resistance".equals(property)) return value + " added";
        if ("incompatible-modes".equals(property)) {
            if (value == null || value.trim().isEmpty()) return "none";
            return Integer.toString(value.split(",").length) + " blocked";
        }
        return value;
    }

    private static String canonicalJutsuName(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
        clean = clean.replaceAll("\\b(jutsu|technique)\\b", "").replaceAll(" +", " ").trim();
        return clean.isEmpty() ? value : clean;
    }

    private static String quotaLabel(String kind) {
        if ("anbu_mask".equals(kind)) return text("gui.rebornaddon.admin.anbu_masks", "ANBU Masks");
        if ("anbu_cloak".equals(kind)) return text("gui.rebornaddon.admin.anbu_cloaks", "ANBU Cloaks");
        if ("kage_hat".equals(kind)) return text("gui.rebornaddon.admin.kage_hat", "Kage Hat");
        if ("kage_robe".equals(kind)) return text("gui.rebornaddon.admin.kage_robe", "Kage Robe");
        if ("akatsuki_headband".equals(kind)) return text("gui.rebornaddon.admin.akatsuki_headband", "Rogue Headband");
        if ("akatsuki_hat".equals(kind)) return text("gui.rebornaddon.admin.akatsuki_hat", "Akatsuki Headwear");
        return text("gui.rebornaddon.admin.akatsuki_robe", "Akatsuki Robe");
    }

    private static String scopeLabel(ScopeEntry scope) {
        if (scope == null) return "";
        if ("tier".equals(scope.type)) return "Armor / " + (scope.tier == 0 ? "Base" : "Tier " + scope.tier);
        String category = title(scope.category);
        return scope.subcategory.isEmpty() ? category : category + " / " + title(scope.subcategory);
    }

    private static String title(String value) {
        if (value == null || value.isEmpty()) return "";
        String clean = value.replace('_', ' ');
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static int sectionAccent(int index) {
        return index == 0 ? Theme.PURPLE_LIGHT : index == 1 ? Theme.COPPER_BRIGHT
                : index == 2 ? Theme.TEAL : index == 3 ? Theme.GOLD
                : index == 4 ? Theme.SUCCESS : index == 5 ? Theme.PURPLE_LIGHT
                : index == 6 ? Theme.RANKED_RED : index == 7 ? Theme.GOLD
                : index == 8 ? Theme.TEAL : index == 9 ? Theme.SUCCESS
                : index == 11 ? Theme.TEAL : index == 12 ? Theme.GOLD
                : index == 13 ? Theme.TEAL : Theme.RANKED_RED;
    }

    private static JsonArray examRanks() {
        JsonObject root = ClientExamData.rankCatalogSnapshot();
        return root.has("rankCatalog") && root.get("rankCatalog").isJsonArray()
                ? root.getAsJsonArray("rankCatalog") : new JsonArray();
    }

    private static String jsonString(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int background, int hover) {
        return new ThemedButton(id, x, y, Math.max(20, width), Math.max(10, height), label,
                background, hover, Theme.BUTTON_TEXT);
    }

    private static String trim(FontRenderer font, String value, int maximumWidth) {
        if (value == null || font.getStringWidth(value) <= maximumWidth) return value == null ? "" : value;
        return font.trimStringToWidth(value, Math.max(8, maximumWidth - font.getStringWidth("..."))) + "...";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }

    private static <T> T preserve(T current, List<T> list) {
        return current != null && list.contains(current) ? current : list.isEmpty() ? null : list.get(0);
    }

    private List<String> jutsuNames(List<JutsuEntry> list) {
        List<String> names = new ArrayList<String>();
        for (JutsuEntry entry : list) names.add(entry.displayName);
        return names;
    }

    private List<String> storeNames(List<StoreEntry> list) {
        List<String> names = new ArrayList<String>();
        for (StoreEntry entry : list) names.add(entry.name);
        return names;
    }

    private List<String> scopeNames(List<ScopeEntry> list) {
        List<String> names = new ArrayList<String>();
        for (ScopeEntry entry : list) names.add(scopeLabel(entry));
        return names;
    }

    private List<String> modeNames(List<ModeEntry> list) {
        List<String> names = new ArrayList<String>();
        for (ModeEntry entry : list) names.add(entry.displayName);
        return names;
    }

    private JutsuEntry findJutsu(String id) {
        for (JutsuEntry entry : jutsus) if (entry.id.equals(id)) return entry;
        return null;
    }

    private StoreEntry findStore(String key) {
        for (StoreEntry entry : storeEntries) if (entry.key.equals(key)) return entry;
        return null;
    }

    private ScopeEntry findScope(String key) {
        for (ScopeEntry entry : scopes) if (entry.key.equals(key)) return entry;
        return null;
    }

    private ModeEntry findMode(String id) {
        for (ModeEntry entry : modes) if (entry.id.equals(id)) return entry;
        return null;
    }

    private static final class ModeEntry {
        private final String id;
        private final String displayName;
        private final String source;
        private final String nativeSummary;
        private final List<String> properties;
        private final Map<String, String> defaults;
        private final Map<String, String> overrides;

        private ModeEntry(String id, String displayName, String source, String nativeSummary,
                          List<String> properties, Map<String, String> defaults,
                          Map<String, String> overrides) {
            this.id = id;
            this.displayName = displayName;
            this.source = source;
            this.nativeSummary = nativeSummary == null || nativeSummary.isEmpty()
                    ? "Native values are supplied by the source mod." : nativeSummary;
            this.properties = properties;
            this.defaults = defaults;
            this.overrides = overrides;
        }

        private String effective(String property) {
            String value = overrides.get(property);
            if (value == null) value = defaults.get(property);
            if (value == null) return "";
            if ("movement-speed-multiplier".equals(property)) {
                try {
                    double stored = Double.parseDouble(value);
                    return BigDecimal.valueOf(ModeDefinition.movementTarget(id, stored))
                            .stripTrailingZeros().toPlainString();
                } catch (NumberFormatException ignored) {
                }
            }
            return value;
        }

        private boolean hasOverride(String property) { return overrides.containsKey(property); }

        private String sortKey() {
            return "server:global".equals(id) ? "0" : "1" + displayName.toLowerCase(Locale.ROOT) + id;
        }

        private static ModeEntry parse(String record) {
            String[] fields = record == null ? new String[0] : record.split("\\t", -1);
            if (fields.length < 6 || fields[0].isEmpty()) return null;
            List<String> properties = new ArrayList<String>();
            for (String property : fields[4].split(",")) if (!property.isEmpty()) properties.add(property);
            return new ModeEntry(fields[0], fields[1], fields[2],
                    fields.length > 6 ? fields[6] : "", properties,
                    JutsuEntry.values(fields[5]), JutsuEntry.values(fields.length > 8 ? fields[8] : ""));
        }
    }

    private static final class JutsuEntry {
        private final String id;
        private final String displayName;
        private final String source;
        private final String category;
        private final boolean restricted;
        private final List<String> properties;
        private final Map<String, String> defaults;
        private final Map<String, String> overrides;

        private JutsuEntry(String id, String displayName, String source, String category,
                           boolean restricted, List<String> properties,
                           Map<String, String> defaults, Map<String, String> overrides) {
            this.id = id;
            this.displayName = displayName;
            this.source = source;
            this.category = category == null || category.isEmpty() ? "Other" : category;
            this.restricted = restricted;
            this.properties = properties;
            this.defaults = defaults;
            this.overrides = overrides;
        }

        private String effective(String property) {
            String value = overrides.get(property);
            if (value == null) value = defaults.get(property);
            return value == null ? "" : value;
        }

        private boolean hasOverride(String property) { return overrides.containsKey(property); }

        private String sortKey() {
            return category + "\n" + displayName.toLowerCase(Locale.ROOT) + "\n" + id;
        }

        private static JutsuEntry parse(String record) {
            String[] fields = record == null ? new String[0] : record.split("\\t", -1);
            if (fields.length < 5 || fields[0].isEmpty()) return null;
            List<String> properties = new ArrayList<String>();
            for (String property : fields[4].split(",")) if (!property.isEmpty()) properties.add(property);
            String category = fields.length >= 9 ? fields[6] : "Other";
            boolean restricted = fields.length >= 9 && Boolean.parseBoolean(fields[7]);
            String overrides = fields.length >= 9 ? fields[8] : fields.length > 6 ? fields[6] : "";
            return new JutsuEntry(fields[0], fields[1], fields[2], category, restricted, properties,
                    values(fields.length > 5 ? fields[5] : ""),
                    values(overrides));
        }

        private static Map<String, String> values(String value) {
            Map<String, String> result = new LinkedHashMap<String, String>();
            for (String part : value.split(";")) {
                int separator = part.indexOf('=');
                if (separator > 0) result.put(part.substring(0, separator), part.substring(separator + 1));
            }
            return result;
        }
    }

    private static final class StoreEntry {
        private final String key;
        private final String name;
        private final int price;
        private final boolean purchasable;

        private StoreEntry(String key, String name, int price, boolean purchasable) {
            this.key = key;
            this.name = name;
            this.price = price;
            this.purchasable = purchasable;
        }

        private String sortKey() {
            return name.toLowerCase(Locale.ROOT) + "\n" + key;
        }

        private static StoreEntry parse(NBTTagCompound data) {
            String key = data.getString("Key");
            return key.isEmpty() ? null : new StoreEntry(key, data.getString("Name"),
                    data.getInteger("Price"), data.getBoolean("Purchasable"));
        }
    }

    private static final class ScopeEntry {
        private final String key;
        private final String type;
        private final String category;
        private final String subcategory;
        private final int tier;
        private final boolean enabled;

        private ScopeEntry(String key, String type, String category, String subcategory,
                           int tier, boolean enabled) {
            this.key = key;
            this.type = type;
            this.category = category;
            this.subcategory = subcategory;
            this.tier = tier;
            this.enabled = enabled;
        }

        private String sortKey() {
            return type + "\n" + category + "\n" + subcategory + "\n" + tier;
        }

        private static ScopeEntry parse(NBTTagCompound data) {
            String key = data.getString("Key");
            return key.isEmpty() ? null : new ScopeEntry(key, data.getString("Type"),
                    data.getString("Category"), data.getString("Subcategory"),
                    data.getInteger("Tier"), data.getBoolean("Enabled"));
        }
    }
}
