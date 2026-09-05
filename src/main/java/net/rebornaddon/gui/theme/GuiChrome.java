package net.rebornaddon.gui.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GuiChrome {
    private static final Map<String, ResourceLocation> SKIN_CACHE = new HashMap<String, ResourceLocation>();
    private static NetHandlerPlayClient cachedConnection;

    private GuiChrome() {
    }

    public static void frame(int left, int top, int width, int height, int accent) {
        int right = left + width;
        int bottom = top + height;
        Gui.drawRect(left - 5, top - 4, right + 7, bottom + 8, Theme.SHADOW);
        Gui.drawRect(left - 2, top - 2, right + 2, bottom + 2, Theme.EDGE_DARK);
        Gui.drawRect(left - 1, top - 1, right + 1, bottom + 1, Theme.BUTTON_BORDER);
        Gui.drawRect(left, top, right, bottom, Theme.PANEL_BG_DEEP);
        Gui.drawRect(left + 2, top + 2, right - 2, bottom - 2, Theme.PANEL_BG);
        Gui.drawRect(left + 2, top + 2, right - 2, top + 5, accent);
        Gui.drawRect(left + 2, top + 5, right - 2, top + 6, Theme.EDGE_LIGHT);
        Gui.drawRect(left + 7, top + 9, left + 36, top + 10, accent);
        Gui.drawRect(left + 7, top + 10, left + 20, top + 11, Theme.GOLD_DARK);
        Gui.drawRect(right - 36, bottom - 10, right - 7, bottom - 9, accent);
        Gui.drawRect(right - 20, bottom - 9, right - 7, bottom - 8, Theme.GOLD_DARK);
        corner(left + 5, top + 7, accent, false, false);
        corner(right - 6, top + 7, accent, true, false);
        corner(left + 5, bottom - 8, accent, false, true);
        corner(right - 6, bottom - 8, accent, true, true);
    }

    public static void header(int left, int top, int width, int height, int accent) {
        int right = left + width;
        int bottom = top + height;
        Gui.drawRect(left, top, right, bottom, Theme.PANEL_BG_RAISED);
        Gui.drawRect(left, top, right, top + 2, Theme.PANEL_BG_LIGHT);
        Gui.drawRect(left, bottom - 2, right, bottom, Theme.EDGE_DARK);
        Gui.drawRect(left, bottom - 2, right, bottom - 1, Theme.BUTTON_BORDER);
        Gui.drawRect(left, top, left + 5, bottom, accent);
        Gui.drawRect(left + 5, top + 5, left + 7, bottom - 5, Theme.GOLD_DARK);
        Gui.drawRect(left + 13, bottom - 7, left + 55, bottom - 6, accent);
        Gui.drawRect(right - 50, top + 7, right - 11, top + 8, accent);
        Gui.drawRect(right - 25, top + 9, right - 11, top + 10, Theme.GOLD_DARK);
    }

    public static void section(int left, int top, int width, int height, int accent) {
        int right = left + width;
        int bottom = top + height;
        Gui.drawRect(left + 2, top + 2, right + 2, bottom + 2, 0x56000000);
        Gui.drawRect(left, top, right, bottom, Theme.EDGE_DARK);
        Gui.drawRect(left + 1, top + 1, right - 1, bottom - 1, Theme.BUTTON_BORDER);
        Gui.drawRect(left + 2, top + 2, right - 2, bottom - 2, Theme.PARCHMENT_DARK);
        Gui.drawRect(left + 3, top + 3, right - 3, top + 5, Theme.PARCHMENT_LIGHT);
        Gui.drawRect(left + 2, top + 2, left + 5, bottom - 2, accent);
        Gui.drawRect(left + 8, top + 3, left + Math.min(34, width - 8), top + 4, accent);
        Gui.drawRect(right - 20, bottom - 6, right - 7, bottom - 5, accent);
        Gui.drawRect(right - 7, bottom - 11, right - 6, bottom - 5, Theme.GOLD_DARK);
    }

    public static void rule(int left, int right, int y, int accent) {
        int length = Math.max(0, right - left);
        Gui.drawRect(left, y, right, y + 1, Theme.BUTTON_BORDER);
        Gui.drawRect(left, y, left + Math.min(34, length), y + 1, accent);
        if (length > 46) {
            Gui.drawRect(right - 10, y - 1, right - 1, y, Theme.GOLD_DARK);
        }
    }

    public static void caption(FontRenderer font, String text, int x, int y, int color) {
        font.drawString(text, x, y, color);
    }

    public static void itemPlate(int left, int top, int size, int accent) {
        int right = left + size;
        int bottom = top + size;
        Gui.drawRect(left + 2, top + 3, right + 3, bottom + 3, 0x70000000);
        Gui.drawRect(left, top, right, bottom, Theme.EDGE_DARK);
        Gui.drawRect(left + 1, top + 1, right - 1, bottom - 1, Theme.BUTTON_BORDER);
        Gui.drawRect(left + 3, top + 3, right - 3, bottom - 3, Theme.PANEL_BG_DEEP);
        Gui.drawRect(left + 4, top + 4, right - 4, top + 6, accent);
        Gui.drawRect(left + 4, top + 6, left + 6, top + 13, accent);
        Gui.drawRect(right - 7, bottom - 6, right - 4, bottom - 4, accent);
        Gui.drawRect(right - 13, bottom - 5, right - 7, bottom - 4, accent);
    }

    private static void corner(int x, int y, int color, boolean right, boolean bottom) {
        int horizontalStart = right ? x - 5 : x;
        int verticalStart = bottom ? y - 5 : y;
        Gui.drawRect(horizontalStart, y, horizontalStart + 6, y + 1, color);
        Gui.drawRect(x, verticalStart, x + 1, verticalStart + 6, color);
    }

    public static void playerHead(String playerName, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient connection = minecraft.getConnection();
        if (connection != cachedConnection) {
            SKIN_CACHE.clear();
            cachedConnection = connection;
        }
        String key = playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
        ResourceLocation skin = SKIN_CACHE.get(key);
        if (skin == null && connection != null && !key.isEmpty()) {
            Collection<NetworkPlayerInfo> players = connection.getPlayerInfoMap();
            for (NetworkPlayerInfo candidate : players) {
                if (candidate.getGameProfile() != null
                        && playerName.equalsIgnoreCase(candidate.getGameProfile().getName())) {
                    skin = candidate.getLocationSkin();
                    SKIN_CACHE.put(key, skin);
                    break;
                }
            }
        }
        if (skin == null) {
            Gui.drawRect(x, y, x + size, y + size, Theme.TAB_INACTIVE_BG);
            return;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        minecraft.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
    }
}
