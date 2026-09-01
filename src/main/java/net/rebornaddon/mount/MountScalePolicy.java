package net.rebornaddon.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side global form scales, with conservative fallbacks for oversized models. */
final class MountScalePolicy {
    static final float MINIMUM = 0.01F;
    static final float MAXIMUM = 10.0F;
    private static final Map<String, ScalePair> RECOMMENDED = recommended();
    private volatile Map<String, ScalePair> configured = Collections.emptyMap();

    float scale(String entityId, boolean mount) {
        ScalePair override = configured.get(normalize(entityId));
        Float value = override == null ? null : override.value(mount);
        if (value != null) return value.floatValue();
        ScalePair recommended = recommended(entityId);
        value = recommended == null ? null : recommended.value(mount);
        return value == null ? 1.0F : value.floatValue();
    }

    synchronized boolean accept(JsonObject root) {
        if (root == null || !root.has("scalePolicies")
                || !root.get("scalePolicies").isJsonArray()) return false;
        Map<String, ScalePair> next = new HashMap<String, ScalePair>();
        JsonArray entries = root.getAsJsonArray("scalePolicies");
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String id = text(entry, "entityId");
            if (id.isEmpty()) id = text(entry, "id");
            id = normalize(id);
            if (id.isEmpty()) continue;
            Float companion = number(entry, "companionScale");
            Float mount = number(entry, "mountScale");
            if (companion != null || mount != null) {
                next.put(id, new ScalePair(companion, mount));
            }
        }
        Map<String, ScalePair> immutable = Collections.unmodifiableMap(next);
        if (immutable.equals(configured)) return false;
        configured = immutable;
        return true;
    }

    synchronized void set(String entityId, boolean mount, float scale) {
        String id = normalize(entityId);
        if (id.isEmpty()) throw new IllegalArgumentException("Missing entity id.");
        float checked = checked(scale);
        Map<String, ScalePair> next = new HashMap<String, ScalePair>(configured);
        ScalePair old = next.get(id);
        Float companion = old == null ? null : old.companion;
        Float mountValue = old == null ? null : old.mount;
        if (mount) mountValue = Float.valueOf(checked);
        else companion = Float.valueOf(checked);
        next.put(id, new ScalePair(companion, mountValue));
        configured = Collections.unmodifiableMap(next);
    }

    boolean hasPolicy(String entityId) {
        String id = normalize(entityId);
        return configured.containsKey(id) || recommended(id) != null;
    }

    void reset() {
        configured = Collections.emptyMap();
    }

    static float checked(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)
                || scale < MINIMUM || scale > MAXIMUM) {
            throw new IllegalArgumentException("Scale must be between 0.01 and 10.");
        }
        return scale;
    }

    private static Map<String, ScalePair> recommended() {
        Map<String, ScalePair> values = new LinkedHashMap<String, ScalePair>();
        pair(values, "narutomod:one_tail", 0.055F, 0.11F);
        pair(values, "narutomod:two_tails", 0.10F, 0.20F);
        pair(values, "narutomod:three_tails", 0.05F, 0.10F);
        pair(values, "narutomod:four_tails", 0.055F, 0.11F);
        pair(values, "narutomod:five_tails", 0.05F, 0.10F);
        pair(values, "narutomod:six_tails", 0.045F, 0.09F);
        pair(values, "narutomod:seven_tails", 0.07F, 0.14F);
        pair(values, "narutomod:eight_tails", 0.10F, 0.20F);
        pair(values, "narutomod:nine_tails", 0.10F, 0.20F);
        pair(values, "narutomod:ten_tails", 0.021F, 0.042F);
        pair(values, "narutomod:eleven_tails", 0.055F, 0.11F);
        pair(values, "narutomod:gamabunta", 0.063F, 0.126F);
        pair(values, "narutomod:manda", 0.056F, 0.112F);
        pair(values, "narutomod:giant_dog_2h", 0.125F, 0.25F);
        // Equivalent form names used by some Naruto add-ons.
        pair(values, "narutomod:toad_summon", 0.063F, 0.126F);
        pair(values, "narutomod:snake_summon", 0.056F, 0.112F);
        return Collections.unmodifiableMap(values);
    }

    private static ScalePair recommended(String entityId) {
        String id = normalize(entityId);
        ScalePair exact = RECOMMENDED.get(id);
        if (exact != null) return exact;
        int separator = id.indexOf(':');
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        if (path.isEmpty()) return null;
        for (Map.Entry<String, ScalePair> entry : RECOMMENDED.entrySet()) {
            if (entry.getKey().endsWith(":" + path)) return entry.getValue();
        }
        return null;
    }

    private static void pair(Map<String, ScalePair> values, String id,
                             float companion, float mount) {
        values.put(id, new ScalePair(Float.valueOf(companion), Float.valueOf(mount)));
    }

    private static Float number(JsonObject value, String key) {
        try {
            if (!value.has(key) || !value.get(key).isJsonPrimitive()
                    || !value.get(key).getAsJsonPrimitive().isNumber()) return null;
            return Float.valueOf(checked(value.get(key).getAsFloat()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String text(JsonObject value, String key) {
        try {
            return value.has(key) ? value.get(key).getAsString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class ScalePair {
        private final Float companion;
        private final Float mount;

        private ScalePair(Float companion, Float mount) {
            this.companion = companion;
            this.mount = mount;
        }

        private Float value(boolean requestedMount) {
            return requestedMount ? mount : companion;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ScalePair)) return false;
            ScalePair pair = (ScalePair) other;
            return java.util.Objects.equals(companion, pair.companion)
                    && java.util.Objects.equals(mount, pair.mount);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(companion, mount);
        }
    }
}
