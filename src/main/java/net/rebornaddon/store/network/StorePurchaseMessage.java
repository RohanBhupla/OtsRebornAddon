package net.rebornaddon.store.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.store.StoreConfigurationService;

public final class StorePurchaseMessage implements IMessage {
    private String itemRegistryName;

    public StorePurchaseMessage() {
    }

    public StorePurchaseMessage(String itemRegistryName) {
        this.itemRegistryName = itemRegistryName == null ? "" : itemRegistryName;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        itemRegistryName = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, itemRegistryName);
    }

    public static final class Handler implements IMessageHandler<StorePurchaseMessage, IMessage> {
        @Override
        public IMessage onMessage(final StorePurchaseMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    StoreConfigurationService.INSTANCE.purchase(player, message.itemRegistryName);
                }
            });
            return null;
        }
    }
}
