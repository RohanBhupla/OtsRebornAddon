package net.rebornaddon.ranked.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Party {

    public static final int MAX_SIZE = 3;

    private final UUID partyId = UUID.randomUUID();
    private final List<UUID> memberUuids = new ArrayList<>();
    private final List<String> memberNames = new ArrayList<>();

    public Party(UUID leaderUuid, String leaderName) {
        memberUuids.add(leaderUuid);
        memberNames.add(leaderName);
    }

    public UUID getPartyId() { return partyId; }

    public UUID getLeaderUuid() {
        return memberUuids.get(0);
    }

    public boolean isLeader(UUID uuid) {
        return getLeaderUuid().equals(uuid);
    }

    public int size() {
        return memberUuids.size();
    }

    public boolean isFull() {
        return memberUuids.size() >= MAX_SIZE;
    }

    public boolean contains(UUID uuid) {
        return memberUuids.contains(uuid);
    }

    public List<UUID> getMemberUuids() {
        return Collections.unmodifiableList(new ArrayList<UUID>(memberUuids));
    }

    public List<String> getMemberNames() {
        return Collections.unmodifiableList(new ArrayList<String>(memberNames));
    }

    public void addMember(UUID uuid, String name) {
        if (uuid == null || contains(uuid) || isFull()) {
            return;
        }
        memberUuids.add(uuid);
        memberNames.add(name == null ? "" : name);
    }

    public boolean removeMember(UUID uuid) {
        int idx = memberUuids.indexOf(uuid);
        if (idx >= 0) {
            memberUuids.remove(idx);
            memberNames.remove(idx);
        }
        return memberUuids.isEmpty();
    }

    public void updateMemberName(UUID uuid, String currentName) {
        int idx = memberUuids.indexOf(uuid);
        if (idx >= 0) memberNames.set(idx, currentName);
    }

    public void promoteToLeader(UUID uuid) {
        int idx = memberUuids.indexOf(uuid);
        if (idx <= 0) return;
        UUID movedUuid = memberUuids.remove(idx);
        String movedName = memberNames.remove(idx);
        memberUuids.add(0, movedUuid);
        memberNames.add(0, movedName);
    }
}
