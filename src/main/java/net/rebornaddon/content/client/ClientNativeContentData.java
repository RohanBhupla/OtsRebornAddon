package net.rebornaddon.content.client;

import com.google.gson.JsonObject;

public final class ClientNativeContentData {
    private static JsonObject admin = new JsonObject();
    private static int revision;
    private static long requestedAt;

    private ClientNativeContentData() {
    }

    public static void updateAdmin(JsonObject value) {
        admin = value == null ? new JsonObject() : value;
        requestedAt = 0L;
        revision++;
    }

    public static JsonObject admin() {
        return admin;
    }

    public static int revision() {
        return revision;
    }

    public static boolean request(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (admin.has("success") || now - requestedAt < 3000L)) return false;
        requestedAt = now;
        return true;
    }

    public static void clear() {
        admin = new JsonObject();
        requestedAt = 0L;
        revision++;
    }
}
