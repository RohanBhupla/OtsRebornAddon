package net.rebornaddon.quest;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Loader;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageSelectionHandler;
import net.rebornaddon.village.network.RebornAddonNetwork;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.wrapper.PlayerWrapper;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerQuestData;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.quests.QuestDialog;
import noppes.npcs.quests.QuestItem;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestLocation;
import noppes.npcs.quests.QuestManual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CustomNpcQuestService {
    private static final Pattern[] RANK_PATTERNS = new Pattern[]{
            rankPattern("D"), rankPattern("C"), rankPattern("B"), rankPattern("A"), rankPattern("S")
    };
    private static final String[] RANKS = new String[]{"D", "C", "B", "A", "S"};
    private static final int MAX_QUESTS = 1500;
    private static final int MAX_OBJECTIVES = 24;
    private static final long REQUEST_DELAY_MILLIS = 2000L;

    private static List<CatalogQuest> catalog = Collections.emptyList();
    private static final Map<UUID, Long> lastRequest = new HashMap<UUID, Long>();
    private static int catalogVersion;
    private static boolean catalogLoaded;

    private CustomNpcQuestService() {
    }

    public static int refresh() {
        if (!Loader.isModLoaded("customnpcs") || QuestController.instance == null
                || QuestController.instance.quests == null) {
            catalog = Collections.emptyList();
            catalogLoaded = false;
            catalogVersion++;
            return 0;
        }

        Collection<Quest> values = QuestController.instance.quests.values();
        List<Quest> quests = new ArrayList<Quest>();
        for (Quest quest : values) {
            if (quest != null) {
                quests.add(quest);
            }
        }
        Collections.sort(quests, new Comparator<Quest>() {
            @Override
            public int compare(Quest left, Quest right) {
                String leftCategory = category(left);
                String rightCategory = category(right);
                int categoryOrder = leftCategory.compareToIgnoreCase(rightCategory);
                if (categoryOrder != 0) {
                    return categoryOrder;
                }
                int titleOrder = clean(left.title, "Untitled Quest").compareToIgnoreCase(clean(right.title, "Untitled Quest"));
                return titleOrder != 0 ? titleOrder : Integer.compare(left.id, right.id);
            }
        });

        List<CatalogQuest> rebuilt = new ArrayList<CatalogQuest>();
        for (Quest quest : quests) {
            if (rebuilt.size() < MAX_QUESTS) {
                try {
                    rebuilt.add(new CatalogQuest(quest));
                } catch (Throwable ignored) {
                }
            }
        }
        catalog = Collections.unmodifiableList(rebuilt);
        catalogLoaded = true;
        catalogVersion++;
        return rebuilt.size();
    }

    public static void reset() {
        catalog = Collections.emptyList();
        catalogVersion = 0;
        catalogLoaded = false;
        lastRequest.clear();
    }

    public static void request(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastRequest.get(player.getUniqueID());
        if (previous != null && now - previous.longValue() < REQUEST_DELAY_MILLIS) {
            return;
        }
        lastRequest.put(player.getUniqueID(), Long.valueOf(now));
        send(player);
    }

    public static void send(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        if (!catalogLoaded) {
            refresh();
        }
        RebornAddonNetwork.sendQuestSnapshot(player, buildSnapshot(player));
    }

    public static void sendAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private static NBTTagCompound buildSnapshot(EntityPlayerMP player) {
        NBTTagCompound root = new NBTTagCompound();
        boolean available = Loader.isModLoaded("customnpcs") && QuestController.instance != null;
        root.setBoolean("Available", available);
        root.setInteger("CatalogVersion", catalogVersion);

        Village village = VillageSelectionHandler.INSTANCE.getAssignedVillage(player);
        root.setString("Village", village == null ? "" : village.group());

        QuestTabStore store = QuestTabStore.get(player.getServer());
        NBTTagList tabs = new NBTTagList();
        if (store != null) {
            for (QuestTabDefinition definition : store.all()) {
                tabs.appendTag(definition.write());
            }
        }
        root.setTag("Tabs", tabs);

        PlayerData playerData = null;
        PlayerWrapper<EntityPlayerMP> wrapper = null;
        if (available) {
            try {
                playerData = PlayerData.get(player);
                wrapper = new PlayerWrapper<EntityPlayerMP>(player);
            } catch (Throwable ignored) {
            }
        }
        PlayerQuestData questData = playerData == null ? null : playerData.questData;
        NBTTagList quests = new NBTTagList();
        for (CatalogQuest entry : catalog) {
            quests.appendTag(entry.write(player, wrapper, questData));
        }
        root.setTag("Quests", quests);
        return root;
    }

    private static final class CatalogQuest {
        private final Quest quest;
        private final String title;
        private final String category;
        private final String rank;
        private final String village;
        private final String description;
        private final String completer;
        private final List<String> rewards;
        private final List<StaticObjective> objectives;

        private CatalogQuest(Quest quest) {
            this.quest = quest;
            this.title = limit(clean(quest.title, "Untitled Quest"), 160);
            this.category = limit(category(quest), 160);
            this.rank = rank(quest);
            Village matchedVillage = village(this.category);
            this.village = matchedVillage == null ? "" : matchedVillage.group();
            this.description = limit(clean(quest.logText, "No mission briefing is available."), 3000);
            this.completer = limit(clean(quest.completerNpc, ""), 120);
            this.rewards = rewards(quest);
            this.objectives = objectives(quest);
        }

        private NBTTagCompound write(EntityPlayerMP player, PlayerWrapper<EntityPlayerMP> wrapper,
                                      PlayerQuestData playerQuests) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Id", quest.id);
            tag.setString("Title", title);
            tag.setString("Category", category);
            tag.setString("Rank", rank);
            tag.setString("Village", village);
            tag.setString("Description", description);
            tag.setString("Completer", completer);

            QuestData active = playerQuests == null || playerQuests.activeQuests == null
                    ? null : playerQuests.activeQuests.get(Integer.valueOf(quest.id));
            boolean finished = playerQuests != null && playerQuests.finishedQuests != null
                    && playerQuests.finishedQuests.containsKey(Integer.valueOf(quest.id));
            int status;
            if (active != null) {
                status = isCompleted(player, active) ? 3 : 2;
            } else if (finished) {
                status = 4;
            } else {
                status = canAccept(wrapper) ? 1 : 0;
            }
            tag.setByte("Status", (byte) status);

            NBTTagList objectiveTags = new NBTTagList();
            boolean liveObjectives = false;
            if (wrapper != null) {
                try {
                    NBTTagList liveTags = new NBTTagList();
                    IQuestObjective[] objectives = quest.getObjectives(wrapper);
                    if (objectives != null) {
                        for (int i = 0; i < objectives.length && i < MAX_OBJECTIVES; i++) {
                            IQuestObjective objective = objectives[i];
                            if (objective == null) {
                                continue;
                            }
                            NBTTagCompound objectiveTag = new NBTTagCompound();
                            objectiveTag.setString("Text", limit(clean(objective.getText(), "Objective"), 300));
                            objectiveTag.setInteger("Progress", Math.max(0, objective.getProgress()));
                            objectiveTag.setInteger("Max", Math.max(0, objective.getMaxProgress()));
                            objectiveTag.setBoolean("Done", objective.isCompleted());
                            liveTags.appendTag(objectiveTag);
                        }
                        if (liveTags.tagCount() > 0) {
                            objectiveTags = liveTags;
                            liveObjectives = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (!liveObjectives) {
                for (StaticObjective objective : objectives) {
                    NBTTagCompound objectiveTag = new NBTTagCompound();
                    objectiveTag.setString("Text", objective.text);
                    objectiveTag.setInteger("Progress", status == 4 ? objective.maximum : 0);
                    objectiveTag.setInteger("Max", objective.maximum);
                    objectiveTag.setBoolean("Done", status == 4);
                    objectiveTags.appendTag(objectiveTag);
                }
            }
            tag.setTag("Objectives", objectiveTags);

            NBTTagList rewardTags = new NBTTagList();
            for (String reward : rewards) {
                NBTTagCompound rewardTag = new NBTTagCompound();
                rewardTag.setString("Text", reward);
                rewardTags.appendTag(rewardTag);
            }
            tag.setTag("Rewards", rewardTags);
            return tag;
        }

        private boolean canAccept(PlayerWrapper<EntityPlayerMP> wrapper) {
            if (wrapper == null) {
                return false;
            }
            try {
                return wrapper.canQuestBeAccepted(quest.id);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean isCompleted(EntityPlayerMP player, QuestData active) {
            if (active.isCompleted) {
                return true;
            }
            try {
                return quest.questInterface != null && quest.questInterface.isCompleted(player);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static List<String> rewards(Quest quest) {
        List<String> rewards = new ArrayList<String>();
        if (quest.rewardExp > 0) {
            rewards.add(quest.rewardExp + " XP");
        }
        if (quest.rewardItems != null && quest.rewardItems.items != null) {
            for (ItemStack stack : quest.rewardItems.items) {
                if (stack != null && !stack.isEmpty() && rewards.size() < 16) {
                    rewards.add(stack.getCount() + "x " + limit(stack.getDisplayName(), 120));
                }
            }
        }
        return Collections.unmodifiableList(rewards);
    }

    private static List<StaticObjective> objectives(Quest quest) {
        List<StaticObjective> objectives = new ArrayList<StaticObjective>();
        if (quest.questInterface instanceof QuestItem) {
            QuestItem itemQuest = (QuestItem) quest.questInterface;
            if (itemQuest.items != null && itemQuest.items.items != null) {
                for (ItemStack stack : itemQuest.items.items) {
                    if (stack != null && !stack.isEmpty()) {
                        objectives.add(new StaticObjective(stack.getDisplayName(), Math.max(1, stack.getCount())));
                    }
                }
            }
        } else if (quest.questInterface instanceof QuestKill) {
            QuestKill killQuest = (QuestKill) quest.questInterface;
            addTargets(objectives, killQuest.targets);
        } else if (quest.questInterface instanceof QuestManual) {
            QuestManual manualQuest = (QuestManual) quest.questInterface;
            addTargets(objectives, manualQuest.manuals);
        } else if (quest.questInterface instanceof QuestLocation) {
            QuestLocation locationQuest = (QuestLocation) quest.questInterface;
            addLocation(objectives, locationQuest.location);
            addLocation(objectives, locationQuest.location2);
            addLocation(objectives, locationQuest.location3);
        } else if (quest.questInterface instanceof QuestDialog) {
            QuestDialog dialogQuest = (QuestDialog) quest.questInterface;
            List<Integer> ids = new ArrayList<Integer>();
            if (dialogQuest.dialogs != null) {
                for (Integer id : dialogQuest.dialogs.values()) {
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            Collections.sort(ids);
            for (Integer id : ids) {
                if (id == null || id.intValue() <= 0) {
                    continue;
                }
                Dialog dialog = DialogController.instance == null || DialogController.instance.dialogs == null
                        ? null : DialogController.instance.dialogs.get(id);
                String name = dialog == null ? "Read dialogue " + id : "Read: " + clean(dialog.title, "dialogue " + id);
                objectives.add(new StaticObjective(name, 1));
            }
        }

        if (objectives.size() > MAX_OBJECTIVES) {
            objectives = new ArrayList<StaticObjective>(objectives.subList(0, MAX_OBJECTIVES));
        }
        return Collections.unmodifiableList(objectives);
    }

    private static void addTargets(List<StaticObjective> objectives, Map<String, Integer> targets) {
        if (targets == null) {
            return;
        }
        for (Map.Entry<String, Integer> target : targets.entrySet()) {
            String name = clean(target.getKey(), "Target");
            int amount = target.getValue() == null ? 1 : Math.max(1, target.getValue().intValue());
            objectives.add(new StaticObjective(name, amount));
        }
    }

    private static void addLocation(List<StaticObjective> objectives, String location) {
        String name = clean(location, "");
        if (!name.isEmpty()) {
            objectives.add(new StaticObjective(name, 1));
        }
    }

    private static final class StaticObjective {
        private final String text;
        private final int maximum;

        private StaticObjective(String text, int maximum) {
            this.text = limit(text, 300);
            this.maximum = maximum;
        }
    }

    private static Pattern rankPattern(String rank) {
        return Pattern.compile("(^|[^A-Z0-9])(?:" + rank + "\\s*[-_ ]?\\s*RANK|RANK\\s*[-_ ]?\\s*" + rank
                + ")([^A-Z0-9]|$)");
    }

    private static String rank(Quest quest) {
        String found = rank(category(quest));
        return found.isEmpty() ? rank(quest.title) : found;
    }

    private static String rank(String value) {
        String normalized = clean(value, "").toUpperCase(Locale.ROOT);
        for (int i = 0; i < RANK_PATTERNS.length; i++) {
            if (RANK_PATTERNS[i].matcher(normalized).find() || normalized.trim().equals(RANKS[i])) {
                return RANKS[i];
            }
        }
        return "";
    }

    private static Village village(String category) {
        String value = " " + clean(category, "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        if (has(value, "leaf") || has(value, "konoha") || has(value, "konohagakure")) return Village.LEAF;
        if (has(value, "sand") || has(value, "suna") || has(value, "sunagakure")) return Village.SAND;
        if (has(value, "mist") || has(value, "kiri") || has(value, "kirigakure")) return Village.MIST;
        if (has(value, "cloud") || has(value, "kumo") || has(value, "kumogakure")) return Village.CLOUD;
        if (has(value, "stone") || has(value, "iwa") || has(value, "iwagakure")) return Village.STONE;
        if (has(value, "rain") || has(value, "ame") || has(value, "amegakure")) return Village.RAIN;
        return null;
    }

    private static boolean has(String value, String token) {
        return value.contains(" " + token + " ");
    }

    private static String category(Quest quest) {
        return quest == null || quest.category == null ? "Unsorted" : limit(clean(quest.category.title, "Unsorted"), 160);
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String cleaned = value.replace('\r', ' ').trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
