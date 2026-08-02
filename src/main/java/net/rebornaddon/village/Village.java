package net.rebornaddon.village;

import java.util.Locale;

public enum Village {
    STONE(0, "stone", "Stone", 0xFF8D8575, 0xFFC4B28A),
    LEAF(1, "leaf", "Leaf", 0xFF4F8D58, 0xFF86C46E),
    CLOUD(2, "cloud", "Cloud", 0xFF5A86A6, 0xFFE2B95B),
    SAND(3, "sand", "Sand", 0xFFB88A45, 0xFFE3C16A),
    MIST(4, "mist", "Mist", 0xFF4E8D91, 0xFF9AD4CF);

    private static final Village[] CHOICES = values();

    private final int id;
    private final String group;
    private final String displayName;
    private final int accentColor;
    private final int hoverColor;

    Village(int id, String group, String displayName, int accentColor, int hoverColor) {
        this.id = id;
        this.group = group;
        this.displayName = displayName;
        this.accentColor = accentColor;
        this.hoverColor = hoverColor;
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

    public static Village byId(int id) {
        for (Village village : CHOICES) {
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
        for (Village village : CHOICES) {
            if (village.group.equals(normalized)) {
                return village;
            }
        }
        return null;
    }

    public static Village[] choices() {
        return CHOICES.clone();
    }
}
