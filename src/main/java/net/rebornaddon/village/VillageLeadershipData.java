package net.rebornaddon.village;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageLeadershipData extends WorldSavedData {
    private static final String DATA_NAME = "rebornaddon_village_leadership";

    private final Map<String, RemovalVote> votes = new LinkedHashMap<String, RemovalVote>();
    private final Map<Integer, HuntRecord> hunts = new HashMap<Integer, HuntRecord>();
    private final Map<String, EventRecord> events = new HashMap<String, EventRecord>();

    public VillageLeadershipData() {
        super(DATA_NAME);
    }

    public VillageLeadershipData(String name) {
        super(name);
    }

    public static VillageLeadershipData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        WorldServer world = server.getWorld(0);
        MapStorage storage = world.getMapStorage();
        VillageLeadershipData data = (VillageLeadershipData) storage.getOrLoadData(
                VillageLeadershipData.class, DATA_NAME);
        if (data == null) {
            data = new VillageLeadershipData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public int castRemovalVote(Village village, UUID target, String targetName,
                               UUID voter, int weight) {
        String key = voteKey(village, target);
        RemovalVote vote = votes.get(key);
        if (vote == null) {
            vote = new RemovalVote(village.id(), target, targetName);
            votes.put(key, vote);
        }
        if (vote.weights.containsKey(voter)) {
            return -1;
        }
        vote.weights.put(voter, Math.max(1, Math.min(2, weight)));
        vote.updatedAt = System.currentTimeMillis();
        markDirty();
        return vote.total();
    }

    public int removalVotes(Village village, UUID target) {
        RemovalVote vote = votes.get(voteKey(village, target));
        return vote == null ? 0 : vote.total();
    }

    public void clearRemovalVote(Village village, UUID target) {
        if (votes.remove(voteKey(village, target)) != null) {
            markDirty();
        }
    }

    public void retainRemovalVotes(Village village, Set<UUID> members) {
        boolean changed = false;
        Iterator<Map.Entry<String, RemovalVote>> iterator = votes.entrySet().iterator();
        while (iterator.hasNext()) {
            RemovalVote vote = iterator.next().getValue();
            if (vote.villageId == village.id() && !members.contains(vote.target)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public long huntCooldownRemaining(Village village, long now) {
        HuntRecord hunt = hunts.get(village.id());
        return hunt == null ? 0L : Math.max(0L, hunt.cooldownUntil - now);
    }

    public HuntRecord hunt(Village village) {
        return hunts.get(village.id());
    }

    public void recordHuntAttempt(Village village, UUID captain, String captainName,
                                  UUID target, String targetName, long durationMillis,
                                  long cooldownMillis, boolean validTarget, Set<UUID> hunters) {
        long now = System.currentTimeMillis();
        HuntRecord record = new HuntRecord();
        record.villageId = village.id();
        record.captain = captain;
        record.captainName = safe(captainName);
        record.target = target;
        record.targetName = safe(targetName);
        record.startedAt = now;
        record.expiresAt = validTarget ? now + durationMillis : now;
        record.cooldownUntil = now + cooldownMillis;
        record.state = validTarget ? HuntRecord.ACTIVE : HuntRecord.WRONG_TARGET;
        if (hunters != null) {
            record.hunters.addAll(hunters);
        }
        hunts.put(village.id(), record);
        markDirty();
    }

    public void finishHunt(Village village, int state, long cooldownMillis) {
        HuntRecord record = hunts.get(village.id());
        if (record != null && record.state == HuntRecord.ACTIVE) {
            long now = System.currentTimeMillis();
            record.state = state;
            record.expiresAt = now;
            record.cooldownUntil = now + Math.max(0L, cooldownMillis);
            markDirty();
        }
    }

    public void startEvent(Village village, boolean serverWide, String eventId,
                           String eventName, String locationId, String locationName,
                           String startedBy) {
        EventRecord record = new EventRecord();
        record.villageId = village.id();
        record.serverWide = serverWide;
        record.eventId = safe(eventId);
        record.eventName = safe(eventName);
        record.locationId = safe(locationId);
        record.locationName = safe(locationName);
        record.startedBy = safe(startedBy);
        record.startedAt = System.currentTimeMillis();
        events.put(eventKey(village, serverWide), record);
        markDirty();
    }

    public EventRecord event(Village village, boolean serverWide) {
        return events.get(eventKey(village, serverWide));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        votes.clear();
        NBTTagList voteList = tag.getTagList("Votes", 10);
        for (int i = 0; i < voteList.tagCount(); i++) {
            RemovalVote vote = RemovalVote.read(voteList.getCompoundTagAt(i));
            Village village = Village.byId(vote.villageId);
            if (village != null && vote.target != null) {
                votes.put(voteKey(village, vote.target), vote);
            }
        }

        hunts.clear();
        NBTTagList huntList = tag.getTagList("Hunts", 10);
        for (int i = 0; i < huntList.tagCount(); i++) {
            HuntRecord hunt = HuntRecord.read(huntList.getCompoundTagAt(i));
            if (Village.byId(hunt.villageId) != null) {
                hunts.put(hunt.villageId, hunt);
            }
        }

        events.clear();
        NBTTagList eventList = tag.getTagList("Events", 10);
        for (int i = 0; i < eventList.tagCount(); i++) {
            EventRecord event = EventRecord.read(eventList.getCompoundTagAt(i));
            Village village = Village.byId(event.villageId);
            if (village != null) {
                events.put(eventKey(village, event.serverWide), event);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList voteList = new NBTTagList();
        for (RemovalVote vote : votes.values()) {
            voteList.appendTag(vote.write());
        }
        tag.setTag("Votes", voteList);

        NBTTagList huntList = new NBTTagList();
        for (HuntRecord hunt : hunts.values()) {
            huntList.appendTag(hunt.write());
        }
        tag.setTag("Hunts", huntList);

        NBTTagList eventList = new NBTTagList();
        for (EventRecord event : events.values()) {
            eventList.appendTag(event.write());
        }
        tag.setTag("Events", eventList);
        return tag;
    }

    private static String voteKey(Village village, UUID target) {
        return village.id() + ":" + target.toString();
    }

    private static String eventKey(Village village, boolean serverWide) {
        return serverWide ? "server" : "village:" + village.id();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class RemovalVote {
        private int villageId;
        private UUID target;
        private String targetName;
        private long updatedAt;
        private final Map<UUID, Integer> weights = new LinkedHashMap<UUID, Integer>();

        private RemovalVote(int villageId, UUID target, String targetName) {
            this.villageId = villageId;
            this.target = target;
            this.targetName = safe(targetName);
        }

        private int total() {
            int total = 0;
            for (Integer weight : weights.values()) {
                total += weight == null ? 0 : weight.intValue();
            }
            return total;
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Village", villageId);
            tag.setString("Target", target == null ? "" : target.toString());
            tag.setString("TargetName", targetName);
            tag.setLong("Updated", updatedAt);
            NBTTagList voterList = new NBTTagList();
            for (Map.Entry<UUID, Integer> entry : weights.entrySet()) {
                NBTTagCompound voter = new NBTTagCompound();
                voter.setString("Id", entry.getKey().toString());
                voter.setInteger("Weight", entry.getValue().intValue());
                voterList.appendTag(voter);
            }
            tag.setTag("Voters", voterList);
            return tag;
        }

        private static RemovalVote read(NBTTagCompound tag) {
            RemovalVote vote = new RemovalVote(tag.getInteger("Village"), uuid(tag.getString("Target")),
                    tag.getString("TargetName"));
            vote.updatedAt = tag.getLong("Updated");
            NBTTagList voterList = tag.getTagList("Voters", 10);
            for (int i = 0; i < voterList.tagCount(); i++) {
                NBTTagCompound voter = voterList.getCompoundTagAt(i);
                UUID id = uuid(voter.getString("Id"));
                if (id != null) {
                    vote.weights.put(id, Math.max(1, Math.min(2, voter.getInteger("Weight"))));
                }
            }
            return vote;
        }
    }

    public static final class HuntRecord {
        public static final int ACTIVE = 1;
        public static final int SUCCESS = 2;
        public static final int FAILED = 3;
        public static final int WRONG_TARGET = 4;

        public int villageId;
        public UUID captain;
        public String captainName = "";
        public UUID target;
        public String targetName = "";
        public long startedAt;
        public long expiresAt;
        public long cooldownUntil;
        public int state;
        public final Set<UUID> hunters = new HashSet<UUID>();

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Village", villageId);
            tag.setString("Captain", captain == null ? "" : captain.toString());
            tag.setString("CaptainName", captainName);
            tag.setString("Target", target == null ? "" : target.toString());
            tag.setString("TargetName", targetName);
            tag.setLong("Started", startedAt);
            tag.setLong("Expires", expiresAt);
            tag.setLong("Cooldown", cooldownUntil);
            tag.setInteger("State", state);
            NBTTagList hunterList = new NBTTagList();
            for (UUID hunter : hunters) {
                NBTTagCompound hunterTag = new NBTTagCompound();
                hunterTag.setString("Id", hunter.toString());
                hunterList.appendTag(hunterTag);
            }
            tag.setTag("Hunters", hunterList);
            return tag;
        }

        private static HuntRecord read(NBTTagCompound tag) {
            HuntRecord record = new HuntRecord();
            record.villageId = tag.getInteger("Village");
            record.captain = uuid(tag.getString("Captain"));
            record.captainName = tag.getString("CaptainName");
            record.target = uuid(tag.getString("Target"));
            record.targetName = tag.getString("TargetName");
            record.startedAt = tag.getLong("Started");
            record.expiresAt = tag.getLong("Expires");
            record.cooldownUntil = tag.getLong("Cooldown");
            record.state = tag.getInteger("State");
            NBTTagList hunterList = tag.getTagList("Hunters", 10);
            for (int i = 0; i < hunterList.tagCount(); i++) {
                UUID hunter = uuid(hunterList.getCompoundTagAt(i).getString("Id"));
                if (hunter != null) {
                    record.hunters.add(hunter);
                }
            }
            return record;
        }
    }

    public static final class EventRecord {
        public int villageId;
        public boolean serverWide;
        public String eventId = "";
        public String eventName = "";
        public String locationId = "";
        public String locationName = "";
        public String startedBy = "";
        public long startedAt;

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Village", villageId);
            tag.setBoolean("ServerWide", serverWide);
            tag.setString("EventId", eventId);
            tag.setString("EventName", eventName);
            tag.setString("LocationId", locationId);
            tag.setString("LocationName", locationName);
            tag.setString("StartedBy", startedBy);
            tag.setLong("Started", startedAt);
            return tag;
        }

        private static EventRecord read(NBTTagCompound tag) {
            EventRecord record = new EventRecord();
            record.villageId = tag.getInteger("Village");
            record.serverWide = tag.getBoolean("ServerWide");
            record.eventId = tag.getString("EventId");
            record.eventName = tag.getString("EventName");
            record.locationId = tag.getString("LocationId");
            record.locationName = tag.getString("LocationName");
            record.startedBy = tag.getString("StartedBy");
            record.startedAt = tag.getLong("Started");
            return record;
        }
    }
}
