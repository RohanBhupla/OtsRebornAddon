package net.rebornaddon.compat.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class NarutoAddonOptionsGuard {
    public static final NarutoAddonOptionsGuard INSTANCE = new NarutoAddonOptionsGuard();
    private static final String OPTIONS_GUI = "narutomodaddon.gui.GuiOptions$GuiWindow";
    private static final String REROLL_GUI = "narutomodaddon.gui.GuiChangeKKG$GuiWindow";

    private NarutoAddonOptionsGuard() {
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui != null && (OPTIONS_GUI.equals(gui.getClass().getName())
                || REROLL_GUI.equals(gui.getClass().getName()))) {
            event.setCanceled(true);
        }
    }
}
