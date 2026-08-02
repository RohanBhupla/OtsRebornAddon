package net.rebornaddon.compat;

import com.feed_the_beast.ftbquests.integration.customnpcs.NPCDialogTask;
import com.feed_the_beast.ftbquests.integration.customnpcs.NPCQuestTask;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestShape;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.loot.RewardTable;
import com.feed_the_beast.ftbquests.quest.loot.WeightedReward;
import com.feed_the_beast.ftbquests.quest.reward.CommandReward;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.feed_the_beast.ftbquests.quest.reward.RandomReward;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.reward.XPReward;
import com.feed_the_beast.ftbquests.quest.task.Task;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.FactionOptions;
import noppes.npcs.controllers.data.PlayerMail;
import noppes.npcs.quests.QuestDialog;
import noppes.npcs.quests.QuestInterface;
import noppes.npcs.quests.QuestItem;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestLocation;
import noppes.npcs.quests.QuestManual;
import noppes.npcs.util.NBTJsonUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

public final class CustomNpcQuestImporter {
    private static final String TAG_SOURCE = "customnpcs";
    private static final String TAG_QUEST_PREFIX = "customnpc_quest_";
    private static final String TAG_REWARD_PREFIX = "customnpc_reward_";
    private static final String[] RANKS = {"D", "C", "B", "A", "S"};
    private static final Pattern[] RANK_PATTERNS = new Pattern[RANKS.length];

    static {
        for (int i = 0; i < RANKS.length; i++) {
            String rank = RANKS[i];
            RANK_PATTERNS[i] = Pattern.compile(
                    "(^|[^A-Z0-9])(" + rank + "\\s*[-_ ]?\\s*RANKS?|RANK\\s*[-_ ]?\\s*" + rank + ")([^A-Z0-9]|$)");
        }
    }

    private CustomNpcQuestImporter() {
    }

    public static Result importQuests(boolean preview) {
        return importQuests(preview, false);
    }

    public static Result importQuests(boolean preview, boolean keepCustomNpcRewards) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        QuestController questController = QuestController.instance;
        DialogController dialogController = DialogController.instance;

        if (file == null) {
            throw new IllegalStateException("FTB Quests is not ready yet");
        }

        if (questController == null || questController.quests == null) {
            throw new IllegalStateException("CustomNPCs quests are not ready yet");
        }

        Result result = new Result(preview, keepCustomNpcRewards);
        Map<Integer, Quest> ftbByNpcQuest = findExistingFtbQuests(file);
        List<noppes.npcs.controllers.data.Quest> npcQuests = sortedQuests(questController.quests.values());
        Map<Integer, Quest> importedByNpcQuest = new LinkedHashMap<Integer, Quest>(ftbByNpcQuest);
        List<noppes.npcs.controllers.data.Quest> rewardStripCandidates = new ArrayList<noppes.npcs.controllers.data.Quest>();

        for (noppes.npcs.controllers.data.Quest npcQuest : npcQuests) {
            result.scanned++;

            String rank = getRank(npcQuest);
            if (rank == null) {
                result.skipped++;
                continue;
            }

            if (hasCustomNpcRewardPayload(npcQuest)) {
                result.customNpcRewardSources++;
                if (!preview && !keepCustomNpcRewards) {
                    rewardStripCandidates.add(npcQuest);
                }
            }

            Quest existing = ftbByNpcQuest.get(Integer.valueOf(npcQuest.id));
            if (existing != null) {
                importedByNpcQuest.put(Integer.valueOf(npcQuest.id), existing);
                result.existing++;
                if (!preview) {
                    result.rewards += addRewards(file, existing, npcQuest, result);
                }
                continue;
            }

            result.imported++;

            if (preview) {
                continue;
            }

            Chapter chapter = getOrCreateChapter(file, rank, result);
            Quest ftbQuest = createQuest(file, chapter, npcQuest, dialogController);
            importedByNpcQuest.put(Integer.valueOf(npcQuest.id), ftbQuest);
            result.dialogTasks += addDialogTasks(file, ftbQuest, npcQuest, dialogController);
            result.rewards += addRewards(file, ftbQuest, npcQuest, result);
        }

        if (!preview) {
            result.dependencies = applyDependencies(npcQuests, importedByNpcQuest);

            if (!keepCustomNpcRewards && !rewardStripCandidates.isEmpty()) {
                try {
                    result.backupFile = writeRewardBackup(rewardStripCandidates);
                    for (noppes.npcs.controllers.data.Quest npcQuest : rewardStripCandidates) {
                        if (stripCustomNpcRewards(npcQuest)) {
                            npcQuest.save();
                            result.customNpcRewardsCleared++;
                        }
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("Reward backup failed: " + ex.getMessage(), ex);
                }
            }

            if (result.imported > 0 || result.dependencies > 0 || result.chaptersCreated > 0
                    || result.rewards > 0 || result.rewardTables > 0 || result.customNpcRewardsCleared > 0) {
                file.refreshIDMap();
                file.save();
                file.saveNow();
            }
        }

        return result;
    }

    private static Quest createQuest(ServerQuestFile file, Chapter chapter, noppes.npcs.controllers.data.Quest npcQuest,
                                     DialogController dialogController) {
        Quest ftbQuest = new Quest(chapter);
        ftbQuest.id = file.newID();
        ftbQuest.title = clean(npcQuest.title, "CustomNPCs Quest " + npcQuest.id);
        ftbQuest.subtitle = npcQuest.category == null ? "" : clean(npcQuest.category.title, "");
        ftbQuest.icon = ItemStack.EMPTY;
        ftbQuest.shape = QuestShape.CIRCLE;
        ftbQuest.canRepeat = npcQuest.getIsRepeatable();
        ftbQuest.x = (chapter.quests.size() % 6) * 2.0D;
        ftbQuest.y = (chapter.quests.size() / 6) * 2.0D;
        ftbQuest.description.addAll(buildDescription(npcQuest, dialogController));
        ftbQuest.getTags().add(TAG_SOURCE);
        ftbQuest.getTags().add(TAG_QUEST_PREFIX + npcQuest.id);
        ftbQuest.onCreated();
        file.refreshIDMap();

        NPCQuestTask questTask = new NPCQuestTask(ftbQuest);
        questTask.id = file.newID();
        questTask.title = "Complete CustomNPCs quest";
        questTask.npcQuest = npcQuest.id;
        questTask.checkActive = false;
        questTask.onCreated();
        file.refreshIDMap();

        return ftbQuest;
    }

    private static int addDialogTasks(ServerQuestFile file, Quest ftbQuest, noppes.npcs.controllers.data.Quest npcQuest,
                                      DialogController dialogController) {
        int count = 0;

        for (Integer dialogId : getRequiredDialogIds(npcQuest)) {
            if (dialogId == null || dialogId.intValue() <= 0) {
                continue;
            }

            NPCDialogTask dialogTask = new NPCDialogTask(ftbQuest);
            dialogTask.id = file.newID();
            dialogTask.npcDialog = dialogId.intValue();
            Dialog dialog = getDialog(dialogController, dialogId.intValue());
            dialogTask.title = dialog == null ? "Read dialog " + dialogId : clean(dialog.title, "Read dialog " + dialogId);
            dialogTask.onCreated();
            file.refreshIDMap();
            count++;
        }

        return count;
    }

    private static int addRewards(ServerQuestFile file, Quest ftbQuest, noppes.npcs.controllers.data.Quest npcQuest,
                                  Result result) {
        int count = 0;

        if (npcQuest.rewardExp > 0) {
            String tag = rewardTag(npcQuest, "xp");
            if (!hasRewardTag(ftbQuest, tag)) {
                XPReward reward = new XPReward(ftbQuest);
                reward.xp = npcQuest.rewardExp;
                count += addRewardObject(file, reward, "Experience", tag);
            }
        }

        if (!isBlank(npcQuest.command)) {
            String tag = rewardTag(npcQuest, "command");
            if (!hasRewardTag(ftbQuest, tag)) {
                CommandReward reward = new CommandReward(ftbQuest);
                reward.command = normalizeCommand(npcQuest.command);
                reward.playerCommand = false;
                count += addRewardObject(file, reward, "Command", tag);
            }
        }

        count += addFactionRewards(file, ftbQuest, npcQuest);
        count += addInventoryRewards(file, ftbQuest, npcQuest, result);
        count += addMailRewards(file, ftbQuest, npcQuest);

        if (addMailDescription(ftbQuest, npcQuest)) {
            result.descriptionsUpdated++;
        }

        return count;
    }

    private static int addFactionRewards(ServerQuestFile file, Quest ftbQuest,
                                         noppes.npcs.controllers.data.Quest npcQuest) {
        FactionOptions options = npcQuest.factionOptions;
        if (options == null) {
            return 0;
        }

        int count = 0;
        count += addFactionReward(file, ftbQuest, npcQuest, "faction_1", options.factionId,
                options.factionPoints, options.decreaseFactionPoints);
        count += addFactionReward(file, ftbQuest, npcQuest, "faction_2", options.faction2Id,
                options.faction2Points, options.decreaseFaction2Points);
        return count;
    }

    private static int addFactionReward(ServerQuestFile file, Quest ftbQuest,
                                        noppes.npcs.controllers.data.Quest npcQuest, String key, int factionId,
                                        int points, boolean decrease) {
        if (factionId < 0 || points == 0) {
            return 0;
        }

        String tag = rewardTag(npcQuest, key);
        if (hasRewardTag(ftbQuest, tag)) {
            return 0;
        }

        CommandReward reward = new CommandReward(ftbQuest);
        reward.command = "/noppes faction @p " + factionId + " " + (decrease ? "subtract" : "add") + " " + Math.abs(points);
        reward.playerCommand = false;
        return addRewardObject(file, reward, "Faction", tag);
    }

    private static int addInventoryRewards(ServerQuestFile file, Quest ftbQuest,
                                           noppes.npcs.controllers.data.Quest npcQuest, Result result) {
        List<InventoryReward> items = collectInventoryRewards(npcQuest.rewardItems);
        if (items.isEmpty()) {
            return 0;
        }

        if (npcQuest.randomReward && items.size() > 1) {
            String tag = rewardTag(npcQuest, "random_items");
            if (hasRewardTag(ftbQuest, tag)) {
                return 0;
            }

            RewardTable table = new RewardTable(file);
            table.id = file.newID();
            table.title = clean(npcQuest.title, "Quest") + " Rewards";
            table.useTitle = true;
            table.lootSize = 1;
            table.onCreated();
            file.refreshIDMap();
            result.rewardTables++;

            for (InventoryReward item : items) {
                ItemReward reward = new ItemReward(table.fakeQuest, item.stack.copy());
                reward.id = file.newID();
                reward.title = item.stack.getDisplayName();
                reward.getTags().add(TAG_SOURCE);
                reward.getTags().add(rewardTag(npcQuest, "random_item_" + item.slot));
                table.rewards.add(new WeightedReward(reward, 1));
                file.refreshIDMap();
                result.rewardTableEntries++;
            }

            RandomReward reward = new RandomReward(ftbQuest);
            reward.table = table;
            return addRewardObject(file, reward, "Random Item", tag);
        }

        int count = 0;
        for (InventoryReward item : items) {
            String tag = rewardTag(npcQuest, "item_" + item.slot);
            if (hasRewardTag(ftbQuest, tag)) {
                continue;
            }

            count += addItemReward(file, ftbQuest, item.stack, item.stack.getDisplayName(), tag);
        }

        return count;
    }

    private static int addMailRewards(ServerQuestFile file, Quest ftbQuest, noppes.npcs.controllers.data.Quest npcQuest) {
        PlayerMail mail = npcQuest.mail;
        if (mail == null || mail.items == null || mail.items.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < mail.items.size(); slot++) {
            ItemStack stack = mail.items.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String tag = rewardTag(npcQuest, "mail_item_" + slot);
            if (hasRewardTag(ftbQuest, tag)) {
                continue;
            }

            count += addItemReward(file, ftbQuest, stack, "Mail: " + stack.getDisplayName(), tag);
        }

        return count;
    }

    private static int addItemReward(ServerQuestFile file, Quest ftbQuest, ItemStack stack, String title, String tag) {
        ItemReward reward = new ItemReward(ftbQuest, stack.copy());
        return addRewardObject(file, reward, title, tag);
    }

    private static int addRewardObject(ServerQuestFile file, Reward reward, String title, String tag) {
        reward.id = file.newID();
        reward.title = title;
        reward.getTags().add(TAG_SOURCE);
        reward.getTags().add(tag);
        reward.onCreated();
        file.refreshIDMap();
        return 1;
    }

    private static boolean hasRewardTag(Quest quest, String tag) {
        for (Reward reward : quest.rewards) {
            if (reward.getTags().contains(tag)) {
                return true;
            }
        }

        return false;
    }

    private static String rewardTag(noppes.npcs.controllers.data.Quest quest, String key) {
        return TAG_REWARD_PREFIX + quest.id + "_" + key;
    }

    private static List<InventoryReward> collectInventoryRewards(NpcMiscInventory inventory) {
        List<InventoryReward> items = new ArrayList<InventoryReward>();
        if (inventory == null || inventory.items == null || inventory.items.isEmpty()) {
            return items;
        }

        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack != null && !stack.isEmpty()) {
                items.add(new InventoryReward(slot, stack.copy()));
            }
        }

        return items;
    }

    private static Chapter getOrCreateChapter(ServerQuestFile file, String rank, Result result) {
        String title = rank + "-Rank";

        for (Chapter chapter : file.chapters) {
            if (sameRankTitle(chapter.title, rank)) {
                return chapter;
            }
        }

        Chapter chapter = new Chapter(file);
        chapter.id = file.newID();
        chapter.title = title;
        chapter.defaultQuestShape = QuestShape.CIRCLE;
        chapter.icon = ItemStack.EMPTY;
        chapter.onCreated();
        file.refreshIDMap();
        result.chaptersCreated++;
        return chapter;
    }

    private static int applyDependencies(List<noppes.npcs.controllers.data.Quest> npcQuests, Map<Integer, Quest> ftbByNpcQuest) {
        int count = 0;

        for (noppes.npcs.controllers.data.Quest npcQuest : npcQuests) {
            if (npcQuest.nextQuestid <= 0) {
                continue;
            }

            Quest current = ftbByNpcQuest.get(Integer.valueOf(npcQuest.id));
            Quest next = ftbByNpcQuest.get(Integer.valueOf(npcQuest.nextQuestid));

            if (current != null && next != null && current != next && !next.dependencies.contains(current)) {
                next.dependencies.add(current);
                count++;
            }
        }

        return count;
    }

    private static Map<Integer, Quest> findExistingFtbQuests(ServerQuestFile file) {
        Map<Integer, Quest> quests = new LinkedHashMap<Integer, Quest>();

        for (Chapter chapter : file.chapters) {
            for (Quest quest : chapter.quests) {
                Integer id = getTaggedNpcQuestId(quest);
                if (id != null) {
                    quests.put(id, quest);
                    continue;
                }

                for (Task task : quest.tasks) {
                    if (task instanceof NPCQuestTask) {
                        NPCQuestTask npcQuestTask = (NPCQuestTask) task;
                        if (npcQuestTask.npcQuest > 0) {
                            quests.put(Integer.valueOf(npcQuestTask.npcQuest), quest);
                            break;
                        }
                    }
                }
            }
        }

        return quests;
    }

    private static Integer getTaggedNpcQuestId(Quest quest) {
        for (String tag : quest.getTags()) {
            if (!tag.startsWith(TAG_QUEST_PREFIX)) {
                continue;
            }

            try {
                return Integer.valueOf(Integer.parseInt(tag.substring(TAG_QUEST_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private static List<noppes.npcs.controllers.data.Quest> sortedQuests(Collection<noppes.npcs.controllers.data.Quest> quests) {
        List<noppes.npcs.controllers.data.Quest> sorted = new ArrayList<noppes.npcs.controllers.data.Quest>(quests);
        Collections.sort(sorted, new Comparator<noppes.npcs.controllers.data.Quest>() {
            @Override
            public int compare(noppes.npcs.controllers.data.Quest left, noppes.npcs.controllers.data.Quest right) {
                int leftCategory = left.category == null ? 0 : left.category.id;
                int rightCategory = right.category == null ? 0 : right.category.id;

                if (leftCategory != rightCategory) {
                    return leftCategory < rightCategory ? -1 : 1;
                }

                return left.id < right.id ? -1 : left.id == right.id ? 0 : 1;
            }
        });
        return sorted;
    }

    private static String getRank(noppes.npcs.controllers.data.Quest quest) {
        if (quest.category == null) {
            return null;
        }

        String title = clean(quest.category.title, "").toUpperCase(Locale.ROOT);

        for (int i = 0; i < RANKS.length; i++) {
            if (RANK_PATTERNS[i].matcher(title).find()) {
                return RANKS[i];
            }
        }

        return null;
    }

    private static boolean sameRankTitle(String title, String rank) {
        String cleaned = clean(title, "").toUpperCase(Locale.ROOT);
        return cleaned.equals(rank + "-RANK") || cleaned.equals(rank + " RANK") || cleaned.equals(rank + "_RANK");
    }

    private static List<String> buildDescription(noppes.npcs.controllers.data.Quest quest, DialogController dialogController) {
        List<String> lines = new ArrayList<String>();
        addTextBlock(lines, quest.logText);
        addObjectiveLines(lines, quest);
        addDialogLines(lines, quest, dialogController);
        addMailLines(lines, quest);
        addCompletionLines(lines, quest);
        return lines;
    }

    private static void addObjectiveLines(List<String> lines, noppes.npcs.controllers.data.Quest quest) {
        QuestInterface questInterface = quest.questInterface;

        if (questInterface == null) {
            return;
        }

        List<String> objectives = new ArrayList<String>();

        if (questInterface instanceof QuestItem) {
            QuestItem itemQuest = (QuestItem) questInterface;
            if (itemQuest.items != null) {
                addItemObjectives(objectives, itemQuest.items);
            }
        } else if (questInterface instanceof QuestKill) {
            QuestKill killQuest = (QuestKill) questInterface;
            addTargetObjectives(objectives, killQuest.targets);
        } else if (questInterface instanceof QuestManual) {
            QuestManual manualQuest = (QuestManual) questInterface;
            addTargetObjectives(objectives, manualQuest.manuals);
        } else if (questInterface instanceof QuestLocation) {
            QuestLocation locationQuest = (QuestLocation) questInterface;
            addLocationObjective(objectives, locationQuest.location);
            addLocationObjective(objectives, locationQuest.location2);
            addLocationObjective(objectives, locationQuest.location3);
        } else if (questInterface instanceof QuestDialog) {
            Set<Integer> required = getRequiredDialogIds(quest);
            for (Integer dialogId : required) {
                objectives.add("Read dialog " + dialogId);
            }
        }

        if (!objectives.isEmpty()) {
            addBreak(lines);
            lines.add("Objectives");
            lines.addAll(objectives);
        }
    }

    private static void addItemObjectives(List<String> objectives, NpcMiscInventory inventory) {
        for (ItemStack stack : inventory.items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            objectives.add(stack.getCount() + "x " + stack.getDisplayName());
        }
    }

    private static void addTargetObjectives(List<String> objectives, Map<String, Integer> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : targets.entrySet()) {
            String name = clean(entry.getKey(), "");
            if (name.isEmpty()) {
                continue;
            }

            int amount = entry.getValue() == null ? 1 : Math.max(1, entry.getValue().intValue());
            objectives.add(amount + "x " + name);
        }
    }

    private static void addLocationObjective(List<String> objectives, String location) {
        String cleaned = clean(location, "");
        if (!cleaned.isEmpty()) {
            objectives.add(cleaned);
        }
    }

    private static void addDialogLines(List<String> lines, noppes.npcs.controllers.data.Quest quest, DialogController dialogController) {
        if (dialogController == null || dialogController.dialogs == null || dialogController.dialogs.isEmpty()) {
            return;
        }

        Set<Integer> required = getRequiredDialogIds(quest);
        List<Dialog> dialogs = new ArrayList<Dialog>(dialogController.dialogs.values());
        Collections.sort(dialogs, new Comparator<Dialog>() {
            @Override
            public int compare(Dialog left, Dialog right) {
                return left.id < right.id ? -1 : left.id == right.id ? 0 : 1;
            }
        });

        boolean started = false;

        for (Dialog dialog : dialogs) {
            if (dialog == null || (dialog.quest != quest.id && !required.contains(Integer.valueOf(dialog.id)))) {
                continue;
            }

            if (!started) {
                addBreak(lines);
                lines.add("Dialogue");
                started = true;
            }

            String title = clean(dialog.title, "");
            if (!title.isEmpty()) {
                lines.add(title);
            }

            addTextBlock(lines, dialog.text);
        }
    }

    private static void addCompletionLines(List<String> lines, noppes.npcs.controllers.data.Quest quest) {
        if (!isBlank(quest.completeText)) {
            addBreak(lines);
            lines.add("Completion");
            addTextBlock(lines, quest.completeText);
        }

        if (!isBlank(quest.nextQuestTitle)) {
            addBreak(lines);
            lines.add("Next");
            lines.add(clean(quest.nextQuestTitle, ""));
        }
    }

    private static boolean addMailDescription(Quest ftbQuest, noppes.npcs.controllers.data.Quest npcQuest) {
        if (containsLine(ftbQuest.description, "Mail")) {
            return false;
        }

        int previousSize = ftbQuest.description.size();
        addMailLines(ftbQuest.description, npcQuest);
        return ftbQuest.description.size() != previousSize;
    }

    private static void addMailLines(List<String> lines, noppes.npcs.controllers.data.Quest quest) {
        PlayerMail mail = quest.mail;
        if (mail == null || (!mail.isValid() && !mailHasItems(mail))) {
            return;
        }

        boolean hasText = !isBlank(mail.subject) || !isBlank(mail.sender);
        String[] text = mail.getText();
        if (text != null) {
            for (String line : text) {
                if (!isBlank(line)) {
                    hasText = true;
                    break;
                }
            }
        }

        if (!hasText) {
            return;
        }

        addBreak(lines);
        lines.add("Mail");

        if (!isBlank(mail.subject)) {
            lines.add(clean(mail.subject, ""));
        }

        if (!isBlank(mail.sender)) {
            lines.add("From: " + clean(mail.sender, ""));
        }

        if (text != null) {
            for (String line : text) {
                addTextBlock(lines, line);
            }
        }
    }

    private static boolean containsLine(List<String> lines, String value) {
        for (String line : lines) {
            if (value.equals(line)) {
                return true;
            }
        }

        return false;
    }

    private static Set<Integer> getRequiredDialogIds(noppes.npcs.controllers.data.Quest quest) {
        Set<Integer> ids = new LinkedHashSet<Integer>();

        if (quest.questInterface instanceof QuestDialog) {
            QuestDialog dialogQuest = (QuestDialog) quest.questInterface;
            if (dialogQuest.dialogs == null) {
                return ids;
            }

            for (Integer id : dialogQuest.dialogs.values()) {
                if (id != null && id.intValue() > 0) {
                    ids.add(id);
                }
            }
        }

        return ids;
    }

    private static Dialog getDialog(DialogController dialogController, int id) {
        if (dialogController == null || dialogController.dialogs == null) {
            return null;
        }

        return dialogController.dialogs.get(Integer.valueOf(id));
    }

    private static void addTextBlock(List<String> lines, String text) {
        if (isBlank(text)) {
            return;
        }

        String[] split = text.replace('\r', '\n').split("\n");
        for (String line : split) {
            String cleaned = clean(line, "");
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
    }

    private static void addBreak(List<String> lines) {
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
            lines.add("");
        }
    }

    private static boolean hasCustomNpcRewardPayload(noppes.npcs.controllers.data.Quest quest) {
        return quest.rewardExp > 0
                || !isBlank(quest.command)
                || inventoryHasItems(quest.rewardItems)
                || hasFactionReward(quest.factionOptions)
                || hasMailReward(quest.mail)
                || quest.randomReward;
    }

    private static boolean stripCustomNpcRewards(noppes.npcs.controllers.data.Quest quest) {
        boolean changed = false;

        if (quest.rewardExp != 0) {
            quest.rewardExp = 0;
            changed = true;
        }

        if (!isBlank(quest.command)) {
            quest.command = "";
            changed = true;
        }

        if (inventoryHasItems(quest.rewardItems)) {
            quest.rewardItems = new NpcMiscInventory(9);
            changed = true;
        }

        if (quest.randomReward) {
            quest.randomReward = false;
            changed = true;
        }

        if (hasFactionReward(quest.factionOptions)) {
            quest.factionOptions = new FactionOptions();
            changed = true;
        }

        if (hasMailReward(quest.mail)) {
            quest.mail = new PlayerMail();
            changed = true;
        }

        return changed;
    }

    private static String writeRewardBackup(List<noppes.npcs.controllers.data.Quest> quests) throws Exception {
        File worldDir = CustomNpcs.getWorldSaveDirectory();
        if (worldDir == null) {
            throw new IllegalStateException("CustomNPC world save directory is not available");
        }

        File dir = new File(worldDir, "rebornaddon_quest_backups");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create " + dir.getAbsolutePath());
        }

        File file = new File(dir, "customnpc_quest_rewards_" + backupStamp() + ".json");
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();

        root.setInteger("Version", 1);
        root.setLong("Created", System.currentTimeMillis());

        for (noppes.npcs.controllers.data.Quest quest : quests) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("QuestId", quest.id);
            entry.setString("Title", clean(quest.title, ""));
            entry.setString("Category", quest.category == null ? "" : clean(quest.category.title, ""));
            entry.setTag("Quest", quest.writeToNBT(new NBTTagCompound()));
            list.appendTag(entry);
        }

        root.setTag("Quests", list);
        NBTJsonUtil.SaveFile(file, root);
        return file.getAbsolutePath();
    }

    private static String backupStamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date()) + "-utc";
    }

    private static boolean inventoryHasItems(NpcMiscInventory inventory) {
        return inventory != null && inventory.items != null && itemListHasItems(inventory.items);
    }

    private static boolean mailHasItems(PlayerMail mail) {
        return mail != null && mail.items != null && itemListHasItems(mail.items);
    }

    private static boolean itemListHasItems(Iterable<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasFactionReward(FactionOptions options) {
        return options != null
                && ((options.factionId >= 0 && options.factionPoints != 0)
                || (options.faction2Id >= 0 && options.faction2Points != 0));
    }

    private static boolean hasMailReward(PlayerMail mail) {
        return mail != null && (mail.isValid() || mailHasItems(mail));
    }

    private static String normalizeCommand(String command) {
        String cleaned = clean(command, "");
        return cleaned.isEmpty() || cleaned.startsWith("/") ? cleaned : "/" + cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static final class InventoryReward {
        private final int slot;
        private final ItemStack stack;

        private InventoryReward(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    public static final class Result {
        public final boolean preview;
        public final boolean keepCustomNpcRewards;
        public int scanned;
        public int imported;
        public int existing;
        public int skipped;
        public int dialogTasks;
        public int dependencies;
        public int chaptersCreated;
        public int rewards;
        public int rewardTables;
        public int rewardTableEntries;
        public int descriptionsUpdated;
        public int customNpcRewardSources;
        public int customNpcRewardsCleared;
        public String backupFile = "";

        private Result(boolean preview, boolean keepCustomNpcRewards) {
            this.preview = preview;
            this.keepCustomNpcRewards = keepCustomNpcRewards;
        }
    }
}
