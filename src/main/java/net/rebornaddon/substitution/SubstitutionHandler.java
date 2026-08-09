package net.rebornaddon.substitution;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.rebornaddon.compat.MinecraftAccess;
import net.rebornaddon.config.RebornAddonConfig;
import net.rebornaddon.village.LuckPermsBridge;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SubstitutionHandler {
    public static final SubstitutionHandler INSTANCE = new SubstitutionHandler();

    private static final String CHAKRA_KEY = "ChakraPathwaySystem";
    private static final String VILLAGE_KEY = "RebornAddonVillage";
    private static final int COOLDOWN_TICKS = 20 * 20;
    private static final int DECOY_TICKS = 20;
    private static final int TEST_DECOY_TICKS = 100;
    private static final int INVULNERABLE_TICKS = 10;
    private static final double CHAKRA_COST = 25.0D;
    private static final double TELEPORT_DISTANCE = 8.0D;

    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Long> invulnerableUntil = new HashMap<UUID, Long>();
    private final Map<UUID, Decoy> decoys = new HashMap<UUID, Decoy>();

    private SubstitutionHandler() {
    }

    public void request(EntityPlayerMP player) {
        if (player == null || MinecraftAccess.isRemote(player) || player.isDead) {
            return;
        }

        World world = player.world;
        long now = world.getTotalWorldTime();
        Long cooldown = cooldowns.get(player.getUniqueID());
        if (!player.capabilities.isCreativeMode && cooldown != null && cooldown.longValue() > now) {
            long seconds = Math.max(1L, (cooldown.longValue() - now + 19L) / 20L);
            sendActionBarError(player, "Substitution is on cooldown for " + seconds + "s.");
            return;
        }

        Village village = resolveVillage(player);
        if (village == null) {
            sendActionBarError(player, "Choose a village before using Substitution.");
            return;
        }

        if (!consumeChakra(player)) {
            sendActionBarError(player, "Not enough chakra for Substitution.");
            return;
        }

        if (!performSubstitution(player, village, !player.capabilities.isCreativeMode)) {
            refundChakra(player);
        }
    }

    public boolean testFull(EntityPlayerMP player, Village village) {
        if (!isUsable(player, village)) {
            return false;
        }

        removeStoredDecoy(player.world, player.getUniqueID());
        return performSubstitution(player, village, false);
    }

    public boolean testPreview(EntityPlayerMP player, Village village) {
        if (!isUsable(player, village)) {
            return false;
        }

        removeStoredDecoy(player.world, player.getUniqueID());
        Vec3d preview = testPosition(player);
        spawnDecoy(player, village, preview.x, preview.y, preview.z,
                player.rotationYaw + 180.0F, TEST_DECOY_TICKS);
        if (player.world instanceof WorldServer) {
            playActivationEffects((WorldServer) player.world, village, preview.x, preview.y + 1.0D, preview.z);
        }
        return true;
    }

    public boolean testHit(EntityPlayerMP player, Village village) {
        if (!isUsable(player, village) || !(player.world instanceof WorldServer)) {
            return false;
        }

        Vec3d preview = testPosition(player);
        playHitEffects((WorldServer) player.world, village, preview.x, preview.y + 1.0D, preview.z);
        return true;
    }

    private boolean performSubstitution(EntityPlayerMP player, Village village, boolean applyCooldown) {
        if (isSusanoo(player.getRidingEntity())) {
            sendActionBarError(player, "Substitution cannot be used while inside Susanoo.");
            return false;
        }
        Vec3d target = findTeleportTarget(player);
        if (target == null) {
            sendActionBarError(player, "No safe substitution point found.");
            return false;
        }

        World world = player.world;
        long now = world.getTotalWorldTime();
        double oldX = player.posX;
        double oldY = player.posY;
        double oldZ = player.posZ;
        spawnDecoy(player, village, oldX, oldY, oldZ, player.rotationYaw, DECOY_TICKS);
        player.dismountRidingEntity();
        player.setPositionAndUpdate(target.x, target.y, target.z);
        player.fallDistance = 0.0F;
        player.hurtResistantTime = Math.max(player.hurtResistantTime, INVULNERABLE_TICKS);
        if (applyCooldown) {
            cooldowns.put(player.getUniqueID(), Long.valueOf(now + COOLDOWN_TICKS));
        }
        invulnerableUntil.put(player.getUniqueID(), Long.valueOf(now + INVULNERABLE_TICKS));

        if (world instanceof WorldServer) {
            WorldServer serverWorld = (WorldServer) world;
            playActivationEffects(serverWorld, village, oldX, oldY + 1.0D, oldZ);
            playArrivalEffects(serverWorld, village, target.x, target.y + 1.0D, target.z);
        }
        return true;
    }

    private static boolean isSusanoo(Entity entity) {
        for (Class<?> type = entity == null ? null : entity.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("susanoo")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.world == null || MinecraftAccess.isRemote(entity)) {
            return;
        }

        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        Long until = invulnerableUntil.get(entity.getUniqueID());
        if (until == null || entity.world.getTotalWorldTime() > until.longValue()) {
            return;
        }

        event.setCanceled(true);
        triggerStoredDecoyHit((EntityPlayer) entity);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) {
            return;
        }

        UUID id = event.player.getUniqueID();
        cooldowns.remove(id);
        invulnerableUntil.remove(id);
        removeStoredDecoy(event.player.world, id);
    }

    public void onDecoyPulse(EntitySubstitutionDecoy decoy, int age) {
        if (decoy != null && decoy.world instanceof WorldServer) {
            playDecoyPulseEffects((WorldServer) decoy.world, decoy.getVillage(),
                    decoy.posX, decoy.posY + 1.0D, decoy.posZ, age);
        }
    }

    public void onDecoyHit(EntitySubstitutionDecoy decoy) {
        onDecoyHit(decoy, null);
    }

    public void onDecoyHit(EntitySubstitutionDecoy decoy, Entity attacker) {
        if (decoy == null) {
            return;
        }

        UUID ownerId = decoy.getOwnerId();
        if (ownerId != null) {
            decoys.remove(ownerId);
        }

        if (decoy.world instanceof WorldServer) {
            playHitEffects((WorldServer) decoy.world, decoy.getVillage(),
                    decoy.posX, decoy.posY + 1.0D, decoy.posZ);
        }

        applyStun(decoy, attacker);
    }

    private static void applyStun(EntitySubstitutionDecoy decoy, Entity attacker) {
        if (!(attacker instanceof EntityLivingBase) || RebornAddonConfig.substitutionStunTicks <= 0) {
            return;
        }

        EntityLivingBase living = (EntityLivingBase) attacker;
        UUID ownerId = decoy.getOwnerId();
        if (ownerId != null && ownerId.equals(living.getUniqueID())) {
            return;
        }
        if (living instanceof EntityPlayer && ((EntityPlayer) living).capabilities.isCreativeMode) {
            return;
        }

        int duration = RebornAddonConfig.substitutionStunTicks;
        living.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, duration, 9, false, false));
        living.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, duration, 4, false, false));
        living.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, duration, 4, false, false));
        living.motionX = 0.0D;
        living.motionZ = 0.0D;
        if (living instanceof EntityPlayerMP) {
            sendActionBarError((EntityPlayerMP) living, "The substitution caught you off guard.");
        }
    }

    public void onDecoyExpired(EntitySubstitutionDecoy decoy) {
        if (decoy == null) {
            return;
        }

        UUID ownerId = decoy.getOwnerId();
        if (ownerId != null) {
            Decoy stored = decoys.get(ownerId);
            if (stored != null && stored.entityId == decoy.getEntityId()) {
                decoys.remove(ownerId);
            }
        }

        if (decoy.world instanceof WorldServer) {
            playFadeEffects((WorldServer) decoy.world, decoy.getVillage(),
                    decoy.posX, decoy.posY + 1.0D, decoy.posZ);
        }
    }

    private void spawnDecoy(EntityPlayerMP player, Village village, double x, double y, double z,
                            float yaw, int maxAge) {
        EntitySubstitutionDecoy decoy = new EntitySubstitutionDecoy(player.world, player.getUniqueID(), village, x, y, z);
        decoy.setMaxAge(maxAge);
        decoy.setLocationAndAngles(x, y, z, yaw, 0.0F);
        decoy.prevRotationYaw = yaw;
        if (player.world.spawnEntity(decoy)) {
            decoys.put(player.getUniqueID(),
                    new Decoy(player.world.provider.getDimension(), decoy.getEntityId(), village, x, y, z));
        }
        if (player.world instanceof WorldServer) {
            playDecoySpawnEffects((WorldServer) player.world, village, x, y + 1.0D, z);
        }
    }

    private void triggerStoredDecoyHit(EntityPlayer player) {
        Decoy decoy = decoys.remove(player.getUniqueID());
        if (decoy == null) {
            return;
        }

        Entity entity = player.world.getEntityByID(decoy.entityId);
        if (entity instanceof EntitySubstitutionDecoy && !entity.isDead) {
            ((EntitySubstitutionDecoy) entity).triggerHit();
        } else if (player.world instanceof WorldServer) {
            playHitEffects((WorldServer) player.world, decoy.village, decoy.x, decoy.y + 1.0D, decoy.z);
        }
    }

    private void removeStoredDecoy(World world, UUID ownerId) {
        Decoy decoy = decoys.remove(ownerId);
        if (decoy == null || world == null || world.provider.getDimension() != decoy.dimension) {
            return;
        }

        Entity entity = world.getEntityByID(decoy.entityId);
        if (entity instanceof EntitySubstitutionDecoy) {
            entity.setDead();
        }
    }

    private static Village resolveVillage(EntityPlayerMP player) {
        NBTTagCompound persisted = persisted(player);
        Village village = Village.byGroup(persisted.getString(VILLAGE_KEY));
        if (village != null) {
            return village;
        }

        return LuckPermsBridge.INSTANCE.findKnownVillage(player);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static boolean isUsable(EntityPlayerMP player, Village village) {
        return player != null && village != null && !MinecraftAccess.isRemote(player) && !player.isDead;
    }

    private static void sendActionBarError(EntityPlayerMP player, String message) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED + message), true);
    }

    private static Vec3d testPosition(EntityPlayerMP player) {
        double radians = Math.toRadians(player.rotationYaw);
        return new Vec3d(
                player.posX - MathHelper.sin((float) radians) * 2.5D,
                player.posY,
                player.posZ + MathHelper.cos((float) radians) * 2.5D);
    }

    private static boolean consumeChakra(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(CHAKRA_KEY)) {
            return !Loader.isModLoaded("narutomodaddon");
        }

        double chakra = data.getDouble(CHAKRA_KEY);
        if (chakra < CHAKRA_COST) {
            return false;
        }

        data.setDouble(CHAKRA_KEY, chakra - CHAKRA_COST);
        return true;
    }

    private static void refundChakra(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        if (data.hasKey(CHAKRA_KEY)) {
            data.setDouble(CHAKRA_KEY, data.getDouble(CHAKRA_KEY) + CHAKRA_COST);
        }
    }

    private static Vec3d findTeleportTarget(EntityPlayerMP player) {
        World world = player.world;
        float baseYaw = player.rotationYaw + 180.0F;
        float[] offsets = new float[]{0.0F, -35.0F, 35.0F, -70.0F, 70.0F, 180.0F};
        double[] distances = new double[]{TELEPORT_DISTANCE, 6.0D, 4.0D};

        for (double distance : distances) {
            for (float offset : offsets) {
                double radians = Math.toRadians(baseYaw + offset);
                double x = player.posX - MathHelper.sin((float) radians) * distance;
                double z = player.posZ + MathHelper.cos((float) radians) * distance;
                for (int yOffset = 1; yOffset >= -2; yOffset--) {
                    double y = Math.floor(player.posY) + yOffset;
                    if (isSafe(player, world, x, y, z)) {
                        return new Vec3d(x, y, z);
                    }
                }
            }
        }

        return null;
    }

    private static boolean isSafe(EntityPlayerMP player, World world, double x, double y, double z) {
        if (y < 1.0D || y > world.getActualHeight() - 2) {
            return false;
        }

        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(pos)) {
            return false;
        }

        AxisAlignedBB box = player.getEntityBoundingBox().offset(x - player.posX, y - player.posY, z - player.posZ);
        return world.getCollisionBoxes(player, box).isEmpty()
                && world.getBlockState(pos).getMaterial().isReplaceable()
                && world.getBlockState(pos.up()).getMaterial().isReplaceable()
                && !world.getBlockState(pos.down()).getMaterial().isLiquid();
    }

    private static void playActivationEffects(WorldServer world, Village village, double x, double y, double z) {
        sendCustomEffect(world, village, SubstitutionEffectMessage.ACTIVATION, x, y, z);
        if (village == Village.SAND) {
            blockDust(world, Blocks.SAND, x, y, z, 8, 0.3D, 0.5D, 0.3D, 0.035D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.32F, 1.25F);
        } else if (village == Village.CLOUD) {
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z, 5, 0.25D, 0.5D, 0.25D, 0.05D);
            world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 2, 0.2D, 0.3D, 0.2D, 0.0D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.PLAYERS, 0.38F, 1.65F);
        } else if (village == Village.RAIN) {
            world.spawnParticle(EnumParticleTypes.WATER_DROP, x, y + 0.4D, z, 4, 0.3D, 0.45D, 0.3D, 0.02D);
            world.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y - 0.45D, z, 6, 0.4D, 0.03D, 0.4D, 0.035D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.4F, 1.3F);
        } else if (village == Village.MIST) {
            world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 5, 0.48D, 0.4D, 0.48D, 0.006D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.27F, 1.75F);
        } else if (village == Village.STONE) {
            blockDust(world, Blocks.STONE, x, y, z, 8, 0.3D, 0.45D, 0.3D, 0.035D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_IRONGOLEM_HURT, SoundCategory.PLAYERS, 0.28F, 1.35F);
        } else {
            blockDust(world, Blocks.LEAVES, x, y, z, 6, 0.4D, 0.5D, 0.4D, 0.035D);
            world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y, z, 2, 0.3D, 0.35D, 0.3D, 0.01D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.3F, 1.45F);
        }
    }

    private static void playArrivalEffects(WorldServer world, Village village, double x, double y, double z) {
        sendCustomEffect(world, village, SubstitutionEffectMessage.ARRIVAL, x, y, z);
        if (village == Village.SAND) {
            blockDust(world, Blocks.SAND, x, y - 0.1D, z, 3, 0.2D, 0.15D, 0.2D, 0.02D);
        } else if (village == Village.CLOUD) {
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z, 3, 0.18D, 0.3D, 0.18D, 0.03D);
        } else if (village == Village.RAIN) {
            world.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y - 0.45D, z, 3, 0.25D, 0.02D, 0.25D, 0.02D);
        } else if (village == Village.MIST) {
            world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 3, 0.28D, 0.25D, 0.28D, 0.004D);
        } else if (village == Village.STONE) {
            blockDust(world, Blocks.STONE, x, y - 0.1D, z, 3, 0.2D, 0.15D, 0.2D, 0.02D);
        } else {
            blockDust(world, Blocks.LEAVES, x, y, z, 3, 0.25D, 0.25D, 0.25D, 0.02D);
        }
    }

    private static void playDecoySpawnEffects(WorldServer world, Village village, double x, double y, double z) {
        if (village == Village.SAND) {
            blockDust(world, Blocks.SAND, x, y, z, 3, 0.2D, 0.35D, 0.2D, 0.02D);
        } else if (village == Village.CLOUD) {
            world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x, y, z, 3, 0.2D, 0.35D, 0.2D, 0.02D);
        } else if (village == Village.RAIN) {
            world.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y - 0.45D, z, 3, 0.25D, 0.03D, 0.25D, 0.02D);
        } else if (village == Village.MIST) {
            world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 3, 0.3D, 0.3D, 0.3D, 0.004D);
        } else if (village == Village.STONE) {
            blockDust(world, Blocks.STONE, x, y, z, 3, 0.2D, 0.3D, 0.2D, 0.02D);
        } else {
            blockDust(world, Blocks.LEAVES, x, y, z, 3, 0.25D, 0.3D, 0.25D, 0.02D);
        }
    }

    private static void playDecoyPulseEffects(WorldServer world, Village village, double x, double y, double z,
                                              long age) {
        sendCustomEffect(world, village, SubstitutionEffectMessage.PULSE, x, y, z);
        if (village == Village.SAND) {
            blockDust(world, Blocks.SAND, x, y, z, 2, 0.16D, 0.25D, 0.16D, 0.015D);
        } else if (village == Village.CLOUD) {
            world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x, y, z, 2, 0.18D, 0.3D, 0.18D, 0.02D);
        } else if (village == Village.RAIN) {
            world.spawnParticle(EnumParticleTypes.WATER_WAKE, x, y - 0.58D, z, 2, 0.22D, 0.01D, 0.22D, 0.01D);
        } else if (village == Village.MIST) {
            world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 2, 0.3D, 0.28D, 0.3D, 0.003D);
        } else if (village == Village.STONE) {
            blockDust(world, Blocks.STONE, x, y, z, 2, 0.16D, 0.25D, 0.16D, 0.012D);
        } else {
            blockDust(world, Blocks.LEAVES, x, y, z, 2, 0.2D, 0.25D, 0.2D, 0.012D);
        }
    }

    private static void playHitEffects(WorldServer world, Village village, double x, double y, double z) {
        sendCustomEffect(world, village, SubstitutionEffectMessage.HIT, x, y, z);
        if (village == Village.SAND) {
            blockDust(world, Blocks.SAND, x, y, z, 10, 0.45D, 0.55D, 0.45D, 0.055D);
            world.playSound(null, x, y, z, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.65F, 0.78F);
        } else if (village == Village.CLOUD) {
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z, 8, 0.45D, 0.55D, 0.45D, 0.07D);
            world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x, y, z, 4, 0.35D, 0.35D, 0.35D, 0.045D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.PLAYERS, 0.62F, 1.5F);
        } else if (village == Village.RAIN) {
            world.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y, z, 8, 0.6D, 0.3D, 0.6D, 0.06D);
            world.spawnParticle(EnumParticleTypes.WATER_DROP, x, y + 0.25D, z, 4, 0.4D, 0.4D, 0.4D, 0.025D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.62F, 0.95F);
        } else if (village == Village.MIST) {
            world.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y, z, 3, 0.45D, 0.25D, 0.45D, 0.035D);
            world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 8, 0.7D, 0.4D, 0.7D, 0.012D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.42F, 1.2F);
        } else if (village == Village.STONE) {
            blockDust(world, Blocks.STONE, x, y, z, 12, 0.5D, 0.6D, 0.5D, 0.06D);
            world.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, x, y, z, 3, 0.2D, 0.2D, 0.2D, 0.015D);
            world.playSound(null, x, y, z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.7F, 0.72F);
        } else {
            blockDust(world, Blocks.LOG, x, y, z, 8, 0.4D, 0.45D, 0.4D, 0.045D);
            blockDust(world, Blocks.LEAVES, x, y, z, 10, 0.55D, 0.55D, 0.55D, 0.055D);
            world.playSound(null, x, y, z, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 0.62F, 1.05F);
        }
    }

    private static void playFadeEffects(WorldServer world, Village village, double x, double y, double z) {
        sendCustomEffect(world, village, SubstitutionEffectMessage.FADE, x, y, z);
        if (village == Village.RAIN) {
            world.spawnParticle(EnumParticleTypes.WATER_WAKE, x, y - 0.58D, z, 3, 0.25D, 0.01D, 0.25D, 0.01D);
        } else if (village == Village.MIST) {
            world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 3, 0.35D, 0.25D, 0.35D, 0.004D);
        } else if (village == Village.CLOUD) {
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z, 3, 0.25D, 0.25D, 0.25D, 0.025D);
        } else if (village == Village.SAND) {
            blockDust(world, Blocks.SAND, x, y - 0.1D, z, 3, 0.3D, 0.15D, 0.3D, 0.015D);
        } else if (village == Village.STONE) {
            blockDust(world, Blocks.STONE, x, y - 0.1D, z, 3, 0.25D, 0.15D, 0.25D, 0.015D);
        } else {
            blockDust(world, Blocks.LEAVES, x, y, z, 3, 0.3D, 0.2D, 0.3D, 0.015D);
        }
    }

    private static void sendCustomEffect(WorldServer world, Village village, int phase,
                                         double x, double y, double z) {
        RebornAddonNetwork.sendSubstitutionEffect(world.provider.getDimension(), village, phase, x, y, z);
    }

    private static void blockDust(WorldServer world, Block block, double x, double y, double z, int count,
                                  double xOffset, double yOffset, double zOffset, double speed) {
        IBlockState state = block.getDefaultState();
        world.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y, z, count, xOffset, yOffset, zOffset, speed,
                Block.getStateId(state));
    }

    private static final class Decoy {
        private final int dimension;
        private final int entityId;
        private final Village village;
        private final double x;
        private final double y;
        private final double z;

        private Decoy(int dimension, int entityId, Village village, double x, double y, double z) {
            this.dimension = dimension;
            this.entityId = entityId;
            this.village = village;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
