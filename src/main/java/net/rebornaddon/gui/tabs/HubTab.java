package net.rebornaddon.gui.tabs;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;

/**
 * A single tab's content within the hub GUI. The hub owns the tab bar and the
 * outer panel frame; each tab only worries about its own content area and buttons.
 *
 * Button IDs: each tab should use its own reserved ID range to avoid clashing
 * with other tabs or the hub's own tab-switch buttons (which use 0-9).
 * Ranked uses 100-199.
 */
public interface HubTab {

    /** Short label shown on the tab button itself, e.g. "Ranked". */
    String getTabName();

    /**
     * Called whenever the hub (re)builds its button list - e.g. on open, on tab
     * switch, or after an action that changes what should be shown. Add this
     * tab's buttons to the provided list using this tab's reserved ID range.
     */
    void buildButtons(List<GuiButton> buttonList, int contentLeft, int contentTop, int contentWidth, int contentHeight);

    /** Draw any non-button content: labels, stats, dividers, etc. */
    void drawContent(GuiScreen screen, int contentLeft, int contentTop, int contentWidth, int contentHeight,
                      int mouseX, int mouseY);

    /**
     * Handle a click on one of this tab's own buttons (ID is in this tab's reserved range).
     * Return true if this tab handled it.
     */
    boolean handleButtonClick(int buttonId);

    /** Called every client tick while this tab is active, for polling live data. */
    default void onTick() {}

    /** This tab's own accent color for its tab-bar button when active. Defaults to
     *  the standard purple; override for tabs that want their own identity (Ranked
     *  uses red). */
    default int getTabColorActive() {
        return net.rebornaddon.gui.theme.Theme.PURPLE_DEEP;
    }

    default int getTabColorHover() {
        return net.rebornaddon.gui.theme.Theme.PURPLE_MID;
    }
}
