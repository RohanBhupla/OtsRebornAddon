package net.rebornaddon.chakra.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;

import java.io.IOException;

public final class GuiChakraModeLearn extends RebornScaledGuiScreen {
    private static final ResourceLocation SCROLL =
            new ResourceLocation("narutomod", "textures/scoll_screen.png");
    private static final ResourceLocation SIGN_CHOU =
            new ResourceLocation("narutomod", "textures/chou_.png");
    private static final ResourceLocation SIGN_ZI =
            new ResourceLocation("narutomod", "textures/zi_.png");
    private static final ResourceLocation SIGN_CHEN =
            new ResourceLocation("narutomod", "textures/chen_.png");
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final ChakraMode mode;
    private int guiLeft;
    private int guiTop;

    public GuiChakraModeLearn(ChakraMode mode) {
        this.mode = mode;
    }

    @Override
    public void initGui() {
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;
        buttonList.clear();
        buttonList.add(new ThemedButton(0, guiLeft - 56, guiTop + 127, 48, 20,
                ClientLocalization.format("gui.rebornaddon.chakra.learn", "Learn"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            RebornAddonNetwork.sendChakraLearn(mode);
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(SCROLL);
        drawModalRectWithCustomSizedTexture(guiLeft - 70, guiTop - 70,
                0.0F, 0.0F, 320, 320, 320.0F, 320.0F);

        drawTexture(new ResourceLocation(mode.iconTexture()), guiLeft + 89, guiTop + 49);
        drawTexture(SIGN_CHOU, guiLeft, guiTop + 108);
        drawTexture(SIGN_ZI, guiLeft + 56, guiTop + 108);
        drawTexture(SIGN_CHEN, guiLeft + 112, guiTop + 108);
        fontRenderer.drawString(mode.displayName(), guiLeft + 38, guiTop + 13, 0x202020);
        drawRect(guiLeft + 31, guiTop + 29, guiLeft + 145, guiTop + 30, 0x553A2416);
        drawRect(guiLeft + 84, guiTop + 42, guiLeft + 140, guiTop + 43, 0x553A2416);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawTexture(ResourceLocation texture, int x, int y) {
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F,
                48, 48, 48.0F, 48.0F);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
