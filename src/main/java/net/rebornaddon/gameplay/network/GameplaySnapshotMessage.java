package net.rebornaddon.gameplay.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.performance.PerformanceMonitor;

public final class GameplaySnapshotMessage implements IMessage {
    private String section = "";
    private String json = "";

    public GameplaySnapshotMessage() {
    }

    public GameplaySnapshotMessage(String section, String json) {
        this.section = section == null ? "" : section;
        this.json = json == null ? "" : json;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        section = ByteBufUtils.readUTF8String(buffer);
        json = ByteBufUtils.readUTF8String(buffer);
        PerformanceMonitor.recordInbound(buffer.readerIndex());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        ByteBufUtils.writeUTF8String(buffer, section);
        ByteBufUtils.writeUTF8String(buffer, json);
        PerformanceMonitor.recordOutbound(buffer.writerIndex() - start);
    }

    public String section() { return section; }
    public String json() { return json; }

    public static final class Handler implements IMessageHandler<GameplaySnapshotMessage, IMessage> {
        @Override
        public IMessage onMessage(GameplaySnapshotMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleGameplaySnapshot(message);
            return null;
        }
    }
}
