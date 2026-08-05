package net.rebornaddon.ranked.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A group of 2-3 players queueing together, guaranteed to land on the same team.
 * Ordered by join time - index 0 is always the original leader unless leadership
 * has been passed on (see PartyManager.leaveParty).
 */
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
        return memberUuids;
    }

    public List<String> getMemberNames() {
        return memberNames;
    }

    public void addMember(UUID uuid, String name) {
        memberUuids.add(uuid);
        memberNames.add(name);
    }

    /** Removes a member. If they were the leader, the next-oldest member (index 0
     *  after removal) automatically becomes the new leader - no explicit promotion
     *  step needed given the ordering invariant. Returns true if the party is now
     *  empty and should be discarded entirely. */
    public boolean removeMember(UUID uuid) {
        int idx = memberUuids.indexOf(uuid);
        if (idx >= 0) {
            memberUuids.remove(idx);
            memberNames.remove(idx);
        }
        return memberUuids.isEmpty();
    }

    /** Keeps a cached display name fresh (players can change name, rejoin, etc). */
    public void updateMemberName(UUID uuid, String currentName) {
        int idx = memberUuids.indexOf(uuid);
        if (idx >= 0) memberNames.set(idx, currentName);
    }
}
