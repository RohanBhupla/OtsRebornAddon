package net.rebornaddon.exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.UUID;

public final class GuiExamEditor extends RebornScaledGuiScreen {
    private static final int PAGE_BASE = 3000;
    private static final int SAVE = 3010;
    private static final int CANCEL = 3011;
    private static final int RANK_PREVIOUS = 3020;
    private static final int RANK_NEXT = 3021;
    private static final int STAGE_WRITTEN = 3022;
    private static final int STAGE_PRACTICAL = 3023;
    private static final int STAGE_TOURNAMENT = 3024;
    private static final int QUESTION_PREVIOUS = 3030;
    private static final int QUESTION_NEXT = 3031;
    private static final int QUESTION_ADD = 3032;
    private static final int QUESTION_DELETE = 3033;
    private static final int ANSWER_PREVIOUS = 3040;
    private static final int ANSWER_NEXT = 3041;
    private static final int ANSWER_ADD = 3042;
    private static final int ANSWER_DELETE = 3043;
    private static final int ANSWER_CORRECT = 3044;
    private static final int SELECTION_SINGLE = 3045;
    private static final int SELECTION_ANY = 3046;
    private static final int SELECTION_ALL = 3047;
    private static final int MAX_SELECTIONS = 3048;
    private static final int RULE_NATURE = 3050;
    private static final int RULE_KG = 3051;
    private static final int RULE_MODES = 3052;
    private static final int RULE_ARMOR = 3053;
    private static final int RULE_WEAPONS = 3054;
    private static final int WEAPON_ADD = 3055;
    private static final int WEAPON_REMOVE = 3056;
    private static final int WEAPON_PREVIOUS = 3057;
    private static final int WEAPON_NEXT = 3058;
    private static final int ARENA_PREVIOUS = 3060;
    private static final int ARENA_NEXT = 3061;
    private static final int FIGHTER_A_PREVIOUS = 3070;
    private static final int FIGHTER_A_NEXT = 3071;
    private static final int FIGHTER_B_PREVIOUS = 3072;
    private static final int FIGHTER_B_NEXT = 3073;
    private static final int BRACKET_ADD = 3074;
    private static final int BRACKET_REMOVE = 3075;
    private static final int BRACKET_PREVIOUS = 3076;
    private static final int BRACKET_NEXT = 3077;
    private static final int PRACTICAL_PASS = 3078;
    private static final int PRACTICAL_FAIL = 3079;
    private static final int TOURNAMENT_START = 3080;
    private static final int ANSWER_ROW_BASE = 3200;
    private static final int ANSWERS_PER_PAGE = 6;

    private final JsonObject draft;
    private final JsonObject snapshot;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int rankIndex;
    private int questionIndex;
    private int answerIndex;
    private int weaponIndex;
    private int arenaIndex;
    private int fighterAIndex;
    private int fighterBIndex = 1;
    private int bracketIndex;
    private GuiTextField titleField;
    private GuiTextField dateField;
    private GuiTextField timeField;
    private GuiTextField capacityField;
    private GuiTextField questionField;
    private GuiTextField answerField;
    private GuiTextField practicalField;

    public GuiExamEditor(JsonObject draft, JsonObject snapshot) {
        this.draft = copy(draft);
        this.snapshot = copy(snapshot);
        defaults();
        locateRank();
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.max(320, Math.min(840, width - 12));
        panelHeight = Math.max(260, Math.min(460, height - 12));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        buttonList.clear();
        clearFields();
        String[] tabs = pageLabels();
        page = Math.max(0, Math.min(page, tabs.length - 1));
        int tabWidth = (panelWidth - 16 - 4 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            ThemedButton button = button(PAGE_BASE + i, panelLeft + 8 + i * (tabWidth + 4),
                    panelTop + 36, tabWidth, 20, tabs[i], i == page ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.GOLD);
            button.setSelected(i == page);
            buttonList.add(button);
        }
        int contentTop = panelTop + 64;
        if (page == 0) setupPage(contentTop);
        else if (page == 1) questionPage(contentTop);
        else if (page == 2) rulesPage(contentTop);
        else if (pageKey(page).equals("progress")) progressPage(contentTop);
        else bracketPage(contentTop);
        buttonList.add(button(SAVE, panelLeft + panelWidth - 206, panelTop + panelHeight - 29,
                96, 20, text("gui.rebornaddon.exam_editor.save", "Save Exam"), Theme.TEAL_DARK, Theme.TEAL));
        buttonList.add(button(CANCEL, panelLeft + panelWidth - 104, panelTop + panelHeight - 29,
                96, 20, text("gui.rebornaddon.exam_editor.cancel", "Cancel"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void setupPage(int y) {
        titleField = field(3100, panelLeft + 18, y + 25, panelWidth - 36, 18, string(draft, "title"), 96);
        dateField = field(3101, panelLeft + 18, y + 72, (panelWidth - 42) / 2, 18,
                string(draft, "scheduleDate"), 10);
        timeField = field(3102, dateField.x + dateField.width + 6, y + 72,
                panelWidth - 24 - dateField.width, 18, string(draft, "scheduleTime"), 5);
        capacityField = field(3103, panelLeft + 18, y + 119, 84, 18,
                integer(draft, "capacity", 32) + "", 3);
        int buttonX = panelLeft + 116;
        int buttonWidth = panelWidth - 134;
        buttonList.add(button(RANK_PREVIOUS, buttonX, y + 117, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(RANK_NEXT, buttonX + buttonWidth - 24, y + 117, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void questionPage(int y) {
        JsonArray questions = array(draft, "questions");
        normalizeIndex(questions, true);
        int x = panelLeft + 18;
        int bodyWidth = panelWidth - 36;
        buttonList.add(button(QUESTION_PREVIOUS, x, y + 3, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(QUESTION_NEXT, x + 28, y + 3, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(QUESTION_ADD, x + 58, y + 3, 98, 20,
                trim(text("gui.rebornaddon.exam_editor.add_question", "Add Question"), 86), Theme.TEAL_DARK, Theme.TEAL));
        buttonList.add(button(QUESTION_DELETE, x + 162, y + 3, 104, 20,
                trim(text("gui.rebornaddon.exam_editor.delete_question", "Delete Question"), 92),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        JsonObject question = question();
        questionField = field(3110, x, y + 44, bodyWidth, 18, string(question, "text"), 512);
        int selectionWidth = (bodyWidth - 8) / 3;
        selectionButton(SELECTION_SINGLE, x, y + 68, selectionWidth, "single",
                text("gui.rebornaddon.exam_editor.selection_single", "Single Choice"), question);
        selectionButton(SELECTION_ANY, x + selectionWidth + 4, y + 68, selectionWidth, "any",
                text("gui.rebornaddon.exam_editor.selection_any_short", "Multiple: Any Correct"), question);
        selectionButton(SELECTION_ALL, x + (selectionWidth + 4) * 2, y + 68,
                bodyWidth - (selectionWidth + 4) * 2, "all",
                text("gui.rebornaddon.exam_editor.selection_all_short", "Multiple: All Correct"), question);
        JsonArray answers = array(question, "answers");
        normalizeAnswer(answers);
        int listWidth = Math.max(168, (bodyWidth - 8) * 48 / 100);
        int editorX = x + listWidth + 8;
        int editorWidth = bodyWidth - listWidth - 8;
        int answerY = y + (panelHeight < 350 ? 100 : 104);
        buttonList.add(button(ANSWER_PREVIOUS, x + listWidth - 52, answerY, 24, 18, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(ANSWER_NEXT, x + listWidth - 24, answerY, 24, 18, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        int rows = answersPerPage();
        int answerStart = answers.size() == 0 ? 0 : (answerIndex / rows) * rows;
        int answerEnd = Math.min(answers.size(), answerStart + rows);
        for (int i = answerStart; i < answerEnd; i++) {
            JsonObject rowAnswer = answers.get(i).getAsJsonObject();
            String state = bool(rowAnswer, "correct")
                    ? text("gui.rebornaddon.exam_editor.correct_short", "Correct")
                    : text("gui.rebornaddon.exam_editor.incorrect_short", "Incorrect");
            String rowText = (i + 1) + ". [" + state + "] " + string(rowAnswer, "text");
            ThemedButton row = button(ANSWER_ROW_BASE + i - answerStart, x,
                    answerY + 22 + (i - answerStart) * 22, listWidth, 19,
                    trim(rowText, listWidth - 14), i == answerIndex ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    i == answerIndex ? Theme.GOLD : Theme.NEUTRAL);
            row.setSelected(i == answerIndex);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(6);
            buttonList.add(row);
        }
        int actionWidth = (editorWidth - 4) / 2;
        buttonList.add(button(ANSWER_ADD, editorX, answerY, actionWidth, 20,
                text("gui.rebornaddon.exam_editor.add_answer", "Add Answer"), Theme.TEAL_DARK, Theme.TEAL));
        ThemedButton delete = button(ANSWER_DELETE, editorX + actionWidth + 4, answerY,
                editorWidth - actionWidth - 4, 20,
                text("gui.rebornaddon.exam_editor.delete_answer", "Delete Answer"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        delete.enabled = answers.size() > 0;
        buttonList.add(delete);
        JsonObject answer = answer();
        answerField = field(3111, editorX, answerY + 25, editorWidth, 18, string(answer, "text"), 256);
        answerField.setEnabled(answers.size() > 0);
        ThemedButton correct = button(ANSWER_CORRECT, editorX, answerY + 49, editorWidth, 20,
                bool(answer, "correct") ? text("gui.rebornaddon.exam_editor.correct", "Correct Answer")
                        : text("gui.rebornaddon.exam_editor.incorrect", "Incorrect Answer"),
                bool(answer, "correct") ? Theme.SUCCESS : Theme.TAB_INACTIVE_BG,
                bool(answer, "correct") ? Theme.SUCCESS_BRIGHT : Theme.NEUTRAL);
        correct.enabled = answers.size() > 0;
        buttonList.add(correct);
        if ("any".equals(string(question, "selectionMode"))) {
            buttonList.add(button(MAX_SELECTIONS, editorX, answerY + 71, editorWidth, 20,
                    text("gui.rebornaddon.exam_editor.max_choices", "Choices allowed") + ": "
                            + integer(question, "maxSelections", 1), Theme.TAB_INACTIVE_BG, Theme.GOLD));
        }
    }

    private void rulesPage(int y) {
        int x = panelLeft + 18;
        int w = panelWidth - 36;
        boolean compact = panelHeight < 350;
        int stageY = compact ? y + 14 : y + 24;
        int practicalY = compact ? y + 49 : y + 75;
        int rulesY = compact ? y + 83 : y + 116;
        int weaponY = compact ? y + 158 : y + 202;
        JsonArray stages = array(draft, "stages");
        int third = (w - 8) / 3;
        stageButton(STAGE_WRITTEN, x, stageY, third, "written",
                text("gui.rebornaddon.exam_editor.written", "Written"), stages);
        stageButton(STAGE_PRACTICAL, x + third + 4, stageY, third, "practical",
                text("gui.rebornaddon.exam_editor.practical", "Practical"), stages);
        stageButton(STAGE_TOURNAMENT, x + (third + 4) * 2, stageY,
                w - (third + 4) * 2, "tournament",
                text("gui.rebornaddon.exam_editor.tournament", "Tournament"), stages);
        practicalField = field(3120, x, practicalY, w, 18, string(draft, "practicalInstructions"), 512);
        JsonObject rules = object(draft, "tournamentRules");
        int half = (w - 4) / 2;
        ruleButton(RULE_NATURE, x, rulesY, half, "natureOnly",
                text("gui.rebornaddon.exam_editor.nature_only", "Nature Jutsu Only"), rules);
        ruleButton(RULE_KG, x + half + 4, rulesY, w - half - 4, "kekkeiGenkai",
                text("gui.rebornaddon.exam_editor.kg", "Kekkei Genkai"), rules);
        ruleButton(RULE_MODES, x, rulesY + 25, half, "modes",
                text("gui.rebornaddon.exam_editor.modes", "Modes"), rules);
        ruleButton(RULE_ARMOR, x + half + 4, rulesY + 25, w - half - 4, "armor",
                text("gui.rebornaddon.exam_editor.armor", "Armor"), rules);
        ruleButton(RULE_WEAPONS, x, rulesY + 50, half, "weapons",
                text("gui.rebornaddon.exam_editor.weapons", "Weapons"), rules);
        buttonList.add(button(WEAPON_ADD, x + half + 4, rulesY + 50, w - half - 4, 20,
                text("gui.rebornaddon.exam_editor.add_held_weapon", "Add Held Weapon"),
                Theme.TEAL_DARK, Theme.TEAL));
        buttonList.add(button(WEAPON_PREVIOUS, x, weaponY, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(WEAPON_NEXT, x + 28, weaponY, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(WEAPON_REMOVE, x + w - 122, weaponY, 122, 20,
                text("gui.rebornaddon.exam_editor.remove_weapon", "Remove Weapon"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    private void progressPage(int y) {
        int x = panelLeft + 18;
        int w = panelWidth - 36;
        JsonArray participants = array(draft, "participants");
        fighterAIndex = normalize(fighterAIndex, participants.size());
        buttonList.add(button(FIGHTER_A_PREVIOUS, x, y + 34, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(FIGHTER_A_NEXT, x + w - 24, y + 34, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        if (participants.size() == 0) return;
        JsonObject participant = participants.get(fighterAIndex).getAsJsonObject();
        if (index(array(draft, "stages"), "practical") >= 0) {
            int half = (w - 4) / 2;
            buttonList.add(button(PRACTICAL_PASS, x, y + 126, half, 20,
                    text("gui.rebornaddon.exam_editor.pass_practical", "Pass Practical"),
                    Theme.SUCCESS, Theme.SUCCESS_BRIGHT));
            buttonList.add(button(PRACTICAL_FAIL, x + half + 4, y + 126, w - half - 4, 20,
                    text("gui.rebornaddon.exam_editor.fail_practical", "Fail Practical"),
                    Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        }
    }

    private void bracketPage(int y) {
        int x = panelLeft + 18;
        int w = panelWidth - 36;
        boolean compact = panelHeight < 350;
        int arenaY = compact ? y + 15 : y + 27;
        int fightersY = compact ? y + 55 : y + 85;
        int addY = compact ? y + 82 : y + 116;
        int bracketY = compact ? y + 118 : y + 163;
        JsonArray arenas = array(snapshot, "arenas");
        arenaIndex = normalize(arenaIndex, arenas.size());
        buttonList.add(button(ARENA_PREVIOUS, x, arenaY, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(ARENA_NEXT, x + w - 24, arenaY, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        JsonArray participants = eligibleParticipants();
        fighterAIndex = normalize(fighterAIndex, participants.size());
        fighterBIndex = normalize(fighterBIndex, participants.size());
        int half = (w - 8) / 2;
        buttonList.add(button(FIGHTER_A_PREVIOUS, x, fightersY, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(FIGHTER_A_NEXT, x + half - 24, fightersY, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(FIGHTER_B_PREVIOUS, x + half + 8, fightersY, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(FIGHTER_B_NEXT, x + w - 24, fightersY, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        JsonArray bracket = array(draft, "bracket");
        boolean persisted = persistedBracket(bracket);
        ThemedButton addMatch = button(BRACKET_ADD, x, addY, w, 20,
                text("gui.rebornaddon.exam_editor.add_match", "Add Bracket Match"),
                Theme.TEAL_DARK, Theme.TEAL);
        addMatch.enabled = !persisted && participants.size() >= 2;
        buttonList.add(addMatch);
        buttonList.add(button(BRACKET_PREVIOUS, x, bracketY, 24, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        buttonList.add(button(BRACKET_NEXT, x + 28, bracketY, 24, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
        ThemedButton removeMatch = button(BRACKET_REMOVE, x + w - 122, bracketY, 122, 20,
                text("gui.rebornaddon.exam_editor.remove_match", "Remove Match"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED);
        removeMatch.enabled = !persisted && bracket.size() > 0;
        buttonList.add(removeMatch);
        JsonObject selectedMatch = bracket.size() == 0 ? null
                : bracket.get(normalize(bracketIndex, bracket.size())).getAsJsonObject();
        ThemedButton bracketAction = button(TOURNAMENT_START, x,
                compact ? y + 146 : y + 192, w, 20,
                persisted ? text("gui.rebornaddon.exam_editor.start_match", "Start Selected Match")
                        : text("gui.rebornaddon.exam_editor.create_bracket", "Create Bracket"),
                persisted ? Theme.GOLD_DARK : Theme.TEAL_DARK,
                persisted ? Theme.GOLD : Theme.TEAL);
        bracketAction.enabled = persisted ? selectedMatch != null
                && !string(selectedMatch, "id").isEmpty()
                && "pending".equals(string(selectedMatch, "status")) : bracket.size() > 0;
        buttonList.add(bracketAction);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        captureFields();
        int id = button.id;
        if (id >= PAGE_BASE && id < PAGE_BASE + pageLabels().length) page = id - PAGE_BASE;
        else if (id == CANCEL) { mc.displayGuiScreen(new GuiHub("exams")); return; }
        else if (id == SAVE) { save(); return; }
        else if (id == RANK_PREVIOUS || id == RANK_NEXT) changeRank(id == RANK_PREVIOUS ? -1 : 1);
        else if (id >= STAGE_WRITTEN && id <= STAGE_TOURNAMENT) toggleStage(id);
        else if (id == QUESTION_PREVIOUS || id == QUESTION_NEXT) changeQuestion(id == QUESTION_PREVIOUS ? -1 : 1);
        else if (id == QUESTION_ADD) addQuestion();
        else if (id == QUESTION_DELETE) remove(array(draft, "questions"), questionIndex--);
        else if (id == ANSWER_PREVIOUS || id == ANSWER_NEXT) changeAnswer(id == ANSWER_PREVIOUS ? -1 : 1);
        else if (id == ANSWER_ADD) addAnswer();
        else if (id == ANSWER_DELETE) remove(array(question(), "answers"), answerIndex--);
        else if (id >= ANSWER_ROW_BASE && id < ANSWER_ROW_BASE + answersPerPage()) {
            int rows = answersPerPage();
            int start = answerIndex / rows * rows;
            int selectedAnswer = start + id - ANSWER_ROW_BASE;
            if (selectedAnswer < array(question(), "answers").size()) answerIndex = selectedAnswer;
        }
        else if (id == ANSWER_CORRECT) toggle(answer(), "correct");
        else if (id == SELECTION_SINGLE) setSelection("single");
        else if (id == SELECTION_ANY) setSelection("any");
        else if (id == SELECTION_ALL) setSelection("all");
        else if (id == MAX_SELECTIONS) cycleMaxSelections();
        else if (id >= RULE_NATURE && id <= RULE_WEAPONS) toggleRule(id);
        else if (id == WEAPON_ADD) addHeldWeapon();
        else if (id == WEAPON_REMOVE) remove(array(object(draft, "tournamentRules"), "allowedWeapons"), weaponIndex--);
        else if (id == WEAPON_PREVIOUS || id == WEAPON_NEXT) weaponIndex += id == WEAPON_PREVIOUS ? -1 : 1;
        else if (id == ARENA_PREVIOUS || id == ARENA_NEXT) changeArena(id == ARENA_PREVIOUS ? -1 : 1);
        else if (id == FIGHTER_A_PREVIOUS || id == FIGHTER_A_NEXT) fighterAIndex += id == FIGHTER_A_PREVIOUS ? -1 : 1;
        else if (id == FIGHTER_B_PREVIOUS || id == FIGHTER_B_NEXT) fighterBIndex += id == FIGHTER_B_PREVIOUS ? -1 : 1;
        else if (id == BRACKET_ADD) addBracket();
        else if (id == BRACKET_REMOVE) remove(array(draft, "bracket"), bracketIndex--);
        else if (id == BRACKET_PREVIOUS || id == BRACKET_NEXT) bracketIndex += id == BRACKET_PREVIOUS ? -1 : 1;
        else if (id == PRACTICAL_PASS || id == PRACTICAL_FAIL) sendPracticalResult(id == PRACTICAL_PASS);
        else if (id == TOURNAMENT_START) { sendTournamentStart(); return; }
        initGui();
    }

    private void save() {
        captureFields();
        JsonArray ranks = array(snapshot, "rankCatalog");
        if (ranks.size() > 0) {
            JsonObject rank = ranks.get(normalize(rankIndex, ranks.size())).getAsJsonObject();
            draft.addProperty("rankId", string(rank, "id"));
            draft.addProperty("rankName", string(rank, "name"));
        }
        JsonArray arenas = array(snapshot, "arenas");
        if (arenas.size() > 0) draft.addProperty("arenaId",
                string(arenas.get(normalize(arenaIndex, arenas.size())).getAsJsonObject(), "id"));
        String action = string(draft, "id").isEmpty() ? "exam.create" : "exam.update";
        RebornAddonNetwork.sendExamAction(action, draft.toString());
        mc.displayGuiScreen(new GuiHub("exams"));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (fieldType(titleField, typedChar, keyCode) || fieldType(dateField, typedChar, keyCode)
                || fieldType(timeField, typedChar, keyCode) || fieldType(capacityField, typedChar, keyCode)
                || fieldType(questionField, typedChar, keyCode) || fieldType(answerField, typedChar, keyCode)
                || fieldType(practicalField, typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        click(titleField, mouseX, mouseY, mouseButton);
        click(dateField, mouseX, mouseY, mouseButton);
        click(timeField, mouseX, mouseY, mouseButton);
        click(capacityField, mouseX, mouseY, mouseButton);
        click(questionField, mouseX, mouseY, mouseButton);
        click(answerField, mouseX, mouseY, mouseButton);
        click(practicalField, mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        update(titleField); update(dateField); update(timeField); update(capacityField);
        update(questionField); update(answerField); update(practicalField);
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(panelLeft, panelTop, panelWidth, panelHeight, Theme.GOLD_DARK);
        GuiChrome.header(panelLeft + 5, panelTop + 7, panelWidth - 10, 25, Theme.GOLD_DARK);
        fontRenderer.drawString(text("gui.rebornaddon.exam_editor.title", "Exam Builder"),
                panelLeft + 17, panelTop + 15, Theme.TEXT_LIGHT);
        int y = panelTop + 64;
        GuiChrome.section(panelLeft + 8, y, panelWidth - 16, panelHeight - 101, Theme.GOLD_DARK);
        if (page == 0) drawSetup(y);
        else if (page == 1) drawQuestions(y);
        else if (page == 2) drawRules(y);
        else if (pageKey(page).equals("progress")) drawProgress(y);
        else drawBracket(y);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawSetup(int y) {
        label("gui.rebornaddon.exam_editor.exam_name", "Exam name", panelLeft + 18, y + 12);
        titleField.drawTextBox();
        label("gui.rebornaddon.exam_editor.date", "Server date (YYYY-MM-DD)", panelLeft + 18, y + 59);
        label("gui.rebornaddon.exam_editor.time", "Server time (HH:MM)", timeField.x, y + 59);
        dateField.drawTextBox(); timeField.drawTextBox();
        label("gui.rebornaddon.exam_editor.capacity", "Maximum participants", panelLeft + 18, y + 106);
        capacityField.drawTextBox();
        JsonArray ranks = array(snapshot, "rankCatalog");
        String rank = ranks.size() == 0 ? text("gui.rebornaddon.exam_editor.no_ranks", "No rank catalog")
                : string(ranks.get(normalize(rankIndex, ranks.size())).getAsJsonObject(), "name");
        int rankX = panelLeft + 144;
        int rankWidth = panelWidth - 190;
        String rankLabel = trim(text("gui.rebornaddon.exam_editor.target_rank", "Passing rank") + ": " + rank,
                rankWidth);
        fontRenderer.drawString(rankLabel, rankX + (rankWidth - fontRenderer.getStringWidth(rankLabel)) / 2,
                y + 123, Theme.GOLD);
        fontRenderer.drawSplitString(text("gui.rebornaddon.exam_editor.schedule_help",
                        "Operator exams are approved immediately. Leadership exams must be approved before their schedule becomes active."),
                panelLeft + 18, y + 157, panelWidth - 36, Theme.TEXT_MUTED);
    }

    private void drawQuestions(int y) {
        JsonArray questions = array(draft, "questions");
        JsonArray answers = array(question(), "answers");
        String questionCount = text("gui.rebornaddon.exam_editor.question_count", "Question") + " "
                        + (questions.size() == 0 ? 0 : normalize(questionIndex, questions.size()) + 1)
                        + " / " + questions.size();
        fontRenderer.drawString(questionCount, panelLeft + panelWidth - 18
                - fontRenderer.getStringWidth(questionCount), y + 31, Theme.GOLD);
        label("gui.rebornaddon.exam_editor.question_text", "Question text", panelLeft + 18, y + 31);
        questionField.drawTextBox();
        int bodyWidth = panelWidth - 36;
        int listWidth = Math.max(168, (bodyWidth - 8) * 48 / 100);
        int editorX = panelLeft + 18 + listWidth + 8;
        int answerY = y + (panelHeight < 350 ? 100 : 104);
        fontRenderer.drawString(text("gui.rebornaddon.exam_editor.answers", "Answers"),
                panelLeft + 18, answerY - 10, Theme.GOLD);
        String answerCount = text("gui.rebornaddon.exam_editor.answer_count", "Selected answer") + " "
                        + (answers.size() == 0 ? 0 : normalize(answerIndex, answers.size()) + 1)
                        + " / " + answers.size();
        fontRenderer.drawString(trim(answerCount, panelLeft + panelWidth - 18 - editorX),
                editorX, answerY - 10, Theme.GOLD);
        answerField.drawTextBox();
        if (panelHeight >= 350) {
            fontRenderer.drawSplitString(selectionHelp(question()), editorX, answerY + 101,
                    panelLeft + panelWidth - 18 - editorX, Theme.TEXT_MUTED);
        }
    }

    private void drawRules(int y) {
        boolean compact = panelHeight < 350;
        label("gui.rebornaddon.exam_editor.stages", "Included stages", panelLeft + 18,
                y + (compact ? 3 : 11));
        label("gui.rebornaddon.exam_editor.practical_notes", "Practical stage instructions", panelLeft + 18,
                y + (compact ? 37 : 62));
        practicalField.drawTextBox();
        label("gui.rebornaddon.exam_editor.tournament_rules", "Tournament rules", panelLeft + 18,
                y + (compact ? 71 : 103));
        JsonArray allowed = array(object(draft, "tournamentRules"), "allowedWeapons");
        weaponIndex = normalize(weaponIndex, allowed.size());
        String weapon = allowed.size() == 0 ? text("gui.rebornaddon.exam_editor.no_weapon_limit", "No weapon whitelist entries")
                : weaponName(allowed.get(weaponIndex).getAsString());
        fontRenderer.drawString(trim(weapon, panelWidth - 190), panelLeft + 79,
                y + (compact ? 164 : 208), Theme.TEXT_LIGHT);
    }

    private void drawProgress(int y) {
        JsonArray participants = array(draft, "participants");
        label("gui.rebornaddon.exam_editor.stage_progress", "Stage progress", panelLeft + 18, y + 12);
        if (participants.size() == 0) {
            fontRenderer.drawSplitString(text("gui.rebornaddon.exam_editor.no_enrolled",
                            "No players are enrolled in this exam yet."), panelLeft + 18, y + 38,
                    panelWidth - 36, Theme.TEXT_MUTED);
            return;
        }
        JsonObject participant = participants.get(normalize(fighterAIndex, participants.size())).getAsJsonObject();
        String name = string(participant, "name");
        String fit = trim(name, panelWidth - 120);
        fontRenderer.drawString(fit, panelLeft + (panelWidth - fontRenderer.getStringWidth(fit)) / 2,
                y + 40, Theme.TEXT_LIGHT);
        JsonArray stages = array(draft, "stages");
        JsonArray passed = array(participant, "passedStages");
        JsonArray failed = array(participant, "failedStages");
        int stageY = y + 72;
        for (JsonElement stageValue : stages) {
            String stage = stageValue.getAsString();
            boolean didPass = index(passed, stage) >= 0;
            boolean didFail = index(failed, stage) >= 0;
            String state = didPass ? text("gui.rebornaddon.exam_editor.passed", "Passed")
                    : didFail ? text("gui.rebornaddon.exam_editor.failed", "Failed")
                    : text("gui.rebornaddon.exam_editor.pending", "Pending");
            int color = didPass ? Theme.SUCCESS_BRIGHT : didFail ? Theme.DANGER_BRIGHT : Theme.GOLD;
            fontRenderer.drawString(display(stage) + ": " + state, panelLeft + 28, stageY, color);
            stageY += 15;
        }
        fontRenderer.drawSplitString(text("gui.rebornaddon.exam_editor.progress_help",
                        "Written results are scored automatically. Mark practical results here; tournament brackets become available only after earlier stages are passed."),
                panelLeft + 18, y + 168, panelWidth - 36, Theme.TEXT_MUTED);
    }

    private void drawBracket(int y) {
        boolean compact = panelHeight < 350;
        JsonArray arenas = array(snapshot, "arenas");
        String arena = arenas.size() == 0 ? text("gui.rebornaddon.exam_editor.no_arenas", "No tournament arenas configured")
                : string(arenas.get(normalize(arenaIndex, arenas.size())).getAsJsonObject(), "name");
        label("gui.rebornaddon.exam_editor.arena", "Tournament arena", panelLeft + 18,
                y + (compact ? 3 : 11));
        centered(arena, y + (compact ? 21 : 33));
        JsonArray participants = eligibleParticipants();
        label("gui.rebornaddon.exam_editor.pairing", "Bracket pairing", panelLeft + 18,
                y + (compact ? 43 : 68));
        String first = participant(participants, fighterAIndex);
        String second = participant(participants, fighterBIndex);
        fontRenderer.drawString(trim(first, (panelWidth - 56) / 2), panelLeft + 48,
                y + (compact ? 61 : 91), Theme.TEXT_LIGHT);
        fontRenderer.drawString(trim(second, (panelWidth - 56) / 2), panelLeft + panelWidth / 2 + 30,
                y + (compact ? 61 : 91), Theme.TEXT_LIGHT);
        JsonArray bracket = array(draft, "bracket");
        bracketIndex = normalize(bracketIndex, bracket.size());
        label("gui.rebornaddon.exam_editor.saved_matches", "Saved matches", panelLeft + 18,
                y + (compact ? 107 : 149));
        String pairing = bracket.size() == 0 ? text("gui.rebornaddon.exam_editor.no_matches", "No matches assigned")
                : matchName(bracket.get(bracketIndex).getAsJsonObject());
        fontRenderer.drawString(trim(pairing, panelWidth - 190), panelLeft + 79,
                y + (compact ? 124 : 169), Theme.TEXT_LIGHT);
        fontRenderer.drawSplitString(text("gui.rebornaddon.exam_editor.bracket_help",
                        "Participants ready themselves from the Exams tab. The server handles warps, countdowns, arena boundaries, and selected rules."),
                panelLeft + 18, y + (compact ? 174 : 225), panelWidth - 36, Theme.TEXT_MUTED);
    }

    private void captureFields() {
        if (titleField != null) draft.addProperty("title", titleField.getText().trim());
        if (dateField != null) draft.addProperty("scheduleDate", dateField.getText().trim());
        if (timeField != null) draft.addProperty("scheduleTime", timeField.getText().trim());
        if (capacityField != null) draft.addProperty("capacity", number(capacityField.getText(), 32, 2, 256));
        if (questionField != null) question().addProperty("text", questionField.getText().trim());
        if (answerField != null) answer().addProperty("text", answerField.getText().trim());
        if (practicalField != null) draft.addProperty("practicalInstructions", practicalField.getText().trim());
    }

    private void defaults() {
        if (!draft.has("title")) draft.addProperty("title", "");
        if (!draft.has("scheduleDate")) draft.addProperty("scheduleDate", "");
        if (!draft.has("scheduleTime")) draft.addProperty("scheduleTime", "");
        if (!draft.has("capacity")) draft.addProperty("capacity", 32);
        if (!draft.has("questions")) draft.add("questions", new JsonArray());
        if (!draft.has("stages")) {
            JsonArray stages = new JsonArray(); stages.add("written"); draft.add("stages", stages);
        }
        if (!draft.has("tournamentRules")) {
            JsonObject rules = new JsonObject();
            rules.addProperty("natureOnly", false); rules.addProperty("kekkeiGenkai", true);
            rules.addProperty("modes", true); rules.addProperty("armor", true);
            rules.addProperty("weapons", true); rules.add("allowedWeapons", new JsonArray());
            draft.add("tournamentRules", rules);
        }
        if (!draft.has("bracket")) draft.add("bracket", new JsonArray());
    }

    private void locateRank() {
        JsonArray ranks = array(snapshot, "rankCatalog");
        String wanted = string(draft, "rankId");
        for (int i = 0; i < ranks.size(); i++) if (wanted.equals(string(ranks.get(i).getAsJsonObject(), "id"))) rankIndex = i;
        JsonArray arenas = array(snapshot, "arenas");
        String arena = string(draft, "arenaId");
        for (int i = 0; i < arenas.size(); i++) if (arena.equals(string(arenas.get(i).getAsJsonObject(), "id"))) arenaIndex = i;
    }

    private void addQuestion() {
        JsonObject question = new JsonObject();
        question.addProperty("id", UUID.randomUUID().toString());
        question.addProperty("text", "");
        question.addProperty("selectionMode", "single");
        question.addProperty("maxSelections", 1);
        question.add("answers", new JsonArray());
        array(draft, "questions").add(question);
        questionIndex = array(draft, "questions").size() - 1;
        answerIndex = 0;
    }

    private void addAnswer() {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", UUID.randomUUID().toString());
        answer.addProperty("text", "");
        answer.addProperty("correct", false);
        array(question(), "answers").add(answer);
        answerIndex = array(question(), "answers").size() - 1;
    }

    private void addHeldWeapon() {
        ItemStack held = Minecraft.getMinecraft().player == null ? ItemStack.EMPTY
                : Minecraft.getMinecraft().player.getHeldItemMainhand();
        ResourceLocation id = held.isEmpty() ? null : held.getItem().getRegistryName();
        if (id == null) return;
        JsonArray values = array(object(draft, "tournamentRules"), "allowedWeapons");
        for (JsonElement value : values) if (id.toString().equals(value.getAsString())) return;
        values.add(id.toString());
        weaponIndex = values.size() - 1;
    }

    private void addBracket() {
        JsonArray participants = eligibleParticipants();
        if (participants.size() < 2) return;
        JsonObject first = participants.get(normalize(fighterAIndex, participants.size())).getAsJsonObject();
        JsonObject second = participants.get(normalize(fighterBIndex, participants.size())).getAsJsonObject();
        String firstId = string(first, "uuid");
        String secondId = string(second, "uuid");
        if (firstId.equals(secondId) || bracketContains(firstId) || bracketContains(secondId)) return;
        JsonObject pair = new JsonObject();
        pair.addProperty("fighterA", firstId);
        pair.addProperty("fighterAName", string(first, "name"));
        pair.addProperty("fighterB", secondId);
        pair.addProperty("fighterBName", string(second, "name"));
        array(draft, "bracket").add(pair);
        bracketIndex = array(draft, "bracket").size() - 1;
    }

    private void sendPracticalResult(boolean passed) {
        JsonArray participants = array(draft, "participants");
        if (participants.size() == 0) return;
        JsonObject participant = participants.get(normalize(fighterAIndex, participants.size())).getAsJsonObject();
        JsonObject payload = new JsonObject();
        payload.addProperty("examId", string(draft, "id"));
        payload.addProperty("playerId", string(participant, "uuid"));
        payload.addProperty("stage", "practical");
        payload.addProperty("passed", passed);
        RebornAddonNetwork.sendExamAction("exam.stage.result", payload.toString());
        JsonArray passedStages = array(participant, "passedStages");
        JsonArray failedStages = array(participant, "failedStages");
        removeValue(passedStages, "practical");
        removeValue(failedStages, "practical");
        (passed ? passedStages : failedStages).add("practical");
    }

    private void sendTournamentStart() {
        captureFields();
        String examId = string(draft, "id");
        JsonArray bracket = array(draft, "bracket");
        if (examId.isEmpty() || bracket.size() == 0) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("examId", examId);
        if (persistedBracket(bracket)) {
            JsonObject match = bracket.get(normalize(bracketIndex, bracket.size())).getAsJsonObject();
            String matchId = string(match, "id");
            if (matchId.isEmpty()) return;
            payload.addProperty("matchId", matchId);
            RebornAddonNetwork.sendExamAction("tournament.start", payload.toString());
        } else {
            JsonArray fighters = new JsonArray();
            for (JsonElement value : bracket) {
                if (!value.isJsonObject()) continue;
                JsonObject pair = value.getAsJsonObject();
                addUnique(fighters, string(pair, "fighterA"));
                addUnique(fighters, string(pair, "fighterB"));
            }
            for (JsonElement value : eligibleParticipants()) {
                if (value.isJsonObject()) addUnique(fighters, string(value.getAsJsonObject(), "uuid"));
            }
            if (fighters.size() < 2) return;
            payload.add("fighters", fighters);
            RebornAddonNetwork.sendExamAction("tournament.bracket", payload.toString());
        }
        mc.displayGuiScreen(new GuiHub("exams"));
    }

    private void toggleStage(int id) {
        String stage = id == STAGE_WRITTEN ? "written" : id == STAGE_PRACTICAL ? "practical" : "tournament";
        JsonArray stages = array(draft, "stages");
        int index = index(stages, stage);
        if (index >= 0) { if (stages.size() > 1) stages.remove(index); }
        else stages.add(stage);
        boolean written = index(stages, "written") >= 0;
        boolean practical = index(stages, "practical") >= 0;
        boolean tournament = index(stages, "tournament") >= 0;
        JsonArray ordered = new JsonArray();
        if (written) ordered.add("written");
        if (practical) ordered.add("practical");
        if (tournament) ordered.add("tournament");
        draft.add("stages", ordered);
    }

    private void toggleRule(int id) {
        String key = id == RULE_NATURE ? "natureOnly" : id == RULE_KG ? "kekkeiGenkai"
                : id == RULE_MODES ? "modes" : id == RULE_ARMOR ? "armor" : "weapons";
        toggle(object(draft, "tournamentRules"), key);
    }

    private void setSelection(String mode) {
        JsonObject question = question();
        question.addProperty("selectionMode", mode);
        if ("single".equals(mode)) question.addProperty("maxSelections", 1);
    }

    private void cycleMaxSelections() {
        JsonObject question = question();
        int answers = Math.max(1, array(question, "answers").size());
        int next = integer(question, "maxSelections", 1) + 1;
        question.addProperty("maxSelections", next > answers ? 1 : next);
    }

    private void changeRank(int direction) { rankIndex = normalize(rankIndex + direction, array(snapshot, "rankCatalog").size()); }
    private void changeArena(int direction) { arenaIndex = normalize(arenaIndex + direction, array(snapshot, "arenas").size()); }
    private void changeQuestion(int direction) { questionIndex = normalize(questionIndex + direction, array(draft, "questions").size()); answerIndex = 0; }
    private void changeAnswer(int direction) { answerIndex = normalize(answerIndex + direction, array(question(), "answers").size()); }

    private JsonObject question() {
        JsonArray values = array(draft, "questions");
        if (values.size() == 0) return emptyQuestion();
        questionIndex = normalize(questionIndex, values.size());
        return values.get(questionIndex).getAsJsonObject();
    }

    private JsonObject answer() {
        JsonArray values = array(question(), "answers");
        if (values.size() == 0) return emptyAnswer();
        answerIndex = normalize(answerIndex, values.size());
        return values.get(answerIndex).getAsJsonObject();
    }

    private JsonObject emptyQuestion() {
        JsonObject value = new JsonObject(); value.add("answers", new JsonArray());
        value.addProperty("selectionMode", "single"); value.addProperty("maxSelections", 1); return value;
    }
    private JsonObject emptyAnswer() { JsonObject value = new JsonObject(); value.addProperty("correct", false); return value; }

    private void normalizeIndex(JsonArray values, boolean unused) { questionIndex = normalize(questionIndex, values.size()); }
    private void normalizeAnswer(JsonArray values) { answerIndex = normalize(answerIndex, values.size()); }
    private static int normalize(int value, int size) { return size <= 0 ? 0 : (value % size + size) % size; }
    private static void remove(JsonArray values, int index) { if (values.size() > 0) values.remove(normalize(index, values.size())); }
    private static void toggle(JsonObject object, String key) { object.addProperty(key, !bool(object, key)); }

    private void stageButton(int id, int x, int y, int width, String key, String label, JsonArray stages) {
        boolean enabled = index(stages, key) >= 0;
        String state = enabled ? text("gui.rebornaddon.exam_editor.included", "Included")
                : text("gui.rebornaddon.exam_editor.not_included", "Not Included");
        String full = label + ": " + state;
        if (fontRenderer.getStringWidth(full) > width - 12) {
            full = label + ": " + (enabled
                    ? text("gui.rebornaddon.exam_editor.included_short", "On")
                    : text("gui.rebornaddon.exam_editor.not_included_short", "Off"));
        }
        ThemedButton button = button(id, x, y, width, 20, trim(full, width - 12),
                enabled ? Theme.SUCCESS : Theme.RANKED_RED_DARK,
                enabled ? Theme.SUCCESS_BRIGHT : Theme.RANKED_RED);
        button.setSelected(enabled); buttonList.add(button);
    }

    private void selectionButton(int id, int x, int y, int width, String mode,
                                 String label, JsonObject question) {
        boolean selected = mode.equals(string(question, "selectionMode"));
        ThemedButton button = button(id, x, y, width, 20, trim(label, width - 12),
                selected ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG, Theme.TEAL);
        button.setSelected(selected);
        buttonList.add(button);
    }

    private void ruleButton(int id, int x, int y, int width, String key, String label, JsonObject rules) {
        boolean enabled = bool(rules, key);
        ThemedButton button = button(id, x, y, width, 20,
                label + ": " + (enabled ? text("gui.rebornaddon.common.allowed", "Allowed")
                : text("gui.rebornaddon.common.blocked", "Blocked")),
                enabled ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK, enabled ? Theme.TEAL : Theme.RANKED_RED);
        button.setSelected(enabled); buttonList.add(button);
    }

    private static String selectionLabel(JsonObject question) {
        String mode = string(question, "selectionMode");
        return "any".equals(mode) ? text("gui.rebornaddon.exam_editor.selection_any", "Any Correct Answer")
                : "all".equals(mode) ? text("gui.rebornaddon.exam_editor.selection_all", "All Correct Answers")
                : text("gui.rebornaddon.exam_editor.selection_single", "Single Choice");
    }

    private static String selectionHelp(JsonObject question) {
        String mode = string(question, "selectionMode");
        return "any".equals(mode) ? text("gui.rebornaddon.exam_editor.selection_any_help",
                "The player may select one or more marked answers; every selected answer must be correct.")
                : "all".equals(mode) ? text("gui.rebornaddon.exam_editor.selection_all_help",
                "The player's selected answers must exactly match every answer marked correct.")
                : text("gui.rebornaddon.exam_editor.selection_single_help",
                "The player may choose one answer. Mark one answer as correct.");
    }

    private String participant(JsonArray participants, int index) {
        return participants.size() == 0 ? text("gui.rebornaddon.exam_editor.no_participants", "No enrolled participants")
                : string(participants.get(normalize(index, participants.size())).getAsJsonObject(), "name");
    }

    private String[] pageLabels() {
        boolean existing = !string(draft, "id").isEmpty();
        int count = existing ? canManageBracket() ? 5 : 4 : 3;
        String[] result = new String[count];
        result[0] = text("gui.rebornaddon.exam_editor.setup", "Setup");
        result[1] = text("gui.rebornaddon.exam_editor.questions", "Questions");
        result[2] = text("gui.rebornaddon.exam_editor.rules", "Stages & Rules");
        if (existing) result[3] = text("gui.rebornaddon.exam_editor.progress", "Progress");
        if (count == 5) result[4] = text("gui.rebornaddon.exam_editor.bracket", "Bracket");
        return result;
    }

    private String pageKey(int index) {
        if (index == 0) return "setup";
        if (index == 1) return "questions";
        if (index == 2) return "rules";
        if (!string(draft, "id").isEmpty() && index == 3) return "progress";
        return "bracket";
    }

    private boolean canManageBracket() {
        return !string(draft, "id").isEmpty()
                && index(array(draft, "stages"), "tournament") >= 0
                && eligibleParticipants().size() >= 2;
    }

    private JsonArray eligibleParticipants() {
        JsonArray eligible = new JsonArray();
        JsonArray stages = array(draft, "stages");
        if (index(stages, "tournament") < 0) return eligible;
        for (JsonElement value : array(draft, "participants")) {
            if (!value.isJsonObject()) continue;
            JsonObject participant = value.getAsJsonObject();
            JsonArray passed = array(participant, "passedStages");
            boolean qualified = false;
            for (JsonElement stageValue : stages) {
                String stage = stageValue.getAsString();
                if ("tournament".equals(stage)) {
                    qualified = true;
                    break;
                }
                if (index(passed, stage) < 0) break;
            }
            if (qualified) eligible.add(participant);
        }
        return eligible;
    }

    private boolean bracketContains(String playerId) {
        if (playerId == null || playerId.isEmpty()) return false;
        for (JsonElement value : array(draft, "bracket")) {
            if (!value.isJsonObject()) continue;
            JsonObject match = value.getAsJsonObject();
            if (playerId.equals(string(match, "fighterA"))
                    || playerId.equals(string(match, "fighterB"))) return true;
        }
        return false;
    }

    private String matchName(JsonObject match) {
        String first = participantName(string(match, "fighterA"), string(match, "fighterAName"));
        String secondId = string(match, "fighterB");
        String second = secondId.isEmpty() ? text("gui.rebornaddon.exam_editor.bye", "Bye")
                : participantName(secondId, string(match, "fighterBName"));
        String status = display(string(match, "status"));
        return first + "  vs  " + second + ("-".equals(status) ? "" : "  -  " + status);
    }

    private String participantName(String playerId, String fallback) {
        for (JsonElement value : array(draft, "participants")) {
            if (!value.isJsonObject()) continue;
            JsonObject participant = value.getAsJsonObject();
            if (playerId.equals(string(participant, "uuid"))) return string(participant, "name");
        }
        return fallback == null || fallback.isEmpty() ? playerId : fallback;
    }

    private static boolean persistedBracket(JsonArray bracket) {
        for (JsonElement value : bracket) {
            if (value.isJsonObject() && !string(value.getAsJsonObject(), "id").isEmpty()) return true;
        }
        return false;
    }

    private static void addUnique(JsonArray values, String playerId) {
        if (playerId == null || playerId.isEmpty() || index(values, playerId) >= 0) return;
        values.add(playerId);
    }

    private void clearFields() {
        titleField = null;
        dateField = null;
        timeField = null;
        capacityField = null;
        questionField = null;
        answerField = null;
        practicalField = null;
    }

    private int answersPerPage() {
        return panelHeight < 350 ? 3 : ANSWERS_PER_PAGE;
    }

    private void centered(String value, int y) {
        String fit = trim(value, panelWidth - 110);
        fontRenderer.drawString(fit, panelLeft + (panelWidth - fontRenderer.getStringWidth(fit)) / 2, y, Theme.TEXT_LIGHT);
    }

    private void label(String key, String fallback, int x, int y) { fontRenderer.drawString(text(key, fallback), x, y, Theme.TEXT_MUTED); }
    private String trim(String value, int width) { return fontRenderer.getStringWidth(value) <= width ? value : fontRenderer.trimStringToWidth(value, Math.max(8, width - 12)) + "..."; }
    private ThemedButton button(int id, int x, int y, int w, int h, String label, int bg, int hover) { return new ThemedButton(id, x, y, w, h, label, bg, hover, Theme.BUTTON_TEXT); }
    private GuiTextField field(int id, int x, int y, int w, int h, String value, int max) { GuiTextField field = new GuiTextField(id, fontRenderer, x, y, w, h); field.setMaxStringLength(max); field.setText(value == null ? "" : value); return field; }
    private static boolean fieldType(GuiTextField field, char typed, int key) { return field != null && field.textboxKeyTyped(typed, key); }
    private static void click(GuiTextField field, int x, int y, int button) { if (field != null) field.mouseClicked(x, y, button); }
    private static void update(GuiTextField field) { if (field != null) field.updateCursorCounter(); }
    private static int number(String value, int fallback, int min, int max) { try { return Math.max(min, Math.min(max, Integer.parseInt(value.trim()))); } catch (Exception ignored) { return fallback; } }
    private static int index(JsonArray values, String wanted) { for (int i = 0; i < values.size(); i++) if (wanted.equals(values.get(i).getAsString())) return i; return -1; }
    private static void removeValue(JsonArray values, String wanted) { int found = index(values, wanted); if (found >= 0) values.remove(found); }
    private static JsonObject object(JsonObject root, String key) { if (!root.has(key) || !root.get(key).isJsonObject()) root.add(key, new JsonObject()); return root.getAsJsonObject(key); }
    private static JsonArray array(JsonObject root, String key) { if (!root.has(key) || !root.get(key).isJsonArray()) root.add(key, new JsonArray()); return root.getAsJsonArray(key); }
    private static boolean bool(JsonObject object, String key) { return object != null && object.has(key) && object.get(key).getAsBoolean(); }
    private static int integer(JsonObject object, String key, int fallback) { return object != null && object.has(key) ? object.get(key).getAsInt() : fallback; }
    private static String string(JsonObject object, String key) { return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
    private static String text(String key, String fallback) { return ClientLocalization.format(key, fallback); }
    private static String display(String value) {
        if (value == null || value.isEmpty()) return "-";
        String clean = value.replace('_', ' ');
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }
    private static String weaponName(String registryName) {
        try {
            net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName));
            if (item != null) return new ItemStack(item).getDisplayName();
        } catch (Throwable ignored) {
        }
        return registryName;
    }
    private static JsonObject copy(JsonObject value) {
        return value == null ? new JsonObject()
                : new JsonParser().parse(value.toString()).getAsJsonObject();
    }
}
