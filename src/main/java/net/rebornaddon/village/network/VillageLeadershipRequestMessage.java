package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.village.VillageLeadershipService;

public class VillageLeadershipRequestMessage implements IMessage {
    private int villageId;

    public VillageLeadershipRequestMessage() {
    }

    public VillageLeadershipRequestMessage(int villageId) {
        this.villageId = villageId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        villageId = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(villageId);
    }

    public static class Handler implements IMessageHandler<VillageLeadershipRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(final VillageLeadershipRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    VillageLeadershipService.INSTANCE.request(player, message.villageId, false);
                }
            });
            return null;
        }
    }
}
