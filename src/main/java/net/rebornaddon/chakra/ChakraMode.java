package net.rebornaddon.chakra;

import net.rebornaddon.village.Village;

public enum ChakraMode {
    WATER(0, "water", "Water Chakra Mode", "Fluid Guard", 0xFF4DB9D4,
            "narutomod:textures/blocks/suiton.png", Village.MIST, 15.0D),
    RAIN(1, "rain", "Rain Chakra Mode", "Storm Veil", 0xFF718BCA,
            "narutomod:textures/blocks/suiton.png", Village.RAIN, 16.0D),
    EARTH(2, "earth", "Earth Chakra Mode", "Stone Mantle", 0xFF9A7B4F,
            "narutomod:textures/blocks/doton.png", Village.STONE, 18.0D);

    private static final ChakraMode[] VALUES = values();

    private final int id;
    private final String key;
    private final String displayName;
    private final String styleName;
    private final int color;
    private final String iconTexture;
    private final Village requiredVillage;
    private final double chakraPerSecond;

    ChakraMode(int id, String key, String displayName, String styleName, int color,
               String iconTexture, Village requiredVillage, double chakraPerSecond) {
        this.id = id;
        this.key = key;
        this.displayName = displayName;
        this.styleName = styleName;
        this.color = color;
        this.iconTexture = iconTexture;
        this.requiredVillage = requiredVillage;
        this.chakraPerSecond = chakraPerSecond;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String styleName() {
        return styleName;
    }

    public int color() {
        return color;
    }

    public String iconTexture() {
        return iconTexture;
    }

    public Village requiredVillage() {
        return requiredVillage;
    }

    public double chakraPerSecond() {
        return chakraPerSecond;
    }

    public static ChakraMode byId(int id) {
        for (ChakraMode mode : VALUES) {
            if (mode.id == id) {
                return mode;
            }
        }
        return null;
    }
}
