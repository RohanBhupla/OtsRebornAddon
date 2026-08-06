package net.rebornaddon.substitution;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.rebornaddon.village.Village;

import java.util.UUID;

public class EntitySubstitutionDecoy extends Entity {
    private static final DataParameter<Integer> VILLAGE =
            EntityDataManager.createKey(EntitySubstitutionDecoy.class, DataSerializers.VARINT);
    private static final DataParameter<String> OWNER =
            EntityDataManager.createKey(EntitySubstitutionDecoy.class, DataSerializers.STRING);
    private static final DataParameter<Integer> MAX_AGE =
            EntityDataManager.createKey(EntitySubstitutionDecoy.class, DataSerializers.VARINT);
    private static final int DEFAULT_MAX_AGE = 20;

    public EntitySubstitutionDecoy(World world) {
        super(world);
        setSize(0.7F, 1.8F);
        this.noClip = false;
        this.isImmuneToFire = true;
    }

    public EntitySubstitutionDecoy(World world, UUID ownerId, Village village, double x, double y, double z) {
        this(world);
        setOwnerId(ownerId);
        setVillage(village);
        setPosition(x, y, z);
    }

    @Override
    protected void entityInit() {
        dataManager.register(VILLAGE, Integer.valueOf(0));
        dataManager.register(OWNER, "");
        dataManager.register(MAX_AGE, Integer.valueOf(DEFAULT_MAX_AGE));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;

        if (world.isRemote) {
            return;
        }

        int maxAge = getMaxAge();
        if (ticksExisted >= maxAge) {
            SubstitutionHandler.INSTANCE.onDecoyExpired(this);
            setDead();
        } else if (ticksExisted % Math.max(4, maxAge / 5) == 0) {
            SubstitutionHandler.INSTANCE.onDecoyPulse(this, ticksExisted);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!world.isRemote && !isDead) {
            triggerHit();
        }
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBeAttackedWithItem() {
        return true;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        setVillage(Village.byId(compound.getInteger("Village")));
        if (compound.hasKey("Owner")) {
            try {
                setOwnerId(UUID.fromString(compound.getString("Owner")));
            } catch (IllegalArgumentException ignored) {
                setOwnerId(null);
            }
        }
        setMaxAge(compound.hasKey("MaxAge") ? compound.getInteger("MaxAge") : DEFAULT_MAX_AGE);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("Village", getVillage().id());
        UUID owner = getOwnerId();
        if (owner != null) {
            compound.setString("Owner", owner.toString());
        }
        compound.setInteger("MaxAge", getMaxAge());
    }

    public void triggerHit() {
        SubstitutionHandler.INSTANCE.onDecoyHit(this);
        setDead();
    }

    public UUID getOwnerId() {
        String owner = dataManager.get(OWNER);
        if (owner == null || owner.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public Village getVillage() {
        Village village = Village.byId(dataManager.get(VILLAGE).intValue());
        return village == null ? Village.LEAF : village;
    }

    public int getMaxAge() {
        return dataManager.get(MAX_AGE).intValue();
    }

    void setMaxAge(int maxAge) {
        dataManager.set(MAX_AGE, Integer.valueOf(Math.max(1, maxAge)));
    }

    private void setVillage(Village village) {
        dataManager.set(VILLAGE, Integer.valueOf(village == null ? Village.LEAF.id() : village.id()));
    }

    private void setOwnerId(UUID ownerId) {
        dataManager.set(OWNER, ownerId == null ? "" : ownerId.toString());
    }
}
