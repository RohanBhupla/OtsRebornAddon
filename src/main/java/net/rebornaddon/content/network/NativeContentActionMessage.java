package net.rebornaddon.content.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.content.NativeContentService;

import java.nio.charset.StandardCharsets;

public final class NativeContentActionMessage implements IMessage {
    private String action = "";
    private String payload = "";

    public NativeContentActionMessage() {
    }

    public NativeContentActionMessage(String action, String payload) {
        this.action = action == null ? "" : action;
        this.payload = payload == null ? "" : payload;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, action);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 262144) {
            throw new IllegalArgumentException("Native content action payload exceeds 256 KB.");
        }
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = bounded(ByteBufUtils.readUTF8String(buffer), 64);
        int length = buffer.readInt();
        if (length < 0 || length > 262144 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid native content action payload length.");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        payload = new String(bytes, StandardCharsets.UTF_8);
    }

    private static String bounded(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public static final class Handler implements IMessageHandler<NativeContentActionMessage, IMessage> {
        @Override
        public IMessage onMessage(final NativeContentActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> NativeContentService.INSTANCE
                    .handlePlayerAction(player, message.action, message.payload));
            return null;
        }
    }
}
