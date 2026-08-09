package net.rebornaddon.chakra.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.ChakraModeHandler;

public final class ChakraLearnMessage implements IMessage {
    private int modeId;

    public ChakraLearnMessage() {
    }

    public ChakraLearnMessage(ChakraMode mode) {
        modeId = mode == null ? -1 : mode.id();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        modeId = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(modeId);
    }

    public static final class Handler implements IMessageHandler<ChakraLearnMessage, IMessage> {
        @Override
        public IMessage onMessage(final ChakraLearnMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ChakraMode mode = ChakraMode.byId(message.modeId);
                    if (mode != null) {
                        ChakraModeHandler.INSTANCE.learn(player, mode);
                    }
                }
            });
            return null;
        }
    }
}
