package net.rebornaddon.credits.client;

import net.minecraft.client.Minecraft;

public final class CreatorCreditsClientHooks {
    private CreatorCreditsClientHooks() {
    }

    public static void openCreatorCredits() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(new GuiCreatorCredits());
            }
        });
    }
}
