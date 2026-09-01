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
import java.util.List;
import java.util.Locale;

final class MountAdminPanel {
    private static final int PLAYER_PREVIOUS = 3000;
    private static final int PLAYER_NEXT = 3001;
    private static final int FORM_PREVIOUS = 3002;
    private static final int FORM_NEXT = 3003;
    private static final int TYPE_MOUNT = 3004;
    private static final int TYPE_COMPANION = 3005;
    private static final int FAVORITE = 3006;
    private static final int SELECT = 3007;
    private static final int GRANT = 3008;
    private static final int REVOKE = 3009;
    private static final int REFRESH = 3010;
    private static final int SAVE_SCALE = 3011;
    private static final int PLAYER_ROW_BASE = 3100;
    private static final int FORM_ROW_BASE = 3200;

    private GuiTextField playerSearch;
    private GuiTextField formSearch;
    private GuiTextField scaleField;
    private int playerPage;
    private int formPage;
    private int revision = -1;
    private boolean mount = true;
    private boolean favorite = true;
    private boolean select = true;
    private boolean refreshButtons;
    private long requestedAt;
    private String selectedPlayerId = "";
    private String selectedFormId = "";
    private String scaleContext = "";
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
        prepareFields();
        syncScaleField(false);
        request(false);

        List<JsonObject> players = filtered(dataArray("players"),
                playerSearch == null ? "" : playerSearch.getText());
        boolean compact = compact();
        int playerRows = playerRows();
        int playerPages = pages(players.size(), playerRows);
        playerPage = clampPage(playerPage, playerPages);
        int playerStart = playerPage * playerRows;
        for (int i = 0; i < playerRows && playerStart + i < players.size(); i++) {
            JsonObject player = players.get(playerStart + i);
            String id = string(player, "uuid", "");
            ThemedButton row = button(PLAYER_ROW_BASE + i, compact ? left + 39 : left + 7,
                    bodyTop + (compact ? 38 : 47) + i * 21,
                    compact ? leftWidth - 78 : leftWidth - 14, 18,
                    trim(font(), string(player, "name", "Unknown player"), leftWidth - 28),
                    id.equals(selectedPlayerId) ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.TEAL);
            row.setSelected(id.equals(selectedPlayerId));
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(7);
            buttons.add(row);
        }
        int playerPagerY = compact ? bodyTop + 38 : bottom - 21;
        ThemedButton playerPrevious = button(PLAYER_PREVIOUS, left + 7, playerPagerY,
                29, 18, "<", Theme.TAB_INACTIVE_BG, Theme.TEAL);
        ThemedButton playerNext = button(PLAYER_NEXT,
                compact ? left + leftWidth - 36 : left + 40, playerPagerY,
                29, 18, ">", Theme.TAB_INACTIVE_BG, Theme.TEAL);
        playerPrevious.enabled = playerPage > 0;
        playerNext.enabled = playerPage + 1 < playerPages;
        buttons.add(playerPrevious);
        buttons.add(playerNext);

        List<JsonObject> forms = filteredForms();
        int formRows = formRows();
        int formPages = pages(forms.size(), formRows);
        formPage = clampPage(formPage, formPages);
        int formStart = formPage * formRows;
        for (int i = 0; i < formRows && formStart + i < forms.size(); i++) {
            JsonObject form = forms.get(formStart + i);
            String id = string(form, "id", "");
            String label = string(form, "name", id);
            ThemedButton row = button(FORM_ROW_BASE + i, compact ? rightX + 42 : rightX + 10,
                    bodyTop + (compact ? 38 : 62) + i * 21,
                    compact ? rightWidth - 84 : rightWidth - 20, 18,
                    trim(font(), label + "  [" + id + "]", rightWidth - 34),
                    id.equals(selectedFormId) ? Theme.PURPLE_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.PURPLE_LIGHT);
            row.setSelected(id.equals(selectedFormId));
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(7);
            buttons.add(row);
        }

        int formPagerY = compact ? bodyTop + 38 : bottom - 125;
        ThemedButton formPrevious = button(FORM_PREVIOUS, rightX + 10, formPagerY,
                29, 18, "<", Theme.TAB_INACTIVE_BG, Theme.PURPLE_LIGHT);
        ThemedButton formNext = button(FORM_NEXT,
                compact ? rightX + rightWidth - 39 : rightX + 43, formPagerY,
                29, 18, ">", Theme.TAB_INACTIVE_BG, Theme.PURPLE_LIGHT);
        formPrevious.enabled = formPage > 0;
        formNext.enabled = formPage + 1 < formPages;
        buttons.add(formPrevious);
        buttons.add(formNext);

        int half = Math.max(46, (rightWidth - 23) / 2);
        int typeY = compact ? bodyTop + 58 : bottom - 105;
        int typeHeight = compact ? 18 : 19;
        buttons.add(selectedButton(TYPE_MOUNT, rightX + 10, typeY, half, typeHeight,
                text("gui.rebornaddon.mounts.type_mount", "Mount"), mount));
        buttons.add(selectedButton(TYPE_COMPANION, rightX + 13 + half, typeY,
                rightWidth - 23 - half, typeHeight,
                text("gui.rebornaddon.mounts.type_companion", "Companion"), !mount));
        if (!compact) {
            buttons.add(selectedButton(FAVORITE, rightX + 10, bottom - 47, half, 19,
                    text("gui.rebornaddon.mounts.favorite", "Favorite"), favorite));
            buttons.add(selectedButton(SELECT, rightX + 13 + half, bottom - 47,
                    rightWidth - 23 - half, 19,
                    text("gui.rebornaddon.mounts.make_current", "Make Current"), select));
        }

        int scaleY = compact ? bodyTop + 78 : bottom - 70;
        int scaleButtonWidth = Math.max(62, Math.min(102, rightWidth / 3));
        int scaleFieldWidth = Math.max(38, rightWidth - scaleButtonWidth - 23);
        if (scaleField != null) {
            scaleField.x = rightX + 10;
            scaleField.y = scaleY;
            scaleField.width = scaleFieldWidth;
            scaleField.height = 18;
        }
        JsonObject root = data();
        JsonObject permissions = object(root.get("permissions"));
        boolean formReady = bool(root, "available", false) && bool(root, "modAvailable", false)
                && !selectedFormId.isEmpty() && validScale();
        ThemedButton saveScale = button(SAVE_SCALE, rightX + 13 + scaleFieldWidth, scaleY,
                rightWidth - 23 - scaleFieldWidth, 18,
                text("gui.rebornaddon.mounts.save_scale", "Save Scale"),
                Theme.PURPLE_DARK, Theme.PURPLE_LIGHT);
        saveScale.enabled = formReady && bool(permissions, "scale", false);
        buttons.add(saveScale);

        boolean ready = bool(root, "available", false) && bool(root, "modAvailable", false)
                && !selectedPlayerId.isEmpty() && !selectedFormId.isEmpty();
        int actionWidth = Math.max(42, (rightWidth - 26) / 3);
        int actionY = compact ? bodyTop + 98 : bottom - 24;
        int actionHeight = compact ? 18 : 20;
        ThemedButton grant = button(GRANT, rightX + 10, actionY, actionWidth, actionHeight,
                text("gui.rebornaddon.mounts.grant", "Grant"), Theme.TEAL_DARK, Theme.TEAL);
        ThemedButton revoke = button(REVOKE, rightX + 13 + actionWidth, actionY,
                actionWidth, actionHeight, text("gui.rebornaddon.mounts.revoke", "Revoke"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        ThemedButton refresh = button(REFRESH, rightX + 16 + actionWidth * 2,
                actionY, rightWidth - 26 - actionWidth * 2, actionHeight,
                text("gui.rebornaddon.mounts.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.GOLD);
        grant.enabled = ready && bool(permissions, "grant", false);
        revoke.enabled = ready && bool(permissions, "revoke", false);
        buttons.add(grant);
        buttons.add(revoke);
        buttons.add(refresh);
    }

    void draw(FontRenderer font) {
        font.drawString(text("gui.rebornaddon.mounts.players", "Online Players"),
                left + 8, bodyTop + (compact() ? 5 : 8), Theme.GOLD);
        if (playerSearch != null) playerSearch.drawTextBox();
        font.drawString(mount
                        ? text("gui.rebornaddon.mounts.mount_forms", "Mount Forms")
                        : text("gui.rebornaddon.mounts.companion_forms", "Companion Forms"),
                rightX + 10, bodyTop + (compact() ? 5 : 8), Theme.GOLD);
        if (!compact()) {
            String message = string(data(), "message",
                    text("gui.rebornaddon.mounts.loading", "Loading private mount policy..."));
            font.drawString(trim(font, message, rightWidth - 20), rightX + 10,
                    bodyTop + 22, bool(data(), "available", false) ? Theme.TEXT_MUTED : Theme.RANKED_RED);
        }
        if (formSearch != null) formSearch.drawTextBox();
        if (scaleField != null) scaleField.drawTextBox();
        String scaleLabel = mount
                ? text("gui.rebornaddon.mounts.mount_scale", "Mount Scale")
                : text("gui.rebornaddon.mounts.companion_scale", "Companion Scale");
        if (!compact()) {
            font.drawString(scaleLabel + "  (1 = native size)", rightX + 10,
                    bottom - 82, Theme.TEXT_MUTED);
        }
        if (compact()) return;
        GuiChrome.rule(rightX + 10, rightX + rightWidth - 10, bottom - 130, Theme.GOLD);

        JsonObject selectedPlayer = find(dataArray("players"), "uuid", selectedPlayerId);
        JsonObject selectedForm = find(dataArray("forms"), "id", selectedFormId);
        String target = selectedPlayer.size() == 0
                ? text("gui.rebornaddon.mounts.no_player", "No player selected")
                : string(selectedPlayer, "name", "Unknown player");
        String form = selectedForm.size() == 0
                ? text("gui.rebornaddon.mounts.no_form", "No form selected")
                : string(selectedForm, "name", selectedFormId);
        font.drawString(trim(font, target + "  |  " + form, rightWidth - 88),
                rightX + 79, bottom - 122, Theme.TEXT_LIGHT);
        String page = (playerPage + 1) + "/" + pages(filtered(dataArray("players"),
                playerSearch == null ? "" : playerSearch.getText()).size(), playerRows());
        font.drawString(page, left + 76, bottom - 16, Theme.TEXT_MUTED);
    }

    boolean handleButton(int id) {
        List<JsonObject> players = filtered(dataArray("players"),
                playerSearch == null ? "" : playerSearch.getText());
        List<JsonObject> forms = filteredForms();
        if (id >= PLAYER_ROW_BASE && id < PLAYER_ROW_BASE + playerRows()) {
            int index = playerPage * playerRows() + id - PLAYER_ROW_BASE;
            if (index < players.size()) selectedPlayerId = string(players.get(index), "uuid", "");
            return true;
        }
        if (id >= FORM_ROW_BASE && id < FORM_ROW_BASE + formRows()) {
            int index = formPage * formRows() + id - FORM_ROW_BASE;
            if (index < forms.size()) {
                selectedFormId = string(forms.get(index), "id", "");
                syncScaleField(true);
            }
            return true;
        }
        if (id == PLAYER_PREVIOUS || id == PLAYER_NEXT) {
            playerPage = clampPage(playerPage + (id == PLAYER_PREVIOUS ? -1 : 1),
                    pages(players.size(), playerRows()));
            return true;
        }
        if (id == FORM_PREVIOUS || id == FORM_NEXT) {
            formPage = clampPage(formPage + (id == FORM_PREVIOUS ? -1 : 1),
                    pages(forms.size(), formRows()));
            return true;
        }
        if (id == TYPE_MOUNT || id == TYPE_COMPANION) {
            mount = id == TYPE_MOUNT;
            formPage = 0;
            if (!eligible(find(dataArray("forms"), "id", selectedFormId))) selectedFormId = "";
            syncScaleField(true);
            return true;
        }
        if (id == FAVORITE) {
            favorite = !favorite;
            return true;
        }
        if (id == SELECT) {
            select = !select;
            return true;
        }
        if (id == REFRESH) {
            request(true);
            return false;
        }
        if (id == SAVE_SCALE) {
            if (validScale()) send("mount.scale.set");
            return false;
        }
        if (id == GRANT || id == REVOKE) {
            send(id == GRANT ? "mount.grant" : "mount.revoke");
            return false;
        }
        return false;
    }

    void keyTyped(char character, int keyCode) {
        String oldPlayer = playerSearch == null ? "" : playerSearch.getText();
        String oldForm = formSearch == null ? "" : formSearch.getText();
        String oldScale = scaleField == null ? "" : scaleField.getText();
        if (playerSearch != null) playerSearch.textboxKeyTyped(character, keyCode);
        if (formSearch != null) formSearch.textboxKeyTyped(character, keyCode);
        if (scaleField != null) scaleField.textboxKeyTyped(character, keyCode);
        if (playerSearch != null && !oldPlayer.equals(playerSearch.getText())) {
            playerPage = 0;
            refreshButtons = true;
        }
        if (formSearch != null && !oldForm.equals(formSearch.getText())) {
            formPage = 0;
            refreshButtons = true;
        }
        if (scaleField != null && !oldScale.equals(scaleField.getText())) refreshButtons = true;
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
        if (playerSearch != null) playerSearch.mouseClicked(mouseX, mouseY, button);
        if (formSearch != null) formSearch.mouseClicked(mouseX, mouseY, button);
        if (scaleField != null) scaleField.mouseClicked(mouseX, mouseY, button);
    }

    boolean tick() {
        if (playerSearch != null) playerSearch.updateCursorCounter();
        if (formSearch != null) formSearch.updateCursorCounter();
        if (scaleField != null) scaleField.updateCursorCounter();
        int current = ClientGameplayData.revision();
        if (current != revision) {
            revision = current;
            clearMissingSelections();
            syncScaleField(false);
            refreshButtons = true;
        }
        request(false);
        boolean result = refreshButtons;
        refreshButtons = false;
        return result;
    }

    private void send(String action) {
        JsonObject player = find(dataArray("players"), "uuid", selectedPlayerId);
        JsonObject payload = new JsonObject();
        payload.addProperty("targetUuid", selectedPlayerId);
        payload.addProperty("targetName", string(player, "name", ""));
        payload.addProperty("entityId", selectedFormId);
        payload.addProperty("type", mount ? "mount" : "companion");
        if ("mount.scale.set".equals(action)) {
            payload.addProperty("scale", parsedScale());
            RebornAddonNetwork.sendGameplayAction("mounts", action, payload.toString());
            return;
        }
        payload.addProperty("favorite", favorite);
        payload.addProperty("select", select);
        RebornAddonNetwork.sendGameplayAction("mounts", action, payload.toString());
    }

    private void request(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - requestedAt < 5000L) return;
        requestedAt = now;
        RebornAddonNetwork.requestGameplaySnapshot("mounts");
    }

    private void prepareFields() {
        FontRenderer font = font();
        playerSearch = recreate(playerSearch, 3300, font, left + 7,
                bodyTop + (compact() ? 18 : 24),
                leftWidth - 14, 18, 64);
        formSearch = recreate(formSearch, 3301, font, rightX + 10,
                bodyTop + (compact() ? 18 : 40),
                rightWidth - 20, 18, 96);
        scaleField = recreate(scaleField, 3302, font, rightX + 10,
                compact() ? bodyTop + 78 : bottom - 70,
                Math.max(38, rightWidth - Math.max(62, Math.min(102, rightWidth / 3)) - 23),
                18, 12);
    }

    private void clearMissingSelections() {
        if (find(dataArray("players"), "uuid", selectedPlayerId).size() == 0) selectedPlayerId = "";
        if (!eligible(find(dataArray("forms"), "id", selectedFormId))) selectedFormId = "";
    }

    private int playerRows() {
        if (compact()) return 1;
        return Math.max(3, (bottom - 25 - (bodyTop + 47)) / 21);
    }

    private int formRows() {
        if (compact()) return 1;
        return Math.max(2, (bottom - 131 - (bodyTop + 62)) / 21);
    }

    private void syncScaleField(boolean force) {
        if (scaleField == null) return;
        JsonObject form = find(dataArray("forms"), "id", selectedFormId);
        String key = selectedFormId + ':' + (mount ? "mount" : "companion");
        float value = number(form, mount ? "mountScale" : "companionScale", 1.0F);
        String context = key + ':' + value;
        if (!force && (context.equals(scaleContext) || scaleField.isFocused())) return;
        scaleField.setText(formatScale(value));
        scaleField.setCursorPositionEnd();
        scaleContext = context;
    }

    private boolean validScale() {
        float value = parsedScale();
        return !Float.isNaN(value) && !Float.isInfinite(value) && value >= 0.01F && value <= 10.0F;
    }

    private float parsedScale() {
        try {
            return scaleField == null ? Float.NaN : Float.parseFloat(scaleField.getText().trim());
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static float number(JsonObject value, String key, float fallback) {
        try {
            return value.has(key) ? value.get(key).getAsFloat() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String formatScale(float value) {
        return new java.math.BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }

    private boolean compact() {
        return bottom - bodyTop < 230;
    }

    private JsonObject data() {
        return ClientGameplayData.get("mounts");
    }

    private JsonArray dataArray(String key) {
        JsonObject root = data();
        return root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static List<JsonObject> filtered(JsonArray values, String query) {
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<JsonObject> result = new ArrayList<JsonObject>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String searchable = (string(value, "name", "") + ' '
                    + string(value, "id", "") + ' ' + string(value, "uuid", ""))
                    .toLowerCase(Locale.ROOT);
            if (filter.isEmpty() || searchable.contains(filter)) result.add(value);
        }
        return result;
    }

    private List<JsonObject> filteredForms() {
        List<JsonObject> searched = filtered(dataArray("forms"),
                formSearch == null ? "" : formSearch.getText());
        List<JsonObject> result = new ArrayList<JsonObject>();
        for (JsonObject form : searched) if (eligible(form)) result.add(form);
        return result;
    }

    private boolean eligible(JsonObject form) {
        return form != null && form.size() > 0
                && bool(form, mount ? "mountAllowed" : "companionAllowed", false);
    }

    private static JsonObject find(JsonArray values, String key, String wanted) {
        if (wanted == null || wanted.isEmpty()) return new JsonObject();
        for (JsonElement element : values) {
            if (element.isJsonObject()) {
                JsonObject value = element.getAsJsonObject();
                if (wanted.equals(string(value, key, ""))) return value;
            }
        }
        return new JsonObject();
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject value, String key, String fallback) {
        try {
            return value.has(key) ? value.get(key).getAsString() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject value, String key, boolean fallback) {
        try {
            return value.has(key) ? value.get(key).getAsBoolean() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
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

    private static ThemedButton selectedButton(int id, int x, int y, int width, int height,
                                                String label, boolean selected) {
        ThemedButton button = button(id, x, y, width, height, label,
                selected ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                selected ? Theme.TEAL : Theme.GOLD);
        button.setSelected(selected);
        return button;
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int background, int hover) {
        return new ThemedButton(id, x, y, Math.max(20, width), Math.max(10, height), label,
                background, hover, Theme.BUTTON_TEXT);
    }

    private static int pages(int size, int perPage) {
        return Math.max(1, (size + Math.max(1, perPage) - 1) / Math.max(1, perPage));
    }

    private static int clampPage(int page, int pages) {
        return Math.max(0, Math.min(Math.max(1, pages) - 1, page));
    }

    private static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    private static String trim(FontRenderer font, String value, int maximumWidth) {
        if (value == null || font.getStringWidth(value) <= maximumWidth) return value == null ? "" : value;
        return font.trimStringToWidth(value,
                Math.max(8, maximumWidth - font.getStringWidth("..."))) + "...";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
