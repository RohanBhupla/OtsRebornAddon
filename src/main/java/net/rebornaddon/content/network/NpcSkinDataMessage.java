package net.rebornaddon.content.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.performance.PerformanceMonitor;

import java.util.Arrays;

public final class NpcSkinDataMessage implements IMessage {
    private String url = "";
    private String hash = "";
    private int transferId;
    private int part;
    private int parts;
    private byte[] payload = new byte[0];

    public NpcSkinDataMessage() {
    }

    public NpcSkinDataMessage(String url, String hash, int transferId, int part, int parts, byte[] payload) {
        this.url = url;
        this.hash = hash;
        this.transferId = transferId;
        this.part = part;
        this.parts = parts;
        this.payload = payload == null ? new byte[0] : payload;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int start = buffer.readerIndex();
        url = ByteBufUtils.readUTF8String(buffer);
        hash = ByteBufUtils.readUTF8String(buffer);
        transferId = buffer.readInt();
        part = buffer.readInt();
        parts = buffer.readInt();
        int length = buffer.readInt();
        if (url.length() > 1024 || hash.length() > 64 || parts < 1 || parts > 256
                || part < 0 || part >= parts || length < 0 || length > 24576 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid NPC skin packet.");
        }
        payload = new byte[length];
        buffer.readBytes(payload);
        PerformanceMonitor.recordInbound(buffer.readerIndex() - start);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        ByteBufUtils.writeUTF8String(buffer, url);
        ByteBufUtils.writeUTF8String(buffer, hash);
        buffer.writeInt(transferId);
        buffer.writeInt(part);
        buffer.writeInt(parts);
        buffer.writeInt(payload.length);
        buffer.writeBytes(payload);
        PerformanceMonitor.recordOutbound(buffer.writerIndex() - start);
    }

    public String url() { return url; }
    public String hash() { return hash; }
    public int transferId() { return transferId; }
    public int part() { return part; }
    public int parts() { return parts; }
    public byte[] payload() { return Arrays.copyOf(payload, payload.length); }

    public static final class Handler implements IMessageHandler<NpcSkinDataMessage, IMessage> {
        @Override
        public IMessage onMessage(NpcSkinDataMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleNpcSkinData(message);
            return null;
        }
    }
}
