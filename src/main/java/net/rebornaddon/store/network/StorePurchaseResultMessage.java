package net.rebornaddon.store.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.store.StorePurchaseService;

public final class StorePurchaseResultMessage implements IMessage {
    private boolean success;
    private String messageKey;
    private String itemRegistryName;
    private int price;
    private long balance;

    public StorePurchaseResultMessage() {
    }

    public StorePurchaseResultMessage(StorePurchaseService.Result result) {
        success = result.success();
        messageKey = result.messageKey();
        itemRegistryName = result.itemRegistryName();
        price = result.price();
        balance = result.balance();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        success = buffer.readBoolean();
        messageKey = ByteBufUtils.readUTF8String(buffer);
        itemRegistryName = ByteBufUtils.readUTF8String(buffer);
        price = buffer.readInt();
        balance = buffer.readLong();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(success);
        ByteBufUtils.writeUTF8String(buffer, messageKey);
        ByteBufUtils.writeUTF8String(buffer, itemRegistryName);
        buffer.writeInt(price);
        buffer.writeLong(balance);
    }

    public boolean success() {
        return success;
    }

    public String messageKey() {
        return messageKey;
    }

    public String itemRegistryName() {
        return itemRegistryName;
    }

    public int price() {
        return price;
    }

    public long balance() {
        return balance;
    }

    public static final class Handler
            implements IMessageHandler<StorePurchaseResultMessage, IMessage> {
        @Override
        public IMessage onMessage(StorePurchaseResultMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleStorePurchaseResult(message);
            return null;
        }
    }
}
