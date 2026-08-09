package net.rebornaddon.quest.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public class QuestSyncMessage implements IMessage {
    private NBTTagCompound data;

    public QuestSyncMessage() {
    }

    public QuestSyncMessage(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = ByteBufUtils.readTag(buf);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<QuestSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(QuestSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleQuestSync(message);
            return null;
        }
    }
}
