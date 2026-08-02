package net.rebornaddon.village.client;

import net.minecraft.client.Minecraft;

public final class VillageClientHooks {
    private VillageClientHooks() {
    }

    public static void openVillageSelect() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(new GuiVillageSelect());
            }
        });
    }
}
