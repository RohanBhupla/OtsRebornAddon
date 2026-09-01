package net.rebornaddon.gui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.exam.client.ClientExamData;
import net.rebornaddon.exam.client.GuiExamEditor;
import net.rebornaddon.exam.client.GuiWrittenExam;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.jutsu.client.ClientAdminAccess;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ExamsTab implements HubTab {
    private static final int VIEW_BASE = 2200;
    private static final int ROW_BASE = 2230;
    private static final int PREVIOUS = 2250;
    private static final int NEXT = 2251;
    private static final int REFRESH = 2252;
    private static final int CREATE = 2253;
    private static final int EDIT = 2254;
    private static final int ENROLL = 2255;
    private static final int WRITTEN = 2256;
    private static final int READY = 2257;
    private static final int APPROVE = 2258;
    private static final int REJECT = 2259;

    private int view;
    private int page;
    private int selectedRow = -1;
    private int observedRevision = -1;
    private int left;
    private int top;
    private int width;
    private int height;
    private int headerHeight;
    private int listWidth;
    private int rowsPerPage;
    private boolean refreshButtons;
    private boolean pendingEditor;
    private boolean pendingWritten;
    private List<JsonObject> visible = Collections.emptyList();

    @Override
    public String getTabName() {
        return text("gui.rebornaddon.tab.exams", "Exams");
    }

    @Override
    public int getTabColorActive() {
        return Theme.GOLD_DARK;
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
        JsonObject root = ClientExamData.snapshot();
        if (ClientExamData.request(false)) RebornAddonNetwork.requestExamSnapshot();

        List<String> views = views(root);
        view = Math.max(0, Math.min(view, views.size() - 1));
        int viewWidth = Math.max(62, Math.min(92, (width - 4 * (views.size() - 1)) / views.size()));
        for (int i = 0; i < views.size(); i++) {
            ThemedButton button = new ThemedButton(VIEW_BASE + i,
                    left + i * (viewWidth + 4), top, viewWidth, 20, views.get(i),
                    i == view ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.GOLD, Theme.BUTTON_TEXT);
            button.setSelected(i == view);
            buttons.add(button);
        }
        headerHeight = 28;
        buttons.add(new ThemedButton(REFRESH, left + width - 54, top + 25, 54, 18,
                text("gui.rebornaddon.exams.refresh", "Refresh"), Theme.TAB_INACTIVE_BG,
                Theme.NEUTRAL, Theme.BUTTON_TEXT));

        String key = viewKey(root, view);
        visible = exams(root, key);
        listWidth = Math.max(120, Math.min(235, width * 43 / 100));
        int listTop = top + 50;
        rowsPerPage = Math.max(3, Math.min(9, (height - 80) / 27));
        int pages = Math.max(1, (visible.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(visible.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            JsonObject exam = visible.get(i);
            ThemedButton row = new ThemedButton(ROW_BASE + i - start, left, listTop + (i - start) * 27,
                    listWidth, 23, trim(Minecraft.getMinecraft().fontRenderer,
                    string(exam, "title"), listWidth - 18),
                    Theme.TAB_INACTIVE_BG, Theme.GOLD_DARK, Theme.BUTTON_TEXT);
            row.setSelected(i == selectedRow);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(9);
            buttons.add(row);
        }
        ThemedButton previous = new ThemedButton(PREVIOUS, left, top + height - 20, 22, 18,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        previous.enabled = page > 0;
        buttons.add(previous);
        ThemedButton next = new ThemedButton(NEXT, left + listWidth - 22, top + height - 20, 22, 18,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        next.enabled = page + 1 < pages;
        buttons.add(next);

        int actionX = left + listWidth + 10;
        int actionWidth = width - listWidth - 10;
        int y = top + height - 44;
        JsonObject selected = selected();
        if (canCreate(root) && "mine".equals(key)) {
            int createWidth = Math.max(40, actionWidth - 60);
            buttons.add(new ThemedButton(CREATE, actionX, top + 25, createWidth, 18,
                    text("gui.rebornaddon.exams.create", "Create Exam"), Theme.TEAL_DARK,
                    Theme.TEAL, Theme.BUTTON_TEXT));
        }
        if (selected == null) return;
        if ("approval".equals(key)) {
            int half = (actionWidth - 4) / 2;
            buttons.add(new ThemedButton(APPROVE, actionX, y, half, 20,
                    text("gui.rebornaddon.exams.approve", "Approve"), Theme.SUCCESS,
                    Theme.SUCCESS, Theme.BUTTON_TEXT));
            buttons.add(new ThemedButton(REJECT, actionX + half + 4, y, actionWidth - half - 4, 20,
                    text("gui.rebornaddon.exams.reject", "Reject"), Theme.RANKED_RED_DARK,
                    Theme.RANKED_RED, Theme.BUTTON_TEXT));
        } else if (bool(selected, "canManage")) {
            buttons.add(new ThemedButton(EDIT, actionX, y, actionWidth, 20,
                    text("gui.rebornaddon.exams.manage", "Edit & Manage"), Theme.TEAL_DARK,
                    Theme.TEAL, Theme.BUTTON_TEXT));
        } else if (bool(selected, "enrolled")) {
            int third = Math.max(48, (actionWidth - 8) / 3);
            buttons.add(new ThemedButton(ENROLL, actionX, y, third, 20,
                    text("gui.rebornaddon.exams.withdraw", "Withdraw"), Theme.TAB_INACTIVE_BG,
                    Theme.NEUTRAL, Theme.BUTTON_TEXT));
            buttons.add(new ThemedButton(WRITTEN, actionX + third + 4, y, third, 20,
                    text("gui.rebornaddon.exams.written", "Written"), Theme.GOLD_DARK,
                    Theme.GOLD, Theme.BUTTON_TEXT));
            buttons.add(new ThemedButton(READY, actionX + (third + 4) * 2, y,
                    actionWidth - (third + 4) * 2, 20,
                    text("gui.rebornaddon.exams.ready", "Ready"), Theme.TEAL_DARK,
                    Theme.TEAL, Theme.BUTTON_TEXT));
        } else {
            buttons.add(new ThemedButton(ENROLL, actionX, y, actionWidth, 20,
                    text("gui.rebornaddon.exams.enroll", "Enroll"), Theme.SUCCESS,
                    Theme.SUCCESS, Theme.BUTTON_TEXT));
        }
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        JsonObject root = ClientExamData.snapshot();
        if (!root.has("available")) {
            GuiChrome.header(left, top + 32, width, 42, Theme.GOLD_DARK);
            font.drawString(text("gui.rebornaddon.exams.loading", "Loading exam schedule..."),
                    left + 13, top + 48, Theme.TEXT_LIGHT);
            return;
        }
        if (!bool(root, "available")) {
            GuiChrome.section(left, top + 48, width, Math.max(70, height - 48), Theme.GOLD_DARK);
            font.drawSplitString(string(root, "message"), left + 12, top + 64, width - 24, Theme.TEXT_MUTED);
            return;
        }
        String key = viewKey(root, view);
        GuiChrome.section(left, top + 48, listWidth, Math.max(60, height - 48), Theme.GOLD_DARK);
        int detailX = left + listWidth + 10;
        int detailWidth = width - listWidth - 10;
        GuiChrome.section(detailX, top + 48, detailWidth, Math.max(60, height - 48), Theme.GOLD_DARK);
        font.drawString(text("gui.rebornaddon.exams.schedule", "Exam Schedule"), left + 9, top + 56, Theme.GOLD);
        JsonObject selected = selected();
        if (selected == null) {
            font.drawSplitString(visible.isEmpty()
                            ? text("gui.rebornaddon.exams.empty", "No exams are listed here.")
                            : text("gui.rebornaddon.exams.select", "Select an exam to see its schedule and stages."),
                    detailX + 12, top + 66, detailWidth - 24, Theme.TEXT_MUTED);
            return;
        }
        int y = top + 59;
        font.drawString(trim(font, string(selected, "title"), detailWidth - 22), detailX + 10, y, Theme.TEXT_LIGHT);
        y += 16;
        line(font, detailX, y, text("gui.rebornaddon.exams.rank", "Rank"), string(selected, "rankName"));
        y += 13;
        line(font, detailX, y, text("gui.rebornaddon.exams.status", "Status"), display(string(selected, "status")));
        y += 13;
        line(font, detailX, y, text("gui.rebornaddon.exams.created_by", "Created by"), string(selected, "creatorName"));
        y += 13;
        line(font, detailX, y, text("gui.rebornaddon.exams.date", "Date"), string(selected, "scheduledDisplay"));
        y += 17;
        font.drawString(text("gui.rebornaddon.exams.stages", "Stages"), detailX + 10, y, Theme.GOLD);
        y += 12;
        font.drawSplitString(join(array(selected, "stages")), detailX + 10, y, detailWidth - 20, Theme.TEXT_MUTED);
        y += 25;
        line(font, detailX, y, text("gui.rebornaddon.exams.enrolled_count", "Enrolled"),
                integer(selected, "participantCount") + " / " + integer(selected, "capacity"));
        if (!string(root, "message").isEmpty()) {
            font.drawSplitString(string(root, "message"), detailX + 10, top + height - 68,
                    detailWidth - 20, bool(root, "success") ? Theme.SUCCESS : Theme.RANKED_RED);
        }
    }

    @Override
    public boolean handleButtonClick(int id) {
        JsonObject root = ClientExamData.snapshot();
        List<String> views = views(root);
        if (id >= VIEW_BASE && id < VIEW_BASE + views.size()) {
            view = id - VIEW_BASE;
            page = 0;
            selectedRow = -1;
            return true;
        }
        if (id >= ROW_BASE && id < ROW_BASE + rowsPerPage) {
            int index = page * rowsPerPage + id - ROW_BASE;
            selectedRow = index < visible.size() ? index : -1;
            return true;
        }
        if (id == PREVIOUS || id == NEXT) {
            page += id == PREVIOUS ? -1 : 1;
            selectedRow = -1;
            return true;
        }
        if (id == REFRESH) {
            RebornAddonNetwork.requestExamSnapshot(true);
            return false;
        }
        if (id == CREATE) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiExamEditor(new JsonObject(), root));
            return false;
        }
        JsonObject selected = selected();
        if (selected == null) return false;
        JsonObject payload = new JsonObject();
        payload.addProperty("examId", string(selected, "id"));
        if (id == EDIT) {
            pendingEditor = true;
            send("exam.open_editor", payload);
        } else if (id == ENROLL) send(bool(selected, "enrolled") ? "exam.withdraw" : "exam.enroll", payload);
        else if (id == WRITTEN) { pendingWritten = true; send("written.start", payload); }
        else if (id == READY) send("tournament.ready", payload);
        else if (id == APPROVE) send("exam.approve", payload);
        else if (id == REJECT) send("exam.reject", payload);
        else return false;
        return false;
    }

    @Override
    public void onTick() {
        if (observedRevision == ClientExamData.revision()) return;
        observedRevision = ClientExamData.revision();
        JsonObject root = ClientExamData.snapshot();
        if (pendingEditor && root.has("draft") && root.get("draft").isJsonObject()) {
            pendingEditor = false;
            Minecraft.getMinecraft().displayGuiScreen(new GuiExamEditor(root.getAsJsonObject("draft"), root));
        } else if (pendingWritten && root.has("activeQuestion") && root.get("activeQuestion").isJsonObject()) {
            pendingWritten = false;
            Minecraft.getMinecraft().displayGuiScreen(new GuiWrittenExam(root));
        }
        refreshButtons = true;
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean value = refreshButtons;
        refreshButtons = false;
        return value;
    }

    private JsonObject selected() {
        return selectedRow >= 0 && selectedRow < visible.size() ? visible.get(selectedRow) : null;
    }

    private static List<JsonObject> exams(JsonObject root, String view) {
        String field = "mine".equals(view) ? "myExams" : "approval".equals(view)
                ? "pendingApproval" : "exams";
        JsonArray values = array(root, field);
        List<JsonObject> result = new ArrayList<JsonObject>();
        for (JsonElement value : values) if (value.isJsonObject()) result.add(value.getAsJsonObject());
        Collections.sort(result, new Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject first, JsonObject second) {
                long a = first.has("scheduledAt") ? first.get("scheduledAt").getAsLong() : Long.MAX_VALUE;
                long b = second.has("scheduledAt") ? second.get("scheduledAt").getAsLong() : Long.MAX_VALUE;
                return a == b ? string(first, "title").compareToIgnoreCase(string(second, "title"))
                        : a < b ? -1 : 1;
            }
        });
        return result;
    }

    private static List<String> views(JsonObject root) {
        List<String> values = new ArrayList<String>();
        values.add(text("gui.rebornaddon.exams.available", "Available"));
        values.add(text("gui.rebornaddon.exams.mine", "My Exams"));
        JsonObject access = object(root, "access");
        if (bool(access, "canApprove")) values.add(text("gui.rebornaddon.exams.approval", "Approval"));
        return values;
    }

    private static String viewKey(JsonObject root, int view) {
        JsonObject access = object(root, "access");
        if (view == 0) return "available";
        if (view == 1) return "mine";
        int next = 2;
        if (bool(access, "canApprove")) {
            if (view == next) return "approval";
        }
        return "available";
    }

    private static boolean canCreate(JsonObject root) {
        JsonObject access = object(root, "access");
        String scope = string(root, "viewerScope");
        return bool(access, "canCreate") || ClientAdminAccess.isOperator()
                || "admin".equalsIgnoreCase(scope) || "creator".equalsIgnoreCase(scope);
    }

    private static void send(String action, JsonObject payload) {
        RebornAddonNetwork.sendExamAction(action, payload == null ? "{}" : payload.toString());
    }

    private static void line(FontRenderer font, int x, int y, String label, String value) {
        font.drawString(label + ":", x + 10, y, Theme.TEXT_MUTED);
        font.drawString(value == null || value.isEmpty() ? "-" : value,
                x + 80, y, Theme.TEXT_LIGHT);
    }

    private static String display(String value) {
        if (value == null || value.isEmpty()) return "-";
        String normalized = value.replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String join(JsonArray values) {
        StringBuilder result = new StringBuilder();
        for (JsonElement value : values) {
            if (result.length() > 0) result.append("  |  ");
            result.append(display(value.getAsString()));
        }
        return result.length() == 0 ? "-" : result.toString();
    }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject()
                ? root.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).getAsBoolean();
    }

    private static int integer(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String trim(FontRenderer font, String value, int width) {
        return font.getStringWidth(value) <= width ? value : font.trimStringToWidth(value,
                Math.max(12, width - font.getStringWidth("..."))) + "...";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
