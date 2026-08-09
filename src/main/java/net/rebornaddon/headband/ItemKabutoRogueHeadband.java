package net.rebornaddon.headband;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.narutomod.item.ItemNinjaArmor;
import net.rebornaddon.RebornAddonMod;

public final class ItemKabutoRogueHeadband extends ItemNinjaArmor.Base {
    private final String displayName;

    ItemKabutoRogueHeadband(String name, String displayName, ItemNinjaArmor.Type village) {
        super(village, EntityEquipmentSlot.HEAD);
        this.displayName = displayName;
        setRegistryName(RebornAddonMod.MODID, name);
        setUnlocalizedName(RebornAddonMod.MODID + "." + name);
        setCreativeTab(net.narutomod.creativetab.TabModTab.tab);
    }

    @Override
    protected ItemNinjaArmor.ArmorData setArmorData(ItemNinjaArmor.Type type, EntityEquipmentSlot slot) {
        String texture;
        switch (type) {
            case KONOHA:
                texture = "kabuto_rogue_leaf_headband";
                break;
            case AME:
                texture = "kabuto_rogue_rain_headband";
                break;
            case SUNA:
                texture = "kabuto_rogue_sand_headband";
                break;
            case KUMO:
                texture = "kabuto_rogue_cloud_headband";
                break;
            case IWA:
                texture = "kabuto_rogue_stone_headband";
                break;
            case OTO:
                texture = "kabuto_rogue_sound_headband";
                break;
            default:
                throw new IllegalArgumentException("Unsupported Kabuto rogue headband type: " + type);
        }
        return RebornAddonMod.proxy.createRogueArmorData(ItemNinjaArmor.Type.KONOHA,
                RebornAddonMod.MODID + ":textures/models/armor/" + texture + ".png", false);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String key = getUnlocalizedName(stack) + ".name";
        String localized = net.minecraft.util.text.translation.I18n.translateToLocal(key);
        return key.equals(localized) ? displayName : localized;
    }
}
