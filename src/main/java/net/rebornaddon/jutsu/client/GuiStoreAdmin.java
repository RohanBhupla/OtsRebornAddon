package net.rebornaddon.jutsu.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.jutsu.JutsuConfigurationService;
import net.rebornaddon.store.StoreCatalog;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GuiStoreAdmin extends RebornScaledGuiScreen {
    public static final int STORE_MODE = 0;
    public static final int LIMITS_MODE = 1;
    public static final int SECTIONS_MODE = 2;

    private static final int CATEGORY_FILTER = 1;
    private static final int STATE_FILTER = 2;
    private static final int PREVIOUS_PAGE = 3;
    private static final int NEXT_PAGE = 4;
    private static final int PROPERTY = 5;
    private static final int BOOLEAN_VALUE = 6;
    private static final int SAVE = 7;
    private static final int RESET_PROPERTY = 8;
    private static final int RESET_ENTRY = 9;
    private static final int RESET_ALL = 10;
    private static final int DONE = 11;
    private static final int RELOAD = 12;
    private static final int TAB_JUTSUS = 20;
    private static final int TAB_STORE = 21;
    private static final int TAB_LIMITS = 22;
    private static final int TAB_SECTIONS = 23;
    private static final int SECTION_VALUE = 36;
    private static final int SECTION_SAVE = 37;
    private static final int SECTION_RESET = 38;
    private static final int QUOTA_LOAD = 30;
    private static final int QUOTA_KIND = 31;
    private static final int QUOTA_SET = 32;
    private static final int QUOTA_ADJUST = 33;
    private static final int QUOTA_RESET = 34;
    private static final int QUOTA_RESET_ALL = 35;
    private static final int ENTRY_BASE = 100;
    private static final int SECTION_ENTRY_BASE = 300;

    private static final List<String> QUOTA_KINDS = Collections.unmodifiableList(Arrays.asList(
            StoreCatalog.QUOTA_ANBU_MASK, StoreCatalog.QUOTA_ANBU_CLOAK,
            StoreCatalog.QUOTA_KAGE_HAT, StoreCatalog.QUOTA_KAGE_ROBE,
            StoreCatalog.QUOTA_AKATSUKI_HEADBAND, StoreCatalog.QUOTA_AKATSUKI_HAT,
            StoreCatalog.QUOTA_AKATSUKI_ROBE));

    private final List<StoreEntry> entries = new ArrayList<StoreEntry>();
    private final List<StoreEntry> filtered = new ArrayList<StoreEntry>();
    private final List<String> categories = new ArrayList<String>();
    private final List<ScopeEntry> scopes = new ArrayList<ScopeEntry>();
    private final List<ScopeEntry> filteredScopes = new ArrayList<ScopeEntry>();

    private NBTTagCompound snapshot;
    private NBTTagCompound storeData;
    private GuiTextField searchField;
    private GuiTextField valueField;
    private GuiTextField playerField;
    private StoreEntry selected;
    private ScopeEntry selectedScope;
    private int mode;
    private int categoryIndex;
    private int stateFilter;
    private int propertyIndex;
    private int quotaIndex;
    private int page;
    private boolean booleanValue;
    private boolean resetAllArmed;
    private String notice = "";
    private String quotaPlayer = "";
    private String quotaUuid = "";
    private String quotaMessage = "";
    private boolean quotaAvailable;
    private boolean storeAvailable;
    private boolean scopeValue;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int listTop;
    private int rowsPerPage;

    public GuiStoreAdmin(NBTTagCompound snapshot, int mode) {
        this.snapshot = snapshot == null ? new NBTTagCompound() : snapshot.copy();
        this.mode = mode == LIMITS_MODE ? LIMITS_MODE
                : mode == SECTIONS_MODE ? SECTIONS_MODE : STORE_MODE;
        parseSnapshot();
    }

    public void applySnapshot(NBTTagCompound data) {
        String selectedKey = selected == null ? "" : selected.key;
        String selectedScopeKey = selectedScope == null ? "" : selectedScope.key;
        snapshot = data == null ? new NBTTagCompound() : data.copy();
        parseSnapshot();
        selected = find(selectedKey);
        selectedScope = findScope(selectedScopeKey);
        if (selected == null && !entries.isEmpty()) selected = entries.get(0);
        resetAllArmed = false;
        if (mc != null) initGui();
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.min(760, width - 20);
        panelHeight = Math.min(430, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        leftWidth = Math.max(174, Math.min(268, panelWidth * 38 / 100));
        rightX = panelX + leftWidth + 8;
        rightWidth = panelX + panelWidth - rightX;
        listTop = panelY + 112;
        rowsPerPage = Math.max(3, (panelY + panelHeight - 65 - listTop) / 24);

        String search = searchField == null ? "" : searchField.getText();
        boolean searchFocused = searchField != null && searchField.isFocused();
        int searchCursor = searchField == null ? 0 : searchField.getCursorPosition();
        String player = playerField == null ? quotaPlayer : playerField.getText();
        searchField = field(40, panelX + 14, panelY + 58, leftWidth - 28, search);
        searchField.setFocused(searchFocused);
        searchField.setCursorPosition(Math.min(searchCursor, search.length()));
        playerField = field(42, panelX + 14, panelY + 72, leftWidth - 28, player);
        valueField = field(41, rightX + 14, panelY + 150, rightWidth - 36, "");
        rebuildButtons();
    }

    private GuiTextField field(int id, int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(id, fontRenderer, x, y, Math.max(60, width), 20);
        field.setMaxStringLength(80);
        field.setText(value == null ? "" : value);
        field.setEnableBackgroundDrawing(true);
        return field;
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
        valueField.updateCursorCounter();
        playerField.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == TAB_JUTSUS) {
            mc.displayGuiScreen(new GuiJutsuAdmin(snapshot));
        } else if (button.id == TAB_STORE) {
            mode = STORE_MODE;
            resetAllArmed = false;
            rebuildButtons();
        } else if (button.id == TAB_LIMITS) {
            mode = LIMITS_MODE;
            resetAllArmed = false;
            rebuildButtons();
        } else if (button.id == TAB_SECTIONS) {
            mode = SECTIONS_MODE;
            resetAllArmed = false;
            rebuildButtons();
        } else if (button.id == DONE) {
            mc.displayGuiScreen(new GuiHub());
        } else if (mode == STORE_MODE) {
            handleStoreButton(button);
        } else if (mode == SECTIONS_MODE) {
            handleSectionButton(button);
        } else {
            handleQuotaButton(button);
        }
    }

    private void handleSectionButton(GuiButton button) {
        if (button.id == PREVIOUS_PAGE || button.id == NEXT_PAGE) {
            page += button.id == PREVIOUS_PAGE ? -1 : 1;
            selectedScope = null;
            rebuildButtons();
        } else if (button.id == SECTION_VALUE) {
            scopeValue = !scopeValue;
            button.displayString = scopeValue
                    ? text("gui.rebornaddon.admin.enabled", "Enabled")
                    : text("gui.rebornaddon.admin.disabled", "Disabled");
        } else if (button.id == SECTION_SAVE && selectedScope != null) {
            send(JutsuConfigurationService.STORE_SCOPE_UPDATE, selectedScope.key, "",
                    Boolean.toString(scopeValue));
        } else if (button.id == SECTION_RESET && selectedScope != null) {
            send(JutsuConfigurationService.STORE_SCOPE_RESET, selectedScope.key, "", "");
        } else if (button.id >= SECTION_ENTRY_BASE && button.id < SECTION_ENTRY_BASE + rowsPerPage) {
            int index = page * rowsPerPage + button.id - SECTION_ENTRY_BASE;
            if (index >= 0 && index < filteredScopes.size()) {
                selectedScope = filteredScopes.get(index);
                scopeValue = selectedScope.enabled;
                rebuildButtons();
            }
        }
    }

    private void handleStoreButton(GuiButton button) {
        if (button.id == CATEGORY_FILTER) {
            categoryIndex = (categoryIndex + 1) % Math.max(1, categories.size());
            page = 0;
            rebuildButtons();
        } else if (button.id == STATE_FILTER) {
            stateFilter = (stateFilter + 1) % 3;
            page = 0;
            rebuildButtons();
        } else if (button.id == PREVIOUS_PAGE || button.id == NEXT_PAGE) {
            page += button.id == PREVIOUS_PAGE ? -1 : 1;
            selected = null;
            rebuildButtons();
        } else if (button.id == PROPERTY) {
            propertyIndex = (propertyIndex + 1) % 2;
            rebuildButtons();
        } else if (button.id == BOOLEAN_VALUE) {
            booleanValue = !booleanValue;
            button.displayString = booleanValue
                    ? text("gui.rebornaddon.admin.purchasable", "Purchasable")
                    : text("gui.rebornaddon.admin.blocked", "Blocked");
        } else if (button.id == SAVE && selected != null) {
            String property = storeProperty();
            String value = "purchasable".equals(property)
                    ? Boolean.toString(booleanValue) : valueField.getText();
            send(JutsuConfigurationService.STORE_UPDATE, selected.key, property, value);
        } else if (button.id == RESET_PROPERTY && selected != null) {
            send(JutsuConfigurationService.STORE_RESET, selected.key, storeProperty(), "");
        } else if (button.id == RESET_ENTRY && selected != null) {
            send(JutsuConfigurationService.STORE_RESET, selected.key, "all", "");
        } else if (button.id == RESET_ALL) {
            if (resetAllArmed) send(JutsuConfigurationService.STORE_RESET_ALL, "", "", "");
            resetAllArmed = !resetAllArmed;
            rebuildButtons();
        } else if (button.id == RELOAD) {
            send(JutsuConfigurationService.STORE_RELOAD, "", "", "");
        } else if (button.id >= ENTRY_BASE && button.id < ENTRY_BASE + rowsPerPage) {
            int index = page * rowsPerPage + button.id - ENTRY_BASE;
            if (index >= 0 && index < filtered.size()) {
                selected = filtered.get(index);
                propertyIndex = 0;
                resetAllArmed = false;
                rebuildButtons();
            }
        }
    }

    private void handleQuotaButton(GuiButton button) {
        String player = playerField.getText().trim();
        String kind = quotaKind();
        if (button.id == QUOTA_LOAD) {
            send(JutsuConfigurationService.QUOTA_LOAD, player, "", "");
        } else if (button.id == QUOTA_KIND) {
            quotaIndex = (quotaIndex + 1) % QUOTA_KINDS.size();
            rebuildButtons();
        } else if (button.id == QUOTA_SET) {
            send(JutsuConfigurationService.QUOTA_SET, player, kind, valueField.getText());
        } else if (button.id == QUOTA_ADJUST) {
            send(JutsuConfigurationService.QUOTA_ADD, player, kind, valueField.getText());
        } else if (button.id == QUOTA_RESET) {
            send(JutsuConfigurationService.QUOTA_RESET, player, kind, "");
        } else if (button.id == QUOTA_RESET_ALL) {
            if (resetAllArmed) send(JutsuConfigurationService.QUOTA_RESET, player, "all", "");
            resetAllArmed = !resetAllArmed;
            rebuildButtons();
        }
    }

    private void send(int action, String id, String property, String value) {
        RebornAddonNetwork.sendJutsuAdminAction(action, id, property, value);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mode == STORE_MODE || mode == SECTIONS_MODE) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            if (mode == STORE_MODE && "price".equals(storeProperty())) {
                valueField.mouseClicked(mouseX, mouseY, mouseButton);
            }
        } else {
            playerField.mouseClicked(mouseX, mouseY, mouseButton);
            valueField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if ((mode == STORE_MODE || mode == SECTIONS_MODE)
                && searchField.textboxKeyTyped(typedChar, keyCode)) {
            page = 0;
            rebuildButtons();
            return;
        }
        if (mode == LIMITS_MODE && playerField.textboxKeyTyped(typedChar, keyCode)) return;
        if ((mode == LIMITS_MODE || "price".equals(storeProperty()))
                && valueField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int accent = mode == STORE_MODE ? Theme.COPPER
                : mode == SECTIONS_MODE ? Theme.TEAL : Theme.GOLD;
        GuiChrome.frame(panelX, panelY, panelWidth, panelHeight, accent);
        GuiChrome.header(panelX + 2, panelY + 5, panelWidth - 4, 42, accent);
        fontRenderer.drawString(text("gui.rebornaddon.admin.title", "Server Administration"),
                panelX + 16, panelY + 17, Theme.TEXT_LIGHT);
        if (!notice.isEmpty()) {
            fontRenderer.drawString(trim(notice, Math.max(90, panelWidth - leftWidth - 50)),
                    panelX + leftWidth + 14, panelY + 34, Theme.GOLD);
        }
        if (mode == STORE_MODE) drawStore();
        else if (mode == SECTIONS_MODE) drawSectionsAdmin();
        else drawLimits();
        drawScaledControls(mouseX, mouseY, partialTicks);
        if (mode == STORE_MODE || mode == SECTIONS_MODE) {
            searchField.drawTextBox();
            if (mode == STORE_MODE && selected != null && "price".equals(storeProperty())) {
                valueField.drawTextBox();
            }
        } else {
            playerField.drawTextBox();
            valueField.drawTextBox();
        }
    }

    private void drawSectionsAdmin() {
        drawSections(Theme.TEAL);
        fontRenderer.drawString(text("gui.rebornaddon.admin.search_sections", "Search sections"),
                panelX + 15, panelY + 49, Theme.TEXT_MUTED);
        if (selectedScope == null) {
            fontRenderer.drawSplitString(text("gui.rebornaddon.admin.no_sections",
                            "No store sections match."), rightX + 14, panelY + 70,
                    rightWidth - 36, Theme.TEXT_MUTED);
            return;
        }
        fontRenderer.drawString(trim(scopeLabel(selectedScope), rightWidth - 38),
                rightX + 14, panelY + 70, Theme.TEXT_LIGHT);
        fontRenderer.drawString(trim(selectedScope.key, rightWidth - 38),
                rightX + 14, panelY + 86, Theme.TEXT_MUTED);
        GuiChrome.rule(rightX + 14, rightX + rightWidth - 22, panelY + 108, Theme.TEAL);
        String current = selectedScope.enabled
                ? text("gui.rebornaddon.admin.enabled", "Enabled")
                : text("gui.rebornaddon.admin.disabled", "Disabled");
        fontRenderer.drawString(text("gui.rebornaddon.jutsu.current", "Current") + ": " + current,
                rightX + 14, panelY + 112, selectedScope.enabled ? Theme.SUCCESS_BRIGHT : Theme.DANGER_BRIGHT);
    }

    private void drawStore() {
        drawSections(Theme.COPPER);
        fontRenderer.drawString(text("gui.rebornaddon.admin.search_items", "Search items"),
                panelX + 15, panelY + 49, Theme.TEXT_MUTED);
        if (!storeAvailable) {
            fontRenderer.drawString(trim(text("gui.rebornaddon.admin.store_unavailable",
                            "Store plugin unavailable"), rightWidth - 38),
                    rightX + 14, panelY + 56, Theme.DANGER_BRIGHT);
        }
        if (selected == null) {
            fontRenderer.drawSplitString(text("gui.rebornaddon.admin.no_items", "No store items match."),
                    rightX + 14, panelY + 70, rightWidth - 36, Theme.TEXT_MUTED);
            return;
        }
        fontRenderer.drawString(trim(selected.name, rightWidth - 38), rightX + 14, panelY + 68,
                Theme.TEXT_LIGHT);
        fontRenderer.drawString(trim(selected.key, rightWidth - 38), rightX + 14, panelY + 83,
                Theme.TEXT_MUTED);
        String grouping = categoryLabel(selected.category) + (selected.subcategory.isEmpty()
                ? "" : " / " + selected.subcategory) + (selected.tier > 0 ? " / Tier " + selected.tier : "");
        fontRenderer.drawString(trim(grouping, rightWidth - 38), rightX + 14, panelY + 98, Theme.COPPER_BRIGHT);
        GuiChrome.rule(rightX + 14, rightX + rightWidth - 22, panelY + 111, Theme.COPPER);
        fontRenderer.drawString(text("gui.rebornaddon.jutsu.property", "Property"),
                rightX + 14, panelY + 113, Theme.TEXT_MUTED);
        fontRenderer.drawString(text("gui.rebornaddon.jutsu.value", "Value"),
                rightX + 14, panelY + 140, Theme.TEXT_MUTED);
        String current = "price".equals(storeProperty())
                ? selected.price + " Ryo" : selected.purchasable
                ? text("gui.rebornaddon.admin.purchasable", "Purchasable")
                : text("gui.rebornaddon.admin.blocked", "Blocked");
        fontRenderer.drawString(trim(text("gui.rebornaddon.jutsu.current", "Current") + ": " + current,
                        rightWidth - 38), rightX + 14, panelY + 176, Theme.TEXT_LIGHT);
    }

    private void drawLimits() {
        drawSections(Theme.GOLD);
        fontRenderer.drawString(text("gui.rebornaddon.admin.player", "Player"),
                panelX + 15, panelY + 59, Theme.TEXT_MUTED);
        int y = panelY + 136;
        fontRenderer.drawString(trim(quotaPlayer.isEmpty() ? quotaMessage : quotaPlayer, leftWidth - 32),
                panelX + 15, y, quotaAvailable ? Theme.TEXT_LIGHT : Theme.DANGER_BRIGHT);
        if (!quotaUuid.isEmpty()) fontRenderer.drawString(trim(quotaUuid, leftWidth - 32),
                panelX + 15, y + 14, Theme.TEXT_MUTED);
        y += 40;
        for (String kind : QUOTA_KINDS) {
            fontRenderer.drawString(quotaLabel(kind) + ": " + count(kind) + "/" + limit(kind),
                    panelX + 15, y, kind.equals(quotaKind()) ? Theme.GOLD : Theme.TEXT_MUTED);
            y += 14;
        }

        fontRenderer.drawString(text("gui.rebornaddon.admin.purchase_limit", "Purchase limit"),
                rightX + 14, panelY + 74, Theme.TEXT_LIGHT);
        fontRenderer.drawString(text("gui.rebornaddon.admin.count_or_adjustment", "Count or adjustment"),
                rightX + 14, panelY + 140, Theme.TEXT_MUTED);
        String current = quotaLabel(quotaKind()) + ": " + count(quotaKind()) + "/" + limit(quotaKind());
        fontRenderer.drawString(trim(current, rightWidth - 38), rightX + 14, panelY + 176,
                quotaAvailable ? Theme.GOLD : Theme.TEXT_MUTED);
    }

    private void drawSections(int accent) {
        int top = panelY + 51;
        int height = panelHeight - 108;
        GuiChrome.section(panelX + 8, top, leftWidth - 8, height, accent);
        GuiChrome.section(rightX, top, rightWidth - 8, height, accent);
    }

    private void rebuildButtons() {
        buttonList.clear();
        addAdminTabs();
        if (mode == STORE_MODE) buildStoreButtons();
        else if (mode == SECTIONS_MODE) buildSectionButtons();
        else buildQuotaButtons();
        buttonList.add(button(DONE, panelX + (leftWidth - 78) / 2,
                panelY + panelHeight - 34, 78, 22,
                text("gui.rebornaddon.admin.done", "Done"), Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void buildStoreButtons() {
        applyFilters();
        if (selected == null || !filtered.contains(selected)) {
            selected = filtered.isEmpty() ? null : filtered.get(Math.min(page * rowsPerPage,
                    filtered.size() - 1));
        }
        int totalPages = Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int filterWidth = (leftWidth - 34) / 2;
        buttonList.add(button(CATEGORY_FILTER, panelX + 14, panelY + 81, filterWidth, 20,
                categoryFilterLabel(), Theme.TAB_INACTIVE_BG, Theme.COPPER));
        buttonList.add(button(STATE_FILTER, panelX + 20 + filterWidth, panelY + 81,
                filterWidth, 20, stateLabel(), Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        int start = page * rowsPerPage;
        int end = Math.min(filtered.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            StoreEntry entry = filtered.get(i);
            ThemedButton row = button(ENTRY_BASE + i - start, panelX + 14,
                    listTop + (i - start) * 24, leftWidth - 28, 20,
                    entry.name, Theme.TAB_INACTIVE_BG, Theme.COPPER);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setSelected(selected != null && selected.key.equals(entry.key));
            buttonList.add(row);
        }
        int pagerY = panelY + panelHeight - 59;
        ThemedButton previous = button(PREVIOUS_PAGE, panelX + 14, pagerY, 34, 20,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttonList.add(previous);
        ThemedButton next = button(NEXT_PAGE, panelX + leftWidth - 48, pagerY, 34, 20,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < totalPages;
        buttonList.add(next);

        if (selected != null) {
            buttonList.add(button(PROPERTY, rightX + 14, panelY + 120, rightWidth - 36, 20,
                    storePropertyLabel(), Theme.TAB_INACTIVE_BG, Theme.COPPER));
            if ("purchasable".equals(storeProperty())) {
                booleanValue = selected.purchasable;
                buttonList.add(button(BOOLEAN_VALUE, rightX + 14, panelY + 150, rightWidth - 36, 20,
                        booleanValue ? text("gui.rebornaddon.admin.purchasable", "Purchasable")
                                : text("gui.rebornaddon.admin.blocked", "Blocked"),
                        booleanValue ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                        booleanValue ? Theme.TEAL : Theme.RANKED_RED));
            } else {
                valueField.setText(Integer.toString(selected.price));
            }
            int actionWidth = Math.max(48, (rightWidth - 50) / 3);
            ThemedButton save = button(SAVE, rightX + 14, panelY + 192, actionWidth, 21,
                    text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL);
            save.enabled = storeAvailable;
            buttonList.add(save);
            ThemedButton resetProperty = button(RESET_PROPERTY, rightX + 20 + actionWidth, panelY + 192,
                    actionWidth, 21, text("gui.rebornaddon.jutsu.reset_property", "Reset Property"),
                    Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
            resetProperty.enabled = storeAvailable;
            buttonList.add(resetProperty);
            ThemedButton resetItem = button(RESET_ENTRY, rightX + 26 + actionWidth * 2, panelY + 192,
                    actionWidth, 21, text("gui.rebornaddon.admin.reset_item", "Reset Item"),
                    Theme.RANKED_RED_DARK, Theme.RANKED_RED);
            resetItem.enabled = storeAvailable;
            buttonList.add(resetItem);
        }
        int bottom = panelY + panelHeight - 34;
        int footerWidth = Math.max(100, rightWidth - 8);
        int resetWidth = Math.max(60, footerWidth * 62 / 100);
        ThemedButton resetAll = button(RESET_ALL, rightX, bottom, resetWidth, 22,
                resetAllArmed ? text("gui.rebornaddon.admin.confirm_reset_store", "Confirm Reset")
                        : text("gui.rebornaddon.admin.reset_store", "Reset Store"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        resetAll.enabled = storeAvailable;
        buttonList.add(resetAll);
        ThemedButton reload = button(RELOAD, rightX + resetWidth + 4, bottom,
                footerWidth - resetWidth - 4, 22,
                text("gui.rebornaddon.admin.reload", "Reload"), Theme.TAB_INACTIVE_BG, Theme.COPPER);
        reload.enabled = storeAvailable;
        buttonList.add(reload);
    }

    private void buildQuotaButtons() {
        ThemedButton load = button(QUOTA_LOAD, panelX + 14, panelY + 100, leftWidth - 28, 22,
                text("gui.rebornaddon.admin.load_player", "Load Player"), Theme.TEAL_DARK, Theme.TEAL);
        load.enabled = storeAvailable;
        buttonList.add(load);
        buttonList.add(button(QUOTA_KIND, rightX + 14, panelY + 100, rightWidth - 36, 22,
                quotaLabel(quotaKind()), Theme.TAB_INACTIVE_BG, Theme.GOLD));
        int actionWidth = Math.max(52, (rightWidth - 44) / 3);
        ThemedButton set = button(QUOTA_SET, rightX + 14, panelY + 192, actionWidth, 21,
                text("gui.rebornaddon.admin.set_count", "Set Count"), Theme.TEAL_DARK, Theme.TEAL);
        set.enabled = storeAvailable;
        buttonList.add(set);
        ThemedButton adjust = button(QUOTA_ADJUST, rightX + 18 + actionWidth, panelY + 192,
                actionWidth, 21, text("gui.rebornaddon.admin.adjust_count", "Adjust"),
                Theme.TAB_INACTIVE_BG, Theme.GOLD);
        adjust.enabled = storeAvailable;
        buttonList.add(adjust);
        ThemedButton reset = button(QUOTA_RESET, rightX + 22 + actionWidth * 2, panelY + 192,
                actionWidth, 21, text("gui.rebornaddon.admin.reset_count", "Reset"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        reset.enabled = storeAvailable;
        buttonList.add(reset);
        ThemedButton resetAll = button(QUOTA_RESET_ALL, rightX, panelY + panelHeight - 34, 138, 22,
                resetAllArmed ? text("gui.rebornaddon.admin.confirm_reset_counts", "Confirm Reset")
                        : text("gui.rebornaddon.admin.reset_all_counts", "Reset All Counts"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        resetAll.enabled = storeAvailable;
        buttonList.add(resetAll);
    }

    private void buildSectionButtons() {
        applyScopeFilters();
        if (selectedScope == null || !filteredScopes.contains(selectedScope)) {
            selectedScope = filteredScopes.isEmpty() ? null
                    : filteredScopes.get(Math.min(page * rowsPerPage, filteredScopes.size() - 1));
        }
        int totalPages = Math.max(1, (filteredScopes.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(filteredScopes.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            ScopeEntry scope = filteredScopes.get(i);
            ThemedButton row = button(SECTION_ENTRY_BASE + i - start, panelX + 14,
                    listTop + (i - start) * 24, leftWidth - 28, 20,
                    scopeLabel(scope), Theme.TAB_INACTIVE_BG, Theme.TEAL);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setSelected(selectedScope != null && selectedScope.key.equals(scope.key));
            buttonList.add(row);
        }
        int pagerY = panelY + panelHeight - 59;
        ThemedButton previous = button(PREVIOUS_PAGE, panelX + 14, pagerY, 34, 20,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttonList.add(previous);
        ThemedButton next = button(NEXT_PAGE, panelX + leftWidth - 48, pagerY, 34, 20,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < totalPages;
        buttonList.add(next);

        if (selectedScope != null) {
            scopeValue = selectedScope.enabled;
            buttonList.add(button(SECTION_VALUE, rightX + 14, panelY + 136,
                    rightWidth - 36, 22,
                    scopeValue ? text("gui.rebornaddon.admin.enabled", "Enabled")
                            : text("gui.rebornaddon.admin.disabled", "Disabled"),
                    scopeValue ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                    scopeValue ? Theme.TEAL : Theme.RANKED_RED));
            int width = (rightWidth - 40) / 2;
            ThemedButton save = button(SECTION_SAVE, rightX + 14, panelY + 176, width, 22,
                    text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL);
            save.enabled = storeAvailable;
            buttonList.add(save);
            ThemedButton reset = button(SECTION_RESET, rightX + 18 + width, panelY + 176,
                    width, 22, text("gui.rebornaddon.jutsu.reset_property", "Reset Property"),
                    Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
            reset.enabled = storeAvailable;
            buttonList.add(reset);
        }
    }

    private void addAdminTabs() {
        int width = Math.max(45, Math.min(66, (panelWidth - 150) / 4));
        int start = panelX + panelWidth - 16 - width * 4 - 9;
        buttonList.add(tab(TAB_JUTSUS, start, width,
                text("gui.rebornaddon.admin.jutsus", "Jutsus"), false, Theme.TEAL));
        buttonList.add(tab(TAB_STORE, start + width + 3, width,
                text("gui.rebornaddon.admin.store", "Store"), mode == STORE_MODE, Theme.COPPER));
        buttonList.add(tab(TAB_SECTIONS, start + (width + 3) * 2, width,
                text("gui.rebornaddon.admin.sections", "Sections"), mode == SECTIONS_MODE, Theme.TEAL));
        buttonList.add(tab(TAB_LIMITS, start + (width + 3) * 3, width,
                text("gui.rebornaddon.admin.limits", "Limits"), mode == LIMITS_MODE, Theme.GOLD));
    }

    private ThemedButton tab(int id, int x, int width, String label, boolean selected, int accent) {
        ThemedButton button = button(id, x, panelY + 11, width, 22, label,
                selected ? accent : Theme.TAB_INACTIVE_BG, accent);
        button.setSelected(selected);
        return button;
    }

    private void applyFilters() {
        filtered.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryIndex < categories.size() ? categories.get(categoryIndex) : "";
        for (StoreEntry entry : entries) {
            if (!category.isEmpty() && !category.equals(entry.category)) continue;
            if (stateFilter == 1 && !entry.purchasable || stateFilter == 2 && entry.purchasable) continue;
            if (!query.isEmpty() && !entry.key.toLowerCase(Locale.ROOT).contains(query)
                    && !entry.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            filtered.add(entry);
        }
    }

    private void applyScopeFilters() {
        filteredScopes.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (ScopeEntry scope : scopes) {
            if (!query.isEmpty() && !scope.key.toLowerCase(Locale.ROOT).contains(query)
                    && !scopeLabel(scope).toLowerCase(Locale.ROOT).contains(query)) continue;
            filteredScopes.add(scope);
        }
    }

    private void parseSnapshot() {
        entries.clear();
        categories.clear();
        categories.add("");
        notice = snapshot.getString("Message");
        storeData = snapshot.getCompoundTag("Store");
        storeAvailable = storeData.getBoolean("Available");
        NBTTagList records = storeData.getTagList("Records", 10);
        for (int i = 0; i < records.tagCount(); i++) {
            StoreEntry entry = StoreEntry.parse(records.getCompoundTagAt(i));
            if (entry == null) continue;
            entries.add(entry);
            if (!categories.contains(entry.category)) categories.add(entry.category);
        }
        Collections.sort(entries, Comparator.comparing(StoreEntry::sortKey));
        scopes.clear();
        NBTTagList scopeTags = storeData.getTagList("Scopes", 10);
        for (int i = 0; i < scopeTags.tagCount(); i++) {
            ScopeEntry scope = ScopeEntry.parse(scopeTags.getCompoundTagAt(i));
            if (scope != null) scopes.add(scope);
        }
        Collections.sort(scopes, Comparator.comparing(ScopeEntry::sortKey));
        Collections.sort(categories);
        quotaPlayer = storeData.getString("QuotaPlayer");
        quotaUuid = storeData.getString("QuotaUuid");
        quotaMessage = storeData.getString("QuotaMessage");
        quotaAvailable = storeData.getBoolean("QuotaAvailable");
        if (selected == null && !entries.isEmpty()) selected = entries.get(0);
        if (selectedScope == null && !scopes.isEmpty()) selectedScope = scopes.get(0);
    }

    private StoreEntry find(String key) {
        for (StoreEntry entry : entries) if (entry.key.equals(key)) return entry;
        return null;
    }

    private ScopeEntry findScope(String key) {
        for (ScopeEntry scope : scopes) if (scope.key.equals(key)) return scope;
        return null;
    }

    private static String scopeLabel(ScopeEntry scope) {
        if (scope == null) return "";
        if ("category".equals(scope.type)) return categoryLabel(scope.category);
        if ("tier".equals(scope.type)) return categoryLabel(StoreCatalog.ARMOR) + " / "
                + (scope.tier == 0 ? text("gui.rebornaddon.store.tier_base", "Base")
                : ClientLocalization.format("gui.rebornaddon.store.tier", "Tier %s", scope.tier));
        return categoryLabel(scope.category) + " / " + subcategoryLabel(scope.subcategory);
    }

    private static String subcategoryLabel(String value) {
        if (value == null || value.isEmpty()) return "";
        String key = "gui.rebornaddon.store." + value;
        String fallback = value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).replace('_', ' ');
        return text(key, fallback);
    }

    private String storeProperty() {
        return propertyIndex == 0 ? "price" : "purchasable";
    }

    private String storePropertyLabel() {
        return propertyIndex == 0 ? text("gui.rebornaddon.admin.price", "Price (Ryo)")
                : text("gui.rebornaddon.admin.availability", "Availability");
    }

    private String categoryFilterLabel() {
        String category = categoryIndex < categories.size() ? categories.get(categoryIndex) : "";
        return category.isEmpty() ? text("gui.rebornaddon.admin.all_categories", "All Categories")
                : categoryLabel(category);
    }

    private String stateLabel() {
        return stateFilter == 1 ? text("gui.rebornaddon.admin.purchasable", "Purchasable")
                : stateFilter == 2 ? text("gui.rebornaddon.admin.blocked", "Blocked")
                : text("gui.rebornaddon.jutsu.all_states", "All States");
    }

    private static String categoryLabel(String category) {
        if (StoreCatalog.ARMOR.equals(category)) return text("gui.rebornaddon.store.armor", "Armor");
        if (StoreCatalog.WEAPONS.equals(category)) return text("gui.rebornaddon.store.weapons", "Weapons");
        if (StoreCatalog.FOOD.equals(category)) return text("gui.rebornaddon.store.food", "Food");
        if (StoreCatalog.BUILDING.equals(category)) return text("gui.rebornaddon.store.building", "Building");
        if (StoreCatalog.TOOLS.equals(category)) return text("gui.rebornaddon.store.tools", "Tools");
        if (StoreCatalog.MUSIC.equals(category)) return text("gui.rebornaddon.store.music", "Music");
        return text("gui.rebornaddon.store.scrolls", "Scrolls");
    }

    private String quotaKind() {
        quotaIndex = Math.max(0, Math.min(quotaIndex, QUOTA_KINDS.size() - 1));
        return QUOTA_KINDS.get(quotaIndex);
    }

    private int count(String kind) {
        return storeData.getInteger("Quota_" + kind);
    }

    private int limit(String kind) {
        return storeData.getInteger("Limit_" + kind);
    }

    private static String quotaLabel(String kind) {
        if (StoreCatalog.QUOTA_ANBU_MASK.equals(kind)) return text("gui.rebornaddon.admin.anbu_masks", "ANBU Masks");
        if (StoreCatalog.QUOTA_ANBU_CLOAK.equals(kind)) return text("gui.rebornaddon.admin.anbu_cloaks", "ANBU Cloaks");
        if (StoreCatalog.QUOTA_KAGE_HAT.equals(kind)) return text("gui.rebornaddon.admin.kage_hat", "Kage Hat");
        if (StoreCatalog.QUOTA_KAGE_ROBE.equals(kind)) return text("gui.rebornaddon.admin.kage_robe", "Kage Robe");
        if (StoreCatalog.QUOTA_AKATSUKI_HEADBAND.equals(kind)) return text("gui.rebornaddon.admin.akatsuki_headband", "Rogue Headband");
        if (StoreCatalog.QUOTA_AKATSUKI_HAT.equals(kind)) return text("gui.rebornaddon.admin.akatsuki_hat", "Akatsuki Headwear");
        return text("gui.rebornaddon.admin.akatsuki_robe", "Akatsuki Robe");
    }

    private ThemedButton button(int id, int x, int y, int width, int height, String label,
                                int background, int hover) {
        return new ThemedButton(id, x, y, Math.max(24, width), height, label,
                background, hover, Theme.BUTTON_TEXT);
    }

    private String trim(String value, int maximumWidth) {
        if (fontRenderer.getStringWidth(value) <= maximumWidth) return value;
        return fontRenderer.trimStringToWidth(value,
                Math.max(8, maximumWidth - fontRenderer.getStringWidth("..."))) + "...";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class StoreEntry {
        private final String key;
        private final String name;
        private final String category;
        private final String subcategory;
        private final int tier;
        private final int price;
        private final boolean purchasable;

        private StoreEntry(String key, String name, String category, String subcategory,
                           int tier, int price, boolean purchasable) {
            this.key = key;
            this.name = name;
            this.category = category;
            this.subcategory = subcategory;
            this.tier = tier;
            this.price = price;
            this.purchasable = purchasable;
        }

        private String sortKey() {
            return category + "\n" + name.toLowerCase(Locale.ROOT) + "\n" + key;
        }

        private static StoreEntry parse(NBTTagCompound data) {
            String key = data.getString("Key");
            if (key.isEmpty()) return null;
            return new StoreEntry(key, data.getString("Name"), data.getString("Category"),
                    data.getString("Subcategory"), data.getInteger("Tier"),
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

        private ScopeEntry(String key, String type, String category,
                           String subcategory, int tier, boolean enabled) {
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
            if (key.isEmpty()) return null;
            return new ScopeEntry(key, data.getString("Type"), data.getString("Category"),
                    data.getString("Subcategory"), data.getInteger("Tier"),
                    data.getBoolean("Enabled"));
        }
    }
}
