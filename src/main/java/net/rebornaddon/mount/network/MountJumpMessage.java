package net.rebornaddon.mount.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.mount.ArmorWolfMountJumpHandler;

public final class MountJumpMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buffer) {
    }

    @Override
    public void toBytes(ByteBuf buffer) {
    }

    public static final class Handler implements IMessageHandler<MountJumpMessage, IMessage> {
        @Override
        public IMessage onMessage(MountJumpMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ArmorWolfMountJumpHandler.tryJump(player);
                }
            });
            return null;
        }
    }
}
