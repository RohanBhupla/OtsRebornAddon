package net.rebornaddon.jutsu.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class AdminAccessSyncMessage implements IMessage {
    private boolean operator;

    public AdminAccessSyncMessage() {
    }

    public AdminAccessSyncMessage(boolean operator) {
        this.operator = operator;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        operator = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(operator);
    }

    public boolean operator() {
        return operator;
    }

    public static final class Handler implements IMessageHandler<AdminAccessSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(AdminAccessSyncMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleAdminAccessSync(message);
            return null;
        }
    }
}
