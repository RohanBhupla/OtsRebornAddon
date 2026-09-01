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

        int catalogVersion = root.getInteger("CatalogVersion");
        boolean full = root.getBoolean("Full");
        if (!full && snapshot.catalogVersion != catalogVersion) {
            return;
        }
        List<Tab> tabs = full ? readTabs(root) : snapshot.tabs;
        List<Quest> quests = full ? readQuests(root) : mergeProgress(root, snapshot.quests);

        snapshot = new Snapshot(root.getBoolean("Available"), catalogVersion,
                root.getString("Village"), tabs, quests, readTracker(root), readMail(root));
        revision++;
    }

    private static List<Tab> readTabs(NBTTagCompound root) {
        List<Tab> tabs = new ArrayList<Tab>();
        NBTTagList tags = root.getTagList("Tabs", 10);
        for (int i = 0; i < tags.tagCount(); i++) {
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            tabs.add(new Tab(tag.getString("Id"), tag.getString("Title"),
                    tag.getString("Filter"), tag.getString("Village")));
        }
        return tabs;
    }

    private static List<Quest> readQuests(NBTTagCompound root) {
        List<Quest> quests = new ArrayList<Quest>();
        NBTTagList tags = root.getTagList("Quests", 10);
        for (int i = 0; i < tags.tagCount(); i++) {
            quests.add(readQuest(tags.getCompoundTagAt(i)));
        }
        return quests;
    }

    private static List<Quest> mergeProgress(NBTTagCompound root, List<Quest> current) {
        java.util.Map<String, NBTTagCompound> updates = new java.util.HashMap<String, NBTTagCompound>();
        NBTTagList tags = root.getTagList("Quests", 10);
        for (int i = 0; i < tags.tagCount(); i++) {
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            updates.put(questId(tag), tag);
        }
        List<Quest> merged = new ArrayList<Quest>(current.size());
        for (Quest quest : current) {
            NBTTagCompound update = updates.get(quest.id);
            merged.add(update == null ? quest : quest.withProgress(update));
        }
        return merged;
    }

    public static Snapshot get() {
        return snapshot;
    }

    public static int getRevision() {
        return revision;
    }

    private static Tracker readTracker(NBTTagCompound root) {
        if (!root.hasKey("Tracker", 10)) {
            return Tracker.empty();
        }
        NBTTagCompound tag = root.getCompoundTag("Tracker");
        return new Tracker(tag.getBoolean("active") || tag.getBoolean("Active"),
                string(tag, "title", "Title"), string(tag, "objective", "Objective"),
                integer(tag, "dimension", "Dimension"), decimal(tag, "x", "X"),
                decimal(tag, "y", "Y"), decimal(tag, "z", "Z"),
                Math.max(1.0D, decimal(tag, "radius", "Radius")));
    }

    private static Mailbox readMail(NBTTagCompound root) {
        if (!root.hasKey("Mail", 10)) return Mailbox.empty();
        NBTTagCompound mail = root.getCompoundTag("Mail");
        NBTTagList list = mail.getTagList("Messages", 10);
        List<MailMessage> messages = new ArrayList<MailMessage>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            List<String> items = readStrings(tag, "Items");
            messages.add(new MailMessage(tag.getString("Id"), tag.getString("Sender"),
                    tag.getString("Subject"), tag.getString("Body"), tag.getLong("SentAt"),
                    tag.getBoolean("Read"), tag.getBoolean("Claimed"), items));
        }
        return new Mailbox(mail.getInteger("Unread"), messages);
    }

    private static String string(NBTTagCompound tag, String primary, String alternate) {
        return tag.hasKey(primary) ? tag.getString(primary) : tag.getString(alternate);
    }

    private static int integer(NBTTagCompound tag, String primary, String alternate) {
        return tag.hasKey(primary) ? tag.getInteger(primary) : tag.getInteger(alternate);
    }

    private static double decimal(NBTTagCompound tag, String primary, String alternate) {
        return tag.hasKey(primary) ? tag.getDouble(primary) : tag.getDouble(alternate);
    }

    public static void reset() {
        snapshot = Snapshot.empty();
        revision++;
    }

    private static Quest readQuest(NBTTagCompound tag) {
        List<Objective> objectives = readObjectives(tag);
        List<String> rewards = readStrings(tag, "Rewards");
        List<String> rules = readStrings(tag, "Rules");

        return new Quest(questId(tag), tag.getString("Title"), tag.getString("Category"),
                tag.getString("Rank"), tag.getString("Village"), tag.getString("Description"),
                tag.getString("CompletionText"), tag.getString("Completer"), tag.getString("Repeat"),
                tag.getByte("Status"), tag.getBoolean("InstantCompletion"), tag.getBoolean("Claimable"),
                objectives, rules, rewards);
    }

    private static List<Objective> readObjectives(NBTTagCompound tag) {
        List<Objective> objectives = new ArrayList<Objective>();
        NBTTagList objectiveTags = tag.getTagList("Objectives", 10);
        for (int i = 0; i < objectiveTags.tagCount(); i++) {
            NBTTagCompound objective = objectiveTags.getCompoundTagAt(i);
            objectives.add(new Objective(objective.getString("Text"), objective.getInteger("Progress"),
                    objective.getInteger("Max"), objective.getBoolean("Done")));
        }

        return objectives;
    }

    private static String questId(NBTTagCompound tag) {
        return tag.hasKey("Id", 8) ? tag.getString("Id")
                : Integer.toString(tag.getInteger("Id"));
    }

    private static List<String> readStrings(NBTTagCompound tag, String key) {
        List<String> values = new ArrayList<String>();
        NBTTagList tags = tag.getTagList(key, 10);
        for (int i = 0; i < tags.tagCount(); i++) {
            values.add(tags.getCompoundTagAt(i).getString("Text"));
        }
        return values;
    }

    public static final class Snapshot {
        public final boolean available;
        public final int catalogVersion;
        public final String village;
        public final List<Tab> tabs;
        public final List<Quest> quests;
        public final Tracker tracker;
        public final Mailbox mail;

        private Snapshot(boolean available, int catalogVersion, String village, List<Tab> tabs,
                         List<Quest> quests, Tracker tracker, Mailbox mail) {
            this.available = available;
            this.catalogVersion = catalogVersion;
            this.village = village;
            this.tabs = Collections.unmodifiableList(new ArrayList<Tab>(tabs));
            this.quests = Collections.unmodifiableList(new ArrayList<Quest>(quests));
            this.tracker = tracker == null ? Tracker.empty() : tracker;
            this.mail = mail == null ? Mailbox.empty() : mail;
        }

        private static Snapshot empty() {
            return new Snapshot(false, 0, "", Collections.<Tab>emptyList(),
                    Collections.<Quest>emptyList(), Tracker.empty(), Mailbox.empty());
        }
    }

    public static final class Tracker {
        public final boolean active;
        public final String title;
        public final String objective;
        public final int dimension;
        public final double x;
        public final double y;
        public final double z;
        public final double radius;

        private Tracker(boolean active, String title, String objective, int dimension,
                        double x, double y, double z, double radius) {
            this.active = active;
            this.title = title == null ? "" : title;
            this.objective = objective == null ? "" : objective;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        private static Tracker empty() {
            return new Tracker(false, "", "", 0, 0.0D, 0.0D, 0.0D, 1.0D);
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
        public final String id;
        public final String title;
        public final String category;
        public final String rank;
        public final String village;
        public final String description;
        public final String completionText;
        public final String completer;
        public final String repeat;
        public final int status;
        public final boolean instantCompletion;
        public final boolean claimable;
        public final List<Objective> objectives;
        public final List<String> rules;
        public final List<String> rewards;

        private Quest(String id, String title, String category, String rank, String village, String description,
                      String completionText, String completer, String repeat, int status,
                      boolean instantCompletion, boolean claimable,
                      List<Objective> objectives, List<String> rules, List<String> rewards) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.rank = rank;
            this.village = village;
            this.description = description;
            this.completionText = completionText;
            this.completer = completer;
            this.repeat = repeat;
            this.status = status;
            this.instantCompletion = instantCompletion;
            this.claimable = claimable;
            this.objectives = Collections.unmodifiableList(new ArrayList<Objective>(objectives));
            this.rules = Collections.unmodifiableList(new ArrayList<String>(rules));
            this.rewards = Collections.unmodifiableList(new ArrayList<String>(rewards));
        }

        public String statusLabel() {
            switch (status) {
                case 1: return "Not started";
                case 2: return "Active";
                case 3: return "Ready to turn in";
                case 4: return "Completed";
                case 5: return "Paused";
                default: return "Locked";
            }
        }

        private Quest withProgress(NBTTagCompound tag) {
            List<Objective> updatedObjectives = tag.hasKey("Objectives", 9)
                    ? readObjectives(tag) : objectives;
            return new Quest(id, title, category, rank, village, description, completionText,
                    completer, repeat, tag.getByte("Status"), instantCompletion,
                    tag.getBoolean("Claimable"), updatedObjectives, rules, rewards);
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

    public static final class Mailbox {
        public final int unread;
        public final List<MailMessage> messages;

        private Mailbox(int unread, List<MailMessage> messages) {
            this.unread = Math.max(0, unread);
            this.messages = Collections.unmodifiableList(new ArrayList<MailMessage>(messages));
        }

        private static Mailbox empty() {
            return new Mailbox(0, Collections.<MailMessage>emptyList());
        }
    }

    public static final class MailMessage {
        public final String id;
        public final String sender;
        public final String subject;
        public final String body;
        public final long sentAt;
        public final boolean read;
        public final boolean claimed;
        public final List<String> items;

        private MailMessage(String id, String sender, String subject, String body,
                            long sentAt, boolean read, boolean claimed, List<String> items) {
            this.id = id;
            this.sender = sender;
            this.subject = subject;
            this.body = body;
            this.sentAt = sentAt;
            this.read = read;
            this.claimed = claimed;
            this.items = Collections.unmodifiableList(new ArrayList<String>(items));
        }
    }
}
