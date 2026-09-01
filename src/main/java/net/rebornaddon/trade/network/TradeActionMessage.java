package net.rebornaddon.trade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.trade.TradeService;

import java.util.UUID;

public final class TradeActionMessage implements IMessage {
    private int action;
    private String target = "";
    private int slot = -1;
    private int amount;

    public TradeActionMessage() {
    }

    public TradeActionMessage(int action, UUID target, int slot, int amount) {
        this.action = action;
        this.target = target == null ? "" : target.toString();
        this.slot = slot;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readUnsignedByte();
        target = clean(ByteBufUtils.readUTF8String(buffer));
        slot = buffer.readInt();
        amount = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
        ByteBufUtils.writeUTF8String(buffer, target);
        buffer.writeInt(slot);
        buffer.writeInt(amount);
    }

    private UUID targetId() {
        try {
            return target.isEmpty() ? null : UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() > 36 ? clean.substring(0, 36) : clean;
    }

    public static final class Handler implements IMessageHandler<TradeActionMessage, IMessage> {
        @Override
        public IMessage onMessage(final TradeActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    TradeService.INSTANCE.handle(player, message.action, message.targetId(),
                            message.slot, message.amount);
                }
            });
            return null;
        }
    }
}
