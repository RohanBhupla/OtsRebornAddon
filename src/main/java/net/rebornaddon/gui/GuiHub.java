package net.rebornaddon.gui;

import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.tabs.HubTab;
import net.rebornaddon.gui.tabs.DashboardTab;
import net.rebornaddon.gui.tabs.QuestsTab;
import net.rebornaddon.gui.tabs.ExamsTab;
import net.rebornaddon.gui.tabs.PartyTab;
import net.rebornaddon.gui.tabs.RankedTab;
import net.rebornaddon.gui.tabs.StoreTab;
import net.rebornaddon.gui.tabs.TradeTab;
import net.rebornaddon.gui.tabs.VillageLeadershipTab;
import net.rebornaddon.gui.tabs.AdminTab;
import net.rebornaddon.gui.tabs.WorldEditorTab;
import net.rebornaddon.gui.tabs.ShinobiTab;
import net.rebornaddon.gui.tabs.MarketplaceTab;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.village.client.ClientVillageLeadershipData;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.jutsu.client.ClientAdminAccess;
import net.rebornaddon.trade.client.ClientTradeData;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiHub extends RebornScaledGuiScreen {
    private static final int MAX_PANEL_WIDTH = 720;
    private static final int MAX_PANEL_HEIGHT = 410;
    private static final int HEADER_HEIGHT = 46;
    private static final int NAV_BUTTON_BASE = 10;

    private final List<HubTab> baseTabs = new ArrayList<HubTab>();
    private final List<HubTab> tabs = new ArrayList<HubTab>();
    private final VillageLeadershipTab villageTab = new VillageLeadershipTab();
    private final AdminTab adminTab = new AdminTab();
    private final WorldEditorTab worldEditorTab = new WorldEditorTab();
    private int activeTabIndex;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int navigationWidth;
    private int leadershipRevision = -1;
    private int adminAccessRevision = -1;
    private final String requestedTab;

    public GuiHub() {
        this("");
    }

    public GuiHub(String requestedTab) {
        this.requestedTab = requestedTab == null ? "" : requestedTab;
        baseTabs.add(new DashboardTab());
        baseTabs.add(new QuestsTab());
        baseTabs.add(new ExamsTab());
        baseTabs.add(new ShinobiTab());
        baseTabs.add(new StoreTab());
        baseTabs.add(new TradeTab());
        baseTabs.add(new MarketplaceTab());
        baseTabs.add(new PartyTab());
        baseTabs.add(new RankedTab());
        tabs.addAll(baseTabs);
    }

    @Override
    protected int minimumLayoutWidth() {
        return MAX_PANEL_WIDTH + 12;
    }

    @Override
    protected int minimumLayoutHeight() {
        return MAX_PANEL_HEIGHT + 12;
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.max(260, Math.min(MAX_PANEL_WIDTH, width - 12));
        panelHeight = Math.max(210, Math.min(MAX_PANEL_HEIGHT, height - 12));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        navigationWidth = panelWidth >= 520 ? 116 : 92;
        RebornAddonNetwork.requestVillageLeadership(-1);
        RebornAddonNetwork.requestQuestSync(ClientQuestData.get().catalogVersion);
        RebornAddonNetwork.requestStoreCatalog();
        RebornAddonNetwork.requestAdminAccess();
        RebornAddonNetwork.requestTradeSync();
        syncConditionalTabs();
        selectRequestedTab();
        rebuildButtons();
    }

    private void selectRequestedTab() {
        if (requestedTab.isEmpty()) return;
        for (int i = 0; i < tabs.size(); i++) {
            HubTab tab = tabs.get(i);
            if (("quests".equals(requestedTab) && tab instanceof QuestsTab)
                    || ("exams".equals(requestedTab) && tab instanceof ExamsTab)
                    || ("store".equals(requestedTab) && tab instanceof StoreTab)
                    || ("trade".equals(requestedTab) && tab instanceof TradeTab)
                    || ("market".equals(requestedTab) && tab instanceof MarketplaceTab)
                    || ("world".equals(requestedTab) && tab instanceof WorldEditorTab)
                    || ("admin".equals(requestedTab) && tab instanceof AdminTab)) {
                activeTabIndex = i;
                return;
            }
        }
    }

    private void syncConditionalTabs() {
        leadershipRevision = ClientVillageLeadershipData.getRevision();
        adminAccessRevision = ClientAdminAccess.revision();
        String activeName = tabs.isEmpty() ? "" : getActiveTab().getTabName();
        tabs.clear();
        tabs.addAll(baseTabs);
        if (ClientVillageLeadershipData.get().access) {
            tabs.add(3, villageTab);
        }
        if (ClientAdminAccess.isOperator()) {
            tabs.add(adminTab);
            tabs.add(worldEditorTab);
        }
        activeTabIndex = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getTabName().equals(activeName)) {
                activeTabIndex = i;
                break;
            }
        }
    }

    private void rebuildButtons() {
        buttonList.clear();
        int available = panelHeight - HEADER_HEIGHT - 39;
        int gap = panelHeight >= 300 ? 5 : 2;
        int buttonHeight = Math.max(17, Math.min(25, (available - gap * (tabs.size() - 1)) / tabs.size()));
        int x = panelLeft + 10;
        int y = panelTop + HEADER_HEIGHT + 27;
        int width = navigationWidth - 18;

        for (int i = 0; i < tabs.size(); i++) {
            HubTab tab = tabs.get(i);
            boolean active = i == activeTabIndex;
            ThemedButton button = new ThemedButton(NAV_BUTTON_BASE + i, x, y, width, buttonHeight,
                    tab.getTabName(), active ? tab.getTabColorActive() : Theme.TAB_INACTIVE_BG,
                    tab.getTabColorHover(), Theme.BUTTON_TEXT);
            button.setSelected(active);
            button.setTextAlignment(ThemedButton.Alignment.LEFT);
            button.setTextInset(10);
            buttonList.add(button);
            y += buttonHeight + gap;
        }
        getActiveTab().buildButtons(buttonList, contentLeft(), contentTop(), contentWidth(), contentHeight());
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= NAV_BUTTON_BASE && button.id < NAV_BUTTON_BASE + tabs.size()) {
            activeTabIndex = button.id - NAV_BUTTON_BASE;
            rebuildButtons();
            return;
        }
        if (getActiveTab().handleButtonClick(button.id)) {
            rebuildButtons();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        getActiveTab().handleMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        getActiveTab().handleKeyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (leadershipRevision != ClientVillageLeadershipData.getRevision()
                || adminAccessRevision != ClientAdminAccess.revision()) {
            syncConditionalTabs();
            selectRequestedTab();
            rebuildButtons();
        }
        getActiveTab().onTick();
        if (getActiveTab().consumeButtonRefresh()) {
            rebuildButtons();
        }
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel();
        getActiveTab().drawContent(this, contentLeft(), contentTop(), contentWidth(), contentHeight(), mouseX, mouseY);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        int accent = getActiveTab().getTabColorActive();
        GuiChrome.frame(panelLeft, panelTop, panelWidth, panelHeight, accent);
        GuiChrome.header(panelLeft + 2, panelTop + 5, panelWidth - 4, HEADER_HEIGHT - 7, accent);
        drawRect(panelLeft + 7, panelTop + HEADER_HEIGHT,
                panelLeft + navigationWidth - 6, panelTop + panelHeight - 7, Theme.EDGE_DARK);
        drawRect(panelLeft + 8, panelTop + HEADER_HEIGHT + 1,
                panelLeft + navigationWidth - 7, panelTop + panelHeight - 8, Theme.PARCHMENT_DARK);
        drawRect(panelLeft + 8, panelTop + HEADER_HEIGHT + 1,
                panelLeft + navigationWidth - 7, panelTop + HEADER_HEIGHT + 5, Theme.PARCHMENT_LIGHT);
        drawRect(panelLeft + navigationWidth - 3, panelTop + HEADER_HEIGHT,
                panelLeft + navigationWidth - 2, panelTop + panelHeight - 8, accent);

        drawRect(contentLeft() - 8, contentTop() - 8,
                contentLeft() + contentWidth() + 8, contentTop() + contentHeight() + 8, Theme.EDGE_DARK);
        drawRect(contentLeft() - 7, contentTop() - 7,
                contentLeft() + contentWidth() + 7, contentTop() + contentHeight() + 7, Theme.BUTTON_BORDER);
        drawRect(contentLeft() - 6, contentTop() - 6,
                contentLeft() + contentWidth() + 6, contentTop() + contentHeight() + 6, Theme.PARCHMENT_DARK);
        drawRect(contentLeft() - 6, contentTop() - 6,
                contentLeft() + contentWidth() + 6, contentTop() - 2, Theme.PARCHMENT_LIGHT);
        drawRect(contentLeft() - 6, contentTop() - 6,
                contentLeft() - 2, contentTop() + contentHeight() + 6, accent);
        GuiChrome.rule(contentLeft(), contentLeft() + contentWidth(), contentTop() - 4, accent);

        String title = ClientLocalization.format("gui.rebornaddon.hub.title", "Otsutsuki Reborn");
        fontRenderer.drawString(title, panelLeft + 16, panelTop + 14, Theme.TEXT_LIGHT);
        fontRenderer.drawString(ClientLocalization.format("gui.rebornaddon.hub.subtitle", "Shinobi Operations"),
                panelLeft + 16, panelTop + 27, Theme.TEXT_MUTED);
        String section = getActiveTab().getTabName();
        fontRenderer.drawString(section, panelLeft + panelWidth - 16 - fontRenderer.getStringWidth(section),
                panelTop + 17, Theme.TEXT_MUTED);
        fontRenderer.drawString(ClientLocalization.format("gui.rebornaddon.hub.sections", "Sections"),
                panelLeft + 14, panelTop + HEADER_HEIGHT + 10, accent);
        drawRect(panelLeft + 14, panelTop + HEADER_HEIGHT + 22,
                panelLeft + navigationWidth - 14, panelTop + HEADER_HEIGHT + 23, Theme.BUTTON_BORDER);
        drawRect(panelLeft + 14, panelTop + HEADER_HEIGHT + 22,
                panelLeft + 39, panelTop + HEADER_HEIGHT + 23, accent);
    }

    private HubTab getActiveTab() {
        if (tabs.isEmpty()) {
            tabs.addAll(baseTabs);
        }
        activeTabIndex = Math.max(0, Math.min(activeTabIndex, tabs.size() - 1));
        return tabs.get(activeTabIndex);
    }

    private int contentLeft() {
        return panelLeft + navigationWidth + 10;
    }

    private int contentTop() {
        return panelTop + HEADER_HEIGHT + 8;
    }

    private int contentWidth() {
        return panelWidth - navigationWidth - 20;
    }

    private int contentHeight() {
        return panelHeight - HEADER_HEIGHT - 18;
    }

    public void drawItemTooltip(ItemStack stack, int mouseX, int mouseY) {
        if (stack != null && !stack.isEmpty()) {
            renderToolTip(stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
