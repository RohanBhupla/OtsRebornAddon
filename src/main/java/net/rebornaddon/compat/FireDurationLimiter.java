package net.rebornaddon.compat;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class FireDurationLimiter {
    public static final FireDurationLimiter INSTANCE = new FireDurationLimiter();

    private static final long MAX_AGE_TICKS = 12L * 20L;
    private static final long UNLOADED_RETRY_TICKS = 10L * 20L;
    private static final int MAX_EXPIRATIONS_PER_TICK = 512;
    private static final int MAX_TRACKED_PER_DIMENSION = 50000;
    private static final ResourceLocation AMATERASU_BLOCK = new ResourceLocation("narutomod", "amaterasublock");

    private final Map<Integer, DimensionQueue> queues = new HashMap<Integer, DimensionQueue>();

    private FireDurationLimiter() {
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        track(event.getWorld(), event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        track(event.getWorld(), event.getPos(), event.getState());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote) {
            return;
        }

        DimensionQueue queue = queues.get(Integer.valueOf(event.world.provider.getDimension()));
        if (queue == null) {
            return;
        }

        expireDue(event.world, queue, event.world.getTotalWorldTime());
        if (queue.expiryByPosition.isEmpty()) {
            queues.remove(Integer.valueOf(event.world.provider.getDimension()));
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() != null && !event.getWorld().isRemote) {
            queues.remove(Integer.valueOf(event.getWorld().provider.getDimension()));
        }
    }

    private void track(World world, BlockPos pos, IBlockState state) {
        if (world == null || pos == null || world.isRemote) {
            return;
        }

        int dimension = world.provider.getDimension();
        long key = pos.toLong();
        DimensionQueue queue = queues.get(Integer.valueOf(dimension));

        if (!isLimitedBlock(state)) {
            if (queue != null) {
                queue.expiryByPosition.remove(Long.valueOf(key));
            }
            return;
        }

        if (queue == null) {
            queue = new DimensionQueue();
            queues.put(Integer.valueOf(dimension), queue);
        }

        Long existing = queue.expiryByPosition.get(Long.valueOf(key));
        if (existing != null) {
            return;
        }

        long expiry = world.getTotalWorldTime() + MAX_AGE_TICKS;
        queue.expiryByPosition.put(Long.valueOf(key), Long.valueOf(expiry));
        queue.expirations.offer(new Expiration(key, expiry));
        trim(queue);
    }

    private void expireDue(World world, DimensionQueue queue, long now) {
        int processed = 0;
        while (processed < MAX_EXPIRATIONS_PER_TICK && !queue.expirations.isEmpty()) {
            Expiration next = queue.expirations.peek();
            if (next.expireTick > now) {
                return;
            }

            queue.expirations.poll();
            Long activeExpiry = queue.expiryByPosition.get(Long.valueOf(next.position));
            if (activeExpiry == null || activeExpiry.longValue() != next.expireTick) {
                continue;
            }

            BlockPos pos = BlockPos.fromLong(next.position);
            if (!world.isBlockLoaded(pos, false)) {
                long retry = now + UNLOADED_RETRY_TICKS;
                queue.expiryByPosition.put(Long.valueOf(next.position), Long.valueOf(retry));
                queue.expirations.offer(new Expiration(next.position, retry));
                processed++;
                continue;
            }

            IBlockState state = world.getBlockState(pos);
            if (isLimitedBlock(state)) {
                world.setBlockToAir(pos);
            }
            queue.expiryByPosition.remove(Long.valueOf(next.position));
            processed++;
        }
    }

    private void trim(DimensionQueue queue) {
        while (queue.expiryByPosition.size() > MAX_TRACKED_PER_DIMENSION && !queue.expirations.isEmpty()) {
            Expiration expiration = queue.expirations.poll();
            queue.expiryByPosition.remove(Long.valueOf(expiration.position));
        }
    }

    private boolean isLimitedBlock(IBlockState state) {
        if (state == null) {
            return false;
        }

        Block block = state.getBlock();
        if (block == Blocks.FIRE) {
            return true;
        }

        ResourceLocation name = block.getRegistryName();
        return AMATERASU_BLOCK.equals(name);
    }

    private static final class DimensionQueue {
        private final Map<Long, Long> expiryByPosition = new HashMap<Long, Long>();
        private final PriorityQueue<Expiration> expirations = new PriorityQueue<Expiration>();
    }

    private static final class Expiration implements Comparable<Expiration> {
        private final long position;
        private final long expireTick;

        private Expiration(long position, long expireTick) {
            this.position = position;
            this.expireTick = expireTick;
        }

        @Override
        public int compareTo(Expiration other) {
            if (expireTick < other.expireTick) {
                return -1;
            }
            if (expireTick > other.expireTick) {
                return 1;
            }
            return position < other.position ? -1 : (position == other.position ? 0 : 1);
        }
    }
}
