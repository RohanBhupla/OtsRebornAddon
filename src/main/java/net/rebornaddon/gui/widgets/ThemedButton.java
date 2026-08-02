package net.rebornaddon.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.gui.theme.Theme;

/**
 * Every button in the hub should use this instead of a plain GuiButton - vanilla
 * GuiButton draws with Minecraft's own grey button texture, which is why nothing
 * looked themed before even though colors were being passed around unused.
 */
public class ThemedButton extends GuiButton {

    private final int bgColor;
    private final int hoverColor;
    private final int textColor;

    public ThemedButton(int id, int x, int y, int width, int height, String text,
                         int bgColor, int hoverColor, int textColor) {
        super(id, x, y, width, height, text);
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        boolean hovered = this.enabled
                && mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        int bg = !this.enabled ? Theme.BUTTON_DISABLED : (hovered ? hoverColor : bgColor);

        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bg);
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, Theme.BUTTON_BORDER);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, Theme.BUTTON_BORDER);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, Theme.BUTTON_BORDER);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, Theme.BUTTON_BORDER);

        int strWidth = mc.fontRenderer.getStringWidth(this.displayString);
        int textX = this.x + (this.width - strWidth) / 2;
        int textY = this.y + (this.height - 8) / 2;
        mc.fontRenderer.drawString(this.displayString, textX, textY, textColor);
    }
}
