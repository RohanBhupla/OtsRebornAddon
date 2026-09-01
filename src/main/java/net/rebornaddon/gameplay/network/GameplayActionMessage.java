package net.rebornaddon.gameplay.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.gameplay.GameplaySystemsService;
import net.rebornaddon.performance.PerformanceMonitor;

public final class GameplayActionMessage implements IMessage {
    private String section = "";
    private String action = "";
    private String payload = "";

    public GameplayActionMessage() {
    }

    public GameplayActionMessage(String section, String action, String payload) {
        this.section = section == null ? "" : section;
        this.action = action == null ? "" : action;
        this.payload = payload == null ? "" : payload;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        section = ByteBufUtils.readUTF8String(buffer);
        action = ByteBufUtils.readUTF8String(buffer);
        payload = ByteBufUtils.readUTF8String(buffer);
        PerformanceMonitor.recordInbound(buffer.readerIndex());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        ByteBufUtils.writeUTF8String(buffer, section);
        ByteBufUtils.writeUTF8String(buffer, action);
        ByteBufUtils.writeUTF8String(buffer, payload);
        PerformanceMonitor.recordOutbound(buffer.writerIndex() - start);
    }

    public static final class Handler implements IMessageHandler<GameplayActionMessage, IMessage> {
        @Override
        public IMessage onMessage(final GameplayActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    GameplaySystemsService.INSTANCE.handle(player, message.section,
                            message.action, message.payload);
                }
            });
            return null;
        }
    }
}
