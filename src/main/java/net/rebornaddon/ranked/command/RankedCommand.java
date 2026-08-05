package net.rebornaddon.ranked.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.match.MatchMode;
import net.rebornaddon.ranked.party.Party;
import net.rebornaddon.ranked.queue.QueuedGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RankedCommand extends CommandBase {

    @Override
    public String getName() {
        return "ranked";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ranked [1v1|2v2|3v3|leave|forfeit|stats|top|party]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // everyone
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    Arrays.asList("1v1", "2v2", "3v3", "leave", "forfeit", "stats", "top", "party"));
        }
        if (args.length == 2 && "party".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("invite", "accept", "leave"));
        }
        if (args.length == 3 && "party".equalsIgnoreCase(args[0]) && "invite".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString("This command can only be used in-game."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length == 0) {
            player.sendMessage(msg(TextFormatting.YELLOW + "Press H to open the Ranked hub, or use "
                    + getUsage(sender)));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "1v1": queueFor(server, player, MatchMode.ONE_V_ONE); return;
            case "2v2": queueFor(server, player, MatchMode.TWO_V_TWO); return;
            case "3v3": queueFor(server, player, MatchMode.THREE_V_THREE); return;

            case "leave":
                RankedSystem.queueManager.leaveAll(player.getUniqueID());
                player.sendMessage(msg(TextFormatting.YELLOW + "Left ranked queue."));
                return;

            case "forfeit":
                boolean forfeited = RankedSystem.matchManager.forfeit(player.getUniqueID());
                if (!forfeited) player.sendMessage(msg(TextFormatting.RED + "You're not in a match."));
                return;

            case "stats":
                PlayerStats stats = RankedSystem.eloManager.getStats(player.getUniqueID(), player.getName());
                player.sendMessage(msg(TextFormatting.GOLD + "=== Ranked Stats ==="));
                player.sendMessage(msg(TextFormatting.GRAY + "ELO: " + TextFormatting.WHITE + stats.elo));
                player.sendMessage(msg(TextFormatting.GRAY + "W/L/D: " + TextFormatting.WHITE
                        + stats.wins + "/" + stats.losses + "/" + stats.draws));
                return;

            case "top":
                List<PlayerStats> top = RankedSystem.eloManager.getTop(10);
                player.sendMessage(msg(TextFormatting.GOLD + "=== Ranked Leaderboard ==="));
                int rank = 1;
                for (PlayerStats s : top) {
                    player.sendMessage(msg(TextFormatting.GRAY + "#" + rank + " " + TextFormatting.WHITE
                            + s.name + TextFormatting.GRAY + " - " + s.elo + " ELO"));
                    rank++;
                }
                return;

            case "party":
                handleParty(server, player, args);
                return;

            default:
                player.sendMessage(msg(TextFormatting.RED + "Usage: " + getUsage(sender)));
        }
    }

    private void handleParty(MinecraftServer server, EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg(TextFormatting.GOLD + "=== Party Commands ==="));
            player.sendMessage(msg(TextFormatting.GRAY + "/ranked party invite <player>"));
            player.sendMessage(msg(TextFormatting.GRAY + "/ranked party accept"));
            player.sendMessage(msg(TextFormatting.GRAY + "/ranked party leave"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "invite": {
                if (args.length < 3) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /ranked party invite <player>"));
                    return;
                }
                EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(args[2]);
                if (target == null) {
                    player.sendMessage(msg(TextFormatting.RED + "Player '" + args[2] + "' not found (must be online)."));
                    return;
                }
                String error = RankedSystem.partyManager.invite(player.getUniqueID(), player.getName(),
                        target.getUniqueID(), target.getName());
                if (error != null) {
                    player.sendMessage(msg(TextFormatting.RED + error));
                    return;
                }
                player.sendMessage(msg(TextFormatting.GREEN + "Invited " + target.getName() + " to your party."));
                target.sendMessage(msg(TextFormatting.GOLD + player.getName()
                        + " invited you to their ranked party! Type /ranked party accept"));
                return;
            }

            case "accept": {
                String inviterName = RankedSystem.partyManager.acceptInvite(player.getUniqueID(), player.getName());
                if (inviterName == null) {
                    player.sendMessage(msg(TextFormatting.RED + "No pending invite (it may have expired)."));
                    return;
                }
                player.sendMessage(msg(TextFormatting.GREEN + "Joined " + inviterName + "'s party!"));
                return;
            }

            case "leave":
                RankedSystem.partyManager.leaveParty(player.getUniqueID());
                player.sendMessage(msg(TextFormatting.YELLOW + "You left your party."));
                return;

            default:
                player.sendMessage(msg(TextFormatting.RED + "Usage: /ranked party <invite|accept|leave>"));
        }
    }

    private void queueFor(MinecraftServer server, EntityPlayerMP player, MatchMode mode) {
        UUID uuid = player.getUniqueID();
        if (RankedSystem.matchManager.isInMatch(uuid)) {
            player.sendMessage(msg(TextFormatting.RED + "You're already in a match!"));
            return;
        }

        Party party = RankedSystem.partyManager.getParty(uuid);
        if (party != null && party.size() > 1) {
            if (!party.isLeader(uuid)) {
                player.sendMessage(msg(TextFormatting.RED + "Only your party leader can start the queue."));
                return;
            }
            if (party.size() > mode.getTeamSize()) {
                player.sendMessage(msg(TextFormatting.RED + "Your party of " + party.size()
                        + " can't queue for " + mode.getLabel() + " (max " + mode.getTeamSize() + " per team)."));
                return;
            }

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

            RankedSystem.queueManager.joinGroup(mode, new QueuedGroup(uuids, names, elos));
            for (UUID memberUuid : party.getMemberUuids()) {
                EntityPlayerMP memberPlayer = server.getPlayerList().getPlayerByUUID(memberUuid);
                if (memberPlayer != null) {
                    memberPlayer.sendMessage(msg(TextFormatting.GREEN + "Your party queued for " + mode.getLabel() + "!"));
                }
            }
        } else {
            PlayerStats stats = RankedSystem.eloManager.getStats(uuid, player.getName());
            RankedSystem.queueManager.joinSolo(mode, uuid, player.getName(), stats.elo);
            player.sendMessage(msg(TextFormatting.GREEN + "Queued for " + mode.getLabel() + " (ELO: " + stats.elo + ")"));
        }
    }

    private TextComponentString msg(String s) {
        return new TextComponentString(s);
    }
}
