package net.rebornaddon.mode;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModeDefinition implements Comparable<ModeDefinition> {
    public static final List<String> PROPERTIES = Collections.unmodifiableList(Arrays.asList(
            "enabled", "melee-damage-multiplier", "movement-speed-multiplier",
            "regeneration-per-second", "damage-taken-multiplier",
            "knockback-resistance", "incompatible-modes"));

    private final String id;
    private final String displayName;
    private final String source;
    private final String detector;
    private final String token;
    private final String nativeSummary;
    private final double nativeMovementMultiplier;
    private final Map<String, String> defaults;

    ModeDefinition(String id, String displayName, String source, String detector, String token,
                   String nativeSummary, double nativeMovementMultiplier) {
        this.id = clean(id);
        this.displayName = clean(displayName);
        this.source = clean(source);
        this.detector = clean(detector);
        this.token = clean(token);
        this.nativeSummary = clean(nativeSummary);
        this.nativeMovementMultiplier = nativeMovementMultiplier > 0.0D
                && Double.isFinite(nativeMovementMultiplier) ? nativeMovementMultiplier : 1.0D;
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("enabled", "true");
        values.put("melee-damage-multiplier", "1");
        values.put("movement-speed-multiplier", "1");
        values.put("regeneration-per-second", "0");
        values.put("damage-taken-multiplier", "1");
        values.put("knockback-resistance", "0");
        values.put("incompatible-modes", "");
        defaults = Collections.unmodifiableMap(values);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String source() { return source; }
    String detector() { return detector; }
    String token() { return token; }
    public String nativeSummary() { return nativeSummary; }
    public double nativeMovementMultiplier() { return nativeMovementMultiplier; }
    public double movementAdjustment(double targetMultiplier) {
        return targetMultiplier / nativeMovementMultiplier;
    }
    public double movementTarget(double adjustmentMultiplier) {
        return nativeMovementMultiplier * adjustmentMultiplier;
    }

    public static double nativeMovementMultiplier(String modeId) {
        String id = modeId == null ? "" : modeId;
        if (id.endsWith("toad_sage_mode") || id.endsWith("snake_sage_mode")
                || id.endsWith("slug_sage_mode") || id.endsWith("karma_mode")
                || id.endsWith("cursemark_mode")) return 2.5D;
        if (id.endsWith("byakugou_mode")) return 2.0D;
        if (id.endsWith("wind_chakra_mode")) return 2.4D;
        if (id.endsWith("lightning_chakra_mode")) return 7.6D;
        if (id.endsWith("water_chakra_mode")) return 1.8D;
        if (id.endsWith("rain_chakra_mode")) return 2.2D;
        return 1.0D;
    }

    public static boolean hasFixedNativeMovement(String modeId) {
        String id = modeId == null ? "" : modeId;
        return !(id.endsWith("eight_gates") || id.endsWith("six_path_senjutsu")
                || id.endsWith("tenseigan_chakra_mode") || id.endsWith("biju_cloak"));
    }

    public static boolean isKnownMode(String modeId) {
        String id = modeId == null ? "" : modeId;
        return "server:global".equals(id)
                || "narutomod:toad_sage_mode".equals(id)
                || "narutomod:snake_sage_mode".equals(id)
                || "narutomod:slug_sage_mode".equals(id)
                || "narutomod:karma_mode".equals(id)
                || "ahznbcursemarkaddon:cursemark_mode".equals(id)
                || "ahznbcursemarkaddon:byakugou_mode".equals(id)
                || "narutomod:eight_gates".equals(id)
                || "narutomod:six_path_senjutsu".equals(id)
                || "narutomod:tenseigan_chakra_mode".equals(id)
                || "narutomod:biju_cloak".equals(id)
                || "narutomod:wind_chakra_mode".equals(id)
                || "narutomod:lightning_chakra_mode".equals(id)
                || "narutomod:lava_chakra_mode".equals(id)
                || "rebornaddon:water_chakra_mode".equals(id)
                || "rebornaddon:rain_chakra_mode".equals(id)
                || "rebornaddon:earth_chakra_mode".equals(id);
    }

    public static double movementTarget(String modeId, double adjustmentMultiplier) {
        return nativeMovementMultiplier(modeId) * adjustmentMultiplier;
    }

    public static double movementAdjustment(String modeId, double targetMultiplier) {
        return targetMultiplier / nativeMovementMultiplier(modeId);
    }
    public String nativeDetail(String property) {
        return nativeDetail(id, property, nativeSummary);
    }

    public static String nativeDetail(String modeId, String property, String fallback) {
        String id = modeId == null ? "" : modeId;
        if ("movement-speed-multiplier".equals(property)) {
            if (id.endsWith("toad_sage_mode") || id.endsWith("snake_sage_mode")
                    || id.endsWith("slug_sage_mode") || id.endsWith("karma_mode")
                    || id.endsWith("cursemark_mode")) {
                return "Source mod movement: 2.5x. Set the final movement speed here.";
            }
            if (id.endsWith("byakugou_mode")) {
                return "Source mod movement: 2x. Set the final movement speed here.";
            }
            if (id.endsWith("water_chakra_mode")) {
                return "Source mod movement: Speed IV (1.8x). Set the final movement speed here.";
            }
            if (id.endsWith("rain_chakra_mode")) {
                return "Source mod movement: Speed VI (2.2x). Set the final movement speed here.";
            }
            if (id.endsWith("earth_chakra_mode")) {
                return "Source mod movement: 1x. Set the final movement speed here.";
            }
        }
        if ("melee-damage-multiplier".equals(property)) {
            if (id.endsWith("toad_sage_mode") || id.endsWith("snake_sage_mode")
                    || id.endsWith("slug_sage_mode")) return "Source mod melee: +60 attack damage; this multiplier applies afterward.";
            if (id.endsWith("karma_mode")) return "Source mod melee: +30 attack damage; this multiplier applies afterward.";
            if (id.endsWith("cursemark_mode")) return "Source mod melee: +45 attack damage; this multiplier applies afterward.";
            if (id.endsWith("byakugou_mode")) return "Source mod melee: +40 attack damage; this multiplier applies afterward.";
            if (id.endsWith("water_chakra_mode")) return "Source mod melee: Strength III (+9 attack); this multiplier applies afterward.";
            if (id.endsWith("rain_chakra_mode")) return "Source mod melee: Strength IV (+12 attack); this multiplier applies afterward.";
            if (id.endsWith("earth_chakra_mode")) return "Source mod melee: Strength VI (+18 attack); this multiplier applies afterward.";
        }
        if ("damage-taken-multiplier".equals(property)) {
            if (id.endsWith("water_chakra_mode") || id.endsWith("rain_chakra_mode")) {
                return "Source mod defense: Resistance II (40% reduction); this multiplier applies afterward.";
            }
            if (id.endsWith("earth_chakra_mode")) {
                return "Source mod defense: Resistance IV (80% reduction); this multiplier applies afterward.";
            }
        }
        if ("regeneration-per-second".equals(property)) {
            return "Source regeneration remains unchanged; this value adds health each second.";
        }
        if ("knockback-resistance".equals(property)) {
            return "Source knockback behavior remains unchanged; this value adds resistance from 0 to 1.";
        }
        if ("incompatible-modes".equals(property)) {
            return "Named modes selected here cannot remain active together.";
        }
        if ("enabled".equals(property)) {
            return "Disabling this entry prevents its activation and removes it when detected.";
        }
        return fallback == null ? "" : fallback;
    }
    public String defaultValue(String property) { return defaults.get(property); }
    public boolean supports(String property) { return defaults.containsKey(property); }

    public String descriptor() {
        StringBuilder properties = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (String property : PROPERTIES) {
            if (properties.length() > 0) properties.append(',');
            properties.append(property);
            if (values.length() > 0) values.append(';');
            values.append(property).append('=').append(defaults.get(property));
        }
        return id + '\t' + displayName + '\t' + source + "\t\t" + properties + '\t'
                + values + '\t' + nativeSummary + "\tfalse";
    }

    @Override
    public int compareTo(ModeDefinition other) {
        int name = displayName.compareToIgnoreCase(other.displayName);
        return name == 0 ? id.compareTo(other.id) : name;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ')
                .replace('\n', ' ').trim();
    }

}
