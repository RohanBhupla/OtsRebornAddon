package net.rebornaddon.gui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.content.client.ClientNativeContentData;
import net.rebornaddon.content.client.GuiNativeRecordEditor;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.List;

public final class WorldEditorTab implements HubTab {
    private static final int SECTION_BASE = 2100;
    private static final int ROW_BASE = 2200;
    private static final int PREVIOUS = 2290;
    private static final int NEXT = 2291;
    private static final int CREATE = 2292;
    private static final int EDIT = 2293;
    private static final int DUPLICATE = 2294;
    private static final int DELETE = 2295;
    private static final int REFRESH = 2296;
    private static final int VALIDATE = 2297;
    private static final int PUBLISH = 2298;
    private static final int ROLLBACK = 2299;
    private static final String[] TYPES = {
            "templates", "actors", "quests", "bosses", "dungeons",
            "paths", "worldBossPools"
    };

    private int section;
    private int page;
    private int selectedIndex = -1;
    private int observedRevision = -1;
    private int left;
    private int top;
    private int width;
    private int height;
    private int rowsPerPage;
    private boolean deleteArmed;
    private boolean rollbackArmed;
    private boolean refreshButtons;

    @Override
    public String getTabName() {
        return text("gui.rebornaddon.tab.world_editor", "World Editor");
    }

    @Override
    public int getTabColorActive() {
        return Theme.GOLD;
    }

    @Override
    public int getTabColorHover() {
        return Theme.GOLD;
    }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        if (observedRevision < 0) {
            requestSnapshot(false);
        }
        int columns = width >= 470 ? 4 : 2;
        int gap = 3;
        int tabWidth = Math.max(54, (width - gap * (columns - 1)) / columns);
        for (int i = 0; i < TYPES.length; i++) {
            int row = i / columns;
            int column = i % columns;
            ThemedButton button = button(SECTION_BASE + i, left + column * (tabWidth + gap),
                    top + row * 22, tabWidth, 19, sectionLabel(i),
                    i == section ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG, Theme.GOLD);
            button.setSelected(i == section);
            buttons.add(button);
        }
        int headerRows = (TYPES.length + columns - 1) / columns;
        int listTop = top + headerRows * 22 + 38;
        int lifecycleTop = top + height - 68;
        int actionTop = top + height - 44;
        rowsPerPage = Math.max(3, (lifecycleTop - listTop - 2) / 22);
        List<JsonObject> records = records();
        int pages = Math.max(1, (records.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(records.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            JsonObject record = records.get(i);
            ThemedButton row = button(ROW_BASE + i - start, left + 7,
                    listTop + (i - start) * 22, width - 14, 19,
                    recordLabel(record), Theme.TAB_INACTIVE_BG, Theme.GOLD);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(8);
            row.setSelected(i == selectedIndex);
            buttons.add(row);
        }
        ThemedButton previous = button(PREVIOUS, left + 7, actionTop, 24, 19,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttons.add(previous);
        ThemedButton next = button(NEXT, left + 34, actionTop, 24, 19,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < pages;
        buttons.add(next);
        int actionWidth = Math.max(42, (width - 75) / 5);
        int x = left + 65;
        buttons.add(button(CREATE, x, actionTop, actionWidth, 19,
                text("gui.rebornaddon.world.create", "New"), Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(EDIT, x + actionWidth + 3, actionTop, actionWidth, 19,
                text("gui.rebornaddon.world.edit", "Edit"), Theme.TAB_INACTIVE_BG, Theme.GOLD));
        buttons.add(button(DUPLICATE, x + (actionWidth + 3) * 2, actionTop, actionWidth, 19,
                text("gui.rebornaddon.world.duplicate", "Duplicate"), Theme.TAB_INACTIVE_BG, Theme.GOLD));
        buttons.add(button(DELETE, x + (actionWidth + 3) * 3, actionTop, actionWidth, 19,
                deleteArmed ? text("gui.rebornaddon.world.confirm", "Confirm")
                        : text("gui.rebornaddon.world.delete", "Delete"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        int refreshX = x + (actionWidth + 3) * 4;
        buttons.add(button(REFRESH, refreshX, actionTop,
                Math.max(34, left + width - refreshX), 19,
                text("gui.rebornaddon.world.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        int lifecycleWidth = Math.max(56, (width - 20) / 3);
        buttons.add(button(VALIDATE, left + 7, lifecycleTop, lifecycleWidth, 19,
                text("gui.rebornaddon.world.validate", "Validate Draft"),
                Theme.TAB_INACTIVE_BG, Theme.GOLD));
        buttons.add(button(PUBLISH, left + 10 + lifecycleWidth, lifecycleTop, lifecycleWidth, 19,
                text("gui.rebornaddon.world.publish", "Publish Draft"),
                Theme.TEAL_DARK, Theme.TEAL));
        buttons.add(button(ROLLBACK, left + 13 + lifecycleWidth * 2, lifecycleTop,
                Math.max(48, width - 20 - lifecycleWidth * 2), 19,
                rollbackArmed ? text("gui.rebornaddon.world.confirm_rollback", "Confirm Rollback")
                        : text("gui.rebornaddon.world.rollback", "Rollback"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int columns = width >= 470 ? 4 : 2;
        int headerRows = (TYPES.length + columns - 1) / columns;
        int bodyTop = top + headerRows * 22 + 5;
        GuiChrome.section(left, bodyTop, width, Math.max(40, height - headerRows * 22 - 50), Theme.GOLD);
        font.drawString(sectionLabel(section), left + 13, bodyTop + 9, Theme.TEXT_LIGHT);
        font.drawString(font.trimStringToWidth(sectionDescription(section), width - 26),
                left + 13, bodyTop + 23, Theme.TEXT_MUTED);
        JsonObject wrapper = ClientNativeContentData.admin();
        if (!wrapper.has("success") || !wrapper.get("success").getAsBoolean()) {
            String message = wrapper.has("message") ? wrapper.get("message").getAsString() : "";
            font.drawString(message.isEmpty()
                            ? text("gui.rebornaddon.world.loading", "Loading private server content...")
                            : font.trimStringToWidth(message, width - 26),
                    left + 13, bodyTop + 42, message.isEmpty() ? Theme.TEXT_MUTED : Theme.DANGER_BRIGHT);
        } else if (records().isEmpty()) {
            font.drawString(text("gui.rebornaddon.world.empty", "No entries in this section."),
                    left + 13, bodyTop + 42, Theme.TEXT_MUTED);
        }
        String status = wrapper.has("message") ? wrapper.get("message").getAsString() : "";
        if (!status.isEmpty()) {
            font.drawString(font.trimStringToWidth(status, width - 18),
                    left + 9, top + height - 15, Theme.TEXT_MUTED);
        }
    }

    @Override
    public boolean handleButtonClick(int id) {
        if (id >= SECTION_BASE && id < SECTION_BASE + TYPES.length) {
            section = id - SECTION_BASE;
            page = 0;
            selectedIndex = -1;
            deleteArmed = false;
            rollbackArmed = false;
            return true;
        }
        if (id == PREVIOUS || id == NEXT) {
            page += id == PREVIOUS ? -1 : 1;
            return true;
        }
        if (id >= ROW_BASE && id < ROW_BASE + rowsPerPage) {
            selectedIndex = page * rowsPerPage + id - ROW_BASE;
            deleteArmed = false;
            rollbackArmed = false;
            return true;
        }
        if (id == REFRESH) {
            requestSnapshot(true);
            return true;
        }
        if (id == VALIDATE) {
            RebornAddonNetwork.sendNativeContentAction("admin.draft.validate", "{}");
            return false;
        }
        if (id == PUBLISH) {
            rollbackArmed = false;
            RebornAddonNetwork.sendNativeContentAction("admin.draft.publish", "{}");
            return false;
        }
        if (id == ROLLBACK) {
            if (!rollbackArmed) {
                rollbackArmed = true;
                return true;
            }
            RebornAddonNetwork.sendNativeContentAction("admin.draft.rollback", "{}");
            rollbackArmed = false;
            return false;
        }
        if (id == CREATE) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiNativeRecordEditor(TYPES[section], null, false));
            return false;
        }
        JsonObject selected = selected();
        if (selected == null) return false;
        if (id == EDIT || id == DUPLICATE) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new GuiNativeRecordEditor(TYPES[section], selected, id == DUPLICATE));
            return false;
        }
        if (id == DELETE) {
            if (!deleteArmed) {
                deleteArmed = true;
                return true;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("type", TYPES[section]);
            payload.addProperty("id", value(selected, "id"));
            RebornAddonNetwork.sendNativeContentAction("admin.draft.delete", payload.toString());
            deleteArmed = false;
            selectedIndex = -1;
            return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (observedRevision != ClientNativeContentData.revision()) {
            observedRevision = ClientNativeContentData.revision();
            refreshButtons = true;
        }
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean result = refreshButtons;
        refreshButtons = false;
        return result;
    }

    private void requestSnapshot(boolean force) {
        if (ClientNativeContentData.request(force)) {
            RebornAddonNetwork.sendNativeContentAction("admin.snapshot", "{}");
        }
    }

    private List<JsonObject> records() {
        List<JsonObject> result = new ArrayList<JsonObject>();
        JsonObject wrapper = ClientNativeContentData.admin();
        if (!wrapper.has("data") || !wrapper.get("data").isJsonObject()) return result;
        JsonObject data = wrapper.getAsJsonObject("data");
        if (!data.has(TYPES[section]) || !data.get(TYPES[section]).isJsonArray()) return result;
        JsonArray array = data.getAsJsonArray(TYPES[section]);
        for (JsonElement element : array) {
            if (element.isJsonObject()) result.add(element.getAsJsonObject());
        }
        return result;
    }

    private JsonObject selected() {
        List<JsonObject> values = records();
        return selectedIndex >= 0 && selectedIndex < values.size() ? values.get(selectedIndex) : null;
    }

    private String recordLabel(JsonObject object) {
        String id = value(object, "id");
        String name = value(object, "name");
        if (name.isEmpty()) name = value(object, "title");
        return name.isEmpty() ? id : name + (id.isEmpty() ? "" : "  [" + id + "]");
    }

    private String sectionLabel(int index) {
        String[] fallback = {"NPC Studio", "World NPCs", "Quest Builder", "Bosses",
                "Dungeons", "Pathing", "World Bosses"};
        return text("gui.rebornaddon.world." + TYPES[index], fallback[index]);
    }

    private String sectionDescription(int index) {
        String[] fallback = {
                "Create reusable NPC appearances, combat behavior, equipment, factions, and jutsu loadouts.",
                "Place configured NPCs in the world and control activation, leash, and respawn behavior.",
                "Build mission steps, objectives, dialogue, requirements, rewards, and progression.",
                "Configure persistent and world bosses, contribution rules, drops, and spawn locations.",
                "Create wave encounters or fixed NPC layouts with independent respawn timing.",
                "Build reusable NPC movement routes with per-point waits and movement speeds.",
                "Schedule randomized boss pools, weighted bosses, locations, and announcements."
        };
        return fallback[Math.max(0, Math.min(index, fallback.length - 1))];
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int base, int hover) {
        return new ThemedButton(id, x, y, Math.max(12, width), height,
                label, base, hover, Theme.BUTTON_TEXT);
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
