package net.rebornaddon.performance.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.performance.PerformanceMonitor;

public final class PerformanceOverlayMessage implements IMessage {
    private boolean enabled;

    public PerformanceOverlayMessage() {
    }

    public PerformanceOverlayMessage(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        enabled = buffer.readBoolean();
        PerformanceMonitor.recordInbound(1);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(enabled);
        PerformanceMonitor.recordOutbound(1);
    }

    public static final class Handler implements IMessageHandler<PerformanceOverlayMessage, IMessage> {
        @Override
        public IMessage onMessage(PerformanceOverlayMessage message, MessageContext context) {
            RebornAddonMod.proxy.handlePerformanceOverlay(message.enabled);
            return null;
        }
    }
}
