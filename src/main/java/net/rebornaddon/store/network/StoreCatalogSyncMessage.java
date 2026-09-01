package net.rebornaddon.store.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class StoreCatalogSyncMessage implements IMessage {
    private NBTTagCompound data = new NBTTagCompound();

    public StoreCatalogSyncMessage() {
    }

    public StoreCatalogSyncMessage(NBTTagCompound data) {
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        NBTTagCompound read = ByteBufUtils.readTag(buffer);
        data = read == null ? new NBTTagCompound() : read;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, data);
    }

    public NBTTagCompound data() {
        return data;
    }

    public static final class Handler implements IMessageHandler<StoreCatalogSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(StoreCatalogSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleStoreCatalogSync(message);
            return null;
        }
    }
}
