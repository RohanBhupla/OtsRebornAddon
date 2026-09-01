package net.rebornaddon.village.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class OpenDiscordLinkCodeMessageHandler
        implements IMessageHandler<OpenDiscordLinkCodeMessage, IMessage> {
    @Override
    public IMessage onMessage(OpenDiscordLinkCodeMessage message, MessageContext context) {
        RebornAddonMod.proxy.openDiscordLinkCode(
                message.getCode(), message.getCommand(), message.getInstruction());
        return null;
    }
}
