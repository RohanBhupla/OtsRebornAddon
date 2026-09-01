package net.rebornaddon.content;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class NativeContentModel {
    private NativeContentModel() {
    }

    public static final class Catalog {
        public long revision;
        public List<NpcTemplate> templates = new ArrayList<NpcTemplate>();
        public List<Actor> actors = new ArrayList<Actor>();
        public List<Quest> quests = new ArrayList<Quest>();
        public List<Boss> bosses = new ArrayList<Boss>();
        public List<Dungeon> dungeons = new ArrayList<Dungeon>();
        public List<PathDefinition> paths = new ArrayList<PathDefinition>();
    }

    public static class NpcTemplate {
        public String id = "";
        public String name = "NPC";
        public String skin = "";
        public boolean slim;
        public double health = 20.0D;
        public double meleeDamage = 2.0D;
        public double damageReduction;
        public double projectileDamageReduction;
        public double magicDamageReduction;
        public double explosionDamageReduction;
        public double knockbackResistance;
        public double attackKnockback;
        public boolean fireImmune;
        public boolean invulnerable;
        public boolean canDrown = true;
        public boolean takesFallDamage = true;
        public boolean pathAroundWater = true;
        public boolean chakraControl;
        public boolean chakraWaterWalking;
        public boolean chakraWallClimbing;
        public boolean canOpenDoors;
        public boolean stationary;
        public boolean retaliates = true;
        public boolean assistsAllies = true;
        public boolean immuneToPoison;
        public boolean immuneToWither;
        public boolean immuneToNegativeEffects;
        public boolean ignoresWebs;
        public boolean burnsInSun;
        public boolean attacksInvisible;
        public boolean requiresLineOfSight = true;
        public boolean sprintsInCombat;
        public boolean hostile;
        public String aggression = "passive";
        public double followRange = 24.0D;
        public double moveSpeed = 0.28D;
        public double wanderRadius = 12.0D;
        public double meleeReach;
        public double regenerationPerSecond;
        public double combatRegenerationPerSecond;
        public int meleeIntervalTicks = 20;
        public String role = "none";
        public String linkedTarget = "";
        public String faction = "neutral";
        public String ambientSound = "";
        public String hurtSound = "";
        public String deathSound = "";
        public Equipment equipment = new Equipment();
        public List<Jutsu> jutsus = new ArrayList<Jutsu>();
        public List<String> friendlyVillages = new ArrayList<String>();
        public List<String> hostileVillages = new ArrayList<String>();
        public List<String> friendlyFactions = new ArrayList<String>();
        public List<String> hostileFactions = new ArrayList<String>();
    }

    public static final class Equipment {
        public String mainHand = "";
        public int mainHandMeta;
        public String mainHandNbt = "";
        public String offHand = "";
        public int offHandMeta;
        public String offHandNbt = "";
        public String head = "";
        public int headMeta;
        public String headNbt = "";
        public String chest = "";
        public int chestMeta;
        public String chestNbt = "";
        public String legs = "";
        public int legsMeta;
        public String legsNbt = "";
        public String feet = "";
        public int feetMeta;
        public String feetNbt = "";
    }

    public static final class Jutsu {
        public String id = "";
        public double damage = -1.0D;
        public int cooldownTicks = 100;
        public int durationTicks = 100;
        public boolean obeyCooldowns = true;
        public boolean obeyGlobalRules = true;
        public float power = 1.0F;
        public int weight = 1;
        public double minimumRange = 2.0D;
        public double maximumRange = 24.0D;
    }

    public static final class Quest {
        public String id = "";
        public String title = "Quest";
        public String category = "Missions";
        public String rank = "D";
        public String village = "";
        public String description = "";
        public String completionText = "";
        public String completionNpc = "";
        public boolean enabled = true;
        public boolean repeatable;
        public boolean randomReward;
        public String rewardDelivery = "direct";
        public int rewardExperience;
        public int rewardLevels;
        public String nextQuestId = "";
        public List<String> prerequisites = new ArrayList<String>();
        public List<String> rewardCommands = new ArrayList<String>();
        public List<ItemAmount> rewards = new ArrayList<ItemAmount>();
        public MailReward rewardMail = new MailReward();
        public List<QuestStep> steps = new ArrayList<QuestStep>();
    }

    public static final class MailReward {
        public String sender = "";
        public String subject = "";
        public String body = "";
        public List<ItemAmount> items = new ArrayList<ItemAmount>();
    }

    public static final class QuestStep {
        public String id = "";
        public String objective = "";
        public String type = "travel";
        public int dimension;
        public double x;
        public double y;
        public double z;
        public double radius = 8.0D;
        public boolean spawnNpc;
        public Double spawnX;
        public Double spawnY;
        public Double spawnZ;
        public int amount = 1;
        public String target = "";
        public String pathId = "";
        public String templateId = "";
        public boolean consumeItems = true;
        public JsonObject overrides;
        public List<DialoguePage> dialogue = new ArrayList<DialoguePage>();
        public List<ItemAmount> requirements = new ArrayList<ItemAmount>();
        public List<ItemAmount> rewards = new ArrayList<ItemAmount>();
    }

    public static final class DialoguePage {
        public String id = "";
        public String speaker = "npc";
        public String text = "";
        public List<DialogueChoice> choices = new ArrayList<DialogueChoice>();
    }

    public static final class DialogueChoice {
        public String id = "";
        public String text = "";
        public String nextPageId = "";
    }

    public static final class ItemAmount {
        public String item = "";
        public int count = 1;
        public int meta;
        public String nbt = "";
        public double chance = 1.0D;
        public boolean ignoreMeta;
        public boolean ignoreNbt;
    }

    public static class SpawnDefinition {
        public String id = "";
        public String name = "";
        public String templateId = "";
        public JsonObject overrides;
        public int dimension;
        public double x;
        public double y;
        public double z;
        public double activationRadius = 32.0D;
        public double leashRadius = 48.0D;
        public long respawnSeconds = 3600L;
        public boolean enabled = true;
        public List<ItemAmount> drops = new ArrayList<ItemAmount>();
    }

    public static final class Boss extends SpawnDefinition {
        public boolean worldBoss;
        public double minimumContribution;
        public String poolId = "";
        public double arenaRadius = 64.0D;
        public double healthPerAdditionalPlayer = 0.35D;
        public int maximumScaledPlayers = 8;
        public long enrageSeconds;
        public double enrageDamageMultiplier = 1.5D;
        public double enrageSpeedMultiplier = 1.15D;
        public List<BossPhase> phases = new ArrayList<BossPhase>();
    }

    public static final class BossPhase {
        public String id = "phase";
        public String name = "Boss Phase";
        public double healthPercent = 75.0D;
        public String telegraph = "";
        public long invulnerableSeconds;
        public List<Jutsu> jutsus = new ArrayList<Jutsu>();
        public List<PhaseSummon> summons = new ArrayList<PhaseSummon>();
    }

    public static final class PhaseSummon {
        public String templateId = "";
        public JsonObject overrides;
        public int count = 1;
        public double radius = 4.0D;
    }

    public static final class Actor extends SpawnDefinition {
    }

    public static final class Dungeon extends SpawnDefinition {
        public String mode = "waves";
        public long resetSeconds = 900L;
        public boolean announceCompletion;
        public List<DungeonSpawn> spawns = new ArrayList<DungeonSpawn>();
    }

    public static final class DungeonSpawn extends SpawnDefinition {
        public int count = 1;
        public int wave = 1;
        public int waveDelaySeconds;
    }

    public static final class PathDefinition {
        public String id = "";
        public boolean loop = true;
        public List<PathPoint> points = new ArrayList<PathPoint>();
    }

    public static final class PathPoint {
        public int dimension;
        public double x;
        public double y;
        public double z;
        public int waitTicks;
        public double speed = 0.85D;
    }
}
