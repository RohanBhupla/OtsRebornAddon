package net.rebornaddon.gui.theme;

/**
 * Central color palette so every tab/screen in the hub looks consistent.
 * Colors are packed ARGB ints, the format Minecraft's GuiScreen drawing
 * methods expect (0xAARRGGBB).
 */
public final class Theme {

    private Theme() {}

    // Core palette - deep purple frame, space-grey body, per-tab accent colors.
    public static final int WHITE          = 0xFFFFFFFF;
    public static final int PANEL_BG       = 0xFF3A3A3F; // space grey
    public static final int PANEL_BG_LIGHT = 0xFF48484E; // slightly lighter grey, for content cards
    public static final int PURPLE_DEEP    = 0xFF4B1F63; // outer frame border
    public static final int PURPLE_DARK    = 0xFF2E1338; // inactive tab bg
    public static final int PURPLE_MID     = 0xFF6B3489; // hover states (non-red tabs)
    public static final int PURPLE_LIGHT   = 0xFFB98FD1; // subtle highlights / dividers

    public static final int RANKED_RED       = 0xFFB22222; // Ranked tab's own accent color
    public static final int RANKED_RED_HOVER = 0xFFD64545;
    public static final int RANKED_RED_DARK  = 0xFF7A1717;

    public static final int TEXT_LIGHT     = 0xFFF2F2F2; // primary body text on grey bg
    public static final int TEXT_ON_PURPLE = 0xFFFFFFFF;
    public static final int TEXT_MUTED     = 0xFFAFAFAF;

    public static final int TAB_INACTIVE_BG = PURPLE_DARK;
    public static final int TAB_HOVER_BG    = PURPLE_MID;

    public static final int BUTTON_BORDER    = 0xFF1E1E22;
    public static final int BUTTON_DISABLED  = 0xFF6E6E72;
    public static final int BUTTON_TEXT      = 0xFFFFFFFF;

    public static final int SUCCESS = 0xFF4CAF50; // wins / positive ELO
    public static final int DANGER  = 0xFFC0392B; // losses / forfeit / negative ELO
    public static final int NEUTRAL = 0xFF9E9E9E; // draws
}

