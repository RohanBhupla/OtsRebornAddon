package net.rebornaddon.quest.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientQuestData {
    private static Snapshot snapshot = Snapshot.empty();
    private static int revision;

    private ClientQuestData() {
    }

    public static void update(NBTTagCompound root) {
        if (root == null) {
            snapshot = Snapshot.empty();
            revision++;
            return;
        }

        List<Tab> tabs = new ArrayList<Tab>();
        NBTTagList tabTags = root.getTagList("Tabs", 10);
        for (int i = 0; i < tabTags.tagCount(); i++) {
            NBTTagCompound tag = tabTags.getCompoundTagAt(i);
            tabs.add(new Tab(tag.getString("Id"), tag.getString("Title"),
                    tag.getString("Filter"), tag.getString("Village")));
        }

        List<Quest> quests = new ArrayList<Quest>();
        NBTTagList questTags = root.getTagList("Quests", 10);
        for (int i = 0; i < questTags.tagCount(); i++) {
            quests.add(readQuest(questTags.getCompoundTagAt(i)));
        }

        snapshot = new Snapshot(root.getBoolean("Available"), root.getInteger("CatalogVersion"),
                root.getString("Village"), tabs, quests);
        revision++;
    }

    public static Snapshot get() {
        return snapshot;
    }

    public static int getRevision() {
        return revision;
    }

    private static Quest readQuest(NBTTagCompound tag) {
        List<Objective> objectives = new ArrayList<Objective>();
        NBTTagList objectiveTags = tag.getTagList("Objectives", 10);
        for (int i = 0; i < objectiveTags.tagCount(); i++) {
            NBTTagCompound objective = objectiveTags.getCompoundTagAt(i);
            objectives.add(new Objective(objective.getString("Text"), objective.getInteger("Progress"),
                    objective.getInteger("Max"), objective.getBoolean("Done")));
        }

        List<String> rewards = new ArrayList<String>();
        NBTTagList rewardTags = tag.getTagList("Rewards", 10);
        for (int i = 0; i < rewardTags.tagCount(); i++) {
            rewards.add(rewardTags.getCompoundTagAt(i).getString("Text"));
        }

        return new Quest(tag.getInteger("Id"), tag.getString("Title"), tag.getString("Category"),
                tag.getString("Rank"), tag.getString("Village"), tag.getString("Description"),
                tag.getString("Completer"), tag.getByte("Status"), objectives, rewards);
    }

    public static final class Snapshot {
        public final boolean available;
        public final int catalogVersion;
        public final String village;
        public final List<Tab> tabs;
        public final List<Quest> quests;

        private Snapshot(boolean available, int catalogVersion, String village, List<Tab> tabs, List<Quest> quests) {
            this.available = available;
            this.catalogVersion = catalogVersion;
            this.village = village;
            this.tabs = Collections.unmodifiableList(new ArrayList<Tab>(tabs));
            this.quests = Collections.unmodifiableList(new ArrayList<Quest>(quests));
        }

        private static Snapshot empty() {
            return new Snapshot(false, 0, "", Collections.<Tab>emptyList(), Collections.<Quest>emptyList());
        }
    }

    public static final class Tab {
        public final String id;
        public final String title;
        public final String filter;
        public final String village;

        private Tab(String id, String title, String filter, String village) {
            this.id = id;
            this.title = title;
            this.filter = filter;
            this.village = village;
        }
    }

    public static final class Quest {
        public final int id;
        public final String title;
        public final String category;
        public final String rank;
        public final String village;
        public final String description;
        public final String completer;
        public final int status;
        public final List<Objective> objectives;
        public final List<String> rewards;

        private Quest(int id, String title, String category, String rank, String village, String description,
                      String completer, int status, List<Objective> objectives, List<String> rewards) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.rank = rank;
            this.village = village;
            this.description = description;
            this.completer = completer;
            this.status = status;
            this.objectives = Collections.unmodifiableList(new ArrayList<Objective>(objectives));
            this.rewards = Collections.unmodifiableList(new ArrayList<String>(rewards));
        }

        public String statusLabel() {
            switch (status) {
                case 1: return "Available";
                case 2: return "Active";
                case 3: return "Ready to turn in";
                case 4: return "Completed";
                default: return "Locked";
            }
        }
    }

    public static final class Objective {
        public final String text;
        public final int progress;
        public final int maximum;
        public final boolean complete;

        private Objective(String text, int progress, int maximum, boolean complete) {
            this.text = text;
            this.progress = progress;
            this.maximum = maximum;
            this.complete = complete;
        }
    }
}
