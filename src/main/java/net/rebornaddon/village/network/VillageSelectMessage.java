package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.village.VillageSelectionHandler;

public class VillageSelectMessage implements IMessage {
    private int villageId;

    public VillageSelectMessage() {
    }

    public VillageSelectMessage(int villageId) {
        this.villageId = villageId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        villageId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(villageId);
    }

    public static class Handler implements IMessageHandler<VillageSelectMessage, IMessage> {
        @Override
        public IMessage onMessage(final VillageSelectMessage message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    VillageSelectionHandler.INSTANCE.handleSelection(player, message.villageId);
                }
            });
            return null;
        }
    }
}
