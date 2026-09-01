package net.rebornaddon.mode.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.mode.ModeConfigurationService;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GuiModeCombinationEditor extends RebornScaledGuiScreen {
    private static final int ROW_BASE = 1000;
    private static final int PREVIOUS = 2000;
    private static final int NEXT = 2001;
    private static final int SAVE = 2002;
    private static final int CANCEL = 2003;

    private final GuiScreen parent;
    private final String modeId;
    private final String modeName;
    private final List<String> ids;
    private final List<String> names;
    private final Set<String> blocked = new LinkedHashSet<String>();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int rowsPerPage;

    public GuiModeCombinationEditor(GuiScreen parent, String modeId, String modeName,
                                    List<String> ids, List<String> names, String current) {
        this.parent = parent;
        this.modeId = modeId;
        this.modeName = modeName;
        this.ids = new ArrayList<String>(ids);
        this.names = new ArrayList<String>(names);
        if (current != null) for (String id : current.split(",")) {
            String clean = id.trim();
            if (!clean.isEmpty() && !clean.equals(modeId)) blocked.add(clean);
        }
    }

    @Override
    public void initGui() {
        panelWidth = Math.max(320, Math.min(540, width - 24));
        panelHeight = Math.max(240, Math.min(390, height - 24));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rowsPerPage = Math.max(5, (panelHeight - 105) / 24);
        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        int pages = Math.max(1, (ids.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(ids.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            boolean selected = blocked.contains(ids.get(i));
            ThemedButton row = button(ROW_BASE + i - start, left + 14,
                    top + 62 + (i - start) * 24, panelWidth - 28, 21,
                    (selected ? "Blocked: " : "Allowed: ") + names.get(i),
                    selected ? Theme.RANKED_RED_DARK : Theme.TAB_INACTIVE_BG,
                    selected ? Theme.RANKED_RED : Theme.TEAL);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setSelected(selected);
            buttonList.add(row);
        }
        int y = top + panelHeight - 29;
        ThemedButton previous = button(PREVIOUS, left + 14, y, 30, 20, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttonList.add(previous);
        ThemedButton next = button(NEXT, left + 48, y, 30, 20, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < pages;
        buttonList.add(next);
        int actionWidth = Math.max(70, (panelWidth - 112) / 2);
        buttonList.add(button(SAVE, left + 84, y, actionWidth, 20,
                text("gui.rebornaddon.mode.save_combinations", "Save Blocked Modes"),
                Theme.TEAL_DARK, Theme.TEAL));
        buttonList.add(button(CANCEL, left + 88 + actionWidth, y,
                left + panelWidth - 14 - (left + 88 + actionWidth), 20,
                text("gui.rebornaddon.world.cancel", "Cancel"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == PREVIOUS || button.id == NEXT) {
            page += button.id == PREVIOUS ? -1 : 1;
            rebuild();
        } else if (button.id >= ROW_BASE && button.id < ROW_BASE + rowsPerPage) {
            int index = page * rowsPerPage + button.id - ROW_BASE;
            if (index >= 0 && index < ids.size()) {
                String id = ids.get(index);
                if (!blocked.remove(id)) blocked.add(id);
                rebuild();
            }
        } else if (button.id == SAVE) {
            StringBuilder value = new StringBuilder();
            for (String id : blocked) {
                if (value.length() > 0) value.append(',');
                value.append(id);
            }
            RebornAddonNetwork.sendJutsuAdminAction(ModeConfigurationService.UPDATE,
                    modeId, "incompatible-modes", value.toString());
            mc.displayGuiScreen(parent);
        } else if (button.id == CANCEL) mc.displayGuiScreen(parent);
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(left, top, panelWidth, panelHeight, Theme.TEAL);
        GuiChrome.header(left + 3, top + 5, panelWidth - 6, 39, Theme.TEAL);
        fontRenderer.drawString(text("gui.rebornaddon.mode.combinations", "Blocked Mode Combinations"),
                left + 15, top + 15, Theme.TEXT_LIGHT);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(modeName, panelWidth - 28),
                left + 15, top + 46, Theme.TEXT_MUTED);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private static String text(String key, String fallback, Object... arguments) {
        return ClientLocalization.format(key, fallback, arguments);
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int base, int hover) {
        return new ThemedButton(id, x, y, Math.max(20, width), height, label,
                base, hover, Theme.BUTTON_TEXT);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
