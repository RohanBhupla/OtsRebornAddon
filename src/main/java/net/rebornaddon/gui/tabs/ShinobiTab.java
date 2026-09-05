package net.rebornaddon.gui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gameplay.client.ClientGameplayData;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.List;

public final class ShinobiTab implements HubTab {
    private static final int SECTION_BASE = 2300;
    private static final int ROW_BASE = 2320;
    private static final int PREVIOUS = 2380;
    private static final int NEXT = 2381;
    private static final int ACTION = 2382;
    private static final int RESET_TRAINING = 2383;
    private static final String[] SECTIONS = {"codex", "training", "reputation", "missions",
            "conflicts", "spectating"};
    private static final String[] NAMES = {"Codex", "Training", "Reputation", "Missions",
            "Events", "Spectate"};
    private int selected;
    private int selectedRow = -1;
    private int page;
    private int revision = -1;
    private boolean refresh;
    private int left;
    private int top;
    private int width;
    private int height;

    @Override
    public String getTabName() {
        return text("gui.rebornaddon.tab.shinobi", "Shinobi");
    }

    @Override
    public int getTabColorActive() { return Theme.GOLD_DARK; }

    @Override
    public int getTabColorHover() { return Theme.GOLD; }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        int tabWidth = Math.max(46, (width - 12) / SECTIONS.length);
        for (int i = 0; i < SECTIONS.length; i++) {
            ThemedButton button = button(SECTION_BASE + i, left + i * tabWidth, top,
                    i == SECTIONS.length - 1 ? width - i * tabWidth : tabWidth - 2, 21,
                    text("gui.rebornaddon.shinobi." + SECTIONS[i], NAMES[i]),
                    selected == i ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG, Theme.GOLD);
            button.setSelected(selected == i);
            buttons.add(button);
        }
        JsonArray entries = entries();
        int rows = rowsPerPage();
        int start = page * rows;
        if (start >= entries.size() && page > 0) {
            page = Math.max(0, (entries.size() - 1) / rows);
            start = page * rows;
        }
        for (int i = 0; i < rows && start + i < entries.size(); i++) {
            JsonObject entry = object(entries.get(start + i));
            ThemedButton row = button(ROW_BASE + i, left + 10, top + 77 + i * 31,
                    width - 20, 27, string(entry, "title", "Entry"),
                    selectedRow == start + i ? Theme.PURPLE_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.GOLD);
            row.setSelected(selectedRow == start + i);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(9);
            buttons.add(row);
        }
        int bottom = top + height - 23;
        buttons.add(button(PREVIOUS, left + 10, bottom, 30, 19, "<",
                Theme.TAB_INACTIVE_BG, Theme.GOLD));
        buttons.add(button(NEXT, left + 44, bottom, 30, 19, ">",
                Theme.TAB_INACTIVE_BG, Theme.GOLD));
        JsonObject chosen = selectedEntry();
        String action = string(chosen, "action", "");
        if (!action.isEmpty()) {
            buttons.add(button(ACTION, left + width - 166, bottom, 156, 19,
                    string(chosen, "actionLabel", "Open"), Theme.TEAL_DARK, Theme.TEAL));
        } else if (selected == 1) {
            buttons.add(button(RESET_TRAINING, left + width - 166, bottom, 156, 19,
                    text("gui.rebornaddon.training.reset", "Reset Combat Recap"),
                    Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        }
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        JsonObject data = data();
        GuiChrome.header(left, top + 27, width, 42, Theme.GOLD_DARK);
        font.drawString(string(data, "title", NAMES[selected]), left + 12, top + 36, Theme.TEXT_LIGHT);
        font.drawString(trim(font, string(data, "message", "Loading..."), width - 24),
                left + 12, top + 52, bool(data, "available", false) ? Theme.TEXT_MUTED : Theme.GOLD);
        JsonArray metrics = array(data, "metrics");
        int metricX = left + width - 12;
        for (int i = Math.min(4, metrics.size()) - 1; i >= 0; i--) {
            JsonObject metric = object(metrics.get(i));
            String value = string(metric, "label", "") + ": " + string(metric, "value", "");
            metricX -= font.getStringWidth(value);
            font.drawString(value, metricX, top + 36, Theme.GOLD);
            metricX -= 14;
        }
        JsonObject chosen = selectedEntry();
        if (chosen.size() > 0) {
            String detail = string(chosen, "detail", string(chosen, "subtitle", ""));
            font.drawSplitString(detail, left + 13, top + height - 52, width - 190, Theme.TEXT_MUTED);
        } else if (entries().size() == 0) {
            font.drawString(text("gui.rebornaddon.shinobi.empty", "No entries are available."),
                    left + 12, top + 87, Theme.TEXT_MUTED);
        }
    }

    @Override
    public boolean handleButtonClick(int id) {
        if (id >= SECTION_BASE && id < SECTION_BASE + SECTIONS.length) {
            selected = id - SECTION_BASE;
            selectedRow = -1;
            page = 0;
            request(true);
            return true;
        }
        if (id == PREVIOUS || id == NEXT) {
            page = Math.max(0, page + (id == PREVIOUS ? -1 : 1));
            return true;
        }
        if (id >= ROW_BASE && id < ROW_BASE + rowsPerPage()) {
            selectedRow = page * rowsPerPage() + id - ROW_BASE;
            return true;
        }
        if (id == ACTION) {
            JsonObject entry = selectedEntry();
            RebornAddonNetwork.sendGameplayAction(SECTIONS[selected], string(entry, "action", ""),
                    "{\"id\":\"" + escape(string(entry, "id", "")) + "\"}");
            return false;
        }
        if (id == RESET_TRAINING) {
            RebornAddonNetwork.sendGameplayAction("training", "reset-recap", "{}");
            return false;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (revision != ClientGameplayData.revision(SECTIONS[selected])) {
            revision = ClientGameplayData.revision(SECTIONS[selected]);
            refresh = true;
        }
        request(false);
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean result = refresh;
        refresh = false;
        return result;
    }

    private void request(boolean force) {
        if (force || ClientGameplayData.shouldRequest(SECTIONS[selected])) {
            RebornAddonNetwork.requestGameplaySnapshot(SECTIONS[selected]);
        }
    }

    private int rowsPerPage() { return Math.max(2, (height - 155) / 31); }
    private JsonObject data() { return ClientGameplayData.get(SECTIONS[selected]); }
    private JsonArray entries() { return array(data(), "entries"); }

    private JsonObject selectedEntry() {
        JsonArray values = entries();
        return selectedRow >= 0 && selectedRow < values.size()
                ? object(values.get(selectedRow)) : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsBoolean() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int base, int hover) {
        return new ThemedButton(id, x, y, width, height, label, base, hover, Theme.BUTTON_TEXT);
    }

    private static String trim(FontRenderer font, String value, int width) {
        return font.trimStringToWidth(value == null ? "" : value, Math.max(10, width));
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
