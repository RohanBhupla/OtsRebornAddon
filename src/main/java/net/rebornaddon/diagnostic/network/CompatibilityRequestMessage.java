package net.rebornaddon.diagnostic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class CompatibilityRequestMessage implements IMessage {
    private NBTTagCompound manifest = new NBTTagCompound();

    public CompatibilityRequestMessage() {
    }

    public CompatibilityRequestMessage(NBTTagCompound manifest) {
        this.manifest = manifest == null ? new NBTTagCompound() : manifest;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        manifest = ByteBufUtils.readTag(buffer);
        if (manifest == null) manifest = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, manifest);
    }

    public NBTTagCompound manifest() {
        return manifest;
    }

    public static final class Handler implements IMessageHandler<CompatibilityRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(CompatibilityRequestMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleCompatibilityRequest(message);
            return null;
        }
    }
}
