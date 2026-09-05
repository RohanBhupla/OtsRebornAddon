package net.rebornaddon.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ProtectedNarutoMountHandler {
    public static final ProtectedNarutoMountHandler INSTANCE = new ProtectedNarutoMountHandler();

    static final String C2_BIRD_CLASS = "net.narutomod.entity.EntityC2$EC";
    static final String WATER_SHARK_CLASS = "net.narutomod.entity.EntitySuitonShark$EC";
    static final double REGION_SAMPLE_SPACING = 0.75D;
    static final int MAX_REGION_SAMPLES = 64;
    static final int MAX_BARRIER_BLOCKS = 4096;
    private static final double MIN_MOVEMENT_SQUARED = 1.0E-8D;

    private final Map<UUID, RiderState> riders = new HashMap<UUID, RiderState>();
    private final WorldGuardEntryBridge worldGuard = new WorldGuardEntryBridge();

    private ProtectedNarutoMountHandler() {
    }

    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntityMounting() instanceof EntityPlayerMP)
                || event.getEntityMounting().world.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityMounting();
        if (event.isMounting() && isProtectedMount(event.getEntityBeingMounted())) {
            track(player, event.getEntityBeingMounted());
        } else if (!event.isMounting()) {
            riders.remove(player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof EntityPlayerMP)
                || event.player.world.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Entity mount = protectedMount(player);
        RiderState state = riders.get(player.getUniqueID());
        if (mount == null) {
            if (state != null) riders.remove(player.getUniqueID());
        } else if (state == null || !state.matches(mount)) {
            track(player, mount);
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || riders.isEmpty()) return;
        int dimension = event.world.provider.getDimension();
        Iterator<Map.Entry<UUID, RiderState>> iterator = riders.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RiderState> entry = iterator.next();
            RiderState state = entry.getValue();
            if (state.dimension != dimension) continue;
            EntityPlayerMP player = event.world.getMinecraftServer().getPlayerList()
                    .getPlayerByUUID(entry.getKey());
            Entity mount = player == null ? null : protectedMount(player);
            if (mount == null || !state.matches(mount)) {
                iterator.remove();
                continue;
            }
            guardMovement(player, mount, state);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) riders.remove(event.player.getUniqueID());
    }

    public void reset() {
        riders.clear();
        worldGuard.reset();
    }

    private void track(EntityPlayerMP player, Entity mount) {
        WorldGuardEntryBridge.Access access = worldGuard.entryAccess(
                player, player.posX, player.posY, player.posZ);
        riders.put(player.getUniqueID(), new RiderState(player, mount, access));
    }

    private void guardMovement(EntityPlayerMP player, Entity mount, RiderState state) {
        double dx = mount.posX - state.x;
        double dy = mount.posY - state.y;
        double dz = mount.posZ - state.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < MIN_MOVEMENT_SQUARED) return;

        double playerDx = player.posX - state.playerX;
        double playerDy = player.posY - state.playerY;
        double playerDz = player.posZ - state.playerZ;
        int samples = Math.max(sampleCount(dx, dy, dz), sampleCount(playerDx, playerDy, playerDz));
        if (samples > MAX_REGION_SAMPLES || crossesBarrier(mount.world, mount, state, dx, dy, dz)
                || crossesDeniedEntry(player, state, playerDx, playerDy, playerDz, samples)) {
            restore(mount, state);
            return;
        }
        state.accept(player, mount);
    }

    private boolean crossesDeniedEntry(EntityPlayerMP player, RiderState state,
                                       double dx, double dy, double dz, int samples) {
        if (sameBlock(state.playerX, state.playerY, state.playerZ,
                state.playerX + dx, state.playerY + dy, state.playerZ + dz)) {
            return false;
        }

        WorldGuardEntryBridge.Access source = state.entryAccess;
        if (source == WorldGuardEntryBridge.Access.UNAVAILABLE) {
            source = worldGuard.entryAccess(player, state.playerX, state.playerY, state.playerZ);
        }
        if (source == WorldGuardEntryBridge.Access.UNAVAILABLE) return false;

        if (source == WorldGuardEntryBridge.Access.DENIED) {
            state.entryAccess = worldGuard.entryAccess(
                    player, state.playerX + dx, state.playerY + dy, state.playerZ + dz);
            return false;
        }

        for (int i = 1; i <= samples; i++) {
            double amount = i / (double) samples;
            WorldGuardEntryBridge.Access access = worldGuard.entryAccess(player,
                    state.playerX + dx * amount, state.playerY + dy * amount,
                    state.playerZ + dz * amount);
            if (blocksEntryTransition(source, access)) return true;
            if (access == WorldGuardEntryBridge.Access.UNAVAILABLE) {
                state.entryAccess = access;
                return false;
            }
        }
        state.entryAccess = WorldGuardEntryBridge.Access.ALLOWED;
        return false;
    }

    private static boolean crossesBarrier(World world, Entity mount, RiderState state,
                                          double dx, double dy, double dz) {
        AxisAlignedBB current = mount.getEntityBoundingBox();
        AxisAlignedBB previous = current.offset(-dx, -dy, -dz);
        BlockBounds previousBounds = BlockBounds.of(previous);
        BlockBounds currentBounds = BlockBounds.of(current);
        if (previousBounds.equals(currentBounds)) return false;

        BarrierScan previousScan = scanBarriers(world, previousBounds);
        if (previousScan == BarrierScan.TOO_LARGE) return true;
        BlockBounds swept = previousBounds.union(currentBounds);
        BarrierScan sweptScan = scanBarriers(world, swept);
        if (sweptScan == BarrierScan.TOO_LARGE) return true;
        return previousScan == BarrierScan.CLEAR && sweptScan == BarrierScan.BARRIER;
    }

    private static BarrierScan scanBarriers(World world, BlockBounds bounds) {
        if (bounds.volume() > MAX_BARRIER_BLOCKS) return BarrierScan.TOO_LARGE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(0, bounds.minY);
        int maxY = Math.min(world.getActualHeight() - 1, bounds.maxY);
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                pos.setPos(x, minY, z);
                if (!world.isBlockLoaded(pos)) return BarrierScan.TOO_LARGE;
                for (int y = minY; y <= maxY; y++) {
                    pos.setPos(x, y, z);
                    if (world.getBlockState(pos).getBlock() == Blocks.BARRIER) {
                        return BarrierScan.BARRIER;
                    }
                }
            }
        }
        return BarrierScan.CLEAR;
    }

    private static void restore(Entity mount, RiderState state) {
        mount.motionX = 0.0D;
        mount.motionY = 0.0D;
        mount.motionZ = 0.0D;
        mount.fallDistance = 0.0F;
        mount.velocityChanged = true;
        mount.setPositionAndUpdate(state.x, state.y, state.z);
        for (Entity passenger : mount.getPassengers()) mount.updatePassenger(passenger);
    }

    static boolean isProtectedMountClassName(String className) {
        return C2_BIRD_CLASS.equals(className) || WATER_SHARK_CLASS.equals(className);
    }

    static int sampleCount(double dx, double dy, double dz) {
        double greatestAxis = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        return Math.max(1, (int) Math.ceil(greatestAxis / REGION_SAMPLE_SPACING));
    }

    static boolean blocksEntryTransition(WorldGuardEntryBridge.Access source,
                                         WorldGuardEntryBridge.Access destination) {
        return source == WorldGuardEntryBridge.Access.ALLOWED
                && destination == WorldGuardEntryBridge.Access.DENIED;
    }

    private static boolean sameBlock(double fromX, double fromY, double fromZ,
                                     double toX, double toY, double toZ) {
        return MathHelper.floor(fromX) == MathHelper.floor(toX)
                && MathHelper.floor(fromY) == MathHelper.floor(toY)
                && MathHelper.floor(fromZ) == MathHelper.floor(toZ);
    }

    private static Entity protectedMount(EntityPlayerMP player) {
        Entity mount = player.getRidingEntity();
        while (mount != null) {
            if (isProtectedMount(mount)) return mount;
            mount = mount.getRidingEntity();
        }
        return null;
    }

    private static boolean isProtectedMount(Entity entity) {
        return entity != null && isProtectedMountClassName(entity.getClass().getName());
    }

    private enum BarrierScan {
        CLEAR,
        BARRIER,
        TOO_LARGE
    }

    private static final class RiderState {
        private final UUID mountId;
        private final int dimension;
        private double x;
        private double y;
        private double z;
        private double playerX;
        private double playerY;
        private double playerZ;
        private WorldGuardEntryBridge.Access entryAccess;

        private RiderState(EntityPlayerMP player, Entity mount,
                           WorldGuardEntryBridge.Access entryAccess) {
            mountId = mount.getUniqueID();
            dimension = mount.dimension;
            x = mount.posX;
            y = mount.posY;
            z = mount.posZ;
            playerX = player.posX;
            playerY = player.posY;
            playerZ = player.posZ;
            this.entryAccess = entryAccess;
        }

        private boolean matches(Entity mount) {
            return dimension == mount.dimension && mountId.equals(mount.getUniqueID());
        }

        private void accept(EntityPlayerMP player, Entity mount) {
            x = mount.posX;
            y = mount.posY;
            z = mount.posZ;
            playerX = player.posX;
            playerY = player.posY;
            playerZ = player.posZ;
        }
    }

    private static final class BlockBounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private static BlockBounds of(AxisAlignedBB box) {
            double epsilon = 1.0E-7D;
            return new BlockBounds(MathHelper.floor(box.minX + epsilon),
                    MathHelper.floor(box.minY + epsilon), MathHelper.floor(box.minZ + epsilon),
                    MathHelper.floor(box.maxX - epsilon), MathHelper.floor(box.maxY - epsilon),
                    MathHelper.floor(box.maxZ - epsilon));
        }

        private BlockBounds union(BlockBounds other) {
            return new BlockBounds(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }

        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof BlockBounds)) return false;
            BlockBounds other = (BlockBounds) value;
            return minX == other.minX && minY == other.minY && minZ == other.minZ
                    && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
        }

        @Override
        public int hashCode() {
            int result = minX;
            result = 31 * result + minY;
            result = 31 * result + minZ;
            result = 31 * result + maxX;
            result = 31 * result + maxY;
            return 31 * result + maxZ;
        }
    }
}
