package net.rebornaddon.exam;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

public final class ExamDraftValidator {
    private ExamDraftValidator() {
    }

    public static String validate(JsonObject draft) {
        if (draft == null) return "The exam draft is missing.";
        String title = string(draft, "title");
        if (title.length() < 3 || title.length() > 96) return "Exam names must be 3 to 96 characters.";
        if (string(draft, "rankId").isEmpty()) return "Choose the rank awarded by this exam.";
        int capacity = integer(draft, "capacity", 0);
        if (capacity < 2 || capacity > 256) return "Exam capacity must be between 2 and 256 players.";

        JsonArray stages = array(draft, "stages");
        Set<String> uniqueStages = new HashSet<String>();
        for (JsonElement element : stages) {
            String stage = element.getAsString();
            if (!"written".equals(stage) && !"practical".equals(stage) && !"tournament".equals(stage)) {
                return "The exam contains an unknown stage.";
            }
            uniqueStages.add(stage);
        }
        if (uniqueStages.size() < 1 || uniqueStages.size() > 3) return "Choose one to three exam stages.";
        if (uniqueStages.contains("written")) {
            String problem = validateQuestions(array(draft, "questions"));
            if (!problem.isEmpty()) return problem;
        }
        JsonArray weapons = array(object(draft, "tournamentRules"), "allowedWeapons");
        if (weapons.size() > 128) return "An exam can whitelist at most 128 weapons.";
        JsonArray bracket = array(draft, "bracket");
        if (bracket.size() > 128) return "An exam bracket can contain at most 128 matches.";
        return "";
    }

    private static String validateQuestions(JsonArray questions) {
        if (questions.size() < 1) return "Written exams need at least one question.";
        if (questions.size() > 200) return "Written exams can contain at most 200 questions.";
        for (int i = 0; i < questions.size(); i++) {
            if (!questions.get(i).isJsonObject()) return "Question " + (i + 1) + " is invalid.";
            JsonObject question = questions.get(i).getAsJsonObject();
            String text = string(question, "text");
            if (text.isEmpty() || text.length() > 512) return "Question " + (i + 1) + " needs valid text.";
            JsonArray answers = array(question, "answers");
            if (answers.size() < 2 || answers.size() > 32) {
                return "Question " + (i + 1) + " must have 2 to 32 answers.";
            }
            int correct = 0;
            Set<String> ids = new HashSet<String>();
            for (int answerIndex = 0; answerIndex < answers.size(); answerIndex++) {
                if (!answers.get(answerIndex).isJsonObject()) return "Question " + (i + 1) + " has an invalid answer.";
                JsonObject answer = answers.get(answerIndex).getAsJsonObject();
                String id = string(answer, "id");
                String answerText = string(answer, "text");
                if (id.isEmpty() || !ids.add(id) || answerText.isEmpty() || answerText.length() > 256) {
                    return "Question " + (i + 1) + " has an incomplete or duplicate answer.";
                }
                if (bool(answer, "correct")) correct++;
            }
            String mode = string(question, "selectionMode");
            if ("single".equals(mode) && correct != 1) {
                return "Single-choice question " + (i + 1) + " must have exactly one correct answer.";
            }
            if (!"single".equals(mode) && !"any".equals(mode) && !"all".equals(mode)) {
                return "Question " + (i + 1) + " has an unknown answer mode.";
            }
            if (correct < 1) return "Question " + (i + 1) + " needs a correct answer.";
            int maximum = integer(question, "maxSelections", 1);
            if (maximum < 1 || maximum > answers.size()) {
                return "Question " + (i + 1) + " has an invalid selection limit.";
            }
        }
        return "";
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

    private static int integer(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString().trim() : "";
    }
}
