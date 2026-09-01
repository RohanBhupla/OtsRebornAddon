package net.rebornaddon.jutsu.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.village.network.RebornAddonNetwork;

public final class AdminAccessRequestMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buffer) {
    }

    @Override
    public void toBytes(ByteBuf buffer) {
    }

    public static final class Handler implements IMessageHandler<AdminAccessRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(AdminAccessRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    RebornAddonNetwork.sendAdminAccess(player,
                            player.canUseCommand(2, "rebornadmin"));
                }
            });
            return null;
        }
    }
}
