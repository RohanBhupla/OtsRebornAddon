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

/**
 * Sent by the hub GUI for queue/forfeit actions AND party actions - one message type
 * for all of it, with an int action code and an optional string parameter (only used
 * by ACTION_PARTY_INVITE, for the target player's name).
 */
public class QueueActionMessage implements IMessage {

    public static final int ACTION_QUEUE = 0;
    public static final int ACTION_LEAVE = 1;
    public static final int ACTION_FORFEIT = 2;
    public static final int ACTION_PARTY_INVITE = 3;
    public static final int ACTION_PARTY_ACCEPT = 4;
    public static final int ACTION_PARTY_LEAVE = 5;

    private int action;
    private int modeNetId;    // only meaningful when action == ACTION_QUEUE
    private String targetName = ""; // only meaningful when action == ACTION_PARTY_INVITE

    public QueueActionMessage() {} // required no-arg constructor for IMessage

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
        ByteBufUtils.writeUTF8String(buf, targetName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        modeNetId = buf.readInt();
        targetName = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<QueueActionMessage, IMessage> {
        @Override
        public IMessage onMessage(QueueActionMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // Network handlers run off the main thread - anything touching game state
            // needs to be scheduled back onto it.
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
                    RankedSystem.partyManager.leaveParty(player.getUniqueID());
                    player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "You left your party."));
                    break;
                default:
                    break;
            }
        }

        private void handleQueue(QueueActionMessage message, EntityPlayerMP player) {
            UUID uuid = player.getUniqueID();
            if (RankedSystem.matchManager.isInMatch(uuid)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "You're already in a match!"));
                return;
            }

            MatchMode mode = MatchMode.fromNetId(message.modeNetId);
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
                    String memberName = memberPlayer != null ? memberPlayer.getName()
                            : party.getMemberNames().get(party.getMemberUuids().indexOf(memberUuid));
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
            String inviterName = RankedSystem.partyManager.acceptInvite(player.getUniqueID(), player.getName());
            if (inviterName == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "No pending invite (it may have expired)."));
                return;
            }
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Joined " + inviterName + "'s party!"));
        }
    }
}
