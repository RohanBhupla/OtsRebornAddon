package net.rebornaddon.exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GuiWrittenExam extends RebornScaledGuiScreen {
    private static final int ANSWER_BASE = 3300;
    private static final int SUBMIT = 3390;
    private static final int CLOSE = 3391;

    private final Set<String> selected = new LinkedHashSet<String>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int observedRevision;
    private String questionId = "";

    public GuiWrittenExam(JsonObject ignored) {
        observedRevision = ClientExamData.revision();
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.max(300, Math.min(660, width - 16));
        panelHeight = Math.max(230, Math.min(390, height - 16));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        buttonList.clear();
        JsonObject question = question();
        String nextId = string(question, "id");
        if (!nextId.equals(questionId)) {
            questionId = nextId;
            selected.clear();
        }
        JsonArray answers = array(question, "answers");
        int answerTop = panelTop + 126;
        int rows = Math.min(8, answers.size());
        int columns = answers.size() > 4 ? 2 : 1;
        int perColumn = columns == 1 ? rows : (rows + 1) / 2;
        int gap = 6;
        int answerWidth = columns == 1 ? panelWidth - 36 : (panelWidth - 42 - gap) / 2;
        for (int i = 0; i < rows; i++) {
            JsonObject answer = answers.get(i).getAsJsonObject();
            int column = columns == 1 ? 0 : i / perColumn;
            int row = columns == 1 ? i : i % perColumn;
            ThemedButton button = new ThemedButton(ANSWER_BASE + i,
                    panelLeft + 18 + column * (answerWidth + gap), answerTop + row * 28,
                    answerWidth, 23, prefix(i) + string(answer, "text"),
                    selected.contains(string(answer, "id")) ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.GOLD, Theme.BUTTON_TEXT);
            button.setSelected(selected.contains(string(answer, "id")));
            button.setTextAlignment(ThemedButton.Alignment.LEFT);
            button.setTextInset(10);
            buttonList.add(button);
        }
        ThemedButton submit = new ThemedButton(SUBMIT, panelLeft + panelWidth - 212,
                panelTop + panelHeight - 30, 98, 20,
                text("gui.rebornaddon.written.submit", "Submit Answer"), Theme.TEAL_DARK,
                Theme.TEAL, Theme.BUTTON_TEXT);
        submit.enabled = !selected.isEmpty();
        buttonList.add(submit);
        buttonList.add(new ThemedButton(CLOSE, panelLeft + panelWidth - 108,
                panelTop + panelHeight - 30, 96, 20,
                text("gui.rebornaddon.written.close", "Close"), Theme.TAB_INACTIVE_BG,
                Theme.NEUTRAL, Theme.BUTTON_TEXT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        JsonObject question = question();
        JsonArray answers = array(question, "answers");
        if (button.id >= ANSWER_BASE && button.id < ANSWER_BASE + answers.size()) {
            JsonObject answer = answers.get(button.id - ANSWER_BASE).getAsJsonObject();
            String id = string(answer, "id");
            String mode = string(question, "selectionMode");
            if ("single".equals(mode)) selected.clear();
            if (!selected.remove(id)) {
                int maximum = Math.max(1, integer(question, "maxSelections", 1));
                if (selected.size() < maximum || "all".equals(mode)) selected.add(id);
            }
            initGui();
        } else if (button.id == SUBMIT) {
            JsonObject payload = new JsonObject();
            payload.addProperty("examId", string(question, "examId"));
            payload.addProperty("questionId", string(question, "id"));
            JsonArray ids = new JsonArray();
            for (String id : selected) ids.add(id);
            payload.add("answerIds", ids);
            RebornAddonNetwork.sendExamAction("written.answer", payload.toString());
        } else if (button.id == CLOSE) {
            mc.displayGuiScreen(new GuiHub("exams"));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (observedRevision == ClientExamData.revision()) return;
        observedRevision = ClientExamData.revision();
        JsonObject root = ClientExamData.snapshot();
        if (!root.has("activeQuestion") || !root.get("activeQuestion").isJsonObject()) {
            mc.displayGuiScreen(new GuiHub("exams"));
        } else {
            initGui();
        }
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(panelLeft, panelTop, panelWidth, panelHeight, Theme.GOLD_DARK);
        GuiChrome.header(panelLeft + 5, panelTop + 7, panelWidth - 10, 36, Theme.GOLD_DARK);
        JsonObject question = question();
        String progress = text("gui.rebornaddon.written.question", "Question") + " "
                + integer(question, "number", 1) + " / " + integer(question, "total", 1);
        fontRenderer.drawString(progress, panelLeft + 17, panelTop + 16, Theme.GOLD);
        fontRenderer.drawString(string(question, "examTitle"), panelLeft + 17, panelTop + 29, Theme.TEXT_MUTED);
        GuiChrome.section(panelLeft + 10, panelTop + 51, panelWidth - 20, 64, Theme.GOLD_DARK);
        fontRenderer.drawSplitString(string(question, "text"), panelLeft + 23, panelTop + 67,
                panelWidth - 46, Theme.TEXT_LIGHT);
        String mode = string(question, "selectionMode");
        String instruction = "single".equals(mode)
                ? text("gui.rebornaddon.written.choose_one", "Choose one answer.")
                : text("gui.rebornaddon.written.choose_multiple", "Choose every answer you want to submit.");
        fontRenderer.drawString(instruction, panelLeft + 19, panelTop + 116, Theme.TEXT_MUTED);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String prefix(int index) {
        return Character.toString((char) ('A' + index)) + ".  ";
    }

    private static JsonObject question() {
        JsonObject root = ClientExamData.snapshot();
        return root.has("activeQuestion") && root.get("activeQuestion").isJsonObject()
                ? root.getAsJsonObject("activeQuestion") : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
