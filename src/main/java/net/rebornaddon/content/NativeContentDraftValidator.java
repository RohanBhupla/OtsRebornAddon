package net.rebornaddon.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NativeContentDraftValidator {
    private static final Set<String> TYPES = new HashSet<String>(Arrays.asList(
            "templates", "actors", "quests", "bosses", "dungeons", "paths", "worldBossPools"));

    private NativeContentDraftValidator() {
    }

    public static List<String> validateUpsert(String payload) {
        List<String> errors = new ArrayList<String>();
        if (payload == null || payload.length() > 16 * 1024 * 1024) {
            errors.add("The draft payload is too large.");
            return errors;
        }
        JsonObject wrapper;
        try {
            JsonElement parsed = new JsonParser().parse(payload);
            wrapper = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException invalid) {
            wrapper = null;
        }
        if (wrapper == null) {
            errors.add("The draft payload is not valid JSON.");
            return errors;
        }
        String type = string(wrapper, "type");
        if (!TYPES.contains(type)) errors.add("Unknown native-content record type.");
        if (!wrapper.has("record") || !wrapper.get("record").isJsonObject()) {
            errors.add("The draft record is missing.");
            return errors;
        }
        JsonObject record = wrapper.getAsJsonObject("record");
        String id = string(record, "id").toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_.-]{1,96}")) errors.add("Record ID must contain 1-96 lowercase letters, numbers, dots, dashes, or underscores.");
        validateElement(record, "record", 0, errors);
        if (("templates".equals(type) || "actors".equals(type)) && record.has("skin")) {
            String skin = string(record, "skin");
            if ((skin.startsWith("http://") || skin.startsWith("https://"))
                    && !skin.startsWith("https://")) errors.add("Remote NPC skins must use HTTPS.");
        }
        return errors;
    }

    private static void validateElement(JsonElement element, String path, int depth, List<String> errors) {
        if (errors.size() >= 32) return;
        if (depth > 24) {
            errors.add(path + " is nested too deeply.");
            return;
        }
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() > 4096) errors.add(path + " has too many entries.");
            for (int i = 0; i < Math.min(array.size(), 4096); i++) {
                validateElement(array.get(i), path + "[" + i + "]", depth + 1, errors);
            }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                double number = value.getAsDouble();
                if (Double.isNaN(number) || Double.isInfinite(number)) errors.add(path + "." + key + " must be finite.");
                String normalized = key.toLowerCase(Locale.ROOT);
                if (normalized.contains("radius") && (number < 0.0D || number > 4096.0D)) {
                    errors.add(path + "." + key + " must be between 0 and 4096 blocks.");
                }
                if (("x".equalsIgnoreCase(key) || "z".equalsIgnoreCase(key)
                        || normalized.endsWith("x") || normalized.endsWith("z")) && Math.abs(number) > 30000000.0D) {
                    errors.add(path + "." + key + " is outside Minecraft's world border.");
                }
            }
            validateElement(value, path + "." + key, depth + 1, errors);
        }
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString().trim() : "";
    }
}
