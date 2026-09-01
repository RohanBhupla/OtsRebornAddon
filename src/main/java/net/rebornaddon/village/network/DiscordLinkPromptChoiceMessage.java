package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.discord.DiscordPluginBridge;
import net.rebornaddon.village.VillageSelectionHandler;

public final class DiscordLinkPromptChoiceMessage implements IMessage {
    public static final int SKIP = 0;
    public static final int LINK = 1;
    public static final int UNLINK = 2;
    private int action;

    public DiscordLinkPromptChoiceMessage() {
    }

    public DiscordLinkPromptChoiceMessage(boolean accepted) {
        this(accepted ? LINK : SKIP);
    }

    public DiscordLinkPromptChoiceMessage(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
    }

    public static final class Handler
            implements IMessageHandler<DiscordLinkPromptChoiceMessage, IMessage> {
        @Override
        public IMessage onMessage(final DiscordLinkPromptChoiceMessage message,
                                  final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (message.action == SKIP) {
                        VillageSelectionHandler.INSTANCE.markDiscordPromptSkipped(player, true);
                        return;
                    }
                    if (!DiscordPluginBridge.isAvailable()) {
                        player.sendMessage(new TextComponentString(DiscordPluginBridge.unavailableReason()));
                        return;
                    }

                    if (message.action == UNLINK) {
                        DiscordPluginBridge.ActionResult unlink = DiscordPluginBridge.unlink(player);
                        if (unlink.success()) {
                            VillageSelectionHandler.INSTANCE.markDiscordPromptSkipped(player, true);
                        }
                        player.sendMessage(new TextComponentString(unlink.message()));
                        return;
                    }

                    if (message.action != LINK) {
                        return;
                    }
                    VillageSelectionHandler.INSTANCE.markDiscordPromptSkipped(player, false);

                    DiscordPluginBridge.LinkCodeResult result =
                            DiscordPluginBridge.requestLinkCode(player);
                    if (!result.success()) {
                        player.sendMessage(new TextComponentString(result.message()));
                        return;
                    }
                    RebornAddonNetwork.openDiscordLinkCode(
                            player, result.code(), result.command(), result.message());
                }
            });
            return null;
        }
    }
}
