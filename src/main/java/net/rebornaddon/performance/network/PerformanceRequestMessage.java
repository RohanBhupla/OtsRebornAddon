package net.rebornaddon.performance.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.performance.PerformanceMonitor;

public final class PerformanceRequestMessage implements IMessage {
    public static final int SNAPSHOT = 0;
    public static final int PROFILE = 1;
    public static final int TELEPORT_HOTSPOT = 2;
    public static final int TELEPORT_CHUNK = 3;

    private int action;
    private long durationMillis;
    private int index;

    public PerformanceRequestMessage() {
    }

    public PerformanceRequestMessage(int action, long durationMillis, int index) {
        this.action = action;
        this.durationMillis = durationMillis;
        this.index = index;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readUnsignedByte();
        durationMillis = buffer.readLong();
        index = buffer.readInt();
        PerformanceMonitor.recordInbound(13);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
        buffer.writeLong(durationMillis);
        buffer.writeInt(index);
        PerformanceMonitor.recordOutbound(13);
    }

    public static final class Handler implements IMessageHandler<PerformanceRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(final PerformanceRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (message.action == PROFILE) {
                        PerformanceMonitor.INSTANCE.requestProfile(player, message.durationMillis);
                    } else if (message.action == TELEPORT_HOTSPOT) {
                        PerformanceMonitor.INSTANCE.teleportToHotspot(player, message.index, false);
                    } else if (message.action == TELEPORT_CHUNK) {
                        PerformanceMonitor.INSTANCE.teleportToHotspot(player, message.index, true);
                    } else {
                        PerformanceMonitor.INSTANCE.sendSnapshot(player);
                    }
                }
            });
            return null;
        }
    }
}
