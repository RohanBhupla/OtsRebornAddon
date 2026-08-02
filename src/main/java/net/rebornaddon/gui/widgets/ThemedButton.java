package net.rebornaddon.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.gui.theme.Theme;

public class ThemedButton extends GuiButton {
    private final int bgColor;
    private final int hoverColor;
    private final int textColor;

    public ThemedButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
                        int bgColor, int hoverColor, int textColor) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        FontRenderer fr = minecraft.fontRenderer;
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int fill = !this.enabled ? Theme.BUTTON_DISABLED : this.hovered ? hoverColor : bgColor;

        Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, Theme.BUTTON_BORDER);
        Gui.drawRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, fill);

        String text = trim(fr, this.displayString, this.width - 8);
        drawCenteredString(fr, text, this.x + this.width / 2, this.y + (this.height - 8) / 2,
                this.enabled ? textColor : Theme.TEXT_MUTED);
        mouseDragged(minecraft, mouseX, mouseY);
    }

    private static String trim(FontRenderer fr, String value, int width) {
        if (fr.getStringWidth(value) <= width) {
            return value;
        }

        return fr.trimStringToWidth(value, Math.max(8, width - fr.getStringWidth("..."))) + "...";
    }
}
