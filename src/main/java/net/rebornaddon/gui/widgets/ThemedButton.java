package net.rebornaddon.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.gui.theme.Theme;

public class ThemedButton extends GuiButton {
    public enum Alignment {
        LEFT,
        CENTER
    }

    private final int bgColor;
    private final int hoverColor;
    private final int textColor;
    private boolean selected;
    private Alignment alignment = Alignment.CENTER;
    private int textInset = 6;

    public ThemedButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
                        int bgColor, int hoverColor, int textColor) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setTextAlignment(Alignment alignment) {
        this.alignment = alignment == null ? Alignment.CENTER : alignment;
    }

    public void setTextInset(int textInset) {
        this.textInset = Math.max(4, textInset);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        FontRenderer fr = minecraft.fontRenderer;
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int fill = !this.enabled ? Theme.BUTTON_DISABLED : this.hovered || selected ? hoverColor : bgColor;

        int border = (this.hovered || selected) && this.enabled ? hoverColor : Theme.BUTTON_BORDER;
        Gui.drawRect(this.x + 1, this.y + 2, this.x + this.width + 1,
                this.y + this.height + 1, 0x69000000);
        Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, Theme.EDGE_DARK);
        Gui.drawRect(this.x + 1, this.y + 1, this.x + this.width - 1,
                this.y + this.height - 1, border);
        Gui.drawRect(this.x + 2, this.y + 2, this.x + this.width - 2,
                this.y + this.height - 2, fill);
        Gui.drawRect(this.x + 2, this.y + 2, this.x + this.width - 2, this.y + 4,
                this.hovered ? 0x42FFFFFF : selected ? 0x2AFFFFFF : 0x16FFFFFF);
        Gui.drawRect(this.x + 2, this.y + this.height - 4, this.x + this.width - 2,
                this.y + this.height - 2, 0x4F000000);
        Gui.drawRect(this.x + 2, this.y + 2, this.x + 5, this.y + this.height - 2,
                this.enabled ? hoverColor : Theme.BUTTON_DISABLED);
        Gui.drawRect(this.x + this.width - 4, this.y + 4, this.x + this.width - 2,
                this.y + this.height - 4, 0x30000000);
        if (selected) {
            int underlineX = alignment == Alignment.LEFT ? this.x + textInset : this.x + 5;
            Gui.drawRect(underlineX, this.y + this.height - 4, this.x + this.width - 5,
                    this.y + this.height - 3, hoverColor);
            Gui.drawRect(this.x + 5, this.y + this.height / 2 - 2,
                    this.x + 7, this.y + this.height / 2 + 2, Theme.GOLD);
        } else if (this.hovered && this.enabled) {
            Gui.drawRect(this.x + this.width - 9, this.y + 5,
                    this.x + this.width - 5, this.y + 6, Theme.GOLD);
        }

        int textWidth = alignment == Alignment.LEFT ? this.width - textInset - 6 : this.width - 12;
        String text = trim(fr, this.displayString, textWidth);
        int textY = this.y + Math.max(1, (this.height - fr.FONT_HEIGHT) / 2);
        if (alignment == Alignment.LEFT) {
            fr.drawString(text, this.x + textInset, textY, this.enabled ? textColor : Theme.TEXT_MUTED);
        } else {
            drawCenteredString(fr, text, this.x + this.width / 2, textY,
                    this.enabled ? textColor : Theme.TEXT_MUTED);
        }
        mouseDragged(minecraft, mouseX, mouseY);
    }

    private static String trim(FontRenderer fr, String value, int width) {
        if (fr.getStringWidth(value) <= width) {
            return value;
        }

        return fr.trimStringToWidth(value, Math.max(8, width - fr.getStringWidth("..."))) + "...";
    }
}
