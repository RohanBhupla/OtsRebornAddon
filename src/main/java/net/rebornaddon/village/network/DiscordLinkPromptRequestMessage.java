package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.village.VillageSelectionHandler;

public final class DiscordLinkPromptRequestMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buffer) {
    }

    @Override
    public void toBytes(ByteBuf buffer) {
    }

    public static final class Handler implements IMessageHandler<DiscordLinkPromptRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(DiscordLinkPromptRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    VillageSelectionHandler.INSTANCE.requestDiscordPrompt(player);
                }
            });
            return null;
        }
    }
}
