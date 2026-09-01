package net.rebornaddon.discord.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;

import java.io.IOException;
import java.util.List;

public final class GuiDiscordLinkCode extends RebornScaledGuiScreen {
    private static final int COPY_COMMAND = 1;
    private static final int DONE = 2;
    private static final int PANEL_MAX_WIDTH = 470;
    private static final int PANEL_HEIGHT = 248;
    private static final int MARGIN = 16;

    private final String code;
    private final String command;
    private final String instruction;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public GuiDiscordLinkCode(String code, String command, String instruction) {
        this.code = clean(code, 16);
        this.command = normalizeCommand(command);
        this.instruction = clean(instruction, 240);
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.min(PANEL_MAX_WIDTH, width - 20);
        panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int gap = 8;
        int buttonY = panelY + panelHeight - 38;
        int buttonWidth = (panelWidth - MARGIN * 2 - gap) / 2;
        buttonList.clear();
        buttonList.add(new ThemedButton(COPY_COMMAND, panelX + MARGIN, buttonY, buttonWidth, 22,
                ClientLocalization.format("gui.rebornaddon.discord.copy", "Copy Code"),
                Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT));
        buttonList.add(new ThemedButton(DONE, panelX + MARGIN + buttonWidth + gap, buttonY,
                buttonWidth, 22, ClientLocalization.format("gui.rebornaddon.discord.done", "Done"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == COPY_COMMAND) {
            GuiScreen.setClipboardString(code);
            button.displayString = ClientLocalization.format("gui.rebornaddon.discord.copied", "Copied");
        } else if (button.id == DONE) {
            mc.displayGuiScreen(null);
        }
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
        int bodyTop = panelY + 60;
        int bodyHeight = panelHeight - 113;
        int center = panelX + panelWidth / 2;

        GuiChrome.frame(panelX, panelY, panelWidth, panelHeight, Theme.TEAL);
        GuiChrome.header(panelX + 2, panelY + 5, panelWidth - 4, 47, Theme.TEAL);
        font.drawString(ClientLocalization.format("gui.rebornaddon.discord.code_title", "Discord Link Code"),
                panelX + MARGIN, panelY + 15, Theme.TEXT_LIGHT);
        String expires = ClientLocalization.format("gui.rebornaddon.discord.private", "Keep this code private");
        font.drawString(expires, right - MARGIN - font.getStringWidth(expires),
                panelY + 15, Theme.TEXT_MUTED);
        GuiChrome.rule(panelX + MARGIN, right - MARGIN, panelY + 42, Theme.TEAL);

        GuiChrome.section(panelX + MARGIN, bodyTop, panelWidth - MARGIN * 2, bodyHeight, Theme.TEAL);
        drawCentered(font, ClientLocalization.format("gui.rebornaddon.discord.your_code", "Your code"),
                center, bodyTop + 13, panelWidth - MARGIN * 2 - 20, Theme.TEXT_MUTED);
        drawCentered(font, code, center, bodyTop + 31,
                panelWidth - MARGIN * 2 - 20, Theme.GOLD);
        GuiChrome.rule(panelX + MARGIN + 12, right - MARGIN - 11, bodyTop + 48, Theme.TEAL);
        drawCentered(font, ClientLocalization.format("gui.rebornaddon.discord.use_command",
                        "Use this command in Discord"), center, bodyTop + 60,
                panelWidth - MARGIN * 2 - 20, Theme.TEXT_MUTED);
        drawWrappedCentered(font, fullCommand(), center, bodyTop + 76,
                panelWidth - MARGIN * 2 - 20, 2, Theme.TEXT_LIGHT);
        if (!instruction.isEmpty()) {
            drawWrappedCentered(font, instruction, center, bodyTop + 101,
                    panelWidth - MARGIN * 2 - 24, 2, Theme.TEXT_MUTED);
        }
    }

    private String fullCommand() {
        return code.isEmpty() ? command : command + " " + code;
    }

    private static void drawCentered(FontRenderer font, String value, int center,
                                     int y, int width, int color) {
        String text = font.trimStringToWidth(value, Math.max(20, width));
        font.drawString(text, center - font.getStringWidth(text) / 2, y, color);
    }

    private static void drawWrappedCentered(FontRenderer font, String value, int center,
                                            int y, int width, int maximumLines, int color) {
        List<String> lines = font.listFormattedStringToWidth(value, Math.max(40, width));
        int count = Math.min(maximumLines, lines.size());
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            font.drawString(line, center - font.getStringWidth(line) / 2, y + i * 10, color);
        }
    }

    private static String normalizeCommand(String value) {
        String clean = clean(value, 40);
        if (clean.isEmpty()) {
            return "/link";
        }
        return clean.charAt(0) == '/' ? clean : "/" + clean;
    }

    private static String clean(String value, int maximumLength) {
        String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength).trim();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
