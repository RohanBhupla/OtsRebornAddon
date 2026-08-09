package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.theme.Theme;

import java.util.List;

public class InfoTab implements HubTab {
    private final String name;
    private final String title;
    private final int accent;
    private final String[][] cards;

    private InfoTab(String name, String title, int accent, String[][] cards) {
        this.name = name;
        this.title = title;
        this.accent = accent;
        this.cards = cards;
    }

    public static InfoTab main() {
        return new InfoTab("Main", "Otutsuki Reborn", Theme.GOLD, new String[][]{
                {"Profile", "Ninja data", "Village status", "Rank and standing"},
                {"Progress", "CustomNPC missions", "Live objectives", "NPC rewards"},
                {"Combat", "Ranked queue", "Match record", "Season placement"},
                {"Server", "Dedicated play", "Mohist 1.12.2", "Client-safe screens"}
        });
    }

    public static InfoTab shop() {
        return new InfoTab("Shop", "Supply Shop", Theme.COPPER, new String[][]{
                {"Cosmetics", "Masks", "Robes", "Village outfits"},
                {"Armor", "Village armor", "Clan sets", "Event gear"},
                {"Building", "Blocks", "Decor", "Structure pieces"},
                {"Cart", "Review", "Confirm", "Server validates"}
        });
    }

    public static InfoTab village() {
        return new InfoTab("Village Panel", "Village Panel", Theme.NEUTRAL, new String[][]{
                {"Leaf", "Members", "Standing", "Village tasks"},
                {"Sand", "Members", "Standing", "Village tasks"},
                {"Mist", "Members", "Standing", "Village tasks"},
                {"Cloud", "Members", "Standing", "Village tasks"},
                {"Stone", "Members", "Standing", "Village tasks"}
        });
    }

    public static InfoTab lore() {
        return new InfoTab("Lore", "Lore", Theme.PURPLE_LIGHT, new String[][]{
                {"Chapter I", "Village roots", "Character hooks", "Quest ties"},
                {"Chapter II", "Clan pressure", "New conflicts", "Progress gates"},
                {"Chapter III", "Arc finale", "Server choices", "Rewards"}
        });
    }

    public static InfoTab events() {
        return new InfoTab("Events", "Events", Theme.RANKED_RED_HOVER, new String[][]{
                {"Active", "Live activities", "Timers", "Claims"},
                {"Upcoming", "Scheduled arcs", "Preparation", "Signups"},
                {"Raids", "Boss fights", "Group rewards", "Cooldowns"},
                {"Ranked", "Season blocks", "Match windows", "Final standings"}
        });
    }

    @Override
    public String getTabName() {
        return name;
    }

    @Override
    public int getTabColorActive() {
        return accent;
    }

    @Override
    public int getTabColorHover() {
        return Theme.TAB_HOVER_BG;
    }

    @Override
    public void buildButtons(List<GuiButton> buttonList, int contentLeft, int contentTop, int contentWidth, int contentHeight) {
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height, int mouseX, int mouseY) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        Gui.drawRect(left, top, left + width, top + 26, Theme.PARCHMENT_DARK);
        Gui.drawRect(left, top + 25, left + width, top + 26, Theme.GOLD_DARK);
        fr.drawString(title, left + 10, top + 9, Theme.TEXT_LIGHT);

        int y = top + 38;
        int gap = 8;
        int colGap = 10;
        int cardWidth = (width - colGap) / 2;
        int cardHeight = Math.max(54, Math.min(68, (height - 46) / Math.max(1, (cards.length + 1) / 2) - gap));

        for (int i = 0; i < cards.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = left + col * (cardWidth + colGap);
            int cy = y + row * (cardHeight + gap);
            drawCard(fr, cards[i], x, cy, cardWidth, cardHeight, i);
        }
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        return false;
    }

    private void drawCard(FontRenderer fr, String[] lines, int x, int y, int width, int height, int index) {
        Gui.drawRect(x, y, x + width, y + height, Theme.BUTTON_BORDER);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, Theme.PARCHMENT);

        int iconSize = 28;
        int iconX = x + 8;
        int iconY = y + 8;
        Gui.drawRect(iconX, iconY, iconX + iconSize, iconY + iconSize, accent);
        Gui.drawRect(iconX + 2, iconY + 2, iconX + iconSize - 2, iconY + iconSize - 2, Theme.PARCHMENT_DARK);

        String mark = lines[0].substring(0, 1);
        fr.drawString(mark, iconX + 11, iconY + 10, accent);

        int textX = iconX + iconSize + 8;
        int maxWidth = width - (textX - x) - 8;
        fr.drawString(trim(fr, lines[0], maxWidth), textX, y + 8, Theme.TEXT_LIGHT);

        int textY = y + 22;
        for (int i = 1; i < lines.length && textY < y + height - 8; i++) {
            fr.drawString(trim(fr, lines[i], maxWidth), textX, textY, Theme.TEXT_MUTED);
            textY += 10;
        }
    }

    private static String trim(FontRenderer fr, String value, int width) {
        if (fr.getStringWidth(value) <= width) {
            return value;
        }

        return fr.trimStringToWidth(value, Math.max(8, width - fr.getStringWidth("..."))) + "...";
    }
}
