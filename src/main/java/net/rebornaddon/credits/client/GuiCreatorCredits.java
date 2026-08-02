package net.rebornaddon.credits.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public class GuiCreatorCredits extends GuiScreen {
    private static final String AHZNB_DISCORD = "https://discord.gg/HdFbhPeP";
    private static final String SPRING_DISCORD = "https://discord.gg/MJEcvv8Ftn";
    private static final int BTN_AHZNB = 810;
    private static final int BTN_SPRING = 811;
    private static final int BTN_CLOSE = 812;
    private static final int PANEL_MAX_W = 560;
    private static final int PANEL_MAX_H = 318;
    private static final int MARGIN = 16;

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

        int contentW = panelW - MARGIN * 2;
        int buttonW = Math.min(168, Math.max(118, (contentW - 10) / 2));
        int rowY = panelY + panelH - 42;
        buttonList.add(new ThemedButton(BTN_AHZNB, panelX + MARGIN, rowY, buttonW, 22, "AHZNB Discord",
                Theme.PARCHMENT_DARK, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT));
        buttonList.add(new ThemedButton(BTN_SPRING, panelX + MARGIN + buttonW + 8, rowY, buttonW, 22, "Spring Discord",
                Theme.PARCHMENT_DARK, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT));
        buttonList.add(new ThemedButton(BTN_CLOSE, panelX + panelW - MARGIN - 72, panelY + 12, 72, 20, "Close",
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_AHZNB) {
            openUrl(AHZNB_DISCORD);
            return;
        }

        if (button.id == BTN_SPRING) {
            openUrl(SPRING_DISCORD);
            return;
        }

        if (button.id == BTN_CLOSE) {
            mc.displayGuiScreen(null);
        }
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
        int contentLeft = panelX + MARGIN;
        int contentRight = right - MARGIN;

        drawRect(panelX - 6, panelY - 6, right + 6, bottom + 6, 0x99000000);
        drawRect(panelX, panelY, right, bottom, Theme.GOLD_DARK);
        drawRect(panelX + 2, panelY + 2, right - 2, bottom - 2, Theme.PANEL_BG);
        drawRect(panelX + 4, panelY + 4, right - 4, panelY + 54, Theme.PANEL_BG_LIGHT);
        drawRect(panelX + 4, panelY + 54, right - 4, panelY + 55, Theme.GOLD);

        fr.drawString("Mod Creator Credits", contentLeft, panelY + 15, Theme.TEXT_LIGHT);
        fr.drawString("Otsutsuki Reborn", contentLeft, panelY + 31, Theme.TEXT_MUTED);

        int y = panelY + 70;
        y = drawCreditBlock(fr, contentLeft, contentRight, y, "AHZNB",
                "Credited for Naruto Add-ons content and original add-on work used by this pack.",
                AHZNB_DISCORD, Theme.GOLD);
        y += 14;
        drawCreditBlock(fr, contentLeft, contentRight, y, "Spring",
                "Credited for modified addon work and the Spring-hosted addon link currently shown by the installed mods.",
                SPRING_DISCORD, Theme.SUCCESS);
    }

    private int drawCreditBlock(FontRenderer fr, int left, int right, int y, String name, String detail,
                                String url, int accent) {
        int width = right - left;
        Gui.drawRect(left, y, right, y + 1, Theme.GOLD_DARK);
        Gui.drawRect(left, y + 10, left + 5, y + 64, accent);
        fr.drawString(name, left + 12, y + 12, Theme.TEXT_LIGHT);
        fr.drawSplitString(detail, left + 12, y + 28, width - 20, Theme.TEXT_MUTED);
        fr.drawString(url, left + 12, y + 52, Theme.GOLD);
        return y + 68;
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {
        }

        GuiScreen.setClipboardString(url);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
