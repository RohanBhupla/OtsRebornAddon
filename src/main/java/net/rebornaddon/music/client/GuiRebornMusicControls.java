package net.rebornaddon.music.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.client.ClientLocalization;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public final class GuiRebornMusicControls extends GuiScreen {
    private static final int DONE = 0;
    private static final int TOGGLE_PAUSE = 1;
    private static final int SKIP = 2;

    private final GuiScreen parent;
    private GuiButton pauseButton;

    public GuiRebornMusicControls(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int top = Math.max(76, height / 4 + 16);
        pauseButton = addButton(new GuiButton(TOGGLE_PAUSE, width / 2 - 100, top,
                pauseLabel()));
        addButton(new GuiButton(SKIP, width / 2 - 100, top + 24,
                ClientLocalization.format("gui.rebornaddon.music.skip", "Skip Track")));
        addButton(new GuiButton(DONE, width / 2 - 100, top + 72,
                I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        if (button.id == DONE) {
            mc.displayGuiScreen(parent);
        } else if (button.id == TOGGLE_PAUSE) {
            RebornMusicController.INSTANCE.togglePaused();
            pauseButton.displayString = pauseLabel();
        } else if (button.id == SKIP) {
            RebornMusicController.INSTANCE.skipTrack();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, ClientLocalization.format(
                "gui.rebornaddon.music.title", "Reborn Music"), width / 2, 20, 0xFFFFFF);

        RebornMusicController controller = RebornMusicController.INSTANCE;
        String title = controller.getCurrentTrackTitle();
        String track = title == null
                ? ClientLocalization.format("gui.rebornaddon.music.waiting", "Waiting for music")
                : ClientLocalization.format("gui.rebornaddon.music.current", "Now Playing: %s", title);
        track = fontRenderer.trimStringToWidth(track, Math.max(40, width - 40));
        drawCenteredString(fontRenderer, track, width / 2, 48, 0xFFFFFF);
        if (title != null) {
            String state = controller.isPaused()
                    ? ClientLocalization.format("gui.rebornaddon.music.paused", "Paused")
                    : ClientLocalization.format("gui.rebornaddon.music.playing", "Playing");
            drawCenteredString(fontRenderer, state, width / 2, 60, 0xA0A0A0);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String pauseLabel() {
        return RebornMusicController.INSTANCE.isPaused()
                ? ClientLocalization.format("gui.rebornaddon.music.resume", "Resume Music")
                : ClientLocalization.format("gui.rebornaddon.music.pause", "Pause Music");
    }
}
