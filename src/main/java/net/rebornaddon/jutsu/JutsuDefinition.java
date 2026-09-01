package net.rebornaddon.jutsu;

import net.minecraft.item.Item;
import net.narutomod.item.ItemJutsu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JutsuDefinition implements Comparable<JutsuDefinition> {
    private final String id;
    private final String displayName;
    private final String source;
    private final String parentId;
    private final String category;
    private final boolean restricted;
    private final Item item;
    private final ItemJutsu.JutsuEnum jutsu;
    private final float minimumPower;
    private final float maximumPower;
    private final int defaultChargeTicks;
    private final Set<String> properties;
    private final Map<String, String> defaults;

    JutsuDefinition(String id, String displayName, String source, String parentId,
                    String category, boolean restricted,
                    Item item, ItemJutsu.JutsuEnum jutsu, float minimumPower,
                    float maximumPower, int defaultChargeTicks, Set<String> properties,
                    Map<String, String> defaults) {
        this.id = id;
        this.displayName = displayName;
        this.source = source;
        this.parentId = parentId == null ? "" : parentId;
        this.category = category == null || category.isEmpty() ? "Other" : category;
        this.restricted = restricted;
        this.item = item;
        this.jutsu = jutsu;
        this.minimumPower = minimumPower;
        this.maximumPower = maximumPower;
        this.defaultChargeTicks = defaultChargeTicks;
        this.properties = Collections.unmodifiableSet(new LinkedHashSet<String>(properties));
        this.defaults = Collections.unmodifiableMap(new LinkedHashMap<String, String>(defaults));
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String source() {
        return source;
    }

    public String parentId() {
        return parentId;
    }

    public String category() {
        return category;
    }

    public boolean restricted() {
        return restricted;
    }

    public Item item() {
        return item;
    }

    ItemJutsu.JutsuEnum jutsu() {
        return jutsu;
    }

    float minimumPower() {
        return minimumPower;
    }

    float maximumPower() {
        return maximumPower;
    }

    int defaultChargeTicks() {
        return defaultChargeTicks;
    }

    public boolean supports(String property) {
        return properties.contains(property);
    }

    public Set<String> properties() {
        return properties;
    }

    public String defaultValue(String property) {
        return defaults.get(property);
    }

    public String descriptor() {
        StringBuilder propertyText = new StringBuilder();
        for (String property : properties) {
            if (propertyText.length() > 0) {
                propertyText.append(',');
            }
            propertyText.append(property);
        }

        StringBuilder defaultText = new StringBuilder();
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (defaultText.length() > 0) {
                defaultText.append(';');
            }
            defaultText.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return clean(id) + '\t' + clean(displayName) + '\t' + clean(source) + '\t'
                + clean(parentId) + '\t' + propertyText + '\t' + defaultText + '\t'
                + clean(category) + '\t' + restricted;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    @Override
    public int compareTo(JutsuDefinition other) {
        return id.compareTo(other.id);
    }

    static Set<String> properties(boolean charged, boolean explosionDamage) {
        Set<String> values = new LinkedHashSet<String>();
        values.add("enabled");
        values.add("chakra");
        values.add("cooldown");
        values.add("damage");
        values.add("negative-effects-enabled");
        values.add("effect-duration-multiplier");
        values.add("effect-amplifier-adjustment");
        values.add("negative-effect-rules");
        values.add("additional-negative-effects");
        if (explosionDamage) {
            values.add("explosion-damage");
        }
        if (charged) {
            values.add("charge-time");
            values.add("charge-speed");
        }
        return values;
    }

    static Map<String, String> defaults(ItemJutsu.JutsuEnum jutsu, int chargeTicks,
                                        Long cooldownTicks) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("enabled", "true");
        values.put("chakra", decimal(jutsu.chakraUsage));
        values.put("negative-effects-enabled", "true");
        values.put("effect-duration-multiplier", "1");
        values.put("effect-amplifier-adjustment", "0");
        values.put("negative-effect-rules", "");
        values.put("additional-negative-effects", "");
        if (cooldownTicks != null) {
            values.put("cooldown", Long.toString(cooldownTicks.longValue()));
        }
        if (chargeTicks >= 0) {
            values.put("charge-time", Integer.toString(chargeTicks));
            values.put("charge-speed", "1");
        }
        return values;
    }

    private static String decimal(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
