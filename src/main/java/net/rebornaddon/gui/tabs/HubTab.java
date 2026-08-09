package net.rebornaddon.gui.tabs;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;


public interface HubTab {


    String getTabName();

    void buildButtons(List<GuiButton> buttonList, int contentLeft, int contentTop, int contentWidth, int contentHeight);

    void drawContent(GuiScreen screen, int contentLeft, int contentTop, int contentWidth, int contentHeight,
                      int mouseX, int mouseY);


    boolean handleButtonClick(int buttonId);

    default void onTick() {}

    default boolean consumeButtonRefresh() {
        return false;
    }

    /** Only tabs that actually use a text field need to override these - GuiHub
     *  forwards raw keyboard/mouse events here in addition to its normal button
     *  handling, since GuiTextField isn't part of the buttonList mechanism. */
    default void handleKeyTyped(char typedChar, int keyCode) {}
    default void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {}

    default int getTabColorActive() {
        return net.rebornaddon.gui.theme.Theme.PURPLE_DEEP;
    }

    default int getTabColorHover() {
        return net.rebornaddon.gui.theme.Theme.PURPLE_MID;
    }
}
