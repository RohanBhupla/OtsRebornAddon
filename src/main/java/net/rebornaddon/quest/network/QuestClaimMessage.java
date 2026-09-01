package net.rebornaddon.quest.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.quest.NativeQuestService;

public class QuestClaimMessage implements IMessage {
    private String questId;

    public QuestClaimMessage() {
    }

    public QuestClaimMessage(String questId) {
        this.questId = questId == null ? "" : questId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, questId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        questId = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<QuestClaimMessage, IMessage> {
        @Override
        public IMessage onMessage(final QuestClaimMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    NativeQuestService.claim(player, message.questId);
                }
            });
            return null;
        }
    }
}
