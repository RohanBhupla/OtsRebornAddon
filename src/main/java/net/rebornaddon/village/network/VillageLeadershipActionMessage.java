package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.village.VillageLeadershipService;

import java.util.UUID;

public class VillageLeadershipActionMessage implements IMessage {
    private int action;
    private int villageId;
    private UUID target;
    private String value = "";

    public VillageLeadershipActionMessage() {
    }

    public VillageLeadershipActionMessage(int action, int villageId, UUID target, String value) {
        this.action = action;
        this.villageId = villageId;
        this.target = target;
        this.value = value == null ? "" : value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
        villageId = buf.readByte();
        if (buf.readBoolean()) {
            target = new UUID(buf.readLong(), buf.readLong());
        }
        value = ByteBufUtils.readUTF8String(buf);
        if (value.length() > 96) {
            value = value.substring(0, 96);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeByte(villageId);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeLong(target.getMostSignificantBits());
            buf.writeLong(target.getLeastSignificantBits());
        }
        ByteBufUtils.writeUTF8String(buf, value.length() > 96 ? value.substring(0, 96) : value);
    }

    public static class Handler implements IMessageHandler<VillageLeadershipActionMessage, IMessage> {
        @Override
        public IMessage onMessage(final VillageLeadershipActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    VillageLeadershipService.INSTANCE.handleAction(player, message.action,
                            message.villageId, message.target == null ? "" : message.target.toString(),
                            message.value);
                }
            });
            return null;
        }
    }
}
