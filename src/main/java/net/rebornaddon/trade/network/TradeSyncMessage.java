package net.rebornaddon.trade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class TradeSyncMessage implements IMessage {
    private NBTTagCompound data = new NBTTagCompound();

    public TradeSyncMessage() {
    }

    public TradeSyncMessage(NBTTagCompound data) {
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        NBTTagCompound received = ByteBufUtils.readTag(buffer);
        data = received == null ? new NBTTagCompound() : received;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, data);
    }

    public NBTTagCompound data() {
        return data;
    }

    public static final class Handler implements IMessageHandler<TradeSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(TradeSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleTradeSync(message);
            return null;
        }
    }
}
