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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RankedCommand extends CommandBase {

    @Override
    public String getName() {
        return "ranked";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ranked [1v1|2v2|3v3|leave|forfeit|stats|top]";
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
                    Arrays.asList("1v1", "2v2", "3v3", "leave", "forfeit", "stats", "top"));
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
                    + "/ranked [1v1|2v2|3v3|leave|forfeit|stats|top]"));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "1v1": queueFor(player, MatchMode.ONE_V_ONE); return;
            case "2v2": queueFor(player, MatchMode.TWO_V_TWO); return;
            case "3v3": queueFor(player, MatchMode.THREE_V_THREE); return;

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

            default:
                player.sendMessage(msg(TextFormatting.RED + "Usage: " + getUsage(sender)));
        }
    }

    private void queueFor(EntityPlayerMP player, MatchMode mode) {
        if (RankedSystem.matchManager.isInMatch(player.getUniqueID())) {
            player.sendMessage(msg(TextFormatting.RED + "You're already in a match!"));
            return;
        }
        PlayerStats stats = RankedSystem.eloManager.getStats(player.getUniqueID(), player.getName());
        RankedSystem.queueManager.join(mode, player.getUniqueID(), player.getName(), stats.elo);
        player.sendMessage(msg(TextFormatting.GREEN + "Queued for " + mode.getLabel() + " (ELO: " + stats.elo + ")"));
    }

    private TextComponentString msg(String s) {
        return new TextComponentString(s);
    }
}
