package net.rebornaddon.gui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gameplay.client.ClientGameplayData;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class LuckPermsAdminPanel {
    private static final int PLAYER_PREVIOUS = 3500;
    private static final int PLAYER_NEXT = 3501;
    private static final int GROUP_PREVIOUS = 3502;
    private static final int GROUP_NEXT = 3503;
    private static final int ADD_PARENT = 3504;
    private static final int REMOVE_PARENT = 3505;
    private static final int REFRESH = 3506;
    private static final int PLAYER_ROW_BASE = 3600;
    private static final int GROUP_ROW_BASE = 3700;

    private GuiTextField playerSearch;
    private GuiTextField groupSearch;
    private int playerPage;
    private int groupPage;
    private int revision = -1;
    private boolean refreshButtons;
    private String selectedPlayerId = "";
    private String selectedGroupId = "";
    private JsonObject snapshot = new JsonObject();
    private List<JsonObject> visiblePlayers = Collections.emptyList();
    private List<JsonObject> visibleGroups = Collections.emptyList();
    private int left;
    private int bodyTop;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int bottom;

    void build(List<GuiButton> buttons, int left, int bodyTop, int leftWidth,
               int rightX, int rightWidth, int bottom) {
        this.left = left;
        this.bodyTop = bodyTop;
        this.leftWidth = leftWidth;
        this.rightX = rightX;
        this.rightWidth = rightWidth;
        this.bottom = bottom;
        syncData();
        prepareFields();
        request(false);

        List<JsonObject> players = filteredPlayers();
        visiblePlayers = players;
        int playerRows = playerRows();
        int playerPages = pages(players.size(), playerRows);
        playerPage = clampPage(playerPage, playerPages);
        int playerStart = playerPage * playerRows;
        for (int i = 0; i < playerRows && playerStart + i < players.size(); i++) {
            JsonObject player = players.get(playerStart + i);
            String id = string(player, "uuid", "");
            String name = string(player, "name", "Unknown player");
            String state = bool(player, "online", false)
                    ? text("gui.rebornaddon.luckperms.online", "Online")
                    : text("gui.rebornaddon.luckperms.offline", "Offline");
            ThemedButton row = button(PLAYER_ROW_BASE + i, left + 7,
                    bodyTop + 45 + i * 21, leftWidth - 14, 18,
                    trim(font(), name + "  |  " + state, leftWidth - 28),
                    id.equals(selectedPlayerId) ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.TEAL);
            row.setSelected(id.equals(selectedPlayerId));
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(7);
            buttons.add(row);
        }

        int playerPagerY = bottom - 21;
        ThemedButton playerPrevious = button(PLAYER_PREVIOUS, left + 7, playerPagerY,
                29, 18, "<", Theme.TAB_INACTIVE_BG, Theme.TEAL);
        ThemedButton playerNext = button(PLAYER_NEXT, left + 40, playerPagerY,
                29, 18, ">", Theme.TAB_INACTIVE_BG, Theme.TEAL);
        playerPrevious.enabled = playerPage > 0;
        playerNext.enabled = playerPage + 1 < playerPages;
        buttons.add(playerPrevious);
        buttons.add(playerNext);

        List<JsonObject> groups = filteredGroups();
        visibleGroups = groups;
        int groupRows = groupRows();
        int groupPages = pages(groups.size(), groupRows);
        groupPage = clampPage(groupPage, groupPages);
        int groupStart = groupPage * groupRows;
        for (int i = 0; i < groupRows && groupStart + i < groups.size(); i++) {
            JsonObject group = groups.get(groupStart + i);
            String id = groupId(group);
            String name = string(group, "displayName", id);
            boolean assigned = hasParent(selectedPlayer(), id);
            String label = assigned
                    ? name + "  [" + text("gui.rebornaddon.luckperms.assigned", "Assigned") + "]"
                    : name;
            ThemedButton row = button(GROUP_ROW_BASE + i, rightX + 10,
                    bodyTop + 61 + i * 21, rightWidth - 20, 18,
                    trim(font(), label, rightWidth - 34),
                    id.equals(selectedGroupId) ? Theme.PURPLE_DARK
                            : assigned ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                    assigned ? Theme.TEAL : Theme.PURPLE_LIGHT);
            row.setSelected(id.equals(selectedGroupId));
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(7);
            buttons.add(row);
        }

        int groupPagerY = bottom - 46;
        ThemedButton groupPrevious = button(GROUP_PREVIOUS, rightX + 10, groupPagerY,
                29, 18, "<", Theme.TAB_INACTIVE_BG, Theme.PURPLE_LIGHT);
        ThemedButton groupNext = button(GROUP_NEXT, rightX + 43, groupPagerY,
                29, 18, ">", Theme.TAB_INACTIVE_BG, Theme.PURPLE_LIGHT);
        groupPrevious.enabled = groupPage > 0;
        groupNext.enabled = groupPage + 1 < groupPages;
        buttons.add(groupPrevious);
        buttons.add(groupNext);

        JsonObject root = data();
        JsonObject permissions = object(root.get("permissions"));
        boolean manage = bool(permissions, "manageParents", bool(permissions, "manage", false));
        boolean ready = bool(root, "available", false) && manage
                && !selectedPlayerId.isEmpty() && !selectedGroupId.isEmpty();
        boolean assigned = hasParent(selectedPlayer(), selectedGroupId);
        int actionWidth = Math.max(48, (rightWidth - 26) / 3);
        int actionY = bottom - 22;
        ThemedButton add = button(ADD_PARENT, rightX + 10, actionY, actionWidth, 19,
                text("gui.rebornaddon.luckperms.add", "Add Parent"),
                Theme.TEAL_DARK, Theme.TEAL);
        ThemedButton remove = button(REMOVE_PARENT, rightX + 13 + actionWidth,
                actionY, actionWidth, 19,
                text("gui.rebornaddon.luckperms.remove", "Remove Parent"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        ThemedButton refresh = button(REFRESH, rightX + 16 + actionWidth * 2,
                actionY, rightWidth - 26 - actionWidth * 2, 19,
                text("gui.rebornaddon.luckperms.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.GOLD);
        add.enabled = ready && !assigned;
        remove.enabled = ready && assigned;
        buttons.add(add);
        buttons.add(remove);
        buttons.add(refresh);
    }

    void draw(FontRenderer font) {
        font.drawString(text("gui.rebornaddon.luckperms.players", "Players"),
                left + 8, bodyTop + 7, Theme.GOLD);
        font.drawString(text("gui.rebornaddon.luckperms.groups", "Parent Groups"),
                rightX + 10, bodyTop + 7, Theme.GOLD);
        if (playerSearch != null) playerSearch.drawTextBox();
        if (groupSearch != null) groupSearch.drawTextBox();

        JsonObject root = data();
        String message = string(root, "message",
                text("gui.rebornaddon.luckperms.loading", "Loading private LuckPerms data..."));
        font.drawString(trim(font, message, rightWidth - 20), rightX + 10,
                bodyTop + 45, bool(root, "available", false) ? Theme.TEXT_MUTED : Theme.RANKED_RED);

        JsonObject player = selectedPlayer();
        String selected = player.size() == 0
                ? text("gui.rebornaddon.luckperms.no_player", "Choose a player to manage.")
                : string(player, "name", "Unknown player") + "  |  "
                + parentCount(player) + " parent" + (parentCount(player) == 1 ? "" : "s");
        font.drawString(trim(font, selected, rightWidth - 20), rightX + 10,
                bottom - 64, Theme.TEXT_LIGHT);
        GuiChrome.rule(rightX + 10, rightX + rightWidth - 10, bottom - 53, Theme.TEAL);

        String playerPageLabel = (playerPage + 1) + "/" + pages(visiblePlayers.size(), playerRows());
        font.drawString(playerPageLabel, left + 76, bottom - 16, Theme.TEXT_MUTED);
        String groupPageLabel = (groupPage + 1) + "/" + pages(visibleGroups.size(), groupRows());
        font.drawString(groupPageLabel, rightX + 78, bottom - 41, Theme.TEXT_MUTED);
    }

    boolean handleButton(int id) {
        List<JsonObject> players = visiblePlayers;
        List<JsonObject> groups = visibleGroups;
        if (id >= PLAYER_ROW_BASE && id < PLAYER_ROW_BASE + playerRows()) {
            int index = playerPage * playerRows() + id - PLAYER_ROW_BASE;
            if (index < players.size()) {
                selectedPlayerId = string(players.get(index), "uuid", "");
                groupPage = 0;
            }
            return true;
        }
        if (id >= GROUP_ROW_BASE && id < GROUP_ROW_BASE + groupRows()) {
            int index = groupPage * groupRows() + id - GROUP_ROW_BASE;
            if (index < groups.size()) selectedGroupId = groupId(groups.get(index));
            return true;
        }
        if (id == PLAYER_PREVIOUS || id == PLAYER_NEXT) {
            playerPage = clampPage(playerPage + (id == PLAYER_PREVIOUS ? -1 : 1),
                    pages(players.size(), playerRows()));
            return true;
        }
        if (id == GROUP_PREVIOUS || id == GROUP_NEXT) {
            groupPage = clampPage(groupPage + (id == GROUP_PREVIOUS ? -1 : 1),
                    pages(groups.size(), groupRows()));
            return true;
        }
        if (id == REFRESH) {
            request(true);
            return false;
        }
        if (id == ADD_PARENT || id == REMOVE_PARENT) {
            send(id == ADD_PARENT ? "luckperms.parent.add" : "luckperms.parent.remove");
            return false;
        }
        return false;
    }

    void keyTyped(char character, int keyCode) {
        String previousPlayer = playerSearch == null ? "" : playerSearch.getText();
        String previousGroup = groupSearch == null ? "" : groupSearch.getText();
        if (playerSearch != null) playerSearch.textboxKeyTyped(character, keyCode);
        if (groupSearch != null) groupSearch.textboxKeyTyped(character, keyCode);
        if (playerSearch != null && !previousPlayer.equals(playerSearch.getText())) {
            playerPage = 0;
            refreshButtons = true;
        }
        if (groupSearch != null && !previousGroup.equals(groupSearch.getText())) {
            groupPage = 0;
            refreshButtons = true;
        }
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
        if (playerSearch != null) playerSearch.mouseClicked(mouseX, mouseY, button);
        if (groupSearch != null) groupSearch.mouseClicked(mouseX, mouseY, button);
    }

    boolean tick() {
        if (playerSearch != null) playerSearch.updateCursorCounter();
        if (groupSearch != null) groupSearch.updateCursorCounter();
        if (syncData()) {
            refreshButtons = true;
        }
        request(false);
        boolean result = refreshButtons;
        refreshButtons = false;
        return result;
    }

    private void send(String action) {
        JsonObject player = selectedPlayer();
        JsonObject payload = new JsonObject();
        payload.addProperty("targetUuid", selectedPlayerId);
        payload.addProperty("targetName", string(player, "name", ""));
        payload.addProperty("group", selectedGroupId);
        RebornAddonNetwork.sendGameplayAction("luckperms", action, payload.toString());
    }

    private void request(boolean force) {
        if (!force && !ClientGameplayData.shouldRequest("luckperms")) return;
        RebornAddonNetwork.requestGameplaySnapshot("luckperms", force);
    }

    private boolean syncData() {
        int current = ClientGameplayData.revision("luckperms");
        if (current == revision) return false;
        revision = current;
        snapshot = ClientGameplayData.get("luckperms");
        clearMissingSelections();
        return true;
    }

    private void prepareFields() {
        FontRenderer font = font();
        playerSearch = recreate(playerSearch, 3800, font, left + 7, bodyTop + 23,
                leftWidth - 14, 18, 64);
        groupSearch = recreate(groupSearch, 3801, font, rightX + 10, bodyTop + 23,
                rightWidth - 20, 18, 64);
    }

    private void clearMissingSelections() {
        if (find(dataArray("players"), "uuid", selectedPlayerId).size() == 0) selectedPlayerId = "";
        if (findGroup(dataArray("groups"), selectedGroupId).size() == 0) selectedGroupId = "";
    }

    private List<JsonObject> filteredPlayers() {
        List<JsonObject> result = filtered(dataArray("players"),
                playerSearch == null ? "" : playerSearch.getText(), false);
        Collections.sort(result, new Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject first, JsonObject second) {
                boolean firstOnline = bool(first, "online", false);
                boolean secondOnline = bool(second, "online", false);
                if (firstOnline != secondOnline) return firstOnline ? -1 : 1;
                return string(first, "name", "").compareToIgnoreCase(string(second, "name", ""));
            }
        });
        return result;
    }

    private List<JsonObject> filteredGroups() {
        List<JsonObject> result = filtered(dataArray("groups"),
                groupSearch == null ? "" : groupSearch.getText(), true);
        Collections.sort(result, new Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject first, JsonObject second) {
                return groupId(first).compareToIgnoreCase(groupId(second));
            }
        });
        return result;
    }

    private static List<JsonObject> filtered(JsonArray values, String query, boolean group) {
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<JsonObject> result = new ArrayList<JsonObject>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String searchable = group
                    ? groupId(value) + ' ' + string(value, "displayName", "")
                    : string(value, "name", "") + ' ' + string(value, "uuid", "");
            if (filter.isEmpty() || searchable.toLowerCase(Locale.ROOT).contains(filter)) result.add(value);
        }
        return result;
    }

    private JsonObject selectedPlayer() {
        return find(dataArray("players"), "uuid", selectedPlayerId);
    }

    private static boolean hasParent(JsonObject player, String group) {
        if (player == null || group == null || group.isEmpty()) return false;
        JsonArray parents = player.has("parents") && player.get("parents").isJsonArray()
                ? player.getAsJsonArray("parents") : new JsonArray();
        for (JsonElement element : parents) {
            String id = element.isJsonPrimitive() ? element.getAsString()
                    : element.isJsonObject() ? groupId(element.getAsJsonObject()) : "";
            if (group.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private static int parentCount(JsonObject player) {
        return player != null && player.has("parents") && player.get("parents").isJsonArray()
                ? player.getAsJsonArray("parents").size() : 0;
    }

    private int playerRows() {
        return Math.max(3, (bottom - bodyTop - 69) / 21);
    }

    private int groupRows() {
        return Math.max(2, (bottom - bodyTop - 112) / 21);
    }

    private JsonObject data() {
        return snapshot;
    }

    private JsonArray dataArray(String key) {
        JsonObject root = data();
        return root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject find(JsonArray values, String key, String wanted) {
        if (wanted == null || wanted.isEmpty()) return new JsonObject();
        for (JsonElement element : values) {
            if (element.isJsonObject()) {
                JsonObject value = element.getAsJsonObject();
                if (wanted.equalsIgnoreCase(string(value, key, ""))) return value;
            }
        }
        return new JsonObject();
    }

    private static JsonObject findGroup(JsonArray values, String wanted) {
        if (wanted == null || wanted.isEmpty()) return new JsonObject();
        for (JsonElement element : values) {
            if (element.isJsonObject() && wanted.equalsIgnoreCase(groupId(element.getAsJsonObject()))) {
                return element.getAsJsonObject();
            }
        }
        return new JsonObject();
    }

    private static String groupId(JsonObject group) {
        String id = string(group, "id", "");
        return id.isEmpty() ? string(group, "name", "") : id;
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject value, String key, String fallback) {
        try { return value != null && value.has(key) ? value.get(key).getAsString() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean bool(JsonObject value, String key, boolean fallback) {
        try { return value != null && value.has(key) ? value.get(key).getAsBoolean() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static GuiTextField recreate(GuiTextField old, int id, FontRenderer font,
                                         int x, int y, int width, int height, int maximum) {
        String value = old == null ? "" : old.getText();
        boolean focused = old != null && old.isFocused();
        int cursor = old == null ? 0 : old.getCursorPosition();
        GuiTextField field = new GuiTextField(id, font, x, y, Math.max(24, width), height);
        field.setMaxStringLength(maximum);
        field.setText(value);
        field.setFocused(focused);
        field.setCursorPosition(Math.min(cursor, field.getText().length()));
        return field;
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int background, int hover) {
        return new ThemedButton(id, x, y, Math.max(20, width), Math.max(10, height), label,
                background, hover, Theme.BUTTON_TEXT);
    }

    private static int pages(int size, int rows) {
        return Math.max(1, (size + Math.max(1, rows) - 1) / Math.max(1, rows));
    }

    private static int clampPage(int page, int pages) {
        return Math.max(0, Math.min(page, Math.max(1, pages) - 1));
    }

    private static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    private static String trim(FontRenderer font, String value, int maximumWidth) {
        if (value == null || font.getStringWidth(value) <= maximumWidth) return value == null ? "" : value;
        return font.trimStringToWidth(value, Math.max(8, maximumWidth - font.getStringWidth("..."))) + "...";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
