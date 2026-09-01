package net.rebornaddon.content.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.content.NpcSkinCacheService;
import net.rebornaddon.performance.PerformanceMonitor;

public final class NpcSkinRequestMessage implements IMessage {
    private String url = "";

    public NpcSkinRequestMessage() {
    }

    public NpcSkinRequestMessage(String url) {
        this.url = url == null ? "" : url;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int start = buffer.readerIndex();
        url = ByteBufUtils.readUTF8String(buffer);
        if (url.length() > 1024) throw new IllegalArgumentException("NPC skin URL is too long.");
        PerformanceMonitor.recordInbound(buffer.readerIndex() - start);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        ByteBufUtils.writeUTF8String(buffer, url.length() > 1024 ? url.substring(0, 1024) : url);
        PerformanceMonitor.recordOutbound(buffer.writerIndex() - start);
    }

    public static final class Handler implements IMessageHandler<NpcSkinRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(final NpcSkinRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    NpcSkinCacheService.INSTANCE.request(player, message.url);
                }
            });
            return null;
        }
    }
}
