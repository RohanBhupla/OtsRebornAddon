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

import java.util.List;

final class ModerationAdminPanel {
    private static final int VIEW_BASE = 2600;
    private static final int SEARCH = 2700;
    private static final int REFRESH = 2701;
    private static final int PREVIOUS = 2702;
    private static final int NEXT = 2703;
    private static final int PERMANENT = 2704;
    private static final int BAN = 2705;
    private static final int WARN = 2706;
    private static final int UNBAN = 2707;
    private static final int WARNING_PREVIOUS = 2708;
    private static final int WARNING_NEXT = 2709;
    private static final int ROW_BASE = 2800;
    private static final String[] VIEWS = {"online", "offline", "banned"};
    private static final String[] LABELS = {"Online", "Offline", "Banned"};

    private GuiTextField search;
    private GuiTextField reason;
    private GuiTextField hours;
    private GuiTextField minutes;
    private GuiTextField seconds;
    private int view;
    private int page;
    private int selectedRow = -1;
    private int warningPage;
    private int revision = -1;
    private boolean permanent = true;
    private boolean refreshButtons;
    private long requestedAt;
    private String selectedId = "";
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

        int tabWidth = Math.max(34, (leftWidth - 18) / 3);
        for (int i = 0; i < VIEWS.length; i++) {
            ThemedButton button = button(VIEW_BASE + i, left + 6 + i * (tabWidth + 3), bodyTop + 6,
                    i == 2 ? leftWidth - 12 - i * (tabWidth + 3) : tabWidth, 20,
                    text("gui.rebornaddon.moderation." + VIEWS[i], LABELS[i]),
                    i == view ? Theme.RANKED_RED_DARK : Theme.TAB_INACTIVE_BG, Theme.RANKED_RED);
            button.setSelected(i == view);
            buttons.add(button);
        }
        int searchButtonWidth = Math.min(58, Math.max(42, leftWidth / 4));
        buttons.add(button(SEARCH, left + leftWidth - searchButtonWidth - 6, bodyTop + 31,
                searchButtonWidth, 18, text("gui.rebornaddon.moderation.search", "Search"),
                Theme.TEAL_DARK, Theme.TEAL));

        JsonArray players = players();
        int rows = rowsPerPage();
        for (int i = 0; i < rows && i < players.size(); i++) {
            JsonObject player = object(players.get(i));
            String name = string(player, "name",
                    text("gui.rebornaddon.moderation.unknown_player", "Unknown player"));
            String suffix = bool(player, "banned", false)
                    ? "  [" + text("gui.rebornaddon.moderation.banned", "Banned") + "]"
                    : bool(player, "online", false)
                    ? "  [" + text("gui.rebornaddon.moderation.online", "Online") + "]"
                    : "  [" + text("gui.rebornaddon.moderation.offline", "Offline") + "]";
            ThemedButton row = button(ROW_BASE + i, left + 7, bodyTop + 56 + i * 22,
                    leftWidth - 14, 19, name + suffix,
                    i == selectedRow ? Theme.PURPLE_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.PURPLE_LIGHT);
            row.setSelected(i == selectedRow);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(7);
            buttons.add(row);
        }
        ThemedButton previous = button(PREVIOUS, left + 7, bottom - 20, 30, 18, "<",
                Theme.TAB_INACTIVE_BG, Theme.TEAL);
        ThemedButton next = button(NEXT, left + 41, bottom - 20, 30, 18, ">",
                Theme.TAB_INACTIVE_BG, Theme.TEAL);
        int playerPages = Math.max(1, integer(data(), "pageCount", 1));
        previous.enabled = page > 0;
        next.enabled = page + 1 < playerPages;
        buttons.add(previous);
        buttons.add(next);
        buttons.add(button(REFRESH, left + 75, bottom - 20, leftWidth - 82, 18,
                text("gui.rebornaddon.moderation.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.TEAL));

        JsonObject selected = selected();
        boolean hasPlayer = selected.size() > 0;
        buttons.add(selectedButton(PERMANENT, rightX + 10, bodyTop + 167,
                rightWidth - 20, 19,
                permanent ? text("gui.rebornaddon.moderation.permanent", "Permanent Ban")
                        : text("gui.rebornaddon.moderation.timed", "Timed Ban"), permanent));
        int actionWidth = Math.max(42, (rightWidth - 26) / 3);
        ThemedButton ban = button(BAN, rightX + 10, bodyTop + 191, actionWidth, 20,
                text("gui.rebornaddon.moderation.ban", "Ban"), Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        ThemedButton warn = button(WARN, rightX + 13 + actionWidth, bodyTop + 191,
                actionWidth, 20, text("gui.rebornaddon.moderation.warn", "Warn"),
                Theme.GOLD_DARK, Theme.GOLD);
        ThemedButton unban = button(UNBAN, rightX + 16 + actionWidth * 2, bodyTop + 191,
                rightWidth - 26 - actionWidth * 2, 20,
                text("gui.rebornaddon.moderation.unban", "Unban"), Theme.TEAL_DARK, Theme.TEAL);
        boolean hasReason = reason != null && !reason.getText().trim().isEmpty();
        ban.enabled = hasPlayer && hasReason && (permanent || durationSeconds() > 0L);
        warn.enabled = hasPlayer && hasReason;
        unban.enabled = hasPlayer && bool(selected, "banned", false);
        buttons.add(ban);
        buttons.add(warn);
        buttons.add(unban);
        if (hasPlayer) {
            int warningPages = Math.max(1, integer(selected, "warningPageCount", 1));
            ThemedButton warningPrevious = button(WARNING_PREVIOUS,
                    rightX + rightWidth - 68, bodyTop + 216, 27, 18, "<",
                    Theme.TAB_INACTIVE_BG, Theme.GOLD);
            ThemedButton warningNext = button(WARNING_NEXT,
                    rightX + rightWidth - 37, bodyTop + 216, 27, 18, ">",
                    Theme.TAB_INACTIVE_BG, Theme.GOLD);
            warningPrevious.enabled = warningPage > 0;
            warningNext.enabled = warningPage + 1 < warningPages;
            buttons.add(warningPrevious);
            buttons.add(warningNext);
        }
    }

    void draw(FontRenderer font) {
        JsonObject root = data();
        if (search != null) search.drawTextBox();
        String message = string(root, "message", text("gui.rebornaddon.moderation.loading",
                "Loading private moderation records..."));
        font.drawString(trim(font, message, rightWidth - 20), rightX + 10, bodyTop + 7,
                bool(root, "available", false) ? Theme.TEXT_MUTED : Theme.GOLD);
        GuiChrome.rule(rightX + 10, rightX + rightWidth - 10, bodyTop + 20, Theme.RANKED_RED);

        JsonObject selected = selected();
        if (selected.size() == 0) {
            font.drawSplitString(text("gui.rebornaddon.moderation.choose",
                            "Choose a player to review bans and warnings."),
                    rightX + 10, bodyTop + 34, rightWidth - 20, Theme.TEXT_MUTED);
            return;
        }
        String name = string(selected, "name",
                text("gui.rebornaddon.moderation.unknown_player", "Unknown player"));
        font.drawString(trim(font, name, rightWidth - 20), rightX + 10, bodyTop + 31, Theme.TEXT_LIGHT);
        String state = bool(selected, "banned", false)
                ? text("gui.rebornaddon.moderation.status_banned", "Currently banned")
                : bool(selected, "online", false)
                ? text("gui.rebornaddon.moderation.status_online", "Currently online")
                : text("gui.rebornaddon.moderation.status_offline", "Currently offline");
        String warningsLabel = ClientLocalization.format("gui.rebornaddon.moderation.warning_count",
                "%s warnings", string(selected, "warningCount", "0"));
        font.drawString(state + "  |  " + warningsLabel,
                rightX + 10, bodyTop + 46, bool(selected, "banned", false)
                        ? Theme.RANKED_RED : Theme.SUCCESS_BRIGHT);

        if (bool(selected, "banned", false)) {
            JsonObject ban = object(selected.get("ban"));
            String remaining = bool(ban, "permanent", false)
                    ? text("gui.rebornaddon.moderation.permanent_short", "Permanent")
                    : duration(longValue(ban, "remainingMillis", 0L));
            font.drawString(trim(font, text("gui.rebornaddon.moderation.ban_prefix", "Ban:") + " "
                            + string(ban, "reason", text("gui.rebornaddon.moderation.no_reason",
                            "No reason recorded")),
                    rightWidth - 20), rightX + 10, bodyTop + 62, Theme.TEXT_LIGHT);
            font.drawString(trim(font, text("gui.rebornaddon.moderation.by", "By") + " "
                            + string(ban, "staffName", text("gui.rebornaddon.moderation.unknown_staff",
                            "Unknown staff")) + "  |  "
                            + text("gui.rebornaddon.moderation.remaining", "Remaining:") + " "
                            + remaining, rightWidth - 20),
                    rightX + 10, bodyTop + 76, Theme.TEXT_MUTED);
        }
        font.drawString(text("gui.rebornaddon.moderation.reason", "Reason"),
                rightX + 10, bodyTop + 94, Theme.TEXT_MUTED);
        if (reason != null) reason.drawTextBox();
        font.drawString(text("gui.rebornaddon.time.hours", "Hours"),
                hours.x, bodyTop + 128, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.minutes", "Minutes"),
                minutes.x, bodyTop + 128, Theme.TEXT_MUTED);
        font.drawString(text("gui.rebornaddon.time.seconds", "Seconds"),
                seconds.x, bodyTop + 128, Theme.TEXT_MUTED);
        hours.drawTextBox();
        minutes.drawTextBox();
        seconds.drawTextBox();

        JsonArray warnings = array(selected, "warnings");
        font.drawString(text("gui.rebornaddon.moderation.warning_history", "Warning History"),
                rightX + 10, bodyTop + 220, Theme.GOLD);
        int warningPages = Math.max(1, integer(selected, "warningPageCount", 1));
        String warningPageLabel = ClientLocalization.format("gui.rebornaddon.moderation.page",
                "Page %s/%s", Math.min(warningPages, warningPage + 1), warningPages);
        font.drawString(trim(font, warningPageLabel, 80), rightX + rightWidth - 152,
                bodyTop + 220, Theme.TEXT_MUTED);
        int y = bodyTop + 235;
        for (int i = 0; i < warnings.size() && i < warningRowsPerPage() && y < bottom - 10; i++) {
            JsonObject warning = object(warnings.get(i));
            String line = string(warning, "reason", text("gui.rebornaddon.moderation.no_reason",
                    "No reason recorded")) + "  |  " + string(warning, "staffName",
                    text("gui.rebornaddon.moderation.unknown_staff", "Unknown staff"));
            font.drawString(trim(font, line, rightWidth - 20), rightX + 10, y, Theme.TEXT_MUTED);
            y += 13;
        }
    }

    boolean handleButton(int id) {
        if (id >= VIEW_BASE && id < VIEW_BASE + VIEWS.length) {
            view = id - VIEW_BASE;
            page = 0;
            warningPage = 0;
            selectedRow = -1;
            selectedId = "";
            request("moderation.query", true);
            return true;
        }
        if (id == SEARCH || id == REFRESH) {
            page = 0;
            warningPage = 0;
            selectedRow = -1;
            selectedId = "";
            request("moderation.query", true);
            return false;
        }
        if (id == PREVIOUS || id == NEXT) {
            int pages = Math.max(1, integer(data(), "pageCount", 1));
            page = Math.max(0, Math.min(pages - 1, page + (id == PREVIOUS ? -1 : 1)));
            selectedRow = -1;
            selectedId = "";
            request("moderation.query", true);
            return false;
        }
        if (id == WARNING_PREVIOUS || id == WARNING_NEXT) {
            int pages = Math.max(1, integer(selected(), "warningPageCount", 1));
            warningPage = Math.max(0, Math.min(pages - 1,
                    warningPage + (id == WARNING_PREVIOUS ? -1 : 1)));
            request("moderation.query", true);
            return false;
        }
        if (id >= ROW_BASE && id < ROW_BASE + rowsPerPage()) {
            int row = id - ROW_BASE;
            JsonArray players = players();
            if (row < players.size()) {
                selectedRow = row;
                warningPage = 0;
                JsonObject player = object(players.get(row));
                selectedId = string(player, "uuid", "");
                request("moderation.query", true);
            }
            return true;
        }
        if (id == PERMANENT) {
            permanent = !permanent;
            return true;
        }
        if (id == BAN || id == WARN || id == UNBAN) {
            String action = id == BAN ? "moderation.ban" : id == WARN
                    ? "moderation.warn" : "moderation.unban";
            request(action, true);
            return false;
        }
        return false;
    }

    void keyTyped(char character, int keyCode) {
        if (search != null) search.textboxKeyTyped(character, keyCode);
        if (reason != null) reason.textboxKeyTyped(character, keyCode);
        if (hours != null) hours.textboxKeyTyped(character, keyCode);
        if (minutes != null) minutes.textboxKeyTyped(character, keyCode);
        if (seconds != null) seconds.textboxKeyTyped(character, keyCode);
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
        if (search != null) search.mouseClicked(mouseX, mouseY, button);
        if (reason != null) reason.mouseClicked(mouseX, mouseY, button);
        if (hours != null) hours.mouseClicked(mouseX, mouseY, button);
        if (minutes != null) minutes.mouseClicked(mouseX, mouseY, button);
        if (seconds != null) seconds.mouseClicked(mouseX, mouseY, button);
    }

    boolean tick() {
        if (search != null) search.updateCursorCounter();
        if (reason != null) reason.updateCursorCounter();
        if (hours != null) hours.updateCursorCounter();
        if (minutes != null) minutes.updateCursorCounter();
        if (seconds != null) seconds.updateCursorCounter();
        int current = ClientGameplayData.revision("moderation");
        if (current != revision) {
            revision = current;
            JsonObject root = data();
            page = Math.max(0, integer(root, "page", page));
            JsonObject selected = selected();
            warningPage = Math.max(0, integer(selected, "warningPage", warningPage));
            refreshButtons = true;
        }
        request("moderation.query", false);
        boolean result = refreshButtons;
        refreshButtons = false;
        return result;
    }

    private void request(String action, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - requestedAt < 30000L) return;
        requestedAt = now;
        JsonObject payload = new JsonObject();
        payload.addProperty("view", VIEWS[view]);
        payload.addProperty("query", search == null ? "" : search.getText().trim());
        payload.addProperty("page", page);
        payload.addProperty("pageSize", rowsPerPage());
        payload.addProperty("warningPage", warningPage);
        payload.addProperty("warningPageSize", warningRowsPerPage());
        payload.addProperty("targetUuid", selectedId);
        JsonObject selected = selected();
        payload.addProperty("targetName", string(selected, "name", ""));
        payload.addProperty("reason", reason == null ? "" : reason.getText().trim());
        payload.addProperty("permanent", permanent);
        payload.addProperty("durationSeconds", durationSeconds());
        RebornAddonNetwork.sendGameplayAction("moderation", action, payload.toString());
    }

    private void prepareFields() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        search = recreate(search, 2750, font, left + 7, bodyTop + 31,
                leftWidth - Math.min(58, Math.max(42, leftWidth / 4)) - 16, 18, 64);
        reason = recreate(reason, 2751, font, rightX + 10, bodyTop + 106,
                rightWidth - 20, 18, 180);
        int fieldWidth = Math.max(34, (rightWidth - 28) / 3);
        hours = recreate(hours, 2752, font, rightX + 10, bodyTop + 140, fieldWidth, 18, 8);
        minutes = recreate(minutes, 2753, font, rightX + 14 + fieldWidth,
                bodyTop + 140, fieldWidth, 18, 8);
        seconds = recreate(seconds, 2754, font, rightX + 18 + fieldWidth * 2,
                bodyTop + 140, rightWidth - 28 - fieldWidth * 2, 18, 8);
        if (hours.getText().isEmpty()) hours.setText("0");
        if (minutes.getText().isEmpty()) minutes.setText("0");
        if (seconds.getText().isEmpty()) seconds.setText("0");
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

    private int rowsPerPage() {
        return Math.max(3, (bottom - bodyTop - 82) / 22);
    }

    private int warningRowsPerPage() {
        return Math.max(1, (bottom - bodyTop - 247) / 13);
    }

    private long durationSeconds() {
        return boundedNumber(hours, 100000L) * 3600L
                + boundedNumber(minutes, 1000000L) * 60L
                + boundedNumber(seconds, 100000000L);
    }

    private static long boundedNumber(GuiTextField field, long maximum) {
        try {
            return Math.min(maximum, Math.max(0L, Long.parseLong(field.getText().trim())));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private JsonObject data() { return ClientGameplayData.get("moderation"); }

    private JsonArray players() {
        JsonObject root = data();
        return array(root, root.has(VIEWS[view]) ? VIEWS[view] : "players");
    }

    private JsonObject selected() {
        JsonObject root = data();
        if (root.has("selected") && root.get("selected").isJsonObject()) {
            JsonObject selected = root.getAsJsonObject("selected");
            if (selectedId.isEmpty() || selectedId.equals(string(selected, "uuid", ""))) return selected;
        }
        JsonArray players = players();
        return selectedRow >= 0 && selectedRow < players.size()
                ? object(players.get(selectedRow)) : new JsonObject();
    }

    private static JsonArray array(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsInt() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static long longValue(JsonObject root, String key, long fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsLong() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static String duration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long days = seconds / 86400L;
        long hours = seconds % 86400L / 3600L;
        long minutes = seconds % 3600L / 60L;
        if (days > 0L) return days + "d " + hours + "h";
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + seconds % 60L + "s";
        return seconds + "s";
    }

    private static ThemedButton selectedButton(int id, int x, int y, int width, int height,
                                               String label, boolean selected) {
        ThemedButton button = button(id, x, y, width, height, label,
                selected ? Theme.RANKED_RED_DARK : Theme.TAB_INACTIVE_BG, Theme.RANKED_RED);
        button.setSelected(selected);
        return button;
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int base, int hover) {
        return new ThemedButton(id, x, y, Math.max(20, width), Math.max(10, height),
                label, base, hover, Theme.BUTTON_TEXT);
    }

    private static String trim(FontRenderer font, String value, int width) {
        return font.trimStringToWidth(value == null ? "" : value, Math.max(10, width));
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
