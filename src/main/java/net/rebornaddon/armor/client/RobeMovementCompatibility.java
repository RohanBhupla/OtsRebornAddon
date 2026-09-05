package net.rebornaddon.armor.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.rebornaddon.armor.ItemOtsutsukiCosmeticArmor;
import net.rebornaddon.armor.OtsutsukiArmorSet;

/** Client render hook that keeps player legs completely still inside floor-length robes. */
public final class RobeMovementCompatibility {
    static final float RIGHT_LEG_X = -1.9F;
    static final float LEFT_LEG_X = 1.9F;
    static final float LEG_Y = 12.0F;

    private RobeMovementCompatibility() {
    }

    public static void constrainLegs(ModelBiped model, Entity entity) {
        if (model == null || !(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        if (!wearsLongRobe(living)) {
            return;
        }
        // Armor layers are separate ModelBiped instances. Freezing all of them
        // would also freeze boots and other leg-slot pieces during Naruto run.
        if (!isPlayerBodyModel(model)) {
            return;
        }
        freezeLegGeometry(model);
        hidePlayerLegGeometry(model);
    }

    static void freezeLegGeometry(ModelBiped model) {
        freezeLeg(model.bipedRightLeg, RIGHT_LEG_X);
        freezeLeg(model.bipedLeftLeg, LEFT_LEG_X);
    }

    static boolean isPlayerBodyModel(ModelBiped model) {
        return model instanceof ModelPlayer;
    }

    private static void freezeLeg(net.minecraft.client.model.ModelRenderer leg, float x) {
        leg.rotateAngleX = 0.0F;
        leg.rotateAngleY = 0.0F;
        leg.rotateAngleZ = 0.0F;
        leg.rotationPointX = x;
        leg.rotationPointY = LEG_Y;
        leg.rotationPointZ = 0.0F;
        leg.offsetX = 0.0F;
        leg.offsetY = 0.0F;
        leg.offsetZ = 0.0F;
    }

    static void hidePlayerLegGeometry(ModelBiped model) {
        if (!(model instanceof ModelPlayer)) {
            return;
        }
        ModelPlayer playerModel = (ModelPlayer) model;
        playerModel.bipedRightLeg.showModel = false;
        playerModel.bipedLeftLeg.showModel = false;
        playerModel.bipedRightLegwear.showModel = false;
        playerModel.bipedLeftLegwear.showModel = false;
    }

    static boolean wearsLongRobe(EntityLivingBase entity) {
        ItemStack leggings = entity.getItemStackFromSlot(EntityEquipmentSlot.LEGS);
        if (!(leggings.getItem() instanceof ItemOtsutsukiCosmeticArmor)) {
            return false;
        }
        OtsutsukiArmorSet armorSet =
                ((ItemOtsutsukiCosmeticArmor) leggings.getItem()).armorSet();
        return armorSet == OtsutsukiArmorSet.KAGUYA
                || armorSet == OtsutsukiArmorSet.HAGOROMO;
    }
}
