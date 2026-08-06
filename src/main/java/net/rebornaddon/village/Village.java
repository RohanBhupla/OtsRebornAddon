package net.rebornaddon.village;

import java.util.Locale;

public enum Village {
    STONE(0, "stone", "Stone", 0xFF8D8575, 0xFFC4B28A, true),
    LEAF(1, "leaf", "Leaf", 0xFF4F8D58, 0xFF86C46E, true),
    CLOUD(2, "cloud", "Cloud", 0xFF5A86A6, 0xFFE2B95B, true),
    SAND(3, "sand", "Sand", 0xFFB88A45, 0xFFE3C16A, true),
    MIST(4, "mist", "Mist", 0xFF4E8D91, 0xFF9AD4CF, true),
    RAIN(5, "rain", "Rain", 0xFF5E6D8C, 0xFF8CA5D8, false);

    private static final Village[] ALL = values();
    private static final Village[] CHOICES = selectableChoices();

    private final int id;
    private final String group;
    private final String displayName;
    private final int accentColor;
    private final int hoverColor;
    private final boolean selectable;

    Village(int id, String group, String displayName, int accentColor, int hoverColor, boolean selectable) {
        this.id = id;
        this.group = group;
        this.displayName = displayName;
        this.accentColor = accentColor;
        this.hoverColor = hoverColor;
        this.selectable = selectable;
    }

    public int id() {
        return id;
    }

    public String group() {
        return group;
    }

    public String displayName() {
        return displayName;
    }

    public int accentColor() {
        return accentColor;
    }

    public int hoverColor() {
        return hoverColor;
    }

    public boolean selectable() {
        return selectable;
    }

    public static Village byId(int id) {
        for (Village village : ALL) {
            if (village.id == id) {
                return village;
            }
        }
        return null;
    }

    public static Village byGroup(String group) {
        if (group == null) {
            return null;
        }

        String normalized = group.trim().toLowerCase(Locale.ROOT);
        for (Village village : ALL) {
            if (village.group.equals(normalized)) {
                return village;
            }
        }
        return null;
    }

    public static Village[] choices() {
        return CHOICES.clone();
    }

    public static Village[] all() {
        return ALL.clone();
    }

    private static Village[] selectableChoices() {
        int count = 0;
        for (Village village : ALL) {
            if (village.selectable) {
                count++;
            }
        }

        Village[] choices = new Village[count];
        int index = 0;
        for (Village village : ALL) {
            if (village.selectable) {
                choices[index++] = village;
            }
        }
        return choices;
    }
}
