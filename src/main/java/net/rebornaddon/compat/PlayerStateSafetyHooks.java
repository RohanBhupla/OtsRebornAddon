package net.rebornaddon.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;

public final class PlayerStateSafetyHooks {
    private static final float MAX_ABSORPTION = 1024.0F;
    private static final UUID PARALYSIS_SPEED_MODIFIER =
            UUID.fromString("c69af92a-b96d-49b7-a396-9b3b0d77edd5");

    private PlayerStateSafetyHooks() {
    }

    public static float validateAbsorptionWrite(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F) {
            return 0.0F;
        }
        return Math.min(amount, MAX_ABSORPTION);
    }

    public static boolean reconcileAbsorption(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        float current = entity.getAbsorptionAmount();
        float maximum = absorptionCapacity(entity.getActivePotionEffect(MobEffects.ABSORPTION));
        float corrected;
        if (!Float.isFinite(current) || current <= 0.0F || maximum <= 0.0F) {
            corrected = 0.0F;
        } else {
            corrected = Math.min(current, maximum);
        }
        if (Float.floatToIntBits(current) == Float.floatToIntBits(corrected)) {
            return false;
        }
        entity.setAbsorptionAmount(corrected);
        return true;
    }

    public static void expireNarutoParalysis(Map<String, Object> dependencies) {
        if (dependencies == null) {
            return;
        }
        Object value = dependencies.get("entity");
        if (!(value instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase entity = (EntityLivingBase) value;
        IAttributeInstance movement = entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (movement != null && movement.getModifier(PARALYSIS_SPEED_MODIFIER) != null) {
            movement.removeModifier(PARALYSIS_SPEED_MODIFIER);
        }
        Entity rawEntity = (Entity) value;
        if (rawEntity.getEntityData().hasKey("FearEffect")) {
            rawEntity.getEntityData().removeTag("FearEffect");
        }
    }

    private static float absorptionCapacity(PotionEffect effect) {
        if (effect == null) {
            return 0.0F;
        }
        int amplifier = effect.getAmplifier();
        if (amplifier < 0 || amplifier > 255) {
            return 0.0F;
        }
        return 4.0F * (amplifier + 1);
    }
}
