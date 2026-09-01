package net.rebornaddon.content.entity;

import com.google.gson.Gson;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.world.World;
import net.rebornaddon.content.NativeContentModel;
import net.rebornaddon.content.NativeContentService;
import net.rebornaddon.compat.ChakraControlCompatibility;

import java.util.UUID;

public class EntityMissionNpc extends EntityMob {
    private static final Gson GSON = new Gson();
    private static final DataParameter<String> DISPLAY_NAME = EntityDataManager.createKey(
            EntityMissionNpc.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN = EntityDataManager.createKey(
            EntityMissionNpc.class, DataSerializers.STRING);
    private static final DataParameter<Boolean> SLIM = EntityDataManager.createKey(
            EntityMissionNpc.class, DataSerializers.BOOLEAN);

    private String templateId = "";
    private String contextId = "";
    private String instanceId = "";
    private UUID owningPlayer;
    private String purpose = "ambient";
    private double damageReduction;
    private double projectileDamageReduction;
    private double magicDamageReduction;
    private double explosionDamageReduction;
    private boolean canDrown = true;
    private boolean takesFallDamage = true;
    private boolean pathAroundWater = true;
    private boolean chakraControl;
    private boolean chakraWaterWalking;
    private boolean chakraWallClimbing;
    private boolean configuredHostile;
    private boolean configuredInvulnerable;
    private boolean canOpenDoors;
    private boolean stationary;
    private boolean retaliates = true;
    private boolean assistsAllies = true;
    private boolean immuneToPoison;
    private boolean immuneToWither;
    private boolean immuneToNegativeEffects;
    private boolean ignoresWebs;
    private boolean burnsInSun;
    private boolean sprintsInCombat;
    private double attackKnockback;
    private double meleeReach;
    private double regenerationPerSecond;
    private double combatRegenerationPerSecond;
    private int meleeIntervalTicks = 20;
    private String ambientSound = "";
    private String hurtSound = "";
    private String deathSound = "";
    private NativeContentModel.NpcTemplate configuredTemplate;
    private int jutsuDelay;

    public EntityMissionNpc(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = 0;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(DISPLAY_NAME, "NPC");
        dataManager.register(SKIN, "");
        dataManager.register(SLIM, Boolean.FALSE);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        if (getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE) == null) {
            getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        }
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.28D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24.0D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new ConfigurableMeleeAttack(this));
        tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 0.9D));
        tasks.addTask(6, new EntityAIWanderAvoidWater(this, 0.7D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 10.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!world.isRemote && chakraControl && !stationary) {
            ChakraControlCompatibility.applyNpcMovement(this, chakraWaterWalking,
                    chakraWallClimbing);
        }
        if (world.isRemote) {
            return;
        }
        if (jutsuDelay > 0) {
            jutsuDelay--;
        }
        if (ticksExisted % 10 == 0) {
            NativeContentService.INSTANCE.updateNpcTarget(this);
        }
        if (stationary) {
            getNavigator().clearPath();
            motionX = 0.0D;
            motionZ = 0.0D;
        }
        EntityLivingBase target = getAttackTarget();
        setSprinting(sprintsInCombat && target != null);
        if (ticksExisted % 20 == 0 && getHealth() < getMaxHealth()) {
            double amount = isTrainingTarget() ? getMaxHealth()
                    : target == null ? regenerationPerSecond : combatRegenerationPerSecond;
            if (amount > 0.0D) heal((float) Math.min(amount, getMaxHealth() - getHealth()));
        }
        if (isTrainingTarget() && target != null) {
            setAttackTarget(null);
            setRevengeTarget(null);
        }
        if (burnsInSun && ticksExisted % 20 == 0 && world.isDaytime()
                && world.canSeeSky(getPosition()) && !isWet()) setFire(2);
        if (target != null && jutsuDelay <= 0) {
            jutsuDelay = NativeContentService.INSTANCE.tryNpcJutsu(this, target);
        }
    }

    @Override
    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (!world.isRemote && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            NativeContentService.INSTANCE.interact(
                    (net.minecraft.entity.player.EntityPlayerMP) player, this);
        }
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (configuredInvulnerable || isEntityInvulnerable(source)) {
            return false;
        }
        double reduction = damageReduction;
        if (source.isProjectile()) reduction = 1.0D - (1.0D - reduction) * (1.0D - projectileDamageReduction);
        if (source.isMagicDamage()) reduction = 1.0D - (1.0D - reduction) * (1.0D - magicDamageReduction);
        if (source.isExplosion()) reduction = 1.0D - (1.0D - reduction) * (1.0D - explosionDamageReduction);
        float reduced = (float) Math.max(0.0D, amount * (1.0D - clamp(reduction, 0.0D, 0.99D)));
        boolean damaged = reduced > 0.0F && super.attackEntityFrom(source, reduced);
        if (damaged && retaliates && source.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getTrueSource();
            setRevengeTarget(attacker);
            setAttackTarget(attacker);
            if (assistsAllies) {
                NativeContentService.INSTANCE.alertNpcAllies(this, attacker);
            }
        }
        return damaged;
    }

    @Override
    public boolean attackEntityAsMob(net.minecraft.entity.Entity entity) {
        boolean hit = super.attackEntityAsMob(entity);
        if (hit && attackKnockback > 0.0D && entity instanceof EntityLivingBase) {
            ((EntityLivingBase) entity).knockBack(this, (float) attackKnockback,
                    Math.sin(rotationYaw * 0.017453292D),
                    -Math.cos(rotationYaw * 0.017453292D));
        }
        return hit;
    }

    @Override
    public void addPotionEffect(PotionEffect effect) {
        if (effect == null) return;
        if (immuneToNegativeEffects && effect.getPotion().isBadEffect()) return;
        if (immuneToPoison && effect.getPotion() == MobEffects.POISON) return;
        if (immuneToWither && effect.getPotion() == MobEffects.WITHER) return;
        super.addPotionEffect(effect);
    }

    @Override
    public void setInWeb() {
        if (!ignoresWebs) super.setInWeb();
    }

    @Override
    public boolean canBreatheUnderwater() {
        return !canDrown || super.canBreatheUnderwater();
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
        if (takesFallDamage) {
            super.fall(distance, damageMultiplier);
        }
    }

    @Override
    public boolean getCanSpawnHere() {
        return false;
    }

    @Override
    protected void dropEquipment(boolean wasRecentlyHit, int lootingModifier) {
    }

    @Override
    protected ResourceLocation getLootTable() {
        return null;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public String getName() {
        String value = dataManager.get(DISPLAY_NAME);
        return value == null || value.isEmpty() ? "NPC" : value;
    }

    public void configure(NativeContentModel.NpcTemplate template, String contextId,
                          String instanceId, UUID owner, String purpose) {
        this.templateId = safe(template == null ? "" : template.id, 96);
        this.contextId = safe(contextId, 96);
        this.instanceId = safe(instanceId, 96);
        this.owningPlayer = owner;
        this.purpose = safe(purpose, 24);
        configuredTemplate = template;
        if (template == null) {
            return;
        }
        dataManager.set(DISPLAY_NAME, safe(template.name, 64));
        dataManager.set(SKIN, safe(template.skin, 1024));
        dataManager.set(SLIM, Boolean.valueOf(template.slim));
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
                .setBaseValue(clamp(template.health, 1.0D, 1000000.0D));
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)
                .setBaseValue(clamp(template.meleeDamage, 0.0D, 100000.0D));
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED)
                .setBaseValue(clamp(template.moveSpeed, 0.05D, 1.25D));
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE)
                .setBaseValue(clamp(template.followRange, 4.0D, 128.0D));
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(clamp(template.knockbackResistance, 0.0D, 1.0D));
        setHealth(getMaxHealth());
        damageReduction = clamp(template.damageReduction, 0.0D, 0.95D);
        projectileDamageReduction = clamp(template.projectileDamageReduction, 0.0D, 0.95D);
        magicDamageReduction = clamp(template.magicDamageReduction, 0.0D, 0.95D);
        explosionDamageReduction = clamp(template.explosionDamageReduction, 0.0D, 0.95D);
        isImmuneToFire = template.fireImmune;
        configuredInvulnerable = template.invulnerable;
        canDrown = template.canDrown;
        takesFallDamage = template.takesFallDamage;
        pathAroundWater = template.pathAroundWater;
        chakraControl = template.chakraControl;
        chakraWaterWalking = template.chakraWaterWalking;
        chakraWallClimbing = template.chakraWallClimbing;
        configuredHostile = template.hostile;
        attackKnockback = clamp(template.attackKnockback, 0.0D, 8.0D);
        meleeIntervalTicks = Math.max(1, Math.min(1200, template.meleeIntervalTicks));
        canOpenDoors = template.canOpenDoors;
        stationary = template.stationary;
        retaliates = template.retaliates;
        assistsAllies = template.assistsAllies;
        immuneToPoison = template.immuneToPoison;
        immuneToWither = template.immuneToWither;
        immuneToNegativeEffects = template.immuneToNegativeEffects;
        ignoresWebs = template.ignoresWebs;
        burnsInSun = template.burnsInSun;
        sprintsInCombat = template.sprintsInCombat;
        ambientSound = safe(template.ambientSound, 160);
        hurtSound = safe(template.hurtSound, 160);
        deathSound = safe(template.deathSound, 160);
        meleeReach = clamp(template.meleeReach, 0.0D, 16.0D);
        regenerationPerSecond = clamp(template.regenerationPerSecond, 0.0D, 100000.0D);
        combatRegenerationPerSecond = clamp(template.combatRegenerationPerSecond, 0.0D, 100000.0D);
        configureWaterPathing();
        equip(EntityEquipmentSlot.MAINHAND, template.equipment.mainHand,
                template.equipment.mainHandMeta, template.equipment.mainHandNbt);
        equip(EntityEquipmentSlot.OFFHAND, template.equipment.offHand,
                template.equipment.offHandMeta, template.equipment.offHandNbt);
        equip(EntityEquipmentSlot.HEAD, template.equipment.head,
                template.equipment.headMeta, template.equipment.headNbt);
        equip(EntityEquipmentSlot.CHEST, template.equipment.chest,
                template.equipment.chestMeta, template.equipment.chestNbt);
        equip(EntityEquipmentSlot.LEGS, template.equipment.legs,
                template.equipment.legsMeta, template.equipment.legsNbt);
        equip(EntityEquipmentSlot.FEET, template.equipment.feet,
                template.equipment.feetMeta, template.equipment.feetNbt);
    }

    public void configurePreview(String name, String skin, boolean slim) {
        dataManager.set(DISPLAY_NAME, safe(name, 64));
        dataManager.set(SKIN, safe(skin, 1024));
        dataManager.set(SLIM, Boolean.valueOf(slim));
    }

    private void equip(EntityEquipmentSlot slot, String registryName, int meta, String nbt) {
        setItemStackToSlot(slot, NativeContentService.itemStack(registryName, 1, meta, nbt));
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        templateId = tag.getString("Template");
        contextId = tag.getString("Context");
        instanceId = tag.getString("Instance");
        purpose = tag.getString("Purpose");
        String owner = tag.getString("Owner");
        try {
            owningPlayer = owner.isEmpty() ? null : UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            owningPlayer = null;
        }
        dataManager.set(DISPLAY_NAME, tag.getString("DisplayName"));
        dataManager.set(SKIN, tag.getString("Skin"));
        dataManager.set(SLIM, Boolean.valueOf(tag.getBoolean("Slim")));
        damageReduction = tag.getDouble("DamageReduction");
        projectileDamageReduction = tag.getDouble("ProjectileReduction");
        magicDamageReduction = tag.getDouble("MagicReduction");
        explosionDamageReduction = tag.getDouble("ExplosionReduction");
        canDrown = !tag.hasKey("CanDrown") || tag.getBoolean("CanDrown");
        takesFallDamage = !tag.hasKey("FallDamage") || tag.getBoolean("FallDamage");
        pathAroundWater = !tag.hasKey("AvoidWater") || tag.getBoolean("AvoidWater");
        chakraControl = tag.getBoolean("ChakraControl");
        chakraWaterWalking = tag.getBoolean("ChakraWaterWalking");
        chakraWallClimbing = tag.getBoolean("ChakraWallClimbing");
        configuredHostile = tag.getBoolean("Hostile");
        configuredInvulnerable = tag.getBoolean("Invulnerable");
        canOpenDoors = tag.getBoolean("OpenDoors");
        stationary = tag.getBoolean("Stationary");
        retaliates = !tag.hasKey("Retaliates") || tag.getBoolean("Retaliates");
        assistsAllies = !tag.hasKey("AssistsAllies") || tag.getBoolean("AssistsAllies");
        immuneToPoison = tag.getBoolean("ImmunePoison");
        immuneToWither = tag.getBoolean("ImmuneWither");
        immuneToNegativeEffects = tag.getBoolean("ImmuneNegativeEffects");
        ignoresWebs = tag.getBoolean("IgnoresWebs");
        burnsInSun = tag.getBoolean("BurnsInSun");
        sprintsInCombat = tag.getBoolean("SprintsInCombat");
        attackKnockback = tag.getDouble("AttackKnockback");
        meleeIntervalTicks = tag.hasKey("MeleeInterval")
                ? Math.max(1, tag.getInteger("MeleeInterval")) : 20;
        meleeReach = tag.getDouble("MeleeReach");
        regenerationPerSecond = tag.getDouble("Regeneration");
        combatRegenerationPerSecond = tag.getDouble("CombatRegeneration");
        ambientSound = tag.getString("AmbientSound");
        hurtSound = tag.getString("HurtSound");
        deathSound = tag.getString("DeathSound");
        if (tag.hasKey("TemplateData")) {
            try {
                configuredTemplate = GSON.fromJson(tag.getString("TemplateData"),
                        NativeContentModel.NpcTemplate.class);
            } catch (Throwable ignored) {
                configuredTemplate = null;
            }
        }
        configureWaterPathing();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("Template", templateId);
        tag.setString("Context", contextId);
        tag.setString("Instance", instanceId);
        tag.setString("Purpose", purpose);
        tag.setString("Owner", owningPlayer == null ? "" : owningPlayer.toString());
        tag.setString("DisplayName", getName());
        tag.setString("Skin", getSkin());
        tag.setBoolean("Slim", isSlim());
        tag.setDouble("DamageReduction", damageReduction);
        tag.setDouble("ProjectileReduction", projectileDamageReduction);
        tag.setDouble("MagicReduction", magicDamageReduction);
        tag.setDouble("ExplosionReduction", explosionDamageReduction);
        tag.setBoolean("CanDrown", canDrown);
        tag.setBoolean("FallDamage", takesFallDamage);
        tag.setBoolean("AvoidWater", pathAroundWater);
        tag.setBoolean("ChakraControl", chakraControl);
        tag.setBoolean("ChakraWaterWalking", chakraWaterWalking);
        tag.setBoolean("ChakraWallClimbing", chakraWallClimbing);
        tag.setBoolean("Hostile", configuredHostile);
        tag.setBoolean("Invulnerable", configuredInvulnerable);
        tag.setBoolean("OpenDoors", canOpenDoors);
        tag.setBoolean("Stationary", stationary);
        tag.setBoolean("Retaliates", retaliates);
        tag.setBoolean("AssistsAllies", assistsAllies);
        tag.setBoolean("ImmunePoison", immuneToPoison);
        tag.setBoolean("ImmuneWither", immuneToWither);
        tag.setBoolean("ImmuneNegativeEffects", immuneToNegativeEffects);
        tag.setBoolean("IgnoresWebs", ignoresWebs);
        tag.setBoolean("BurnsInSun", burnsInSun);
        tag.setBoolean("SprintsInCombat", sprintsInCombat);
        tag.setDouble("AttackKnockback", attackKnockback);
        tag.setInteger("MeleeInterval", meleeIntervalTicks);
        tag.setDouble("MeleeReach", meleeReach);
        tag.setDouble("Regeneration", regenerationPerSecond);
        tag.setDouble("CombatRegeneration", combatRegenerationPerSecond);
        tag.setString("AmbientSound", ambientSound);
        tag.setString("HurtSound", hurtSound);
        tag.setString("DeathSound", deathSound);
        if (configuredTemplate != null) tag.setString("TemplateData", GSON.toJson(configuredTemplate));
    }

    public String getTemplateId() { return templateId; }
    public String getContextId() { return contextId; }
    public String getInstanceId() { return instanceId; }
    public UUID getOwningPlayer() { return owningPlayer; }
    public String getPurpose() { return purpose; }

    public boolean isTrainingTarget() {
        return "training".equalsIgnoreCase(purpose)
                || configuredTemplate != null
                && "training_target".equalsIgnoreCase(configuredTemplate.role);
    }
    public String getSkin() { return dataManager.get(SKIN); }
    public boolean isSlim() { return dataManager.get(SLIM).booleanValue(); }
    public boolean isConfiguredHostile() { return configuredHostile; }
    public NativeContentModel.NpcTemplate getConfiguredTemplate() { return configuredTemplate; }
    public boolean assistsAllies() { return assistsAllies; }
    public boolean isStationary() { return stationary; }
    public boolean canUseChakraWaterWalking() {
        return chakraControl && chakraWaterWalking && !stationary;
    }

    private void configureWaterPathing() {
        if (getNavigator() instanceof PathNavigateGround) {
            PathNavigateGround navigator = (PathNavigateGround) getNavigator();
            navigator.setCanSwim(!pathAroundWater);
            navigator.setEnterDoors(canOpenDoors);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() { return sound(ambientSound, super.getAmbientSound()); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return sound(hurtSound, super.getHurtSound(source)); }

    @Override
    protected SoundEvent getDeathSound() { return sound(deathSound, super.getDeathSound()); }

    private static SoundEvent sound(String id, SoundEvent fallback) {
        if (id == null || id.isEmpty()) return fallback;
        try {
            SoundEvent value = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String safe(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static final class ConfigurableMeleeAttack extends EntityAIAttackMelee {
        private final EntityMissionNpc owner;

        private ConfigurableMeleeAttack(EntityMissionNpc owner) {
            super(owner, 1.05D, false);
            this.owner = owner;
        }

        @Override
        protected void checkAndPerformAttack(EntityLivingBase target, double distanceSq) {
            if (distanceSq <= getAttackReachSqr(target) && attackTick <= 0) {
                attackTick = owner.meleeIntervalTicks;
                owner.swingArm(EnumHand.MAIN_HAND);
                owner.attackEntityAsMob((Entity) target);
            }
        }

        @Override
        protected double getAttackReachSqr(EntityLivingBase target) {
            return owner.meleeReach > 0.0D ? owner.meleeReach * owner.meleeReach
                    : super.getAttackReachSqr(target);
        }
    }
}
