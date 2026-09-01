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
import net.rebornaddon.jutsu.JutsuValueParser;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiJutsuAdmin extends RebornScaledGuiScreen {
    private static final int SOURCE_FILTER = 1;
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
    private static final int TAB_JUTSUS = 20;
    private static final int TAB_STORE = 21;
    private static final int TAB_LIMITS = 22;
    private static final int ENTRY_BASE = 100;

    private final List<Entry> entries = new ArrayList<Entry>();
    private final List<Entry> filtered = new ArrayList<Entry>();
    private final List<String> sources = new ArrayList<String>();

    private NBTTagCompound snapshot;
    private GuiTextField searchField;
    private GuiTextField valueField;
    private Entry selected;
    private String notice = "";
    private int sourceIndex;
    private int stateFilter;
    private int propertyIndex;
    private int page;
    private boolean resetAllArmed;
    private boolean booleanValue;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int listTop;
    private int rowsPerPage;

    public GuiJutsuAdmin(NBTTagCompound snapshot) {
        this.snapshot = snapshot == null ? new NBTTagCompound() : snapshot.copy();
        parseSnapshot();
    }

    public void applySnapshot(NBTTagCompound data) {
        String selectedId = selected == null ? "" : selected.id;
        snapshot = data == null ? new NBTTagCompound() : data.copy();
        parseSnapshot();
        selected = find(selectedId);
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        }
        propertyIndex = 0;
        resetAllArmed = false;
        if (mc != null) {
            initGui();
        }
    }

    NBTTagCompound snapshotCopy() {
        return snapshot.copy();
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
        int listBottom = panelY + panelHeight - 65;
        rowsPerPage = Math.max(3, (listBottom - listTop) / 24);

        String oldSearch = searchField == null ? "" : searchField.getText();
        boolean searchFocused = searchField != null && searchField.isFocused();
        int searchCursor = searchField == null ? 0 : searchField.getCursorPosition();
        searchField = new GuiTextField(40, fontRenderer, panelX + 14, panelY + 58,
                leftWidth - 28, 18);
        searchField.setMaxStringLength(80);
        searchField.setText(oldSearch);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setFocused(searchFocused);
        searchField.setCursorPosition(Math.min(searchCursor, oldSearch.length()));

        valueField = new GuiTextField(41, fontRenderer, rightX + 14, panelY + 150,
                Math.max(80, rightWidth - 28), 20);
        valueField.setMaxStringLength(80);
        rebuildButtons();
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
        valueField.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == TAB_STORE) {
            mc.displayGuiScreen(new GuiStoreAdmin(snapshot, GuiStoreAdmin.STORE_MODE));
        } else if (button.id == TAB_LIMITS) {
            mc.displayGuiScreen(new GuiStoreAdmin(snapshot, GuiStoreAdmin.LIMITS_MODE));
        } else if (button.id == TAB_JUTSUS) {
            return;
        } else if (button.id == SOURCE_FILTER) {
            sourceIndex = (sourceIndex + 1) % Math.max(1, sources.size());
            page = 0;
            rebuildButtons();
        } else if (button.id == STATE_FILTER) {
            stateFilter = (stateFilter + 1) % 3;
            page = 0;
            rebuildButtons();
        } else if (button.id == PREVIOUS_PAGE) {
            page = Math.max(0, page - 1);
            rebuildButtons();
        } else if (button.id == NEXT_PAGE) {
            page++;
            rebuildButtons();
        } else if (button.id == PROPERTY && selected != null && !selected.properties.isEmpty()) {
            propertyIndex = (propertyIndex + 1) % selected.properties.size();
            resetAllArmed = false;
            rebuildButtons();
        } else if (button.id == BOOLEAN_VALUE) {
            booleanValue = !booleanValue;
            button.displayString = booleanValue
                    ? text("gui.rebornaddon.jutsu.enabled", "Enabled")
                    : text("gui.rebornaddon.jutsu.disabled", "Disabled");
        } else if (button.id == SAVE) {
            saveProperty();
        } else if (button.id == RESET_PROPERTY && selected != null) {
            RebornAddonNetwork.sendJutsuAdminAction(1, selected.id, selectedProperty(), "");
        } else if (button.id == RESET_ENTRY && selected != null) {
            RebornAddonNetwork.sendJutsuAdminAction(1, selected.id, "all", "");
        } else if (button.id == RESET_ALL) {
            if (resetAllArmed) {
                RebornAddonNetwork.sendJutsuAdminAction(2, "", "", "");
                resetAllArmed = false;
            } else {
                resetAllArmed = true;
                button.displayString = text("gui.rebornaddon.jutsu.confirm_reset_all", "Confirm Reset All");
            }
        } else if (button.id == DONE) {
            mc.displayGuiScreen(new GuiHub());
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

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (!"enabled".equals(selectedProperty())) {
            valueField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            page = 0;
            rebuildButtons();
            return;
        }
        if (!"enabled".equals(selectedProperty()) && valueField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(panelX, panelY, panelWidth, panelHeight, Theme.TEAL);
        GuiChrome.header(panelX + 2, panelY + 5, panelWidth - 4, 42, Theme.TEAL);
        fontRenderer.drawString(text("gui.rebornaddon.admin.title", "Server Administration"),
                panelX + 16, panelY + 17, Theme.TEXT_LIGHT);
        if (notice.length() > 0) {
            fontRenderer.drawString(trim(notice, Math.max(80, panelWidth - leftWidth - 54)),
                    panelX + leftWidth + 16, panelY + 31, Theme.GOLD);
        }

        drawLeftPanel();
        drawEditor();
        drawScaledControls(mouseX, mouseY, partialTicks);
        searchField.drawTextBox();
        if (selected != null && !"enabled".equals(selectedProperty())) {
            valueField.drawTextBox();
        }
    }

    private void drawLeftPanel() {
        int bodyHeight = panelHeight - 108;
        GuiChrome.section(panelX + 8, panelY + 51, leftWidth - 8, bodyHeight, Theme.TEAL);
        fontRenderer.drawString(text("gui.rebornaddon.jutsu.search", "Search"),
                panelX + 15, panelY + 49, Theme.TEXT_MUTED);
        int totalPages = Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
        String pages = (Math.min(page + 1, totalPages)) + " / " + totalPages;
        fontRenderer.drawString(pages, panelX + leftWidth - 14 - fontRenderer.getStringWidth(pages),
                panelY + panelHeight - 57, Theme.TEXT_MUTED);
    }

    private void drawEditor() {
        int top = panelY + 51;
        int height = panelHeight - 108;
        GuiChrome.section(rightX, top, rightWidth - 8, height, Theme.GOLD);
        if (selected == null) {
            String empty = text("gui.rebornaddon.jutsu.empty", "No configurable jutsus match these filters.");
            fontRenderer.drawSplitString(empty, rightX + 16, top + 25, rightWidth - 40, Theme.TEXT_MUTED);
            return;
        }

        fontRenderer.drawString(trim(selected.displayName, rightWidth - 38),
                rightX + 14, top + 14, Theme.TEXT_LIGHT);
        fontRenderer.drawString(trim(selected.id, rightWidth - 38),
                rightX + 14, top + 29, Theme.TEXT_MUTED);
        if (selected.parent.length() > 0) {
            fontRenderer.drawString(trim(text("gui.rebornaddon.jutsu.stage", "Stage") + ": "
                            + selected.parent, rightWidth - 38),
                    rightX + 14, top + 43, Theme.GOLD);
        }
        GuiChrome.rule(rightX + 14, rightX + rightWidth - 22, top + 57, Theme.GOLD);

        String property = selectedProperty();
        fontRenderer.drawString(text("gui.rebornaddon.jutsu.property", "Property"),
                rightX + 14, top + 66, Theme.TEXT_MUTED);
        fontRenderer.drawString(text("gui.rebornaddon.jutsu.value", "Value"),
                rightX + 14, top + 91, Theme.TEXT_MUTED);
        String original = selected.defaults.get(property);
        String current = selected.overrides.get(property);
        String effective = current == null ? original : current;
        String summary = text("gui.rebornaddon.jutsu.current", "Current") + ": "
                + JutsuValueParser.display(property, effective)
                + (current == null ? " (" + text("gui.rebornaddon.jutsu.mod_default", "mod default") + ")" : "");
        fontRenderer.drawString(trim(summary, rightWidth - 38),
                rightX + 14, top + 126, current == null ? Theme.TEXT_MUTED : Theme.TEAL_LIGHT);
    }

    private void rebuildButtons() {
        buttonList.clear();
        applyFilters();
        int totalPages = Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        addAdminTabs();

        int filterWidth = (leftWidth - 34) / 2;
        buttonList.add(button(SOURCE_FILTER, panelX + 14, panelY + 81, filterWidth, 20,
                sourceLabel(), Theme.TAB_INACTIVE_BG, Theme.TEAL));
        buttonList.add(button(STATE_FILTER, panelX + 20 + filterWidth, panelY + 81,
                filterWidth, 20, stateLabel(), Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));

        int start = page * rowsPerPage;
        int end = Math.min(filtered.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            Entry entry = filtered.get(i);
            ThemedButton row = button(ENTRY_BASE + i - start, panelX + 14,
                    listTop + (i - start) * 24, leftWidth - 28, 20,
                    entry.displayName, Theme.TAB_INACTIVE_BG, Theme.TEAL);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setSelected(selected != null && selected.id.equals(entry.id));
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

        if (selected != null && !selected.properties.isEmpty()) {
            String property = selectedProperty();
            buttonList.add(button(PROPERTY, rightX + 14, panelY + 120, rightWidth - 36, 20,
                    propertyLabel(property), Theme.TAB_INACTIVE_BG, Theme.GOLD_DARK));
            if ("enabled".equals(property)) {
                booleanValue = Boolean.parseBoolean(selected.effective(property));
                buttonList.add(button(BOOLEAN_VALUE, rightX + 14, panelY + 150,
                        rightWidth - 36, 20,
                        booleanValue ? text("gui.rebornaddon.jutsu.enabled", "Enabled")
                                : text("gui.rebornaddon.jutsu.disabled", "Disabled"),
                        booleanValue ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                        booleanValue ? Theme.TEAL : Theme.RANKED_RED));
            } else {
                String value = selected.effective(property);
                valueField.setText(value == null ? "" : JutsuValueParser.editable(property, value));
            }

            int actionY = panelY + 185;
            int actionWidth = Math.max(48, (rightWidth - 50) / 3);
            buttonList.add(button(SAVE, rightX + 14, actionY, actionWidth, 21,
                    text("gui.rebornaddon.jutsu.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
            buttonList.add(button(RESET_PROPERTY, rightX + 20 + actionWidth, actionY,
                    actionWidth, 21, text("gui.rebornaddon.jutsu.reset_property", "Reset Property"),
                    Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
            buttonList.add(button(RESET_ENTRY, rightX + 26 + actionWidth * 2, actionY,
                    actionWidth, 21, text("gui.rebornaddon.jutsu.reset_jutsu", "Reset Jutsu"),
                    Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        }

        int bottomY = panelY + panelHeight - 34;
        buttonList.add(button(RESET_ALL, rightX, bottomY, Math.min(150, rightWidth - 100), 22,
                resetAllArmed ? text("gui.rebornaddon.jutsu.confirm_reset_all", "Confirm Reset All")
                        : text("gui.rebornaddon.jutsu.reset_all", "Reset All"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        buttonList.add(button(DONE, panelX + (leftWidth - 78) / 2, bottomY, 78, 22,
                text("gui.rebornaddon.jutsu.cancel", "Cancel"), Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void addAdminTabs() {
        int width = Math.max(48, Math.min(70, (panelWidth - 150) / 3));
        int start = panelX + panelWidth - 16 - width * 3 - 6;
        ThemedButton jutsus = button(TAB_JUTSUS, start, panelY + 11, width, 22,
                text("gui.rebornaddon.admin.jutsus", "Jutsus"), Theme.TEAL_DARK, Theme.TEAL);
        jutsus.setSelected(true);
        buttonList.add(jutsus);
        buttonList.add(button(TAB_STORE, start + width + 3, panelY + 11, width, 22,
                text("gui.rebornaddon.admin.store", "Store"), Theme.TAB_INACTIVE_BG, Theme.COPPER));
        buttonList.add(button(TAB_LIMITS, start + (width + 3) * 2, panelY + 11, width, 22,
                text("gui.rebornaddon.admin.limits", "Limits"), Theme.TAB_INACTIVE_BG, Theme.GOLD));
    }

    private void applyFilters() {
        filtered.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String source = sourceIndex >= 0 && sourceIndex < sources.size() ? sources.get(sourceIndex) : "";
        for (Entry entry : entries) {
            if (source.length() > 0 && !source.equals(entry.source)) {
                continue;
            }
            boolean enabled = Boolean.parseBoolean(entry.effective("enabled"));
            if (stateFilter == 1 && !enabled || stateFilter == 2 && enabled) {
                continue;
            }
            if (query.length() > 0 && !entry.id.toLowerCase(Locale.ROOT).contains(query)
                    && !entry.displayName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            filtered.add(entry);
        }
    }

    private void saveProperty() {
        if (selected == null) {
            return;
        }
        String property = selectedProperty();
        String value = "enabled".equals(property) ? Boolean.toString(booleanValue) : valueField.getText();
        RebornAddonNetwork.sendJutsuAdminAction(0, selected.id, property, value);
    }

    private String selectedProperty() {
        if (selected == null || selected.properties.isEmpty()) {
            return "";
        }
        propertyIndex = Math.max(0, Math.min(propertyIndex, selected.properties.size() - 1));
        return selected.properties.get(propertyIndex);
    }

    private String sourceLabel() {
        String source = sourceIndex >= 0 && sourceIndex < sources.size() ? sources.get(sourceIndex) : "";
        return source.length() == 0 ? text("gui.rebornaddon.jutsu.all_mods", "All Mods") : source;
    }

    private String stateLabel() {
        return stateFilter == 1 ? text("gui.rebornaddon.jutsu.enabled", "Enabled")
                : stateFilter == 2 ? text("gui.rebornaddon.jutsu.disabled", "Disabled")
                : text("gui.rebornaddon.jutsu.all_states", "All States");
    }

    private String propertyLabel(String property) {
        String key = "gui.rebornaddon.jutsu.property." + property.replace('-', '_');
        String fallback = property.replace('-', ' ');
        return text(key, Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1));
    }

    private void parseSnapshot() {
        entries.clear();
        sources.clear();
        sources.add("");
        notice = snapshot.getString("Message");
        NBTTagList records = snapshot.getTagList("Records", 8);
        for (int i = 0; i < records.tagCount(); i++) {
            Entry entry = Entry.parse(records.getStringTagAt(i));
            if (entry == null) {
                continue;
            }
            entries.add(entry);
            if (!sources.contains(entry.source)) {
                sources.add(entry.source);
            }
        }
        Collections.sort(entries, Comparator.comparing(Entry::sortKey));
        Collections.sort(sources);
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        }
    }

    private Entry find(String id) {
        for (Entry entry : entries) {
            if (entry.id.equals(id)) return entry;
        }
        return null;
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

    private static final class Entry {
        private final String id;
        private final String displayName;
        private final String source;
        private final String parent;
        private final List<String> properties;
        private final Map<String, String> defaults;
        private final Map<String, String> overrides;

        private Entry(String id, String displayName, String source, String parent,
                      List<String> properties, Map<String, String> defaults,
                      Map<String, String> overrides) {
            this.id = id;
            this.displayName = displayName;
            this.source = source;
            this.parent = parent;
            this.properties = properties;
            this.defaults = defaults;
            this.overrides = overrides;
        }

        private String effective(String property) {
            String value = overrides.get(property);
            if (value == null) value = defaults.get(property);
            return value == null ? "" : value;
        }

        private String sortKey() {
            return source + "\n" + displayName.toLowerCase(Locale.ROOT) + "\n" + id;
        }

        private static Entry parse(String record) {
            String[] fields = record == null ? new String[0] : record.split("\\t", -1);
            if (fields.length < 5 || fields[0].length() == 0) return null;
            List<String> properties = new ArrayList<String>();
            for (String property : fields[4].split(",")) {
                if (property.length() > 0) properties.add(property);
            }
            return new Entry(fields[0], fields[1], fields[2], fields[3], properties,
                    values(fields.length > 5 ? fields[5] : ""),
                    values(fields.length > 6 ? fields[6] : ""));
        }

        private static Map<String, String> values(String text) {
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (String part : text.split(";")) {
                int separator = part.indexOf('=');
                if (separator > 0) {
                    values.put(part.substring(0, separator), part.substring(separator + 1));
                }
            }
            return values;
        }
    }
}
