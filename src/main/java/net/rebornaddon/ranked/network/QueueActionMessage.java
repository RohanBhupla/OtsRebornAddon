package net.rebornaddon.ranked.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.match.MatchMode;
import net.rebornaddon.ranked.party.Party;
import net.rebornaddon.ranked.queue.QueuedGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QueueActionMessage implements IMessage {

    public static final int ACTION_QUEUE = 0;
    public static final int ACTION_LEAVE = 1;
    public static final int ACTION_FORFEIT = 2;
    public static final int ACTION_PARTY_INVITE = 3;
    public static final int ACTION_PARTY_ACCEPT = 4;
    public static final int ACTION_PARTY_LEAVE = 5;
    public static final int ACTION_PARTY_TELEPORT = 6;
    public static final int ACTION_PARTY_KICK = 7;
    public static final int ACTION_PARTY_PROMOTE = 8;

    private int action;
    private int modeNetId;
    private String targetName = "";

    public QueueActionMessage() {}

    public QueueActionMessage(int action, int modeNetId) {
        this(action, modeNetId, "");
    }

    public QueueActionMessage(int action, int modeNetId, String targetName) {
        this.action = action;
        this.modeNetId = modeNetId;
        this.targetName = targetName == null ? "" : targetName;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(modeNetId);
        ByteBufUtils.writeUTF8String(buf, targetName.length() > 16 ? targetName.substring(0, 16) : targetName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        modeNetId = buf.readInt();
        targetName = ByteBufUtils.readUTF8String(buf);
        if (targetName.length() > 16) targetName = targetName.substring(0, 16);
    }

    public static class Handler implements IMessageHandler<QueueActionMessage, IMessage> {
        @Override
        public IMessage onMessage(QueueActionMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(message, player));
            return null;
        }

        private void handle(QueueActionMessage message, EntityPlayerMP player) {
            if (!RankedSystem.isReady()) return;

            switch (message.action) {
                case ACTION_QUEUE:
                    handleQueue(message, player);
                    break;
                case ACTION_LEAVE:
                    RankedSystem.queueManager.leaveAll(player.getUniqueID());
                    break;
                case ACTION_FORFEIT:
                    RankedSystem.matchManager.forfeit(player.getUniqueID());
                    break;
                case ACTION_PARTY_INVITE:
                    handlePartyInvite(message, player);
                    break;
                case ACTION_PARTY_ACCEPT:
                    handlePartyAccept(player);
                    break;
                case ACTION_PARTY_LEAVE:
                    handlePartyLeave(player);
                    break;
                case ACTION_PARTY_TELEPORT:
                    handlePartyTeleport(message, player);
                    break;
                case ACTION_PARTY_KICK:
                    handlePartyKick(message, player);
                    break;
                case ACTION_PARTY_PROMOTE:
                    handlePartyPromote(message, player);
                    break;
                default:
                    break;
            }
        }

        private void handleQueue(QueueActionMessage message, EntityPlayerMP player) {
            if (!RankedSystem.seasonManager.isAcceptingMatches()) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Ranked play is currently paused."));
                return;
            }
            UUID uuid = player.getUniqueID();
            if (RankedSystem.matchManager.isInMatch(uuid)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "You're already in a match!"));
                return;
            }

            MatchMode mode = MatchMode.fromNetId(message.modeNetId);
            if (mode == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Unknown queue mode."));
                return;
            }
            Party party = RankedSystem.partyManager.getParty(uuid);

            if (party != null && party.size() > 1) {
                if (!party.isLeader(uuid)) {
                    player.sendMessage(new TextComponentString(TextFormatting.RED
                            + "Only your party leader can start the queue."));
                    return;
                }
                if (party.size() > mode.getTeamSize()) {
                    player.sendMessage(new TextComponentString(TextFormatting.RED
                            + "Your party of " + party.size() + " can't queue for " + mode.getLabel()
                            + " (max " + mode.getTeamSize() + " per team)."));
                    return;
                }

                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                List<UUID> uuids = new ArrayList<>();
                List<String> names = new ArrayList<>();
                List<Integer> elos = new ArrayList<>();
                for (UUID memberUuid : party.getMemberUuids()) {
                    EntityPlayerMP memberPlayer = server.getPlayerList().getPlayerByUUID(memberUuid);
                    if (memberPlayer == null) {
                        player.sendMessage(new TextComponentString(TextFormatting.RED
                                + "Every party member must be online to queue."));
                        return;
                    }
                    if (RankedSystem.matchManager.isInMatch(memberUuid)) {
                        player.sendMessage(new TextComponentString(TextFormatting.RED
                                + memberPlayer.getName() + " is already in a match."));
                        return;
                    }
                    String memberName = memberPlayer.getName();
                    PlayerStats stats = RankedSystem.eloManager.getStats(memberUuid, memberName);
                    uuids.add(memberUuid);
                    names.add(memberName);
                    elos.add(stats.elo);
                }

                QueuedGroup group = new QueuedGroup(uuids, names, elos);
                RankedSystem.queueManager.joinGroup(mode, group);

                String label = mode.getLabel();
                for (UUID memberUuid : party.getMemberUuids()) {
                    EntityPlayerMP memberPlayer = server.getPlayerList().getPlayerByUUID(memberUuid);
                    if (memberPlayer != null) {
                        memberPlayer.sendMessage(new TextComponentString(TextFormatting.GREEN
                                + "Your party queued for " + label + "!"));
                    }
                }
            } else {
                PlayerStats stats = RankedSystem.eloManager.getStats(uuid, player.getName());
                RankedSystem.queueManager.joinSolo(mode, uuid, player.getName(), stats.elo);
            }
        }

        private void handlePartyInvite(QueueActionMessage message, EntityPlayerMP player) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(message.targetName);
            if (target == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Player '" + message.targetName + "' not found (must be online)."));
                return;
            }
            if (isBusy(player.getUniqueID()) || isBusy(target.getUniqueID())) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Players cannot change parties while queued or in a match."));
                return;
            }

            String error = RankedSystem.partyManager.invite(player.getUniqueID(), player.getName(),
                    target.getUniqueID(), target.getName());
            if (error != null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + error));
                return;
            }

            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Invited " + target.getName() + " to your party."));
            target.sendMessage(new TextComponentString(TextFormatting.GOLD + player.getName()
                    + " invited you to their ranked party! Accept it from the Party tab, or type /ranked party accept"));
        }

        private void handlePartyAccept(EntityPlayerMP player) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (isBusy(player.getUniqueID())) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Leave your queue or match before joining a party."));
                return;
            }
            String inviterName = RankedSystem.partyManager.acceptInvite(player.getUniqueID(), player.getName(),
                    id -> server.getPlayerList().getPlayerByUUID(id) != null && !isBusy(id));
            if (inviterName == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "No pending invite (it may have expired)."));
                return;
            }
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Joined " + inviterName + "'s party!"));
        }

        private void handlePartyLeave(EntityPlayerMP player) {
            UUID id = player.getUniqueID();
            if (RankedSystem.matchManager.isInMatch(id)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "You cannot leave a party during a match."));
                return;
            }
            RankedSystem.queueManager.leaveAll(id);
            RankedSystem.partyManager.leaveParty(id);
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "You left your party."));
        }

        private boolean isBusy(UUID id) {
            return RankedSystem.queueManager.isQueued(id) || RankedSystem.matchManager.isInMatch(id);
        }

        private void handlePartyTeleport(QueueActionMessage message, EntityPlayerMP player) {
            UUID uuid = player.getUniqueID();
            Party party = RankedSystem.partyManager.getParty(uuid);
            if (party == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "You're not in a party."));
                return;
            }

            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(message.targetName);
            if (target == null || !party.contains(target.getUniqueID())) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "That player isn't in your party (or isn't online)."));
                return;
            }
            if (target.getUniqueID().equals(uuid)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "You can't teleport to yourself."));
                return;
            }

            if (RankedSystem.matchManager.isInMatch(uuid) || RankedSystem.matchManager.isInMatch(target.getUniqueID())) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Can't use party teleport while either of you is in an active match."));
                return;
            }

            long remainingMs = RankedSystem.partyManager.getTeleportCooldownRemainingMillis(uuid);
            if (remainingMs > 0) {
                long remainingSec = (remainingMs / 1000) + 1;
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Party teleport is on cooldown - " + remainingSec + "s remaining."));
                return;
            }

            if (player.dimension != target.dimension) {
                player.changeDimension(target.dimension);
            }
            player.connection.setPlayerLocation(target.posX, target.posY, target.posZ, target.rotationYaw, target.rotationPitch);
            RankedSystem.partyManager.recordTeleport(uuid);
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Teleported to " + target.getName() + "."));
        }

        private void handlePartyKick(QueueActionMessage message, EntityPlayerMP player) {
            if (isBusy(player.getUniqueID())) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Leave the queue or match before changing the party roster."));
                return;
            }
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(message.targetName);
            UUID targetUuid = target != null ? target.getUniqueID() : findPartyMemberUuidByName(player, message.targetName);
            if (targetUuid == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Couldn't find that party member."));
                return;
            }
            if (isBusy(targetUuid)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "That member is queued or in a match."));
                return;
            }

            String error = RankedSystem.partyManager.kickMember(player.getUniqueID(), targetUuid);
            if (error != null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + error));
                return;
            }

            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Removed " + message.targetName + " from the party."));
            if (target != null) {
                target.sendMessage(new TextComponentString(TextFormatting.RED + "You were removed from the party."));
            }
        }

        private void handlePartyPromote(QueueActionMessage message, EntityPlayerMP player) {
            if (isBusy(player.getUniqueID())) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Leave the queue or match before changing party leadership."));
                return;
            }
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(message.targetName);
            UUID targetUuid = target != null ? target.getUniqueID() : findPartyMemberUuidByName(player, message.targetName);
            if (targetUuid == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Couldn't find that party member."));
                return;
            }
            if (isBusy(targetUuid)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "That member is queued or in a match."));
                return;
            }

            String error = RankedSystem.partyManager.promoteMember(player.getUniqueID(), targetUuid);
            if (error != null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + error));
                return;
            }

            player.sendMessage(new TextComponentString(TextFormatting.GREEN + message.targetName + " is now the party leader."));
            if (target != null) {
                target.sendMessage(new TextComponentString(TextFormatting.GOLD + "You are now the party leader."));
            }
        }

        private UUID findPartyMemberUuidByName(EntityPlayerMP requester, String targetName) {
            Party party = RankedSystem.partyManager.getParty(requester.getUniqueID());
            if (party == null) return null;
            List<UUID> uuids = party.getMemberUuids();
            List<String> names = party.getMemberNames();
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equalsIgnoreCase(targetName)) return uuids.get(i);
            }
            return null;
        }
    }
}
