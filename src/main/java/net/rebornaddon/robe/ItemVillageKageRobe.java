package net.rebornaddon.robe;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.narutomod.item.ItemRobe;
import net.rebornaddon.RebornAddonMod;

import java.lang.reflect.Field;

public final class ItemVillageKageRobe extends ItemRobe.Base {
    private final String texture;
    private final String displayName;
    private ModelBiped robeModel;

    ItemVillageKageRobe(String name, String displayName, String texture) {
        super(EntityEquipmentSlot.CHEST);
        this.displayName = displayName;
        this.texture = RebornAddonMod.MODID + ":textures/models/armor/" + texture + ".png";
        setRegistryName(RebornAddonMod.MODID, name);
        setUnlocalizedName(RebornAddonMod.MODID + "." + name);
        setCreativeTab(net.narutomod.creativetab.TabModTab.tab);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entity, ItemStack stack,
                                    EntityEquipmentSlot slot, ModelBiped defaultModel) {
        if (robeModel == null) {
            robeModel = new ItemRobe.ModelRobe();
        }
        robeModel.setModelAttributes(defaultModel);
        ClientModelVisibility.configure(robeModel);
        return robeModel;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return texture;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String key = getUnlocalizedName(stack) + ".name";
        String localized = net.minecraft.util.text.translation.I18n.translateToLocal(key);
        return key.equals(localized) ? displayName : localized;
    }

    @SideOnly(Side.CLIENT)
    private static final class ClientModelVisibility {
        private static final Field HEAD = find("bipedHead", "field_78116_c", "c");
        private static final Field HEADWEAR = find("bipedHeadwear", "field_178720_f", "f");
        private static final Field BODY = find("bipedBody", "field_78115_e", "g");
        private static final Field RIGHT_ARM = find("bipedRightArm", "field_178723_h", "h");
        private static final Field LEFT_ARM = find("bipedLeftArm", "field_178724_i", "i");
        private static final Field RIGHT_LEG = find("bipedRightLeg", "field_178721_j", "j");
        private static final Field LEFT_LEG = find("bipedLeftLeg", "field_178722_k", "k");
        private static final Field COLLAR = find(ItemRobe.ModelRobe.class, "collar");
        private static final Field COLLAR_SECONDARY = find(ItemRobe.ModelRobe.class, "collar2");

        private ClientModelVisibility() {
        }

        private static void configure(ModelBiped model) {
            visible(HEAD, model, false);
            visible(HEADWEAR, model, false);
            visible(BODY, model, true);
            visible(RIGHT_ARM, model, true);
            visible(LEFT_ARM, model, true);
            visible(RIGHT_LEG, model, false);
            visible(LEFT_LEG, model, false);
            visible(COLLAR, model, false);
            visible(COLLAR_SECONDARY, model, false);
        }

        private static void visible(Field field, ModelBiped model, boolean value) {
            if (field == null) {
                return;
            }
            try {
                Object renderer = field.get(model);
                if (renderer instanceof net.minecraft.client.model.ModelRenderer) {
                    ((net.minecraft.client.model.ModelRenderer) renderer).showModel = value;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        private static Field find(String... names) {
            return find(ModelBiped.class, names);
        }

        private static Field find(Class<?> owner, String... names) {
            for (String name : names) {
                try {
                    Field field = owner.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            return null;
        }
    }
}
