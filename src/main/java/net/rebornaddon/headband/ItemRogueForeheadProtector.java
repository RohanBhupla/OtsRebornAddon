package net.rebornaddon.headband;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.narutomod.item.ItemNinjaArmor;
import net.rebornaddon.RebornAddonMod;

public final class ItemRogueForeheadProtector extends ItemNinjaArmor.Base {
    private final String displayName;

    ItemRogueForeheadProtector(String name, String displayName, ItemNinjaArmor.Type type) {
        super(type, EntityEquipmentSlot.HEAD);
        this.displayName = displayName;
        setRegistryName(RebornAddonMod.MODID, name);
        setUnlocalizedName(RebornAddonMod.MODID + "." + name);
        setCreativeTab(net.narutomod.creativetab.TabModTab.tab);
    }

    @Override
    protected ItemNinjaArmor.ArmorData setArmorData(ItemNinjaArmor.Type type, EntityEquipmentSlot slot) {
        String texture;
        boolean hideHeadwear = true;
        switch (type) {
            case KONOHA:
                texture = "rogue_konoha";
                break;
            case WAR1:
                texture = "rogue_konoha_war";
                break;
            case AME:
                texture = "rogue_ame";
                hideHeadwear = false;
                break;
            case IWA:
                texture = "rogue_iwa";
                break;
            case KIRI:
                texture = "rogue_kiri";
                break;
            case KUMO:
                texture = "rogue_kumo";
                break;
            case SUNA:
                texture = "rogue_suna";
                break;
            case OTO:
                texture = "rogue_oto";
                break;
            default:
                throw new IllegalArgumentException("Unsupported rogue forehead protector type: " + type);
        }
        return RebornAddonMod.proxy.createRogueArmorData(type,
                RebornAddonMod.MODID + ":textures/models/armor/" + texture + ".png", hideHeadwear);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String key = getUnlocalizedName(stack) + ".name";
        String localized = net.minecraft.util.text.translation.I18n.translateToLocal(key);
        return key.equals(localized) ? displayName : localized;
    }
}
