package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public class VillageLeadershipSyncMessage implements IMessage {
    private NBTTagCompound data;

    public VillageLeadershipSyncMessage() {
    }

    public VillageLeadershipSyncMessage(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<VillageLeadershipSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(VillageLeadershipSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleVillageLeadershipSync(message);
            return null;
        }
    }
}
