package net.rebornaddon.armor;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.RebornAddonMod;

import java.util.Locale;

public enum OtsutsukiArmorSet {
    HAGOROMO("Hagoromo"),
    HAMURA("Hamura"),
    ISSHIKI("Isshiki"),
    KAGUYA("Kaguya"),
    KINSHIKI("Kinshiki"),
    MOMOSHIKI("Momoshiki"),
    SHIBAI("Shibai"),
    TONERI("Toneri"),
    URASHIKI("Urashiki");

    private final String id;
    private final String displayName;

    OtsutsukiArmorSet(String displayName) {
        this.id = displayName.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String itemId(EntityEquipmentSlot slot) {
        return id + "_" + pieceId(slot);
    }

    public String itemName(EntityEquipmentSlot slot) {
        return displayName + " " + pieceName(slot);
    }

    public ResourceLocation modelResource(boolean leggings) {
        return new ResourceLocation(RebornAddonMod.MODID,
                "armor_models/otsutsuki/" + id + (leggings ? "_leggings.json" : "_main.json"));
    }

    public String texture(boolean leggings) {
        return RebornAddonMod.MODID + ":textures/models/armor/otsutsuki/" + id
                + (leggings ? "_leggings.png" : "_main.png");
    }

    private static String pieceId(EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return "helmet";
            case CHEST:
                return "chestplate";
            case LEGS:
                return "leggings";
            case FEET:
                return "boots";
            default:
                throw new IllegalArgumentException("Unsupported armor slot " + slot);
        }
    }

    private static String pieceName(EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return "Helmet";
            case CHEST:
                return "Chestplate";
            case LEGS:
                return "Leggings";
            case FEET:
                return "Boots";
            default:
                throw new IllegalArgumentException("Unsupported armor slot " + slot);
        }
    }
}
