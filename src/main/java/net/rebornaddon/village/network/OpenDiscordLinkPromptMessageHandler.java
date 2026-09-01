package net.rebornaddon.village.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public final class OpenDiscordLinkPromptMessageHandler
        implements IMessageHandler<OpenDiscordLinkPromptMessage, IMessage> {
    @Override
    public IMessage onMessage(OpenDiscordLinkPromptMessage message, MessageContext context) {
        RebornAddonMod.proxy.openDiscordLinkPrompt(message.linked());
        return null;
    }
}
