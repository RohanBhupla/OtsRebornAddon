package net.rebornaddon.music.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public final class GuiRebornMusicControls extends RebornScaledGuiScreen {
    private static final int DONE = 0;
    private static final int TOGGLE_PAUSE = 1;
    private static final int SKIP = 2;

    private final GuiScreen parent;
    private GuiButton pauseButton;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public GuiRebornMusicControls(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panelW = Math.min(430, width - 16);
        panelH = Math.min(246, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int buttonW = Math.min(180, panelW - 32);
        int x = panelX + (panelW - buttonW) / 2;
        int top = panelY + 112;
        pauseButton = addButton(new ThemedButton(TOGGLE_PAUSE, x, top, buttonW, 22,
                pauseLabel(), Theme.PURPLE_DARK, Theme.TEAL, Theme.BUTTON_TEXT));
        addButton(new ThemedButton(SKIP, x, top + 28, buttonW, 22,
                ClientLocalization.format("gui.rebornaddon.music.skip", "Skip Track"),
                Theme.PURPLE_DARK, Theme.NEUTRAL, Theme.BUTTON_TEXT));
        addButton(new ThemedButton(DONE, x, panelY + panelH - 36, buttonW, 20,
                I18n.format("gui.done"), Theme.TAB_INACTIVE_BG, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT));
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
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(panelX, panelY, panelW, panelH, Theme.TEAL);
        GuiChrome.header(panelX + 2, panelY + 5, panelW - 4, 43, Theme.TEAL);
        drawCenteredString(fontRenderer, ClientLocalization.format(
                "gui.rebornaddon.music.title", "Reborn Music"), width / 2, panelY + 18, Theme.TEXT_LIGHT);

        RebornMusicController controller = RebornMusicController.INSTANCE;
        String title = controller.getCurrentTrackTitle();
        String track = title == null
                ? ClientLocalization.format("gui.rebornaddon.music.waiting", "Waiting for music")
                : ClientLocalization.format("gui.rebornaddon.music.current", "Now Playing: %s", title);
        track = fontRenderer.trimStringToWidth(track, Math.max(40, width - 40));
        GuiChrome.section(panelX + 18, panelY + 61, panelW - 36, 37, Theme.GOLD);
        drawCenteredString(fontRenderer, track, width / 2, panelY + 71, Theme.TEXT_LIGHT);
        if (title != null) {
            String state = controller.isPaused()
                    ? ClientLocalization.format("gui.rebornaddon.music.paused", "Paused")
                    : ClientLocalization.format("gui.rebornaddon.music.playing", "Playing");
            drawCenteredString(fontRenderer, state, width / 2, panelY + 84, Theme.TEXT_MUTED);
        }

        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private String pauseLabel() {
        return RebornMusicController.INSTANCE.isPaused()
                ? ClientLocalization.format("gui.rebornaddon.music.resume", "Resume Music")
                : ClientLocalization.format("gui.rebornaddon.music.pause", "Pause Music");
    }
}
