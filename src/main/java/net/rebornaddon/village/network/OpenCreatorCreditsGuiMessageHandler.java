package net.rebornaddon.village.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class OpenCreatorCreditsGuiMessageHandler implements IMessageHandler<OpenCreatorCreditsGuiMessage, IMessage> {
    @Override
    public IMessage onMessage(OpenCreatorCreditsGuiMessage message, MessageContext context) {
        try {
            Class<?> hooks = Class.forName("net.rebornaddon.credits.client.CreatorCreditsClientHooks");
            hooks.getMethod("openCreatorCredits").invoke(null);
        } catch (Exception ignored) {
        }
        return null;
    }
}
