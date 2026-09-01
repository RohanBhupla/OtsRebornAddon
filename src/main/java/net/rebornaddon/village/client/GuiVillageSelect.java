package net.rebornaddon.village.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;

public class GuiVillageSelect extends RebornScaledGuiScreen {
    private static final int BUTTON_BASE = 700;
    private static final int PANEL_MAX_W = 520;
    private static final int PANEL_MAX_H = 272;
    private static final int MARGIN = 14;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    @Override
    public void initGui() {
        super.initGui();
        panelW = Math.min(PANEL_MAX_W, width - 16);
        panelH = Math.min(PANEL_MAX_H, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        buildButtons();
    }

    private void buildButtons() {
        buttonList.clear();

        Village[] villages = Village.choices();
        int top = panelY + 62;
        int availableW = panelW - MARGIN * 2;
        int buttonH = 42;

        if (availableW >= 400) {
            int gap = 8;
            int colW = (availableW - gap) / 2;
            for (int i = 0; i < villages.length; i++) {
                int col = i % 2;
                int row = i / 2;
                int x = panelX + MARGIN + col * (colW + gap);
                int y = top + row * (buttonH + 8);
                int w = i == villages.length - 1 ? availableW : colW;
                if (i == villages.length - 1) {
                    x = panelX + MARGIN;
                }
                buttonList.add(new VillageButton(BUTTON_BASE + villages[i].id(), x, y, w, buttonH, villages[i]));
            }
        } else {
            int gap = 4;
            int h = Math.max(24, Math.min(30, (panelH - 74 - gap * (villages.length - 1)) / villages.length));
            for (int i = 0; i < villages.length; i++) {
                buttonList.add(new VillageButton(BUTTON_BASE + villages[i].id(), panelX + MARGIN,
                        top + i * (h + gap), availableW, h, villages[i]));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        Village village = Village.byId(button.id - BUTTON_BASE);
        if (village == null) {
            return;
        }

        RebornAddonNetwork.sendVillageSelection(village);
        mc.displayGuiScreen(null);
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel();
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int right = panelX + panelW;
        GuiChrome.frame(panelX, panelY, panelW, panelH, Theme.GOLD);
        GuiChrome.header(panelX + 2, panelY + 5, panelW - 4, 48, Theme.GOLD);

        fr.drawString(ClientLocalization.format("gui.rebornaddon.village.choose", "Choose Your Village"),
                panelX + MARGIN, panelY + 14, Theme.TEXT_LIGHT);
        String mark = ClientLocalization.format("gui.rebornaddon.village.allegiance", "Permanent Allegiance");
        fr.drawString(mark, right - MARGIN - fr.getStringWidth(mark), panelY + 14, Theme.TEXT_MUTED);
        GuiChrome.rule(panelX + MARGIN, right - MARGIN, panelY + 42, Theme.GOLD);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static class VillageButton extends GuiButton {
        private final Village village;

        VillageButton(int id, int x, int y, int width, int height, Village village) {
            super(id, x, y, width, height, village.displayName());
            this.village = village;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }

            FontRenderer fr = minecraft.fontRenderer;
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int fill = hovered ? Theme.PANEL_BG_RAISED : Theme.PARCHMENT_DARK;
            int accent = hovered ? village.hoverColor() : village.accentColor();

            Gui.drawRect(x + 2, y + 2, x + width + 2, y + height + 2, 0x62000000);
            Gui.drawRect(x, y, x + width, y + height, Theme.EDGE_DARK);
            Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1,
                    hovered ? accent : Theme.BUTTON_BORDER);
            Gui.drawRect(x + 2, y + 2, x + width - 2, y + height - 2, fill);
            Gui.drawRect(x + 2, y + 2, x + width - 2, y + 5,
                    hovered ? 0x36FFFFFF : Theme.PARCHMENT_LIGHT);
            Gui.drawRect(x + 2, y + 2, x + 6, y + height - 2, accent);
            Gui.drawRect(x + width - 19, y + height - 6,
                    x + width - 7, y + height - 5, accent);

            int sealSize = Math.max(14, Math.min(24, height - 10));
            int sealX = x + 11;
            int sealY = y + (height - sealSize) / 2;
            Gui.drawRect(sealX + 1, sealY + 2, sealX + sealSize + 2,
                    sealY + sealSize + 2, 0x60000000);
            Gui.drawRect(sealX, sealY, sealX + sealSize, sealY + sealSize, Theme.EDGE_DARK);
            Gui.drawRect(sealX + 1, sealY + 1, sealX + sealSize - 1,
                    sealY + sealSize - 1, accent);
            Gui.drawRect(sealX + 3, sealY + 3, sealX + sealSize - 3,
                    sealY + sealSize - 3, Theme.INK);
            String initial = village.displayName().substring(0, 1);
            fr.drawString(initial, sealX + (sealSize - fr.getStringWidth(initial)) / 2,
                    sealY + (sealSize - fr.FONT_HEIGHT) / 2, accent);

            int textX = sealX + sealSize + 9;
            String title = trim(fr, village.displayName(), width - (textX - x) - 10);
            fr.drawString(title, textX, y + Math.max(1, (height - fr.FONT_HEIGHT) / 2),
                    hovered ? Theme.WHITE : Theme.TEXT_LIGHT);
            mouseDragged(minecraft, mouseX, mouseY);
        }

        private static String trim(FontRenderer fr, String value, int maxWidth) {
            if (fr.getStringWidth(value) <= maxWidth) {
                return value;
            }
            return fr.trimStringToWidth(value, Math.max(8, maxWidth - fr.getStringWidth("..."))) + "...";
        }
    }
}
