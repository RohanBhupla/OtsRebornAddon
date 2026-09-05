package net.rebornaddon.gameplay.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

public final class ClientGameplayData {
    private static final Map<String, JsonObject> DATA = new HashMap<String, JsonObject>();
    private static final Map<String, Long> REQUESTED = new HashMap<String, Long>();
    private static final Map<String, Integer> SECTION_REVISIONS = new HashMap<String, Integer>();
    private static int revision;
    private static int emptyRevision;

    private ClientGameplayData() {
    }

    public static synchronized void update(String section, String json) {
        REQUESTED.put(section, Long.valueOf(System.currentTimeMillis()));
        JsonObject next;
        try {
            next = new JsonParser().parse(json).getAsJsonObject();
        } catch (Throwable invalid) {
            next = new JsonObject();
            next.addProperty("available", false);
            next.addProperty("message", "The server returned invalid gameplay data.");
        }
        JsonObject previous = DATA.get(section);
        if (next.equals(previous)) return;
        DATA.put(section, next);
        SECTION_REVISIONS.put(section, Integer.valueOf(++revision));
    }

    public static synchronized JsonObject get(String section) {
        JsonObject value = DATA.get(section);
        // Snapshots are replaced rather than mutated. UI readers must treat this as read-only.
        return value == null ? new JsonObject() : value;
    }

    public static synchronized boolean shouldRequest(String section) {
        long now = System.currentTimeMillis();
        Long previous = REQUESTED.get(section);
        if (previous != null && now - previous.longValue() < requestInterval(section)) return false;
        REQUESTED.put(section, Long.valueOf(now));
        return true;
    }

    static long requestInterval(String section) {
        if ("spectating".equals(section)) return 3000L;
        if ("training".equals(section)) return 10000L;
        if ("mounts".equals(section) || "luckperms".equals(section)
                || "moderation".equals(section)) return 60000L;
        return 30000L;
    }

    public static synchronized int revision() { return revision; }

    public static synchronized int revision(String section) {
        Integer value = SECTION_REVISIONS.get(section);
        return value == null ? emptyRevision : value.intValue();
    }

    public static synchronized void reset() {
        DATA.clear();
        REQUESTED.clear();
        SECTION_REVISIONS.clear();
        emptyRevision = ++revision;
    }
}
