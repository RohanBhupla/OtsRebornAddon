package net.rebornaddon.store;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class StoreSettingsCache {
    public static final StoreSettingsCache INSTANCE = new StoreSettingsCache();

    private volatile Map<String, Setting> settings = Collections.emptyMap();
    private volatile Map<String, Boolean> scopes = Collections.emptyMap();
    private volatile int revision;

    private StoreSettingsCache() {
    }

    public synchronized boolean replace(String[] records) {
        Map<String, Setting> updated = new HashMap<String, Setting>();
        Map<String, Boolean> updatedScopes = new HashMap<String, Boolean>();
        if (records != null) {
            for (String record : records) {
                String[] fields = record == null ? new String[0] : record.split("\\t", -1);
                if (fields.length >= 3 && "@scope".equals(fields[0])) {
                    if (StoreCatalog.findScope(fields[1]) != null) {
                        updatedScopes.put(fields[1], Boolean.valueOf(Boolean.parseBoolean(fields[2])));
                    }
                    continue;
                }
                if (fields.length < 3 || StoreCatalog.find(fields[0]) == null) {
                    continue;
                }
                try {
                    int price = Integer.parseInt(fields[1]);
                    if (price < 0 || price > 1000000000) {
                        continue;
                    }
                    updated.put(fields[0], new Setting(price, Boolean.parseBoolean(fields[2])));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (updated.equals(settings) && updatedScopes.equals(scopes)) {
            return false;
        }
        settings = Collections.unmodifiableMap(updated);
        scopes = Collections.unmodifiableMap(updatedScopes);
        revision++;
        return true;
    }

    public synchronized void setPreview(String key, String property, String value) {
        StoreCatalog.Entry entry = StoreCatalog.find(key);
        if (entry == null) {
            return;
        }
        Map<String, Setting> updated = new HashMap<String, Setting>(settings);
        Setting current = setting(entry);
        if ("price".equals(property)) {
            current = new Setting(Integer.parseInt(value), current.purchasable());
        } else if ("purchasable".equals(property)) {
            current = new Setting(current.price(), Boolean.parseBoolean(value));
        }
        updated.put(key, current);
        settings = Collections.unmodifiableMap(updated);
        revision++;
    }

    public synchronized void resetPreview(String key, String property) {
        StoreCatalog.Entry entry = StoreCatalog.find(key);
        if (entry == null) return;
        Map<String, Setting> updated = new HashMap<String, Setting>(settings);
        Setting current = setting(entry);
        if ("price".equals(property)) {
            current = new Setting(entry.defaultPrice(), current.purchasable());
        } else if ("purchasable".equals(property)) {
            current = new Setting(current.price(), true);
        } else if ("all".equals(property)) {
            updated.remove(key);
            settings = Collections.unmodifiableMap(updated);
            revision++;
            return;
        } else {
            return;
        }
        updated.put(key, current);
        settings = Collections.unmodifiableMap(updated);
        revision++;
    }

    public synchronized void resetAllPreview() {
        clear();
    }

    public synchronized void setScopePreview(String key, boolean enabled) {
        if (StoreCatalog.findScope(key) == null) return;
        Map<String, Boolean> updated = new HashMap<String, Boolean>(scopes);
        updated.put(key, Boolean.valueOf(enabled));
        scopes = Collections.unmodifiableMap(updated);
        revision++;
    }

    public synchronized void resetScopePreview(String key) {
        Map<String, Boolean> updated = new HashMap<String, Boolean>(scopes);
        if (updated.remove(key) != null) {
            scopes = Collections.unmodifiableMap(updated);
            revision++;
        }
    }

    public boolean scopeEnabled(String key) {
        Boolean enabled = scopes.get(key);
        return enabled == null || enabled.booleanValue();
    }

    public Setting setting(StoreCatalog.Entry entry) {
        Setting setting = entry == null ? null : settings.get(entry.key());
        return setting == null && entry != null
                ? new Setting(entry.defaultPrice(), true) : setting;
    }

    public int revision() {
        return revision;
    }

    public synchronized void clear() {
        settings = Collections.emptyMap();
        scopes = Collections.emptyMap();
        revision++;
    }

    public static final class Setting {
        private final int price;
        private final boolean purchasable;

        private Setting(int price, boolean purchasable) {
            this.price = price;
            this.purchasable = purchasable;
        }

        public int price() { return price; }
        public boolean purchasable() { return purchasable; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Setting)) return false;
            Setting setting = (Setting) other;
            return price == setting.price && purchasable == setting.purchasable;
        }

        @Override
        public int hashCode() {
            return 31 * price + (purchasable ? 1 : 0);
        }
    }
}
