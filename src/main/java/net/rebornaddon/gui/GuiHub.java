package net.rebornaddon.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.tabs.HubTab;
import net.rebornaddon.gui.tabs.RankedTab;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The main hub screen. Owns the outer panel frame and the tab bar; each tab
 * (implementing HubTab) only has to worry about its own content. Currently only
 * the Ranked tab exists - add new HubTab implementations to the `tabs` list
 * below as they're built.
 */
public class GuiHub extends GuiScreen {

    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 300;
    private static final int BORDER_THICKNESS = 10;

    // Tab-switch buttons use IDs 0-9, reserved separately from each tab's own range.
    private static final int BTN_TAB_BASE = 0;

    private final List<HubTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;

    private int panelLeft, panelTop;

    public GuiHub() {
        tabs.add(new RankedTab());
        // Future tabs get added here, e.g.: tabs.add(new SomeOtherTab());
    }

    @Override
    public void initGui() {
        super.initGui();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.buttonList.clear();

        int tabW = 90;
        int tabH = 20;
        int tabX = panelLeft + 10;
        int tabY = panelTop + 10;
        int gap = 4;

        for (int i = 0; i < tabs.size(); i++) {
            HubTab tab = tabs.get(i);
            boolean active = i == activeTabIndex;
            int bg = active ? tab.getTabColorActive() : Theme.TAB_INACTIVE_BG;
            int hover = active ? tab.getTabColorActive() : tab.getTabColorHover();
            this.buttonList.add(new ThemedButton(BTN_TAB_BASE + i, tabX + i * (tabW + gap), tabY, tabW, tabH,
                    tab.getTabName(), bg, hover, Theme.TEXT_ON_PURPLE));
        }

        int contentLeft = panelLeft + 14;
        int contentTop = panelTop + 44;
        int contentWidth = PANEL_WIDTH - 28;
        int contentHeight = PANEL_HEIGHT - 56;

        tabs.get(activeTabIndex).buildButtons(this.buttonList, contentLeft, contentTop, contentWidth, contentHeight);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= BTN_TAB_BASE && button.id < BTN_TAB_BASE + tabs.size()) {
            activeTabIndex = button.id - BTN_TAB_BASE;
            rebuildButtons();
            return;
        }

        HubTab active = tabs.get(activeTabIndex);
        if (active.handleButtonClick(button.id)) {
            rebuildButtons();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tabs.get(activeTabIndex).onTick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel();

        int contentLeft = panelLeft + 14;
        int contentTop = panelTop + 44;
        int contentWidth = PANEL_WIDTH - 28;
        int contentHeight = PANEL_HEIGHT - 56;

        tabs.get(activeTabIndex).drawContent(this, contentLeft, contentTop, contentWidth, contentHeight, mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        // Deep purple outer border - thicker now
        drawRect(panelLeft - BORDER_THICKNESS, panelTop - BORDER_THICKNESS,
                panelLeft + PANEL_WIDTH + BORDER_THICKNESS, panelTop + PANEL_HEIGHT + BORDER_THICKNESS,
                Theme.PURPLE_DEEP);

        // Space-grey inner panel
        drawRect(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, Theme.PANEL_BG);

        // Thin divider under the tab bar
        drawRect(panelLeft + 10, panelTop + 38, panelLeft + PANEL_WIDTH - 10, panelTop + 39, Theme.PURPLE_LIGHT);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
