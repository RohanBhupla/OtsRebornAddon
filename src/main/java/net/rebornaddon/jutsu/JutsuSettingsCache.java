package net.rebornaddon.jutsu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JutsuSettingsCache {
    public static final JutsuSettingsCache INSTANCE = new JutsuSettingsCache();

    private volatile Map<String, Map<String, String>> values =
            Collections.<String, Map<String, String>>emptyMap();
    private volatile Map<String, Map<String, JutsuEffectRules.Rule>> effectRules =
            Collections.<String, Map<String, JutsuEffectRules.Rule>>emptyMap();

    private JutsuSettingsCache() {
    }

    public void replace(String[] records) {
        Map<String, Map<String, String>> next = new LinkedHashMap<String, Map<String, String>>();
        if (records != null) {
            for (String record : records) {
                if (record == null) {
                    continue;
                }
                String[] fields = record.split("\\t", -1);
                if (fields.length < 3 || JutsuRegistry.INSTANCE.get(fields[0]) == null) {
                    continue;
                }
                String property = fields[1];
                JutsuDefinition definition = JutsuRegistry.INSTANCE.get(fields[0]);
                if (!definition.supports(property)) {
                    continue;
                }
                Map<String, String> entry = next.get(definition.id());
                if (entry == null) {
                    entry = new LinkedHashMap<String, String>();
                    next.put(definition.id(), entry);
                }
                entry.put(property, fields[2]);
            }
        }

        Map<String, Map<String, String>> immutable = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> entry : next.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        values = Collections.unmodifiableMap(immutable);
        Map<String, Map<String, JutsuEffectRules.Rule>> parsedRules =
                new LinkedHashMap<String, Map<String, JutsuEffectRules.Rule>>();
        for (Map.Entry<String, Map<String, String>> entry : immutable.entrySet()) {
            String encoded = entry.getValue().get("negative-effect-rules");
            if (encoded != null && !encoded.isEmpty()) {
                parsedRules.put(entry.getKey(), JutsuEffectRules.immutable(encoded));
            }
        }
        effectRules = Collections.unmodifiableMap(parsedRules);
    }

    public void clear() {
        values = Collections.emptyMap();
        effectRules = Collections.emptyMap();
    }

    public synchronized void applyOverride(JutsuDefinition definition, String property,
                                           String value) {
        if (definition == null || !definition.supports(property) || value == null) {
            return;
        }
        Map<String, Map<String, String>> next = mutableCopy(values);
        Map<String, String> entry = next.get(definition.id());
        if (entry == null) {
            entry = new LinkedHashMap<String, String>();
            next.put(definition.id(), entry);
        }
        entry.put(property, value);
        install(next);
    }

    public boolean hasOverride(JutsuDefinition definition, String property, String value) {
        String current = override(definition, property);
        return current != null && current.equals(value);
    }

    public String override(JutsuDefinition definition, String property) {
        if (definition == null) {
            return null;
        }
        Map<String, String> entry = values.get(definition.id());
        return entry == null ? null : entry.get(property);
    }

    public boolean enabled(JutsuDefinition definition) {
        String value = override(definition, "enabled");
        return value == null || Boolean.parseBoolean(value);
    }

    public boolean enabled(JutsuDefinition definition, String property, boolean fallback) {
        String value = override(definition, property);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public String value(JutsuDefinition definition, String property, String fallback) {
        String value = override(definition, property);
        return value == null ? fallback : value;
    }

    public double decimal(JutsuDefinition definition, String property, double fallback) {
        String value = override(definition, property);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public long ticks(JutsuDefinition definition, String property, long fallback) {
        String value = override(definition, property);
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public int integer(JutsuDefinition definition, String property, int fallback) {
        String value = override(definition, property);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public JutsuEffectRules.Rule effectRule(JutsuDefinition definition, String potionId) {
        Map<String, JutsuEffectRules.Rule> rules = definition == null
                ? null : effectRules.get(definition.id());
        return rules == null || potionId == null ? null : rules.get(potionId);
    }

    private static Map<String, Map<String, String>> mutableCopy(
            Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<String, String>(entry.getValue()));
        }
        return copy;
    }

    private void install(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> immutable = new LinkedHashMap<String, Map<String, String>>();
        Map<String, Map<String, JutsuEffectRules.Rule>> parsedRules =
                new LinkedHashMap<String, Map<String, JutsuEffectRules.Rule>>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            Map<String, String> values = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(entry.getValue()));
            immutable.put(entry.getKey(), values);
            String encoded = values.get("negative-effect-rules");
            if (encoded != null && !encoded.isEmpty()) {
                parsedRules.put(entry.getKey(), JutsuEffectRules.immutable(encoded));
            }
        }
        values = Collections.unmodifiableMap(immutable);
        effectRules = Collections.unmodifiableMap(parsedRules);
    }
}
