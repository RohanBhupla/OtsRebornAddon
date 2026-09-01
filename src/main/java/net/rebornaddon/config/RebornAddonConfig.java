package net.rebornaddon.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class RebornAddonConfig {
    public static int substitutionStunTicks = 30;
    public static int akatsukiBellMinimumSeconds = 180;
    public static int jukeboxMusicRadius = 64;
    public static int villageLeadershipCacheSeconds = 30;
    public static int villageHuntCooldownHours = 24;
    public static int villageHuntDurationMinutes = 60;
    public static String[] villageEventPresets = new String[0];
    public static String[] serverEventPresets = new String[0];
    public static String[] eventLocations = new String[0];
    public static String[] npcSkinAllowedHosts = new String[0];
    public static int npcSkinMaximumBytes = 1048576;
    public static int npcSkinCacheMegabytes = 128;

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
            villageLeadershipCacheSeconds = config.getInt("leadershipCacheSeconds", "village", 30, 10, 300,
                    "How long LuckPerms village and role lists are cached.");
            villageHuntCooldownHours = config.getInt("huntCooldownHours", "village", 24, 1, 168,
                    "Cooldown shared by each village after any ANBU hunt attempt.");
            villageHuntDurationMinutes = config.getInt("huntDurationMinutes", "village", 60, 5, 240,
                    "Time an accepted ANBU hunt remains active.");
            villageEventPresets = config.getStringList("villageEventPresets", "village", new String[0],
                    "Village event choices in id|Display Name format.");
            serverEventPresets = config.getStringList("serverEventPresets", "village", new String[0],
                    "Server event choices in id|Display Name format.");
            eventLocations = config.getStringList("eventLocations", "village", new String[0],
                    "Event locations in id|Display Name|dimension|x|y|z format.");
            npcSkinAllowedHosts = config.getStringList("allowedHosts", "npcSkins", new String[0],
                    "Optional HTTPS host allowlist for remote NPC skins. Empty permits any public internet host.");
            npcSkinMaximumBytes = config.getInt("maximumBytes", "npcSkins", 1048576, 65536, 4194304,
                    "Maximum encoded bytes accepted for one remote NPC skin.");
            npcSkinCacheMegabytes = config.getInt("cacheMegabytes", "npcSkins", 128, 16, 1024,
                    "Maximum server disk cache size for validated remote NPC skins.");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
