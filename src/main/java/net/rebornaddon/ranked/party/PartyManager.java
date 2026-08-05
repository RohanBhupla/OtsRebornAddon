package net.rebornaddon.ranked.party;

import java.util.*;

/**
 * Manages parties and the invite flow. A player with no entry in partyByMember is
 * "solo" - they're never given a party-of-one, that state simply doesn't exist here,
 * which keeps the "am I in a party" check trivial (null = no).
 */
public class PartyManager {

    private static final long INVITE_EXPIRY_MILLIS = 60_000L;

    private final Map<UUID, Party> partyByMember = new HashMap<>();

    private static class PendingInvite {
        final UUID inviterUuid;
        final String inviterName;
        final long expiresAt;

        PendingInvite(UUID inviterUuid, String inviterName, long expiresAt) {
            this.inviterUuid = inviterUuid;
            this.inviterName = inviterName;
            this.expiresAt = expiresAt;
        }
    }

    // invitee UUID -> the invite waiting for them
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    public Party getParty(UUID uuid) {
        return partyByMember.get(uuid);
    }

    public boolean isInParty(UUID uuid) {
        return partyByMember.containsKey(uuid);
    }

    public boolean isLeader(UUID uuid) {
        Party p = getParty(uuid);
        return p != null && p.isLeader(uuid);
    }

    /** Returns null on success, or a player-facing error message on failure. */
    public String invite(UUID inviterUuid, String inviterName, UUID inviteeUuid, String inviteeName) {
        if (inviterUuid.equals(inviteeUuid)) {
            return "You can't invite yourself.";
        }
        if (isInParty(inviteeUuid)) {
            return inviteeName + " is already in a party.";
        }

        Party inviterParty = getParty(inviterUuid);
        if (inviterParty != null) {
            if (!inviterParty.isLeader(inviterUuid)) {
                return "Only the party leader can invite new members.";
            }
            if (inviterParty.isFull()) {
                return "Your party is already full (max " + Party.MAX_SIZE + ").";
            }
        }

        pendingInvites.put(inviteeUuid, new PendingInvite(inviterUuid, inviterName, System.currentTimeMillis() + INVITE_EXPIRY_MILLIS));
        return null;
    }

    /** Returns the inviter's name on success (so the caller can announce it), or null
     *  if there's no valid pending invite. */
    public String acceptInvite(UUID inviteeUuid, String inviteeName) {
        PendingInvite invite = pendingInvites.get(inviteeUuid);
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            pendingInvites.remove(inviteeUuid);
            return null;
        }
        pendingInvites.remove(inviteeUuid);

        if (isInParty(inviteeUuid)) return null; // joined/created another party in the meantime

        Party inviterParty = partyByMember.get(invite.inviterUuid);
        if (inviterParty == null) {
            // Inviter had no party yet - create one now, with them as leader.
            inviterParty = new Party(invite.inviterUuid, invite.inviterName);
            partyByMember.put(invite.inviterUuid, inviterParty);
        }

        if (inviterParty.isFull()) return null;

        inviterParty.addMember(inviteeUuid, inviteeName);
        partyByMember.put(inviteeUuid, inviterParty);
        return invite.inviterName;
    }

    public String getPendingInviteFromName(UUID inviteeUuid) {
        PendingInvite invite = pendingInvites.get(inviteeUuid);
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) return null;
        return invite.inviterName;
    }

    /** Removes a player from their party (if any). If the party is left with zero
     *  members it's discarded entirely; if it still has members, leadership has
     *  already implicitly passed to the next-oldest member via Party's ordering. */
    public void leaveParty(UUID uuid) {
        Party party = partyByMember.remove(uuid);
        if (party == null) return;
        boolean nowEmpty = party.removeMember(uuid);
        if (!nowEmpty) {
            // Everyone else stays mapped to the same Party object - leadership
            // read via getLeaderUuid() just naturally reflects the new front-of-list member.
        }
    }

    /** Same handling as leaving cleanly - called on disconnect too, so a dropped
     *  connection doesn't leave the rest of the party in a broken state. */
    public void handleDisconnect(UUID uuid) {
        leaveParty(uuid);
        pendingInvites.remove(uuid);
    }

    public void updateMemberName(UUID uuid, String currentName) {
        Party party = getParty(uuid);
        if (party != null) party.updateMemberName(uuid, currentName);
    }

    /** Call periodically (the existing per-second tick is fine) to clear out invites
     *  nobody ever responded to, so getPendingInviteFromName doesn't need to check
     *  expiry defensively everywhere. */
    public void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }
}
