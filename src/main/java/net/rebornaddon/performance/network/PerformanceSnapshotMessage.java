package net.rebornaddon.performance.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.performance.PerformanceMonitor;

public final class PerformanceSnapshotMessage implements IMessage {
    private NBTTagCompound data = new NBTTagCompound();

    public PerformanceSnapshotMessage() {
    }

    public PerformanceSnapshotMessage(NBTTagCompound data) {
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        data = ByteBufUtils.readTag(buffer);
        if (data == null) data = new NBTTagCompound();
        PerformanceMonitor.recordInbound(buffer.readerIndex());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int start = buffer.writerIndex();
        ByteBufUtils.writeTag(buffer, data);
        PerformanceMonitor.recordOutbound(buffer.writerIndex() - start);
    }

    public NBTTagCompound data() {
        return data;
    }

    public static final class Handler implements IMessageHandler<PerformanceSnapshotMessage, IMessage> {
        @Override
        public IMessage onMessage(PerformanceSnapshotMessage message, MessageContext context) {
            RebornAddonMod.proxy.handlePerformanceSnapshot(message);
            return null;
        }
    }
}
