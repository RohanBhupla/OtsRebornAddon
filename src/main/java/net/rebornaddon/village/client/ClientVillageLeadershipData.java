package net.rebornaddon.village.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ClientVillageLeadershipData {
    private static Snapshot snapshot = Snapshot.empty();
    private static int revision;

    private ClientVillageLeadershipData() {
    }

    public static void update(NBTTagCompound root) {
        snapshot = root == null ? Snapshot.empty() : read(root);
        revision++;
    }

    public static Snapshot get() {
        return snapshot;
    }

    public static int getRevision() {
        return revision;
    }

    public static void reset() {
        snapshot = Snapshot.empty();
        revision++;
    }

    private static Snapshot read(NBTTagCompound root) {
        List<Member> members = new ArrayList<Member>();
        NBTTagList memberTags = root.getTagList("Members", 10);
        for (int i = 0; i < memberTags.tagCount(); i++) {
            NBTTagCompound tag = memberTags.getCompoundTagAt(i);
            UUID id = parse(tag.getString("Id"));
            if (id != null && !tag.getString("Name").isEmpty()) {
                members.add(new Member(id, tag.getString("Name"), tag.getBoolean("Online"),
                        tag.getBoolean("Kage"), tag.getBoolean("Advisor"), tag.getBoolean("Anbu"),
                        tag.getBoolean("Captain"), tag.getInteger("Votes")));
            }
        }
        Collections.sort(members, MEMBER_ORDER);

        List<Member> online = new ArrayList<Member>();
        NBTTagList onlineTags = root.getTagList("Online", 10);
        for (int i = 0; i < onlineTags.tagCount(); i++) {
            NBTTagCompound tag = onlineTags.getCompoundTagAt(i);
            UUID id = parse(tag.getString("Id"));
            if (id != null && !tag.getString("Name").isEmpty()) {
                online.add(new Member(id, tag.getString("Name"), true,
                        false, false, false, false, 0));
            }
        }
        Collections.sort(online, MEMBER_ORDER);

        int villageId = root.hasKey("Village", 3) ? root.getInteger("Village") : -1;
        return new Snapshot(root.getBoolean("Access"), root.getString("Message"),
                villageId, root.getBoolean("Operator"), root.getBoolean("Kage"),
                root.getBoolean("Advisor"), root.getBoolean("Captain"), root.getBoolean("SwitchVillage"),
                root.getBoolean("ManageAdvisors"), root.getBoolean("ManageAnbu"), root.getBoolean("Vote"),
                root.getBoolean("Hunt"), root.getBoolean("StartEvents"),
                root.getInteger("AdvisorCount"), root.getInteger("AnbuCount"), members, online,
                readPresets(root, "VillageEvents"), readPresets(root, "ServerEvents"),
                readPresets(root, "Locations"), readHunt(root), root.getLong("HuntCooldown"),
                readEvent(root, "ActiveVillageEvent"), readEvent(root, "ActiveServerEvent"));
    }

    private static List<Preset> readPresets(NBTTagCompound root, String key) {
        List<Preset> result = new ArrayList<Preset>();
        NBTTagList tags = root.getTagList(key, 10);
        for (int i = 0; i < tags.tagCount(); i++) {
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            result.add(new Preset(tag.getString("Id"), tag.getString("Name")));
        }
        return result;
    }

    private static Hunt readHunt(NBTTagCompound root) {
        if (!root.hasKey("Hunt", 10)) {
            return null;
        }
        NBTTagCompound tag = root.getCompoundTag("Hunt");
        return new Hunt(tag.getInteger("State"), tag.getString("Target"),
                tag.getString("Captain"), tag.getLong("Expires"));
    }

    private static Event readEvent(NBTTagCompound root, String key) {
        if (!root.hasKey(key, 10)) {
            return null;
        }
        NBTTagCompound tag = root.getCompoundTag(key);
        return new Event(tag.getString("Name"), tag.getString("Location"), tag.getString("StartedBy"));
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final Comparator<Member> MEMBER_ORDER = new Comparator<Member>() {
        @Override
        public int compare(Member left, Member right) {
            if (left.online != right.online) {
                return left.online ? -1 : 1;
            }
            return left.name.compareToIgnoreCase(right.name);
        }
    };

    public static final class Snapshot {
        public final boolean access;
        public final String message;
        public final int villageId;
        public final boolean operator;
        public final boolean kage;
        public final boolean advisor;
        public final boolean captain;
        public final boolean switchVillage;
        public final boolean manageAdvisors;
        public final boolean manageAnbu;
        public final boolean vote;
        public final boolean hunt;
        public final boolean startEvents;
        public final int advisorCount;
        public final int anbuCount;
        public final List<Member> members;
        public final List<Member> online;
        public final List<Preset> villageEvents;
        public final List<Preset> serverEvents;
        public final List<Preset> locations;
        public final Hunt activeHunt;
        public final long huntCooldown;
        public final Event villageEvent;
        public final Event serverEvent;

        private Snapshot(boolean access, String message, int villageId, boolean operator,
                         boolean kage, boolean advisor, boolean captain, boolean switchVillage,
                         boolean manageAdvisors, boolean manageAnbu, boolean vote, boolean hunt,
                         boolean startEvents, int advisorCount, int anbuCount,
                         List<Member> members, List<Member> online,
                         List<Preset> villageEvents, List<Preset> serverEvents, List<Preset> locations,
                         Hunt activeHunt, long huntCooldown, Event villageEvent, Event serverEvent) {
            this.access = access;
            this.message = message;
            this.villageId = villageId;
            this.operator = operator;
            this.kage = kage;
            this.advisor = advisor;
            this.captain = captain;
            this.switchVillage = switchVillage;
            this.manageAdvisors = manageAdvisors;
            this.manageAnbu = manageAnbu;
            this.vote = vote;
            this.hunt = hunt;
            this.startEvents = startEvents;
            this.advisorCount = advisorCount;
            this.anbuCount = anbuCount;
            this.members = immutable(members);
            this.online = immutable(online);
            this.villageEvents = immutable(villageEvents);
            this.serverEvents = immutable(serverEvents);
            this.locations = immutable(locations);
            this.activeHunt = activeHunt;
            this.huntCooldown = huntCooldown;
            this.villageEvent = villageEvent;
            this.serverEvent = serverEvent;
        }

        private static Snapshot empty() {
            return new Snapshot(false, "", -1, false, false, false, false, false,
                    false, false, false, false, false, 0, 0, Collections.<Member>emptyList(),
                    Collections.<Member>emptyList(), Collections.<Preset>emptyList(),
                    Collections.<Preset>emptyList(), Collections.<Preset>emptyList(),
                    null, 0L, null, null);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static final class Member {
        public final UUID id;
        public final String name;
        public final boolean online;
        public final boolean kage;
        public final boolean advisor;
        public final boolean anbu;
        public final boolean captain;
        public final int votes;

        private Member(UUID id, String name, boolean online, boolean kage, boolean advisor,
                       boolean anbu, boolean captain, int votes) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.kage = kage;
            this.advisor = advisor;
            this.anbu = anbu;
            this.captain = captain;
            this.votes = votes;
        }
    }

    public static final class Preset {
        public final String id;
        public final String name;

        private Preset(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Hunt {
        public final int state;
        public final String target;
        public final String captain;
        public final long remaining;

        private Hunt(int state, String target, String captain, long remaining) {
            this.state = state;
            this.target = target;
            this.captain = captain;
            this.remaining = remaining;
        }
    }

    public static final class Event {
        public final String name;
        public final String location;
        public final String startedBy;

        private Event(String name, String location, String startedBy) {
            this.name = name;
            this.location = location;
            this.startedBy = startedBy;
        }
    }
}
