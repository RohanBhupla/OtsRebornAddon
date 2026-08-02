package net.rebornaddon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.tabs.HubTab;
import net.rebornaddon.gui.tabs.InfoTab;
import net.rebornaddon.gui.tabs.RankedTab;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiHub extends GuiScreen {
    private static final int MAX_PANEL_WIDTH = 560;
    private static final int MAX_PANEL_HEIGHT = 332;
    private static final int MARGIN = 14;
    private static final int HEADER_HEIGHT = 52;
    private static final int BTN_TAB_BASE = 10;

    private final List<HubTab> tabs;
    private int activeTabIndex;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public GuiHub() {
        tabs = new ArrayList<HubTab>();
        activeTabIndex = 0;
        tabs.add(InfoTab.main());
        tabs.add(InfoTab.quests());
        tabs.add(InfoTab.shop());
        tabs.add(InfoTab.village());
        tabs.add(new RankedTab());
        tabs.add(InfoTab.lore());
        tabs.add(InfoTab.events());
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 16);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonList.clear();

        int tabCount = tabs.size();
        int gap = 4;
        int tabWidth = Math.max(50, (panelWidth - MARGIN * 2 - gap * (tabCount - 1)) / tabCount);
        int x = panelLeft + MARGIN;
        int y = panelTop + 28;

        for (int i = 0; i < tabCount; i++) {
            HubTab tab = tabs.get(i);
            boolean active = i == activeTabIndex;
            int baseColor = active ? tab.getTabColorActive() : Theme.TAB_INACTIVE_BG;
            int hoverColor = active ? tab.getTabColorActive() : tab.getTabColorHover();
            buttonList.add(new ThemedButton(BTN_TAB_BASE + i, x, y, tabWidth, 20, tab.getTabName(),
                    baseColor, hoverColor, Theme.BUTTON_TEXT));
            x += tabWidth + gap;
        }

        getActiveTab().buildButtons(buttonList, contentLeft(), contentTop(), contentWidth(), contentHeight());
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= BTN_TAB_BASE && button.id < BTN_TAB_BASE + tabs.size()) {
            activeTabIndex = button.id - BTN_TAB_BASE;
            rebuildButtons();
            return;
        }

        if (getActiveTab().handleButtonClick(button.id)) {
            rebuildButtons();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        getActiveTab().onTick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel();
        getActiveTab().drawContent(this, contentLeft(), contentTop(), contentWidth(), contentHeight(), mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;

        drawRect(panelLeft - 6, panelTop - 6, right + 6, bottom + 6, 0x99000000);
        drawRect(panelLeft, panelTop, right, bottom, Theme.GOLD_DARK);
        drawRect(panelLeft + 2, panelTop + 2, right - 2, bottom - 2, Theme.PANEL_BG);
        drawRect(panelLeft + 4, panelTop + 4, right - 4, panelTop + HEADER_HEIGHT, Theme.PANEL_BG_LIGHT);
        drawRect(panelLeft + 4, panelTop + HEADER_HEIGHT, right - 4, panelTop + HEADER_HEIGHT + 1, Theme.GOLD);
        drawRect(contentLeft() - 1, contentTop() - 1, contentLeft() + contentWidth() + 1,
                contentTop() + contentHeight() + 1, Theme.GOLD_DARK);
        drawRect(contentLeft(), contentTop(), contentLeft() + contentWidth(),
                contentTop() + contentHeight(), Theme.PARCHMENT_DARK);

        fr.drawString("Otutsuki Reborn", panelLeft + MARGIN, panelTop + 10, Theme.TEXT_LIGHT);
        String tabTitle = getActiveTab().getTabName();
        fr.drawString(tabTitle, right - MARGIN - fr.getStringWidth(tabTitle), panelTop + 10, Theme.TEXT_MUTED);
    }

    private HubTab getActiveTab() {
        return tabs.get(activeTabIndex);
    }

    private int contentLeft() {
        return panelLeft + MARGIN;
    }

    private int contentTop() {
        return panelTop + HEADER_HEIGHT + 10;
    }

    private int contentWidth() {
        return panelWidth - MARGIN * 2;
    }

    private int contentHeight() {
        return panelHeight - HEADER_HEIGHT - MARGIN - 10;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
