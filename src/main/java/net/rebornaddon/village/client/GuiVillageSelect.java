package net.rebornaddon.village.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;

public class GuiVillageSelect extends GuiScreen {
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
            int h = 30;
            for (int i = 0; i < villages.length; i++) {
                buttonList.add(new VillageButton(BUTTON_BASE + villages[i].id(), panelX + MARGIN,
                        top + i * (h + 5), availableW, h, villages[i]));
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int right = panelX + panelW;
        int bottom = panelY + panelH;

        drawRect(panelX - 6, panelY - 6, right + 6, bottom + 6, 0x99000000);
        drawRect(panelX, panelY, right, bottom, Theme.GOLD_DARK);
        drawRect(panelX + 2, panelY + 2, right - 2, bottom - 2, Theme.PANEL_BG);
        drawRect(panelX + 4, panelY + 4, right - 4, panelY + 52, Theme.PANEL_BG_LIGHT);
        drawRect(panelX + 4, panelY + 52, right - 4, panelY + 53, Theme.GOLD);

        fr.drawString("Choose Your Village", panelX + MARGIN, panelY + 14, Theme.TEXT_LIGHT);
        String mark = "Stone  Leaf  Cloud  Sand  Mist";
        fr.drawString(mark, right - MARGIN - fr.getStringWidth(mark), panelY + 14, Theme.TEXT_MUTED);
        drawRect(panelX + MARGIN, panelY + 42, right - MARGIN, panelY + 43, 0x664C3720);
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
            int fill = hovered ? 0xF02B221A : Theme.PARCHMENT_DARK;
            int accent = hovered ? village.hoverColor() : village.accentColor();

            Gui.drawRect(x, y, x + width, y + height, Theme.BUTTON_BORDER);
            Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, fill);
            Gui.drawRect(x + 1, y + 1, x + 6, y + height - 1, accent);
            Gui.drawRect(x + 11, y + 9, x + 23, y + height - 9, accent);
            Gui.drawRect(x + 13, y + 11, x + 21, y + height - 11, 0xAA000000);

            String title = trim(fr, village.displayName(), width - 44);
            fr.drawString(title, x + 32, y + (height - 8) / 2, hovered ? Theme.WHITE : Theme.TEXT_LIGHT);
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
