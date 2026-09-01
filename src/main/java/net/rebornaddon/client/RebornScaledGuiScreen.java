package net.rebornaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class RebornScaledGuiScreen extends GuiScreen {
    private static final int MIN_LAYOUT_WIDTH = 400;
    private static final int MIN_LAYOUT_HEIGHT = 300;

    private float effectiveScale = 1.0F;

    @Override
    public void setWorldAndResolution(Minecraft minecraft, int screenWidth, int screenHeight) {
        float requested = RebornGuiScale.getScale();
        float widthLimit = screenWidth / (float) MIN_LAYOUT_WIDTH;
        float heightLimit = screenHeight / (float) MIN_LAYOUT_HEIGHT;
        effectiveScale = Math.max(0.1F, Math.min(requested, Math.min(widthLimit, heightLimit)));

        int virtualWidth = Math.max(1, MathHelper.ceil(screenWidth / effectiveScale));
        int virtualHeight = Math.max(1, MathHelper.ceil(screenHeight / effectiveScale));
        super.setWorldAndResolution(minecraft, virtualWidth, virtualHeight);
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int scaledMouseX = MathHelper.floor(mouseX / effectiveScale);
        int scaledMouseY = MathHelper.floor(mouseY / effectiveScale);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(effectiveScale, effectiveScale, 1.0F);
            drawScaledScreen(scaledMouseX, scaledMouseY, partialTicks);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    protected abstract void drawScaledScreen(int mouseX, int mouseY, float partialTicks);

    protected final void drawScaledControls(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
