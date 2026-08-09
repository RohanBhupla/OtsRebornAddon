package net.rebornaddon.headband.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.narutomod.item.ItemNinjaArmor;

import java.lang.reflect.Field;

public final class ClientRogueArmorData extends ItemNinjaArmor.ArmorData {
    private static final Field HEADWEAR_FIELD = findHeadwearField();

    private final boolean hideHeadwear;

    public ClientRogueArmorData(ItemNinjaArmor.Type type, String texture, boolean hideHeadwear) {
        this.model = new ItemNinjaArmor.ModelNinjaArmor(type);
        this.texture = texture;
        this.hideHeadwear = hideHeadwear;
    }

    @Override
    public void setSlotVisible() {
        if (!hideHeadwear || HEADWEAR_FIELD == null) {
            return;
        }
        try {
            Object headwear = HEADWEAR_FIELD.get(model);
            if (headwear instanceof ModelRenderer) {
                ((ModelRenderer) headwear).showModel = false;
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field findHeadwearField() {
        String[] names = {"bipedHeadwear", "field_178720_f", "f"};
        for (String name : names) {
            try {
                Field field = ModelBiped.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
