package net.minecraft.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.text.ITextComponent;

public class EntityDamageSourceIndirect extends EntityDamageSource {
    private final Entity immediateEntity;
    private final Entity indirectEntity;

    public EntityDamageSourceIndirect(String damageTypeIn, Entity source, Entity indirectEntityIn) {
        super(damageTypeIn, source);
        this.immediateEntity = source;
        this.indirectEntity = indirectEntityIn;
    }

    public Entity getImmediateSource() {
        return immediateEntity;
    }

    public Entity getTrueSource() {
        return indirectEntity;
    }

    public DamageSource func_151518_m() {
        return this;
    }

    public ITextComponent getDeathMessage(EntityLivingBase entityLivingBaseIn) {
        return super.getDeathMessage(entityLivingBaseIn);
    }
}
