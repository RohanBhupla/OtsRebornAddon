package net.rebornaddon.discord.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.List;

public final class GuiDiscordLinkPrompt extends RebornScaledGuiScreen {
    private static final int LINK = 1;
    private static final int SKIP = 2;
    private static final int PANEL_MAX_WIDTH = 450;
    private static final int PANEL_HEIGHT = 254;
    private static final int MARGIN = 16;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private final boolean linked;

    public GuiDiscordLinkPrompt(boolean linked) {
        this.linked = linked;
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.min(PANEL_MAX_WIDTH, width - 20);
        panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int gap = 8;
        int buttonY = panelY + panelHeight - 34;
        int buttonWidth = (panelWidth - MARGIN * 2 - gap) / 2;
        buttonList.clear();
        buttonList.add(new ThemedButton(LINK, panelX + MARGIN, buttonY, buttonWidth, 22,
                ClientLocalization.format(linked ? "gui.rebornaddon.discord.unlink" : "gui.rebornaddon.discord.link",
                        linked ? "Unlink Discord" : "Link Discord"),
                linked ? Theme.RANKED_RED_DARK : Theme.TEAL_DARK,
                linked ? Theme.RANKED_RED : Theme.TEAL, Theme.BUTTON_TEXT));
        buttonList.add(new ThemedButton(SKIP, panelX + MARGIN + buttonWidth + gap, buttonY,
                buttonWidth, 22, ClientLocalization.format(linked ? "gui.rebornaddon.discord.done" : "gui.rebornaddon.discord.skip",
                        linked ? "Done" : "Skip"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id != LINK && button.id != SKIP) {
            return;
        }
        if (button.id == LINK) {
            RebornAddonNetwork.sendDiscordLinkPromptChoice(linked
                    ? net.rebornaddon.village.network.DiscordLinkPromptChoiceMessage.UNLINK
                    : net.rebornaddon.village.network.DiscordLinkPromptChoiceMessage.LINK);
        } else if (!linked) {
            RebornAddonNetwork.sendDiscordLinkPromptChoice(
                    net.rebornaddon.village.network.DiscordLinkPromptChoiceMessage.SKIP);
        }
        mc.displayGuiScreen(null);
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel();
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawPanel() {
        FontRenderer font = fontRenderer;
        int right = panelX + panelWidth;
        int bodyTop = panelY + 50;
        int buttonTop = panelY + panelHeight - 34;
        int bodyHeight = buttonTop - bodyTop - 8;

        GuiChrome.frame(panelX, panelY, panelWidth, panelHeight, Theme.TEAL);
        GuiChrome.header(panelX + 2, panelY + 5, panelWidth - 4, 39, Theme.TEAL);
        font.drawString(ClientLocalization.format(linked ? "gui.rebornaddon.discord.manage_title"
                        : "gui.rebornaddon.discord.prompt_title", linked ? "Discord Link" : "Link Discord?"),
                panelX + MARGIN, panelY + 13, Theme.TEXT_LIGHT);
        String optional = ClientLocalization.format("gui.rebornaddon.discord.optional", "Optional");
        font.drawString(optional, right - MARGIN - font.getStringWidth(optional),
                panelY + 13, Theme.TEXT_MUTED);
        GuiChrome.rule(panelX + MARGIN, right - MARGIN, panelY + 36, Theme.TEAL);

        GuiChrome.section(panelX + MARGIN, bodyTop, panelWidth - MARGIN * 2, bodyHeight, Theme.TEAL);
        boolean compact = panelWidth < 340;
        if (!compact) {
            drawLinkMark(panelX + MARGIN + 14, bodyTop + 13);
        }
        int textX = compact ? panelX + MARGIN + 10 : panelX + MARGIN + 58;
        int textWidth = compact ? panelWidth - MARGIN * 2 - 20 : panelWidth - MARGIN * 2 - 72;
        font.drawString(ClientLocalization.format(linked ? "gui.rebornaddon.discord.linked_heading"
                        : "gui.rebornaddon.discord.prompt_heading",
                        linked ? "Account linked" : "Connect your account"), textX, bodyTop + 8, Theme.TEAL_LIGHT);
        drawWrapped(font, ClientLocalization.format(linked ? "gui.rebornaddon.discord.linked_body"
                        : "gui.rebornaddon.discord.prompt_body",
                        linked ? "Your village roles are synchronized with Discord."
                                : "Receive your village role automatically in Discord."),
                textX, bodyTop + 22, textWidth, 2, Theme.TEXT_LIGHT);

        int privacyX = panelX + MARGIN + 10;
        int privacyWidth = panelWidth - MARGIN * 2 - 20;
        font.drawString(ClientLocalization.format("gui.rebornaddon.discord.privacy_heading",
                        "What linking shares"), privacyX, bodyTop + 47, Theme.GOLD);
        drawWrapped(font, ClientLocalization.format("gui.rebornaddon.discord.privacy_body",
                        "Only your Minecraft UUID/name and Discord ID are saved for role syncing. "
                                + "Passwords, tokens, email, and messages are never accessed or stored."),
                privacyX, bodyTop + 61, privacyWidth, 5, Theme.TEXT_LIGHT);
        drawWrapped(font, ClientLocalization.format(linked ? "gui.rebornaddon.discord.unlink_notice"
                        : "gui.rebornaddon.discord.prompt_later",
                        linked ? "Unlinking removes the saved account association and synchronized Discord roles."
                                : "You can skip and reopen this screen later."),
                privacyX, bodyTop + 111, privacyWidth, 2, Theme.TEXT_MUTED);
    }

    private void drawLinkMark(int x, int y) {
        drawRect(x, y + 3, x + 20, y + 6, Theme.TEAL_LIGHT);
        drawRect(x, y + 14, x + 20, y + 17, Theme.TEAL_LIGHT);
        drawRect(x, y + 3, x + 3, y + 17, Theme.TEAL_LIGHT);
        drawRect(x + 17, y + 3, x + 20, y + 17, Theme.TEAL_LIGHT);
        drawRect(x + 12, y + 9, x + 32, y + 12, Theme.GOLD);
        drawRect(x + 12, y + 20, x + 32, y + 23, Theme.GOLD);
        drawRect(x + 12, y + 9, x + 15, y + 23, Theme.GOLD);
        drawRect(x + 29, y + 9, x + 32, y + 23, Theme.GOLD);
    }

    private static void drawWrapped(FontRenderer font, String text, int x, int y,
                                    int width, int maximumLines, int color) {
        List<String> lines = font.listFormattedStringToWidth(text, Math.max(40, width));
        int count = Math.min(maximumLines, lines.size());
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == count - 1 && lines.size() > count) {
                line = trim(font, line + "...", width);
            }
            font.drawString(line, x, y + i * 10, color);
        }
    }

    private static String trim(FontRenderer font, String value, int width) {
        if (font.getStringWidth(value) <= width) {
            return value;
        }
        return font.trimStringToWidth(value, Math.max(8, width - font.getStringWidth("..."))) + "...";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
