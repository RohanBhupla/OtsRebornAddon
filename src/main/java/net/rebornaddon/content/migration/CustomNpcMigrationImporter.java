package net.rebornaddon.content.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.RegionFile;
import net.rebornaddon.content.NativeContentPluginBridge;

import java.io.File;
import java.io.DataInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CustomNpcMigrationImporter {
    private CustomNpcMigrationImporter() {
    }

    public static Result inspect(MinecraftServer server) {
        Result result = new Result();
        File world = worldDirectory(server);
        if (world == null) {
            result.error = "The active world's save directory could not be resolved.";
            return result;
        }
        File root = new File(world, "customnpcs");
        result.source = root;
        if (!root.isDirectory()) {
            result.error = "No CustomNPCs data directory exists in this world.";
            return result;
        }
        try {
            Map<Integer, NBTTagCompound> dialogs = loadNumberedData(new File(root, "dialogs"));
            result.dialogCount = dialogs.size();
            importQuests(new File(root, "quests"), dialogs, result);
            linkPrerequisites(result.quests);
            importLoadedNpcs(server, result);
            importSavedNpcs(server, world, result);
            result.bundle.addProperty("format", 2);
            result.bundle.addProperty("source", "customnpcs-1.12.2");
            result.bundle.addProperty("sourceWorld", world.getName());
            result.bundle.addProperty("generatedAt", System.currentTimeMillis());
            result.bundle.add("templates", result.templates);
            result.bundle.add("actors", result.actors);
            result.bundle.add("paths", result.paths);
            result.bundle.add("quests", result.quests);
            JsonArray warnings = new JsonArray();
            for (String warning : result.warnings) warnings.add(warning);
            result.bundle.add("warnings", warnings);
        } catch (Throwable throwable) {
            result.error = "CustomNPCs data could not be parsed: " + throwable.getClass().getSimpleName();
        }
        return result;
    }

    public static String[] importToPlugin(MinecraftServer server, EntityPlayerMP player) {
        Result result = inspect(server);
        if (!result.ok()) return new String[]{"false", result.error, ""};
        return NativeContentPluginBridge.adminAction(player, "import.customnpcs", result.bundle.toString());
    }

    private static void importQuests(File directory, Map<Integer, NBTTagCompound> dialogs,
                                     Result result) throws Exception {
        if (!directory.isDirectory()) return;
        List<File> files = new ArrayList<File>();
        collectJson(directory, files);
        for (File file : files) {
            NBTTagCompound tag = read(file);
            int legacyId = number(file.getName());
            String category = file.getParentFile() == null ? "Missions" : file.getParentFile().getName();
            JsonObject quest = new JsonObject();
            quest.addProperty("id", "customnpcs-" + legacyId);
            quest.addProperty("legacyId", legacyId);
            quest.addProperty("title", fallback(tag.getString("Title"), "Imported Quest " + legacyId));
            quest.addProperty("category", category);
            quest.addProperty("rank", rank(category));
            quest.addProperty("village", village(category));
            quest.addProperty("description", tag.getString("Text"));
            quest.addProperty("completionText", tag.getString("CompleteText"));
            quest.addProperty("completionNpc", tag.getString("CompleterNpc"));
            quest.addProperty("enabled", true);
            quest.addProperty("repeatable", tag.getInteger("QuestRepeat") != 0);
            quest.addProperty("randomReward", tag.getBoolean("RandomReward"));
            quest.addProperty("rewardDelivery", "direct");
            quest.addProperty("rewardExperience", Math.max(0, tag.getInteger("RewardExp")));
            quest.addProperty("rewardLevels", 0);
            int next = tag.getInteger("NextQuestId");
            quest.addProperty("nextQuestId", next < 0 ? "" : "customnpcs-" + next);
            quest.add("prerequisites", new JsonArray());
            JsonArray commands = new JsonArray();
            if (!tag.getString("QuestCommand").trim().isEmpty()) commands.add(tag.getString("QuestCommand"));
            quest.add("rewardCommands", commands);
            quest.add("rewards", items(tag.getCompoundTag("Rewards"), false, false, result));
            quest.add("rewardMail", mail(tag.getCompoundTag("QuestMail"), result));
            JsonArray steps = steps(tag, dialogs, legacyId, result);
            quest.add("steps", steps);
            if (steps.size() == 0 || requiresPlacement(tag.getInteger("Type"))) {
                quest.addProperty("enabled", false);
                result.warnings.add("Quest " + legacyId
                        + " was imported disabled because its objective needs native coordinates or an NPC template.");
            }
            result.quests.add(quest);
        }
    }

    private static void linkPrerequisites(JsonArray quests) {
        Map<String, JsonObject> byId = new HashMap<String, JsonObject>();
        for (com.google.gson.JsonElement element : quests) if (element.isJsonObject()) {
            JsonObject quest = element.getAsJsonObject();
            byId.put(quest.get("id").getAsString(), quest);
        }
        for (JsonObject quest : byId.values()) {
            String next = quest.has("nextQuestId") ? quest.get("nextQuestId").getAsString() : "";
            JsonObject target = byId.get(next);
            if (target != null) target.getAsJsonArray("prerequisites").add(quest.get("id").getAsString());
        }
    }

    private static JsonArray steps(NBTTagCompound tag, Map<Integer, NBTTagCompound> dialogs,
                                   int questId, Result result) {
        JsonArray steps = new JsonArray();
        int type = tag.getInteger("Type");
        if (type == 0) {
            JsonObject step = step("items", "item", "Collect the required items");
            boolean ignoreMeta = tag.getBoolean("IgnoreDamage");
            boolean ignoreNbt = tag.getBoolean("IgnoreNBT");
            step.addProperty("consumeItems", !tag.getBoolean("LeaveItems"));
            step.add("requirements", items(tag.getCompoundTag("Items"), ignoreMeta, ignoreNbt, result));
            steps.add(step);
        } else if (type == 1) {
            NBTTagList values = tag.getTagList("QuestDialogs", 10);
            for (int i = 0; i < values.tagCount(); i++) {
                int dialogId = values.getCompoundTagAt(i).getInteger("Integer");
                NBTTagCompound dialog = dialogs.get(Integer.valueOf(dialogId));
                JsonObject step = step("dialog-" + dialogId, "talk",
                        dialog == null ? "Read dialogue " + dialogId
                                : fallback(dialog.getString("DialogTitle"), "Dialogue"));
                step.addProperty("target", "customnpcs-dialog-" + dialogId);
                step.add("dialogue", dialogueGraph(dialogId, dialogs));
                steps.add(step);
            }
        } else if (type == 2 || type == 4) {
            NBTTagList values = tag.getTagList("QuestDialogs", 10);
            for (int i = 0; i < values.tagCount(); i++) {
                NBTTagCompound value = values.getCompoundTagAt(i);
                String target = value.getString("Slot");
                JsonObject step = step("kill-" + i, "kill", "Defeat " + target);
                step.addProperty("target", target);
                step.addProperty("amount", Math.max(1, value.getInteger("Value")));
                steps.add(step);
            }
        } else if (type == 3) {
            String[] keys = {"QuestLocation", "QuestLocation2", "QuestLocation3"};
            for (int i = 0; i < keys.length; i++) {
                String name = tag.getString(keys[i]).trim();
                if (name.isEmpty()) continue;
                JsonObject step = step("location-" + (i + 1), "travel", name);
                step.addProperty("legacyLocation", name);
                steps.add(step);
            }
        } else if (type == 5) {
            NBTTagList values = tag.getTagList("QuestManual", 10);
            for (int i = 0; i < values.tagCount(); i++) {
                NBTTagCompound value = values.getCompoundTagAt(i);
                JsonObject step = step("manual-" + i, "item", "Obtain " + value.getString("Slot"));
                step.addProperty("target", value.getString("Slot"));
                step.addProperty("amount", Math.max(1, value.getInteger("Value")));
                steps.add(step);
            }
        } else {
            result.warnings.add("Quest " + questId + " uses unsupported legacy type " + type + ".");
        }
        return steps;
    }

    private static JsonArray dialogueGraph(int rootId, Map<Integer, NBTTagCompound> dialogs) {
        JsonArray pages = new JsonArray();
        List<Integer> queue = new ArrayList<Integer>();
        Set<Integer> visited = new HashSet<Integer>();
        queue.add(Integer.valueOf(rootId));
        for (int cursor = 0; cursor < queue.size() && pages.size() < 256; cursor++) {
            int id = queue.get(cursor).intValue();
            if (!visited.add(Integer.valueOf(id))) continue;
            NBTTagCompound dialog = dialogs.get(Integer.valueOf(id));
            JsonObject page = new JsonObject();
            page.addProperty("id", "dialog-" + id);
            page.addProperty("speaker", "npc");
            page.addProperty("text", dialog == null ? "" : dialog.getString("DialogText"));
            JsonArray choices = new JsonArray();
            if (dialog != null) {
                NBTTagList options = dialog.getTagList("Options", 10);
                List<NBTTagCompound> ordered = new ArrayList<NBTTagCompound>();
                for (int i = 0; i < options.tagCount(); i++) ordered.add(options.getCompoundTagAt(i));
                java.util.Collections.sort(ordered, (left, right) -> Integer.compare(
                        left.getInteger("OptionSlot"), right.getInteger("OptionSlot")));
                for (NBTTagCompound wrapper : ordered) {
                    NBTTagCompound option = wrapper.getCompoundTag("Option");
                    String title = option.getString("Title").trim();
                    if (title.isEmpty()) continue;
                    int next = option.getInteger("Dialog");
                    JsonObject choice = new JsonObject();
                    choice.addProperty("id", "dialog-" + id + "-option-" + wrapper.getInteger("OptionSlot"));
                    choice.addProperty("text", title);
                    choice.addProperty("nextPageId", next < 0 ? "" : "dialog-" + next);
                    choices.add(choice);
                    if (next >= 0 && !visited.contains(Integer.valueOf(next))) queue.add(Integer.valueOf(next));
                }
            }
            page.add("choices", choices);
            pages.add(page);
        }
        return pages;
    }

    private static JsonObject step(String id, String type, String objective) {
        JsonObject step = new JsonObject();
        step.addProperty("id", id);
        step.addProperty("objective", objective);
        step.addProperty("type", type);
        step.addProperty("dimension", 0);
        step.addProperty("x", 0.5D);
        step.addProperty("y", 64.0D);
        step.addProperty("z", 0.5D);
        step.addProperty("radius", 8.0D);
        step.addProperty("spawnNpc", false);
        step.addProperty("amount", 1);
        step.addProperty("target", "");
        step.addProperty("pathId", "");
        step.addProperty("templateId", "");
        step.addProperty("consumeItems", true);
        step.add("overrides", new JsonObject());
        step.add("dialogue", new JsonArray());
        step.add("requirements", new JsonArray());
        step.add("rewards", new JsonArray());
        return step;
    }

    private static void importLoadedNpcs(MinecraftServer server, Result result) {
        for (WorldServer world : server.worlds) {
            for (Entity entity : new ArrayList<Entity>(world.loadedEntityList)) {
                if (!(entity instanceof EntityLivingBase)
                        || !entity.getClass().getName().startsWith("noppes.npcs.entity.")) continue;
                NBTTagCompound tag = new NBTTagCompound();
                if (!entity.writeToNBTOptional(tag)) continue;
                String base = cleanId(fallback(tag.getString("Name"), entity.getName()));
                result.entityUuids.add(entity.getUniqueID().toString());
                String id = unique(base.isEmpty() ? "imported-npc" : base, result.templateIds);
                JsonObject template = npcTemplate(id, entity, tag, result);
                JsonObject actor = new JsonObject();
                actor.addProperty("id", "actor-" + id);
                actor.addProperty("name", template.get("name").getAsString());
                actor.addProperty("templateId", id);
                actor.add("overrides", new JsonObject());
                actor.addProperty("dimension", entity.dimension);
                actor.addProperty("x", entity.posX);
                actor.addProperty("y", entity.posY);
                actor.addProperty("z", entity.posZ);
                actor.addProperty("activationRadius", Math.max(16, tag.getInteger("AggroRange") * 2));
                actor.addProperty("leashRadius", Math.max(16, tag.getInteger("WalkingRange")));
                actor.addProperty("respawnSeconds", Math.max(0, tag.getInteger("RespawnTime")));
                actor.addProperty("enabled", true);
                actor.add("drops", new JsonArray());
                result.templates.add(template);
                result.actors.add(actor);
                importPath(id, entity.dimension, tag, result);
            }
        }
    }

    private static void importSavedNpcs(MinecraftServer server, File worldDirectory, Result result) {
        for (WorldServer world : server.worlds) {
            String saveFolder = world.provider.getSaveFolder();
            File dimension = saveFolder == null ? worldDirectory : new File(worldDirectory, saveFolder);
            File regionDirectory = new File(dimension, "region");
            File[] files = regionDirectory.listFiles((dir, name) -> name.startsWith("r.") && name.endsWith(".mca"));
            if (files == null) continue;
            for (File file : files) scanRegion(file, world.provider.getDimension(), result);
        }
    }

    private static void scanRegion(File file, int dimension, Result result) {
        RegionFile region = null;
        try {
            region = new RegionFile(file);
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    if (!region.chunkExists(x, z)) continue;
                    DataInputStream stream = region.getChunkDataInputStream(x, z);
                    if (stream == null) continue;
                    try {
                        NBTTagCompound root = CompressedStreamTools.read(stream);
                        NBTTagCompound level = root.getCompoundTag("Level");
                        NBTTagList entities = level.getTagList("Entities", 10);
                        for (int i = 0; i < entities.tagCount(); i++) {
                            NBTTagCompound tag = entities.getCompoundTagAt(i);
                            if (isCustomNpc(tag)) importSavedNpc(tag, dimension, result);
                        }
                    } finally {
                        stream.close();
                    }
                }
            }
        } catch (Throwable throwable) {
            result.warnings.add("Region " + file.getName() + " could not be fully scanned.");
        } finally {
            if (region != null) try { region.close(); } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isCustomNpc(NBTTagCompound tag) {
        String id = tag.getString("id").toLowerCase(Locale.ROOT);
        return id.contains("customnpc") || tag.hasKey("NpcInv") && tag.hasKey("WalkingRange")
                && tag.hasKey("ModRev");
    }

    private static void importSavedNpc(NBTTagCompound tag, int dimension, Result result) {
        String uuid = tag.getUniqueId("UUID").toString();
        if (!result.entityUuids.add(uuid)) return;
        String name = fallback(tag.getString("Name"), "Imported NPC");
        String requested = cleanId(name);
        String id = unique(requested.isEmpty() ? "imported-npc" : requested, result.templateIds);
        JsonObject template = savedNpcTemplate(id, name, tag, result);
        double[] position = position(tag);
        JsonObject actor = new JsonObject();
        actor.addProperty("id", "actor-" + id);
        actor.addProperty("name", name);
        actor.addProperty("templateId", id);
        actor.add("overrides", new JsonObject());
        actor.addProperty("dimension", dimension);
        actor.addProperty("x", position[0]);
        actor.addProperty("y", position[1]);
        actor.addProperty("z", position[2]);
        actor.addProperty("activationRadius", Math.max(16, tag.getInteger("AggroRange") * 2));
        actor.addProperty("leashRadius", Math.max(16, tag.getInteger("WalkingRange")));
        actor.addProperty("respawnSeconds", Math.max(0, tag.getInteger("RespawnTime")));
        actor.addProperty("enabled", true);
        actor.add("drops", new JsonArray());
        result.templates.add(template);
        result.actors.add(actor);
        importPath(id, dimension, tag, result);
    }

    private static JsonObject savedNpcTemplate(String id, String name, NBTTagCompound tag, Result result) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("name", name);
        String skin = tag.getString("SkinUrl");
        if (skin.isEmpty() && !tag.getString("Texture").isEmpty()) {
            result.warnings.add("NPC " + name
                    + " used a CustomNPCs-local texture and needs a packaged or URL skin selected.");
        }
        value.addProperty("skin", skin);
        value.addProperty("slim", false);
        value.addProperty("health", Math.max(1, tag.getInteger("MaxHealth")));
        value.addProperty("meleeDamage", Math.max(0, tag.getInteger("MeleeStrength")));
        value.addProperty("damageReduction", 0.0D);
        value.addProperty("projectileDamageReduction", 0.0D);
        value.addProperty("magicDamageReduction", 0.0D);
        value.addProperty("explosionDamageReduction", 0.0D);
        value.addProperty("knockbackResistance", 0.0D);
        value.addProperty("attackKnockback", Math.max(0, tag.getInteger("MeleeKnockback")));
        value.addProperty("fireImmune", tag.getBoolean("ImmuneToFire"));
        value.addProperty("invulnerable", tag.getBoolean("Invulnerable"));
        value.addProperty("canDrown", !tag.hasKey("CanDrown") || tag.getBoolean("CanDrown"));
        value.addProperty("takesFallDamage", !tag.getBoolean("NoFallDamage"));
        value.addProperty("pathAroundWater", tag.getBoolean("AvoidsWater"));
        value.addProperty("canOpenDoors", tag.getInteger("DoorInteract") != 0);
        value.addProperty("stationary", tag.getInteger("MovementType") == 2);
        value.addProperty("retaliates", tag.getInteger("OnAttack") != 3);
        value.addProperty("assistsAllies", true);
        value.addProperty("immuneToPoison", tag.getBoolean("PotionImmune"));
        value.addProperty("immuneToWither", tag.getBoolean("PotionImmune"));
        value.addProperty("immuneToNegativeEffects", tag.getBoolean("PotionImmune"));
        value.addProperty("ignoresWebs", tag.getBoolean("IgnoreCobweb"));
        value.addProperty("burnsInSun", tag.getBoolean("BurnInSun"));
        value.addProperty("attacksInvisible", tag.getBoolean("AttackInvisible"));
        value.addProperty("requiresLineOfSight", tag.getBoolean("DirectLOS"));
        value.addProperty("sprintsInCombat", tag.getBoolean("CanSprint"));
        value.addProperty("hostile", false);
        value.addProperty("aggression", "defensive");
        value.addProperty("followRange", Math.max(4, tag.getInteger("AggroRange")));
        value.addProperty("moveSpeed", Math.max(0.05D, tag.getInteger("MoveSpeed") / 5.0D));
        value.addProperty("wanderRadius", Math.max(0, tag.getInteger("WalkingRange")));
        value.addProperty("meleeReach", Math.max(0, tag.getInteger("MeleeRange")));
        value.addProperty("regenerationPerSecond", Math.max(0, tag.getInteger("HealthRegen")));
        value.addProperty("combatRegenerationPerSecond", Math.max(0, tag.getInteger("CombatRegen")));
        value.addProperty("meleeIntervalTicks", Math.max(1, tag.getInteger("MeleeDelay")));
        value.addProperty("role", "none");
        value.addProperty("linkedTarget", tag.getTagList("MovingPathNew", 10).tagCount() > 0
                ? "path-" + id : "");
        value.addProperty("faction", "neutral");
        value.addProperty("ambientSound", "");
        value.addProperty("hurtSound", "");
        value.addProperty("deathSound", "");
        value.add("equipment", savedEquipment(tag));
        value.add("jutsus", new JsonArray());
        value.add("friendlyVillages", new JsonArray());
        value.add("hostileVillages", new JsonArray());
        value.add("friendlyFactions", new JsonArray());
        value.add("hostileFactions", new JsonArray());
        return value;
    }

    private static JsonObject savedEquipment(NBTTagCompound tag) {
        JsonObject equipment = new JsonObject();
        Map<Integer, ItemStack> armor = stackMap(tag.getTagList("Armor", 10));
        Map<Integer, ItemStack> weapons = stackMap(tag.getTagList("Weapons", 10));
        stackFields(equipment, "mainHand", value(weapons, 0));
        stackFields(equipment, "offHand", value(weapons, 1));
        stackFields(equipment, "feet", value(armor, 0));
        stackFields(equipment, "legs", value(armor, 1));
        stackFields(equipment, "chest", value(armor, 2));
        stackFields(equipment, "head", value(armor, 3));
        return equipment;
    }

    private static Map<Integer, ItemStack> stackMap(NBTTagList list) {
        Map<Integer, ItemStack> result = new HashMap<Integer, ItemStack>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) result.put(Integer.valueOf(tag.getByte("Slot") & 255), stack);
        }
        return result;
    }

    private static ItemStack value(Map<Integer, ItemStack> values, int key) {
        ItemStack stack = values.get(Integer.valueOf(key));
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static double[] position(NBTTagCompound tag) {
        NBTTagList pos = tag.getTagList("Pos", 6);
        return pos.tagCount() >= 3 ? new double[]{pos.getDoubleAt(0), pos.getDoubleAt(1), pos.getDoubleAt(2)}
                : new double[]{0.5D, 64.0D, 0.5D};
    }

    private static JsonObject npcTemplate(String id, Entity entity, NBTTagCompound tag, Result result) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("name", fallback(tag.getString("Name"), entity.getName()));
        String skin = tag.getString("SkinUrl");
        if (skin.isEmpty() && tag.getByte("UsingSkinUrl") == 0 && !tag.getString("Texture").isEmpty()) {
            result.warnings.add("NPC " + value.get("name").getAsString()
                    + " used a CustomNPCs-local texture and needs a packaged or URL skin selected.");
        }
        value.addProperty("skin", skin);
        value.addProperty("slim", false);
        double health = tag.hasKey("MaxHealth") ? tag.getInteger("MaxHealth")
                : entity instanceof EntityLivingBase ? ((EntityLivingBase) entity).getMaxHealth() : 20.0D;
        value.addProperty("health", health);
        value.addProperty("meleeDamage", attribute((EntityLivingBase) entity,
                SharedMonsterAttributes.ATTACK_DAMAGE, 2.0D));
        value.addProperty("damageReduction", 0.0D);
        value.addProperty("projectileDamageReduction", 0.0D);
        value.addProperty("magicDamageReduction", 0.0D);
        value.addProperty("explosionDamageReduction", 0.0D);
        value.addProperty("knockbackResistance", attribute((EntityLivingBase) entity,
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE, 0.0D));
        value.addProperty("attackKnockback", 0.0D);
        value.addProperty("fireImmune", tag.getBoolean("ImmuneToFire"));
        value.addProperty("invulnerable", entity.getIsInvulnerable());
        value.addProperty("canDrown", !tag.hasKey("CanDrown") || tag.getBoolean("CanDrown"));
        value.addProperty("takesFallDamage", !tag.getBoolean("NoFallDamage"));
        value.addProperty("pathAroundWater", tag.getBoolean("AvoidsWater"));
        value.addProperty("canOpenDoors", tag.getInteger("DoorInteract") != 0);
        value.addProperty("stationary", tag.getInteger("MovementType") == 2);
        value.addProperty("retaliates", tag.getInteger("OnAttack") != 3);
        value.addProperty("assistsAllies", true);
        value.addProperty("immuneToPoison", tag.getBoolean("PotionImmune"));
        value.addProperty("immuneToWither", tag.getBoolean("PotionImmune"));
        value.addProperty("immuneToNegativeEffects", tag.getBoolean("PotionImmune"));
        value.addProperty("ignoresWebs", tag.getBoolean("IgnoreCobweb"));
        value.addProperty("burnsInSun", tag.getBoolean("BurnInSun"));
        value.addProperty("attacksInvisible", tag.getBoolean("AttackInvisible"));
        value.addProperty("requiresLineOfSight", tag.getBoolean("DirectLOS"));
        value.addProperty("sprintsInCombat", tag.getBoolean("CanSprint"));
        value.addProperty("hostile", false);
        value.addProperty("aggression", "defensive");
        value.addProperty("followRange", Math.max(4, tag.getInteger("AggroRange")));
        value.addProperty("moveSpeed", attribute((EntityLivingBase) entity,
                SharedMonsterAttributes.MOVEMENT_SPEED, 0.28D));
        value.addProperty("wanderRadius", Math.max(0, tag.getInteger("WalkingRange")));
        value.addProperty("meleeReach", Math.max(0, tag.getInteger("MeleeRange")));
        value.addProperty("regenerationPerSecond", Math.max(0, tag.getInteger("HealthRegen")));
        value.addProperty("combatRegenerationPerSecond", Math.max(0, tag.getInteger("CombatRegen")));
        value.addProperty("meleeIntervalTicks", Math.max(1, tag.getInteger("MeleeDelay")));
        value.addProperty("role", "none");
        value.addProperty("linkedTarget", tag.getTagList("MovingPathNew", 10).tagCount() > 0
                ? "path-" + id : "");
        value.addProperty("faction", "neutral");
        value.addProperty("ambientSound", "");
        value.addProperty("hurtSound", "");
        value.addProperty("deathSound", "");
        value.add("equipment", equipment(entity));
        value.add("jutsus", new JsonArray());
        value.add("friendlyVillages", new JsonArray());
        value.add("hostileVillages", new JsonArray());
        value.add("friendlyFactions", new JsonArray());
        value.add("hostileFactions", new JsonArray());
        return value;
    }

    private static void importPath(String id, int dimension, NBTTagCompound tag, Result result) {
        NBTTagList list = tag.getTagList("MovingPathNew", 10);
        if (list.tagCount() == 0) return;
        JsonObject path = new JsonObject();
        path.addProperty("id", "path-" + id);
        path.addProperty("name", "Imported path for " + id);
        path.addProperty("loop", tag.getInteger("MovingPatern") == 0);
        JsonArray points = new JsonArray();
        for (int i = 0; i < list.tagCount(); i++) {
            int[] coordinates = list.getCompoundTagAt(i).getIntArray("Array");
            if (coordinates.length < 3) continue;
            JsonObject point = new JsonObject();
            point.addProperty("dimension", dimension);
            point.addProperty("x", coordinates[0] + 0.5D);
            point.addProperty("y", coordinates[1]);
            point.addProperty("z", coordinates[2] + 0.5D);
            point.addProperty("waitTicks", tag.getBoolean("MovingPause") ? 40 : 0);
            point.addProperty("speed", Math.max(0.05D, tag.getInteger("MoveSpeed") / 5.0D));
            points.add(point);
        }
        path.add("points", points);
        if (points.size() > 0) result.paths.add(path);
    }

    private static JsonObject equipment(Entity entity) {
        JsonObject equipment = new JsonObject();
        EntityLivingBase living = (EntityLivingBase) entity;
        stackFields(equipment, "mainHand", living.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND));
        stackFields(equipment, "offHand", living.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND));
        stackFields(equipment, "head", living.getItemStackFromSlot(EntityEquipmentSlot.HEAD));
        stackFields(equipment, "chest", living.getItemStackFromSlot(EntityEquipmentSlot.CHEST));
        stackFields(equipment, "legs", living.getItemStackFromSlot(EntityEquipmentSlot.LEGS));
        stackFields(equipment, "feet", living.getItemStackFromSlot(EntityEquipmentSlot.FEET));
        return equipment;
    }

    private static void stackFields(JsonObject target, String name, ItemStack stack) {
        ResourceLocation id = stack.isEmpty() || stack.getItem().getRegistryName() == null
                ? null : stack.getItem().getRegistryName();
        target.addProperty(name, id == null ? "" : id.toString());
        target.addProperty(name + "Meta", stack.isEmpty() ? 0 : stack.getMetadata());
        target.addProperty(name + "Nbt", stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
    }

    private static JsonArray items(NBTTagCompound inventory, boolean ignoreMeta, boolean ignoreNbt,
                                   Result result) {
        JsonArray values = new JsonArray();
        NBTTagList list = inventory.getTagList("NpcMiscInv", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack;
            try { stack = new ItemStack(list.getCompoundTagAt(i)); }
            catch (Throwable ignored) { continue; }
            if (stack.isEmpty() || stack.getItem().getRegistryName() == null) continue;
            if ("customnpcs".equals(stack.getItem().getRegistryName().getResourceDomain())) {
                result.warnings.add("A CustomNPCs-owned quest item was omitted because that mod will be removed.");
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("item", stack.getItem().getRegistryName().toString());
            item.addProperty("count", stack.getCount());
            item.addProperty("meta", stack.getMetadata());
            item.addProperty("nbt", stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
            item.addProperty("chance", 1.0D);
            item.addProperty("ignoreMeta", ignoreMeta);
            item.addProperty("ignoreNbt", ignoreNbt);
            values.add(item);
        }
        return values;
    }

    private static JsonObject mail(NBTTagCompound tag, Result result) {
        JsonObject mail = new JsonObject();
        mail.addProperty("sender", tag.getString("Sender"));
        mail.addProperty("subject", tag.getString("Subject"));
        String body = tag.getString("Message");
        if (body.isEmpty() && tag.hasKey("Message", 10)) body = tag.getCompoundTag("Message").toString();
        mail.addProperty("body", body);
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setTag("NpcMiscInv", tag.getTagList("MailItems", 10));
        mail.add("items", items(inventory, false, false, result));
        return mail;
    }

    private static Map<Integer, NBTTagCompound> loadNumberedData(File directory) throws Exception {
        Map<Integer, NBTTagCompound> values = new HashMap<Integer, NBTTagCompound>();
        if (!directory.isDirectory()) return values;
        List<File> files = new ArrayList<File>();
        collectJson(directory, files);
        for (File file : files) values.put(Integer.valueOf(number(file.getName())), read(file));
        return values;
    }

    private static void collectJson(File directory, List<File> output) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) collectJson(file, output);
            else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) output.add(file);
        }
    }

    private static NBTTagCompound read(File file) throws Exception {
        String value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return JsonToNBT.getTagFromJson(value);
    }

    private static File worldDirectory(MinecraftServer server) {
        if (server == null || server.getEntityWorld() == null) return null;
        Object handler = server.getEntityWorld().getSaveHandler();
        try {
            Method method = handler.getClass().getMethod("getWorldDirectory");
            Object value = method.invoke(handler);
            return value instanceof File ? (File) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double attribute(EntityLivingBase entity,
                                    net.minecraft.entity.ai.attributes.IAttribute attribute,
                                    double fallback) {
        if (entity == null || entity.getEntityAttribute(attribute) == null) return fallback;
        return entity.getEntityAttribute(attribute).getBaseValue();
    }

    private static boolean requiresPlacement(int type) { return type == 1 || type == 3; }
    private static int number(String name) {
        String value = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return Math.abs(value.hashCode()); }
    }
    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
    private static String rank(String category) {
        String upper = category.toUpperCase(Locale.ROOT);
        for (String rank : new String[]{"S", "A", "B", "C", "D"}) {
            if (upper.contains(rank + "-RANK") || upper.contains(rank + " RANK")) return rank;
        }
        return "D";
    }
    private static String village(String category) {
        String value = category.toLowerCase(Locale.ROOT);
        for (String village : new String[]{"leaf", "stone", "cloud", "sand", "mist", "rain", "akatsuki"}) {
            if (value.contains(village)) return village;
        }
        return "";
    }
    private static String cleanId(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "-").replaceAll("^-+|-+$", "");
        return clean.length() <= 72 ? clean : clean.substring(0, 72);
    }
    private static String unique(String requested, Set<String> used) {
        String value = requested;
        int suffix = 2;
        while (!used.add(value)) value = requested + "-" + suffix++;
        return value;
    }

    public static final class Result {
        private final JsonObject bundle = new JsonObject();
        private final JsonArray templates = new JsonArray();
        private final JsonArray actors = new JsonArray();
        private final JsonArray paths = new JsonArray();
        private final JsonArray quests = new JsonArray();
        private final List<String> warnings = new ArrayList<String>();
        private final Set<String> entityUuids = new HashSet<String>();
        private final Set<String> templateIds = new HashSet<String>();
        private File source;
        private String error = "";
        private int dialogCount;

        public boolean ok() { return error.isEmpty(); }
        public String error() { return error; }
        public int templates() { return templates.size(); }
        public int actors() { return actors.size(); }
        public int paths() { return paths.size(); }
        public int quests() { return quests.size(); }
        public int dialogs() { return dialogCount; }
        public int warnings() { return warnings.size(); }
        public String source() { return source == null ? "" : source.getAbsolutePath(); }
    }
}
