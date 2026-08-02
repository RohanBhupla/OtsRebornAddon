package net.rebornaddon.village.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class OpenVillageGuiMessageHandler implements IMessageHandler<OpenVillageGuiMessage, IMessage> {
    @Override
    public IMessage onMessage(OpenVillageGuiMessage message, MessageContext context) {
        try {
            Class<?> hooks = Class.forName("net.rebornaddon.village.client.VillageClientHooks");
            hooks.getMethod("openVillageSelect").invoke(null);
        } catch (Exception ignored) {
        }
        return null;
    }
}
