package net.rebornaddon.mode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModeSettingsCache {
    public static final ModeSettingsCache INSTANCE = new ModeSettingsCache();

    private volatile Map<String, Map<String, String>> values = Collections.emptyMap();

    private ModeSettingsCache() {
    }

    public void replace(String[] records) {
        Map<String, Map<String, String>> next = new LinkedHashMap<String, Map<String, String>>();
        if (records != null) {
            for (String record : records) {
                String[] fields = record == null ? new String[0] : record.split("\\t", -1);
                ModeDefinition mode = fields.length < 3 ? null : ModeRegistry.INSTANCE.get(fields[0]);
                if (mode == null || !mode.supports(fields[1])) continue;
                Map<String, String> entry = next.get(mode.id());
                if (entry == null) {
                    entry = new LinkedHashMap<String, String>();
                    next.put(mode.id(), entry);
                }
                entry.put(fields[1], fields[2]);
            }
        }
        Map<String, Map<String, String>> immutable = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> entry : next.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        values = Collections.unmodifiableMap(immutable);
    }

    public void clear() { values = Collections.emptyMap(); }

    public synchronized void applyOverride(ModeDefinition mode, String property, String value) {
        if (mode == null || !mode.supports(property) || value == null) return;
        Map<String, Map<String, String>> next = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> current : values.entrySet()) {
            next.put(current.getKey(), new LinkedHashMap<String, String>(current.getValue()));
        }
        Map<String, String> entry = next.get(mode.id());
        if (entry == null) {
            entry = new LinkedHashMap<String, String>();
            next.put(mode.id(), entry);
        }
        entry.put(property, value);
        Map<String, Map<String, String>> immutable = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> current : next.entrySet()) {
            immutable.put(current.getKey(), Collections.unmodifiableMap(current.getValue()));
        }
        values = Collections.unmodifiableMap(immutable);
    }

    public boolean hasOverride(ModeDefinition mode, String property, String value) {
        Map<String, String> entry = mode == null ? null : values.get(mode.id());
        String current = entry == null ? null : entry.get(property);
        return current != null && current.equals(value);
    }

    public String value(ModeDefinition mode, String property) {
        Map<String, String> entry = mode == null ? null : values.get(mode.id());
        String value = entry == null ? null : entry.get(property);
        return value == null && mode != null ? mode.defaultValue(property) : value;
    }

    public boolean enabled(ModeDefinition mode) {
        return Boolean.parseBoolean(value(mode, "enabled"));
    }

    public double decimal(ModeDefinition mode, String property, double fallback) {
        try {
            double parsed = Double.parseDouble(value(mode, property));
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
