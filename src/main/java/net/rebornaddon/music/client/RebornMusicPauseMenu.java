package net.rebornaddon.music.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.client.ClientLocalization;

import java.util.List;

@SideOnly(Side.CLIENT)
public final class RebornMusicPauseMenu {
    public static final RebornMusicPauseMenu INSTANCE = new RebornMusicPauseMenu();
    private static final int MUSIC_BUTTON = 21069;

    private RebornMusicPauseMenu() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMenuInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiIngameMenu)) {
            return;
        }

        List<GuiButton> buttons = event.getButtonList();
        GuiButton exitButton = null;
        for (GuiButton button : buttons) {
            if (button.id == 1) {
                exitButton = button;
                break;
            }
        }
        if (exitButton == null) {
            return;
        }

        int insertionY = exitButton.y;
        for (GuiButton button : buttons) {
            if (button.y >= insertionY) {
                button.y += 24;
            }
        }
        buttons.add(new GuiButton(MUSIC_BUTTON, exitButton.x, insertionY,
                exitButton.width, exitButton.height, ClientLocalization.format(
                        "gui.rebornaddon.music.button", "Reborn Music")));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onButtonPressed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton().id != MUSIC_BUTTON || !(event.getGui() instanceof GuiIngameMenu)) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getMinecraft().displayGuiScreen(new GuiRebornMusicControls(event.getGui()));
    }
}
