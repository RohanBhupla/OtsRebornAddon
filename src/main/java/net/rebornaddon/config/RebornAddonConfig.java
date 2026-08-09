package net.rebornaddon.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class RebornAddonConfig {
    public static int substitutionStunTicks = 30;
    public static int akatsukiBellMinimumSeconds = 180;
    public static int jukeboxMusicRadius = 64;

    private RebornAddonConfig() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        try {
            config.load();
            substitutionStunTicks = config.getInt("substitutionStunTicks", "gameplay", 30, 0, 200,
                    "Duration in ticks applied to a player who attacks another player's substitution.");
            akatsukiBellMinimumSeconds = config.getInt("akatsukiBellMinimumSeconds", "audio", 180, 0, 3600,
                    "Minimum time between Akatsuki hat bell sounds heard by a client.");
            jukeboxMusicRadius = config.getInt("jukeboxMusicRadius", "audio", 64, 8, 128,
                    "Distance in blocks where an active jukebox suppresses Reborn background music.");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
