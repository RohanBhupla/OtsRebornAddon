package net.rebornaddon.discord.client;

import net.minecraft.client.Minecraft;

public final class DiscordClientHooks {
    private DiscordClientHooks() {
    }

    public static void openDiscordLinkPrompt(boolean linked) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiDiscordLinkPrompt(linked));
    }

    public static void openDiscordLinkCode(String code, String command, String instruction) {
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiDiscordLinkCode(code, command, instruction));
    }
}
