package net.rebornaddon.ranked.party;

import java.util.*;
import java.util.function.Predicate;

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

    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    private static final long TELEPORT_COOLDOWN_MILLIS = 2 * 60 * 1000L;
    private final Map<UUID, Long> lastTeleportMillis = new HashMap<>();

    public long getTeleportCooldownRemainingMillis(UUID uuid) {
        Long last = lastTeleportMillis.get(uuid);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, TELEPORT_COOLDOWN_MILLIS - elapsed);
    }

    public void recordTeleport(UUID uuid) {
        lastTeleportMillis.put(uuid, System.currentTimeMillis());
    }

    public String kickMember(UUID leaderUuid, UUID targetUuid) {
        Party party = getParty(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) {
            return "Only the party leader can kick members.";
        }
        if (leaderUuid.equals(targetUuid)) {
            return "You can't kick yourself - use Leave Party instead.";
        }
        if (!party.contains(targetUuid)) {
            return "That player isn't in your party.";
        }

        boolean nowEmpty = party.removeMember(targetUuid);
        partyByMember.remove(targetUuid);
        if (nowEmpty) partyByMember.remove(leaderUuid);
        return null;
    }

    public String promoteMember(UUID leaderUuid, UUID targetUuid) {
        Party party = getParty(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) {
            return "Only the party leader can promote members.";
        }
        if (leaderUuid.equals(targetUuid)) {
            return "You're already the leader.";
        }
        if (!party.contains(targetUuid)) {
            return "That player isn't in your party.";
        }

        party.promoteToLeader(targetUuid);
        return null;
    }

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

    public String acceptInvite(UUID inviteeUuid, String inviteeName, Predicate<UUID> isOnline) {
        PendingInvite invite = pendingInvites.get(inviteeUuid);
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            pendingInvites.remove(inviteeUuid);
            return null;
        }
        pendingInvites.remove(inviteeUuid);

        if (isInParty(inviteeUuid)) return null;
        if (isOnline == null || !isOnline.test(invite.inviterUuid)) return null;

        Party inviterParty = partyByMember.get(invite.inviterUuid);
        if (inviterParty == null) {
            inviterParty = new Party(invite.inviterUuid, invite.inviterName);
            partyByMember.put(invite.inviterUuid, inviterParty);
        } else if (!inviterParty.isLeader(invite.inviterUuid)) {
            return null;
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

    public void leaveParty(UUID uuid) {
        Party party = partyByMember.remove(uuid);
        if (party == null) return;
        party.removeMember(uuid);
    }

    public void handleDisconnect(UUID uuid) {
        leaveParty(uuid);
        pendingInvites.remove(uuid);
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().inviterUuid.equals(uuid));
        lastTeleportMillis.remove(uuid);
    }

    public void updateMemberName(UUID uuid, String currentName) {
        Party party = getParty(uuid);
        if (party != null) party.updateMemberName(uuid, currentName);
    }

    public void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }
}
