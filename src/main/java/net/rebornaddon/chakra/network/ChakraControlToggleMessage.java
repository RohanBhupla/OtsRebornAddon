package net.rebornaddon.chakra.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.compat.ChakraControlCompatibility;
import net.rebornaddon.village.network.RebornAddonNetwork;

public final class ChakraControlToggleMessage implements IMessage {
    private boolean enabled;

    public ChakraControlToggleMessage() {
    }

    public ChakraControlToggleMessage(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        enabled = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(enabled);
    }

    public static final class Handler implements IMessageHandler<ChakraControlToggleMessage, IMessage> {
        @Override
        public IMessage onMessage(final ChakraControlToggleMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ChakraControlCompatibility.setEnabled(player, message.enabled);
                    RebornAddonNetwork.sendChakraControlState(player, message.enabled);
                    TextComponentString status = new TextComponentString(statusText(message.enabled));
                    status.getStyle().setColor(message.enabled ? TextFormatting.AQUA : TextFormatting.GRAY);
                    player.sendStatusMessage(status, true);
                }
            });
            return null;
        }
    }

    static String statusText(boolean enabled) {
        return enabled ? "Chakra Control: Enabled" : "Chakra Control: Disabled";
    }
}
