package net.rebornaddon.content.client;

import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.tabs.MailTab;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;

import java.io.IOException;

public final class GuiMailbox extends RebornScaledGuiScreen {
    private static final int MAX_PANEL_WIDTH = 680;
    private static final int MAX_PANEL_HEIGHT = 390;
    private static final int HEADER_HEIGHT = 42;

    private final MailTab mail = new MailTab();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.max(330, Math.min(MAX_PANEL_WIDTH, width - 20));
        panelHeight = Math.max(250, Math.min(MAX_PANEL_HEIGHT, height - 20));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonList.clear();
        mail.buildButtons(buttonList, contentLeft(), contentTop(), contentWidth(), contentHeight());
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (mail.handleButtonClick(button.id)) {
            rebuildButtons();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        mail.handleMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        mail.handleKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        mail.onTick();
        if (mail.consumeButtonRefresh()) {
            rebuildButtons();
        }
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(left, top, panelWidth, panelHeight, Theme.COPPER);
        GuiChrome.header(left + 3, top + 5, panelWidth - 6, HEADER_HEIGHT - 7, Theme.COPPER);
        fontRenderer.drawString(
                ClientLocalization.format("gui.rebornaddon.mail.title", "Village Mailbox"),
                left + 15, top + 15, Theme.TEXT_LIGHT);
        fontRenderer.drawString(
                ClientLocalization.format("gui.rebornaddon.mail.subtitle", "Village correspondence and deliveries"),
                left + 15, top + 27, Theme.TEXT_MUTED);
        mail.drawContent(this, contentLeft(), contentTop(), contentWidth(), contentHeight(), mouseX, mouseY);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private int contentLeft() {
        return left + 12;
    }

    private int contentTop() {
        return top + HEADER_HEIGHT + 7;
    }

    private int contentWidth() {
        return panelWidth - 24;
    }

    private int contentHeight() {
        return panelHeight - HEADER_HEIGHT - 19;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
