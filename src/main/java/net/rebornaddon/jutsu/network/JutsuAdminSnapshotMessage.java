package net.rebornaddon.jutsu.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

import java.io.IOException;

public final class JutsuAdminSnapshotMessage implements IMessage {
    private static final int MAX_WIRE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_NBT_BYTES = 16L * 1024L * 1024L;

    private NBTTagCompound data;

    public JutsuAdminSnapshotMessage() {
    }

    public JutsuAdminSnapshotMessage(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        if (!buffer.isReadable()) {
            data = new NBTTagCompound();
            return;
        }
        if (buffer.readableBytes() > MAX_WIRE_BYTES) {
            throw new DecoderException("Admin snapshot exceeds the 16 MiB wire limit");
        }

        int readerIndex = buffer.readerIndex();
        if (buffer.readByte() == 0) {
            data = new NBTTagCompound();
            return;
        }
        buffer.readerIndex(readerIndex);
        try {
            data = CompressedStreamTools.read(new ByteBufInputStream(buffer),
                    new NBTSizeTracker(MAX_NBT_BYTES));
        } catch (IOException exception) {
            throw new DecoderException("Unable to decode the admin snapshot", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, data);
    }

    public NBTTagCompound data() {
        return data;
    }

    public static final class Handler implements IMessageHandler<JutsuAdminSnapshotMessage, IMessage> {
        @Override
        public IMessage onMessage(JutsuAdminSnapshotMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleJutsuAdminSnapshot(message);
            return null;
        }
    }
}
