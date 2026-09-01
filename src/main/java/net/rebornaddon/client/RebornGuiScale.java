package net.rebornaddon.client;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;

@SideOnly(Side.CLIENT)
public final class RebornGuiScale {
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 200;
    public static final int DEFAULT_PERCENT = 100;

    private static final String CATEGORY = "display";
    private static final String PROPERTY = "guiScalePercent";

    private static Configuration configuration;
    private static int percent = DEFAULT_PERCENT;

    private RebornGuiScale() {
    }

    public static void load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        percent = configuration.getInt(PROPERTY, CATEGORY, DEFAULT_PERCENT,
                MIN_PERCENT, MAX_PERCENT, "RebornAddon interface scale percentage.");
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static int getPercent() {
        return percent;
    }

    public static float getScale() {
        return percent / 100.0F;
    }

    public static void setPercent(int value) {
        percent = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value));
        if (configuration != null) {
            configuration.get(CATEGORY, PROPERTY, DEFAULT_PERCENT).set(percent);
        }
    }

    public static void save() {
        if (configuration != null && configuration.hasChanged()) {
            configuration.save();
        }
    }
}
