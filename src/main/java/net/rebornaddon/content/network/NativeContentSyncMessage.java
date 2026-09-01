package net.rebornaddon.content.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.rebornaddon.performance.PerformanceMonitor;

public final class NativeContentSyncMessage implements IMessage {
    private String kind = "";
    private int transferId;
    private int partIndex;
    private int partCount = 1;
    private byte[] payload = new byte[0];

    public NativeContentSyncMessage() {
    }

    public NativeContentSyncMessage(String kind, String json) {
        this(kind, 0, 0, 1, json == null ? new byte[0]
                : json.getBytes(StandardCharsets.UTF_8));
    }

    public NativeContentSyncMessage(String kind, int transferId, int partIndex,
                                    int partCount, byte[] payload) {
        this.kind = kind == null ? "" : kind;
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
        int start = buffer.readerIndex();
        kind = ByteBufUtils.readUTF8String(buffer);
        transferId = buffer.readInt();
        partIndex = buffer.readInt();
        partCount = buffer.readInt();
        int length = buffer.readInt();
        if (length < 0 || length > 32768 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid native content fragment length.");
        }
        payload = new byte[length];
        buffer.readBytes(payload);
        PerformanceMonitor.recordInbound(buffer.readerIndex() - start);
    }

    public String kind() { return kind; }
    public int transferId() { return transferId; }
    public int partIndex() { return partIndex; }
    public int partCount() { return partCount; }
    public byte[] payload() { return Arrays.copyOf(payload, payload.length); }

    public static final class Handler implements IMessageHandler<NativeContentSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(NativeContentSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleNativeContentSync(message);
            return null;
        }
    }
}
