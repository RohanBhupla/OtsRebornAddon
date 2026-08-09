package net.rebornaddon.chakra.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class ChakraModeEffectMessage implements IMessage {
    private int entityId;
    private int modeId;
    private boolean active;

    public ChakraModeEffectMessage() {
    }

    public ChakraModeEffectMessage(int entityId, int modeId, boolean active) {
        this.entityId = entityId;
        this.modeId = modeId;
        this.active = active;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        modeId = buf.readByte();
        active = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(modeId);
        buf.writeBoolean(active);
    }

    public int getEntityId() {
        return entityId;
    }

    public int getModeId() {
        return modeId;
    }

    public boolean isActive() {
        return active;
    }

    public static final class Handler implements IMessageHandler<ChakraModeEffectMessage, IMessage> {
        @Override
        public IMessage onMessage(ChakraModeEffectMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleChakraModeEffect(message);
            return null;
        }
    }
}
