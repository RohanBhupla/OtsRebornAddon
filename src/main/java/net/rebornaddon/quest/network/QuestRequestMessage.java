package net.rebornaddon.quest.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.quest.NativeQuestService;

public class QuestRequestMessage implements IMessage {
    private int catalogVersion = -1;

    public QuestRequestMessage() {
    }

    public QuestRequestMessage(int catalogVersion) {
        this.catalogVersion = catalogVersion;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(catalogVersion);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        catalogVersion = buf.readInt();
    }

    public static class Handler implements IMessageHandler<QuestRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(QuestRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    NativeQuestService.request(player, message.catalogVersion);
                }
            });
            return null;
        }
    }
}
