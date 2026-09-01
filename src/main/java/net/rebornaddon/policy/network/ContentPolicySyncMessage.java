package net.rebornaddon.policy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.RebornAddonMod;

public final class ContentPolicySyncMessage implements IMessage {
    private NBTTagCompound data = new NBTTagCompound();

    public ContentPolicySyncMessage() {
    }

    public ContentPolicySyncMessage(NBTTagCompound data) {
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        data = ByteBufUtils.readTag(buffer);
        if (data == null) data = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, data);
    }

    public static final class Handler implements IMessageHandler<ContentPolicySyncMessage, IMessage> {
        @Override
        public IMessage onMessage(final ContentPolicySyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleContentPolicySync(message.data);
            return null;
        }
    }
}
