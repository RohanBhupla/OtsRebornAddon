package net.rebornaddon.exam.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

import java.util.Arrays;

public final class ExamSyncMessage implements IMessage {
    private String kind = "snapshot";
    private int transferId;
    private int partIndex;
    private int partCount = 1;
    private byte[] payload = new byte[0];

    public ExamSyncMessage() {
    }

    public ExamSyncMessage(String kind, int transferId, int partIndex, int partCount, byte[] payload) {
        this.kind = kind == null ? "snapshot" : kind;
        this.transferId = transferId;
        this.partIndex = partIndex;
        this.partCount = partCount;
        this.payload = payload == null ? new byte[0] : payload;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, kind);
        buffer.writeInt(transferId);
        buffer.writeInt(partIndex);
        buffer.writeInt(partCount);
        buffer.writeInt(payload.length);
        buffer.writeBytes(payload);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        kind = ByteBufUtils.readUTF8String(buffer);
        transferId = buffer.readInt();
        partIndex = buffer.readInt();
        partCount = buffer.readInt();
        int length = buffer.readInt();
        if (length < 0 || length > 32768 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid exam sync fragment.");
        }
        payload = new byte[length];
        buffer.readBytes(payload);
    }

    public String kind() { return kind; }
    public int transferId() { return transferId; }
    public int partIndex() { return partIndex; }
    public int partCount() { return partCount; }
    public byte[] payload() { return Arrays.copyOf(payload, payload.length); }

    public static final class Handler implements IMessageHandler<ExamSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(ExamSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleExamSync(message);
            return null;
        }
    }
}
