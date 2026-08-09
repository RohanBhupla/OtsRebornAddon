package net.rebornaddon.quest.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.quest.CustomNpcQuestService;

public class QuestClaimMessage implements IMessage {
    private int questId;

    public QuestClaimMessage() {
    }

    public QuestClaimMessage(int questId) {
        this.questId = questId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(questId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        questId = buf.readInt();
    }

    public static class Handler implements IMessageHandler<QuestClaimMessage, IMessage> {
        @Override
        public IMessage onMessage(final QuestClaimMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    CustomNpcQuestService.claim(player, message.questId);
                }
            });
            return null;
        }
    }
}
