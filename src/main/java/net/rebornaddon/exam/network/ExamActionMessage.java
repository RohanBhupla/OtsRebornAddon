package net.rebornaddon.exam.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.exam.ExamService;

import java.nio.charset.StandardCharsets;

public final class ExamActionMessage implements IMessage {
    private String action = "";
    private String payload = "";

    public ExamActionMessage() {
    }

    public ExamActionMessage(String action, String payload) {
        this.action = action == null ? "" : action;
        this.payload = payload == null ? "" : payload;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, action);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 262144) throw new IllegalArgumentException("Exam action exceeds 256 KB.");
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = ByteBufUtils.readUTF8String(buffer);
        int length = buffer.readInt();
        if (length < 0 || length > 262144 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid exam action payload.");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        payload = new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class Handler implements IMessageHandler<ExamActionMessage, IMessage> {
        @Override
        public IMessage onMessage(final ExamActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ExamService.INSTANCE.handleAction(player, message.action, message.payload);
                }
            });
            return null;
        }
    }
}
