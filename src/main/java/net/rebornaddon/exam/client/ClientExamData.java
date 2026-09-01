package net.rebornaddon.exam.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.rebornaddon.exam.network.ExamSyncMessage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ClientExamData {
    private static final int MAX_PARTS = 256;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final long TRANSFER_TTL = 30000L;
    private static final Map<String, Transfer> TRANSFERS = new HashMap<String, Transfer>();

    private static JsonObject snapshot = new JsonObject();
    private static JsonObject rankCatalogSnapshot = new JsonObject();
    private static int revision;
    private static int rankRevision;
    private static long requestedAt;
    private static long rankRequestedAt;
    private static long rankUpdatedAt;

    private ClientExamData() {
    }

    public static void handle(ExamSyncMessage message) {
        if (message == null || message.partCount() < 1 || message.partCount() > MAX_PARTS
                || message.partIndex() < 0 || message.partIndex() >= message.partCount()) return;
        expire();
        String key = message.kind() + '\n' + message.transferId();
        Transfer transfer = TRANSFERS.get(key);
        if (transfer == null || transfer.parts.length != message.partCount()) {
            transfer = new Transfer(message.partCount());
            TRANSFERS.put(key, transfer);
        }
        if (!transfer.add(message.partIndex(), message.payload())) {
            TRANSFERS.remove(key);
            return;
        }
        if (!transfer.complete()) return;
        TRANSFERS.remove(key);
        try {
            JsonObject value = new JsonParser().parse(transfer.text()).getAsJsonObject();
            if ("rank_catalog".equals(message.kind())) {
                rankCatalogSnapshot = value;
                rankRequestedAt = 0L;
                rankUpdatedAt = System.currentTimeMillis();
                rankRevision++;
                return;
            }
            snapshot = value;
            requestedAt = 0L;
            revision++;
            if (value.has("rankCatalog") && value.get("rankCatalog").isJsonArray()) {
                JsonObject ranks = new JsonObject();
                ranks.addProperty("available", !value.has("available") || value.get("available").getAsBoolean());
                if (value.has("message")) ranks.add("message", value.get("message"));
                ranks.add("rankCatalog", value.get("rankCatalog"));
                rankCatalogSnapshot = ranks;
                rankRequestedAt = 0L;
                rankUpdatedAt = System.currentTimeMillis();
                rankRevision++;
            }
        } catch (Throwable ignored) {
        }
    }

    public static JsonObject snapshot() {
        return snapshot;
    }

    public static int revision() {
        return revision;
    }

    public static JsonObject rankCatalogSnapshot() {
        return rankCatalogSnapshot;
    }

    public static int rankRevision() {
        return rankRevision;
    }

    public static boolean request(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (snapshot.has("available") || now - requestedAt < 2500L)) return false;
        requestedAt = now;
        return true;
    }

    public static boolean requestRankCatalog(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && rankCatalogSnapshot.has("available") && now - rankUpdatedAt < 60000L) return false;
        if (rankRequestedAt != 0L && now - rankRequestedAt < 2500L) return false;
        rankRequestedAt = now;
        return true;
    }

    public static void reset() {
        TRANSFERS.clear();
        snapshot = new JsonObject();
        rankCatalogSnapshot = new JsonObject();
        requestedAt = 0L;
        rankRequestedAt = 0L;
        rankUpdatedAt = 0L;
        revision++;
        rankRevision++;
    }

    private static void expire() {
        long cutoff = System.currentTimeMillis() - TRANSFER_TTL;
        Iterator<Transfer> iterator = TRANSFERS.values().iterator();
        while (iterator.hasNext()) if (iterator.next().updatedAt < cutoff) iterator.remove();
    }

    private static final class Transfer {
        private final byte[][] parts;
        private int received;
        private int bytes;
        private long updatedAt = System.currentTimeMillis();

        private Transfer(int count) {
            parts = new byte[count][];
        }

        private boolean add(int index, byte[] value) {
            byte[] next = value == null ? new byte[0] : value;
            if (parts[index] != null) return true;
            if (bytes + next.length > MAX_BYTES) return false;
            parts[index] = next;
            bytes += next.length;
            received++;
            updatedAt = System.currentTimeMillis();
            return true;
        }

        private boolean complete() { return received == parts.length; }

        private String text() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(bytes);
            for (byte[] part : parts) output.write(part, 0, part.length);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
