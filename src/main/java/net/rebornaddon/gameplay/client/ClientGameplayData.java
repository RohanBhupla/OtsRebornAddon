package net.rebornaddon.gameplay.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

public final class ClientGameplayData {
    private static final Map<String, JsonObject> DATA = new HashMap<String, JsonObject>();
    private static final Map<String, Long> REQUESTED = new HashMap<String, Long>();
    private static int revision;

    private ClientGameplayData() {
    }

    public static synchronized void update(String section, String json) {
        try {
            DATA.put(section, new JsonParser().parse(json).getAsJsonObject());
        } catch (Throwable invalid) {
            JsonObject error = new JsonObject();
            error.addProperty("available", false);
            error.addProperty("message", "The server returned invalid gameplay data.");
            DATA.put(section, error);
        }
        revision++;
    }

    public static synchronized JsonObject get(String section) {
        JsonObject value = DATA.get(section);
        return value == null ? new JsonObject()
                : new JsonParser().parse(value.toString()).getAsJsonObject();
    }

    public static synchronized boolean shouldRequest(String section) {
        long now = System.currentTimeMillis();
        Long previous = REQUESTED.get(section);
        if (previous != null && now - previous.longValue() < 3000L) return false;
        REQUESTED.put(section, Long.valueOf(now));
        return true;
    }

    public static synchronized int revision() { return revision; }

    public static synchronized void reset() {
        DATA.clear();
        REQUESTED.clear();
        revision++;
    }
}
