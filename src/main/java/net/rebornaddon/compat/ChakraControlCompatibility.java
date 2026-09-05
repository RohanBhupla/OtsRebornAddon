package net.rebornaddon.compat;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.rebornaddon.chakra.ChakraControlConfigurationService;
import net.rebornaddon.content.entity.EntityMissionNpc;
import net.rebornaddon.mount.ArmorWolfCompatibilityHandler;
import net.minecraft.world.World;

import java.util.Map;

public final class ChakraControlCompatibility {
    private static final String STATE_KEY = "RebornChakraControl";
    private static final double WATER_SURFACE_THICKNESS = 0.0625D;
    private static final double WATER_SURFACE_ENTRY_EPSILON = 0.002D;
    private static final double WATER_SURFACE_RECOVERY_DEPTH = 1.25D;
    private static final double MOUNT_SURFACE_RELEASE_VELOCITY = 0.05D;
    private static final double WALL_PROBE_DISTANCE = 0.12D;
    private static final double WALL_PROBE_GROW = 0.035D;
    private static final double WALL_CLIMB_SPEED = 0.17D;
    private static final double LEDGE_CLIMB_SPEED = 0.23D;
    private static final double LEDGE_FORWARD_PULL = 0.16D;
    private static final double MIN_WALL_SCAN_Y = 0.12D;
    private static final double HEAD_CLEARANCE_PROBE = 0.42D;
    private static final double MIN_MOVEMENT_INTENT = 0.0035D;
    private static final double NPC_WATER_ACCELERATION = 0.12D;
    private static final double NPC_WATER_MAX_SPEED = 0.34D;

    private ChakraControlCompatibility() {
    }

    /**
     * Replaces NarutoMod's BasicNinjaSkills procedure. Its passive movement buffs and
     * fall-distance reduction are retained, while water and wall movement are handled
     * by the deterministic controls below.
     */
    public static void applyBaseNinjaSkills(Map<String, Object> dependencies) {
        if (dependencies == null) return;
        Object entityValue = dependencies.get("entity");
        if (!(entityValue instanceof Entity)) return;
        Entity entity = (Entity) entityValue;
        World world = dependencies.get("world") instanceof World
                ? (World) dependencies.get("world") : entity.world;
        if (world == null || entity instanceof EntityPlayer
                && ((EntityPlayer) entity).isSpectator()) return;

        if (!world.isRemote && entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            living.addPotionEffect(new PotionEffect(MobEffects.SPEED, 2, 1, false, false));
            living.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 2, 1, false, false));
            living.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 2, 1, false, false));
        }

        if (entity.fallDistance > 4.0F) entity.fallDistance -= 0.75F;
        if (entity instanceof EntityPlayer
                && ChakraControlConfigurationService.INSTANCE.enabled()
                && isEnabled((EntityPlayer) entity)) {
            applyPlayerMovement((EntityPlayer) entity);
        }
    }

    public static boolean isEnabled(EntityPlayer player) {
        if (player == null) return true;
        NBTTagCompound persisted = persisted(player, false);
        return persisted == null || !persisted.hasKey(STATE_KEY)
                || persisted.getBoolean(STATE_KEY);
    }

    public static void setEnabled(EntityPlayer player, boolean enabled) {
        if (player != null) persisted(player, true).setBoolean(STATE_KEY, enabled);
    }

    public static void applyNpcMovement(EntityLivingBase npc, boolean waterWalking,
                                        boolean wallClimbing) {
        if (npc == null || npc.world == null || npc.isRiding()
                || !ChakraControlConfigurationService.INSTANCE.enabled()) return;
        boolean onWater = waterWalking
                && ChakraControlConfigurationService.INSTANCE.waterWalkingEnabled()
                && applyWaterWalking(npc);
        if (onWater) steerNpcAcrossWater(npc);
        if (!onWater && !npc.isSneaking() && wallClimbing
                && ChakraControlConfigurationService.INSTANCE.wallClimbingEnabled()
                && wantsToClimb(npc, false)) applyWallClimbing(npc);
    }

    public static void applyMountWaterWalking(EntityLivingBase mount) {
        if (!ArmorWolfCompatibilityHandler.isArmorWolfCompanion(mount)
                || !canUseWaterWalking(mount)) return;
        double surface = supportingWaterSurfaceAtCenter(mount);
        if (Double.isNaN(surface)) return;
        double correction = mountSurfaceCorrection(mount.getEntityBoundingBox().minY,
                surface, mount.motionY);
        if (Double.isNaN(correction)) return;
        if (Math.abs(correction) > 0.000001D) {
            mount.setPosition(mount.posX, mount.posY + correction, mount.posZ);
        }
        mount.motionY = 0.0D;
        mount.onGround = true;
        mount.isAirBorne = false;
        mount.fallDistance = 0.0F;
        mount.velocityChanged = true;
    }

    public static void addWaterSurfaceCollisions(GetCollisionBoxesEvent event) {
        if (event == null || !canUseWaterWalking(event.getEntity())) return;
        Entity entity = event.getEntity();
        AxisAlignedBB query = event.getAabb();
        AxisAlignedBB body = entity.getEntityBoundingBox();
        double feet = body.minY;
        int minX = Math.max((int) Math.floor(query.minX - 0.0000001D),
                (int) Math.floor(body.minX) - 1);
        int maxX = Math.min((int) Math.floor(query.maxX + 0.0000001D),
                (int) Math.floor(body.maxX) + 1);
        int minY = Math.max((int) Math.floor(query.minY - 0.25D),
                (int) Math.floor(feet) - 1);
        int maxY = Math.min((int) Math.floor(query.maxY + 0.25D),
                (int) Math.floor(feet + 0.25D));
        int minZ = Math.max((int) Math.floor(query.minZ - 0.0000001D),
                (int) Math.floor(body.minZ) - 1);
        int maxZ = Math.min((int) Math.floor(query.maxZ + 0.0000001D),
                (int) Math.floor(body.maxZ) + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.setPos(x, y, z);
                    if (!isWaterSurface(entity.world, pos)) continue;
                    double top = y + 1.0D;
                    if (!approachingSurfaceFromAbove(feet, top)) continue;
                    AxisAlignedBB surface = new AxisAlignedBB(x, top - WATER_SURFACE_THICKNESS, z,
                            x + 1.0D, top + 0.001D, z + 1.0D);
                    if (surface.intersects(query)) event.getCollisionBoxesList().add(surface);
                }
            }
        }
    }

    private static void applyPlayerMovement(EntityPlayer player) {
        if (player.isRiding() || player.isElytraFlying() || player.capabilities.isFlying) return;
        boolean onWater = ChakraControlConfigurationService.INSTANCE.waterWalkingEnabled()
                && applyWaterWalking(player);
        if (!onWater && !player.isSneaking()
                && ChakraControlConfigurationService.INSTANCE.wallClimbingEnabled()
                && wantsToClimb(player, true)) applyWallClimbing(player);
    }

    private static boolean applyWaterWalking(EntityLivingBase entity) {
        if (!canUseWaterWalking(entity)) return false;
        double surface = supportingWaterSurface(entity);
        if (Double.isNaN(surface)) return false;
        double lift = recoveryLift(entity.getEntityBoundingBox().minY, surface);
        if (lift > 0.0D) {
            entity.setPosition(entity.posX, entity.posY + lift + WATER_SURFACE_ENTRY_EPSILON,
                    entity.posZ);
            if (entity.motionY < 0.0D) entity.motionY = 0.0D;
        }
        entity.fallDistance = 0.0F;
        return true;
    }

    private static boolean canUseWaterWalking(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity.world == null || entity.isRiding()
                || entity.isInLava()
                || !ChakraControlConfigurationService.INSTANCE.waterWalkingEnabled()) return false;
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !player.isSpectator() && !player.capabilities.isFlying
                    && !player.isElytraFlying() && isEnabled(player);
        }
        if (ArmorWolfCompatibilityHandler.isArmorWolfCompanion(entity)) {
            Entity rider = entity.getControllingPassenger();
            return rider instanceof EntityPlayer
                    && !((EntityPlayer) rider).isSpectator()
                    && isEnabled((EntityPlayer) rider);
        }
        return entity instanceof EntityMissionNpc
                && ((EntityMissionNpc) entity).canUseChakraWaterWalking();
    }

    private static double supportingWaterSurface(EntityLivingBase entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(0.08D, 0.0D, 0.08D);
        double feet = entity.getEntityBoundingBox().minY;
        double closest = Double.NaN;
        double closestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lowestSurfaceBlock = (int) Math.floor(feet - WATER_SURFACE_RECOVERY_DEPTH);
        int highestSurfaceBlock = (int) Math.floor(feet + WATER_SURFACE_RECOVERY_DEPTH - 1.0D);
        for (int y = lowestSurfaceBlock; y <= highestSurfaceBlock; y++) {
            double top = y + 1.0D;
            double distance = Math.abs(feet - top);
            if (distance > WATER_SURFACE_RECOVERY_DEPTH || distance >= closestDistance) continue;
            for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    pos.setPos(x, y, z);
                    if (isWaterSurface(entity.world, pos)) {
                        closest = top;
                        closestDistance = distance;
                        break;
                    }
                }
            }
        }
        return closest;
    }

    private static double supportingWaterSurfaceAtCenter(EntityLivingBase entity) {
        double feet = entity.getEntityBoundingBox().minY;
        int x = (int) Math.floor(entity.posX);
        int z = (int) Math.floor(entity.posZ);
        double closest = Double.NaN;
        double closestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lowestSurfaceBlock = (int) Math.floor(feet - WATER_SURFACE_RECOVERY_DEPTH);
        int highestSurfaceBlock = (int) Math.floor(feet + WATER_SURFACE_RECOVERY_DEPTH - 1.0D);
        for (int y = lowestSurfaceBlock; y <= highestSurfaceBlock; y++) {
            double surface = y + 1.0D;
            double distance = Math.abs(feet - surface);
            if (distance > WATER_SURFACE_RECOVERY_DEPTH || distance >= closestDistance) continue;
            pos.setPos(x, y, z);
            if (!isWaterSurface(entity.world, pos)) continue;
            closest = surface;
            closestDistance = distance;
        }
        return closest;
    }

    static double mountSurfaceCorrection(double feet, double surface, double verticalVelocity) {
        if (verticalVelocity > MOUNT_SURFACE_RELEASE_VELOCITY) return Double.NaN;
        double correction = surface + WATER_SURFACE_ENTRY_EPSILON - feet;
        return Math.abs(correction) <= WATER_SURFACE_RECOVERY_DEPTH
                + WATER_SURFACE_ENTRY_EPSILON ? correction : Double.NaN;
    }

    static boolean approachingSurfaceFromAbove(double feet, double surface) {
        return feet >= surface - WATER_SURFACE_ENTRY_EPSILON;
    }

    static double recoveryLift(double feet, double surface) {
        double depth = surface - feet;
        return depth > WATER_SURFACE_ENTRY_EPSILON
                && depth <= WATER_SURFACE_RECOVERY_DEPTH ? depth : 0.0D;
    }

    private static boolean isWaterSurface(World world, BlockPos pos) {
        if (world == null || !world.isBlockLoaded(pos)) return false;
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial() != Material.WATER) return false;
        BlockPos above = pos.up();
        if (!world.isBlockLoaded(above) || world.getBlockState(above).getMaterial() == Material.WATER) {
            return false;
        }
        AxisAlignedBB collision = world.getBlockState(above).getCollisionBoundingBox(world, above);
        return collision == null || collision == Block.NULL_AABB;
    }

    private static boolean wantsToClimb(EntityLivingBase entity, boolean player) {
        if (entity.isInWater() || entity.isInLava() || entity.isOnLadder()
                || entity.isElytraFlying()) return false;
        double[] direction = movementIntent(entity, player);
        return direction != null && hasClimbableWall(entity, direction[0], direction[1]);
    }

    private static void applyWallClimbing(EntityLivingBase entity) {
        double[] direction = movementIntent(entity, entity instanceof EntityPlayer);
        if (direction == null) return;
        boolean ledge = isNearLedge(entity, direction[0], direction[1]);
        double multiplier = ChakraControlConfigurationService.INSTANCE.wallClimbSpeedMultiplier();
        double climbSpeed = scaledWallClimbSpeed(ledge, multiplier);
        entity.motionY = Math.max(entity.motionY, climbSpeed);
        if (ledge) applyForwardPull(entity, direction[0], direction[1]);
        entity.fallDistance = 0.0F;
    }

    static double scaledWallClimbSpeed(boolean ledge, double multiplier) {
        return (ledge ? LEDGE_CLIMB_SPEED : WALL_CLIMB_SPEED) * multiplier;
    }

    private static double[] movementIntent(EntityLivingBase entity, boolean player) {
        double x;
        double z;
        if (player) {
            double yaw = Math.toRadians(entity.rotationYaw);
            x = entity.moveStrafing * Math.cos(yaw) - entity.moveForward * Math.sin(yaw);
            z = entity.moveForward * Math.cos(yaw) + entity.moveStrafing * Math.sin(yaw);
        } else {
            EntityLivingBase target = entity instanceof EntityLiving
                    ? ((EntityLiving) entity).getAttackTarget() : null;
            if (target != null) {
                x = target.posX - entity.posX;
                z = target.posZ - entity.posZ;
            } else {
                x = entity.motionX;
                z = entity.motionZ;
            }
        }
        double length = Math.sqrt(x * x + z * z);
        if (length < MIN_MOVEMENT_INTENT) return null;
        return new double[] {x / length, z / length};
    }

    private static boolean hasClimbableWall(EntityLivingBase entity, double x, double z) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        AxisAlignedBB body = new AxisAlignedBB(box.minX, box.minY + MIN_WALL_SCAN_Y, box.minZ,
                box.maxX, box.maxY - 0.18D, box.maxZ);
        AxisAlignedBB probe = body.union(body.offset(x * WALL_PROBE_DISTANCE, 0.0D,
                z * WALL_PROBE_DISTANCE)).grow(WALL_PROBE_GROW, 0.0D, WALL_PROBE_GROW);
        return intersectsSolidBlock(entity.world, probe);
    }

    private static boolean isNearLedge(EntityLivingBase entity, double x, double z) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        AxisAlignedBB upper = new AxisAlignedBB(box.minX, box.maxY - 0.24D, box.minZ,
                box.maxX, box.maxY + HEAD_CLEARANCE_PROBE, box.maxZ)
                .offset(x * WALL_PROBE_DISTANCE, 0.0D, z * WALL_PROBE_DISTANCE)
                .grow(WALL_PROBE_GROW, 0.0D, WALL_PROBE_GROW);
        return !intersectsSolidBlock(entity.world, upper);
    }

    private static boolean intersectsSolidBlock(World world, AxisAlignedBB probe) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = (int) Math.floor(probe.minY); y <= (int) Math.floor(probe.maxY); y++) {
            for (int x = (int) Math.floor(probe.minX); x <= (int) Math.floor(probe.maxX); x++) {
                for (int z = (int) Math.floor(probe.minZ); z <= (int) Math.floor(probe.maxZ); z++) {
                    pos.setPos(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    IBlockState state = world.getBlockState(pos);
                    if (!state.getMaterial().isSolid()) continue;
                    AxisAlignedBB local = state.getCollisionBoundingBox(world, pos);
                    if (local != null && local != Block.NULL_AABB
                            && local.offset(pos).intersects(probe)) return true;
                }
            }
        }
        return false;
    }

    private static void applyForwardPull(EntityLivingBase entity, double x, double z) {
        double forward = entity.motionX * x + entity.motionZ * z;
        if (forward >= LEDGE_FORWARD_PULL) return;
        entity.motionX += x * (LEDGE_FORWARD_PULL - forward);
        entity.motionZ += z * (LEDGE_FORWARD_PULL - forward);
    }

    private static void steerNpcAcrossWater(EntityLivingBase npc) {
        if (!(npc instanceof EntityLiving)) return;
        EntityLivingBase target = ((EntityLiving) npc).getAttackTarget();
        if (target == null || target.isDead) return;
        double x = target.posX - npc.posX;
        double z = target.posZ - npc.posZ;
        double distance = Math.sqrt(x * x + z * z);
        if (distance < 1.35D || distance > 48.0D) return;
        x /= distance;
        z /= distance;
        npc.motionX += x * NPC_WATER_ACCELERATION;
        npc.motionZ += z * NPC_WATER_ACCELERATION;
        double speed = Math.sqrt(npc.motionX * npc.motionX + npc.motionZ * npc.motionZ);
        if (speed > NPC_WATER_MAX_SPEED) {
            npc.motionX = npc.motionX / speed * NPC_WATER_MAX_SPEED;
            npc.motionZ = npc.motionZ / speed * NPC_WATER_MAX_SPEED;
        }
    }

    private static NBTTagCompound persisted(EntityPlayer player, boolean create) {
        NBTTagCompound root = player.getEntityData();
        if (root.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            return root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        }
        if (!create) return null;
        NBTTagCompound value = new NBTTagCompound();
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, value);
        return value;
    }
}
