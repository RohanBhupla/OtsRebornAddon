package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.performance.PerformanceMonitor;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RebornTpsCommand extends CommandBase {
    @Override
    public String getName() {
        return "reborntps";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("rtps", "rebornperformance");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/reborntps status | profile [10s|5m|1h] | overlay <on|off> | tp <hotspot|chunk> <number>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW
                    + "Run this command as an in-game operator to receive the detailed report."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String action = args.length == 0 ? "profile" : args[0].toLowerCase(java.util.Locale.ROOT);
        if ("status".equals(action)) {
            PerformanceMonitor.INSTANCE.sendReport(player);
            PerformanceMonitor.INSTANCE.sendSnapshot(player);
            return;
        }
        if ("profile".equals(action)) {
            long duration = args.length >= 2
                    ? PerformanceMonitor.parseDuration(args[1]) : PerformanceMonitor.DEFAULT_PROFILE_MILLIS;
            if (duration < 0L) throw new CommandException("Use a duration from 1s through 1h, such as 30s or 5m.");
            PerformanceMonitor.INSTANCE.requestProfile(player, duration);
            return;
        }
        if ("overlay".equals(action)) {
            if (args.length < 2 || !"on".equalsIgnoreCase(args[1]) && !"off".equalsIgnoreCase(args[1])) {
                throw new CommandException("Usage: /reborntps overlay <on|off>");
            }
            boolean enabled = "on".equalsIgnoreCase(args[1]);
            PerformanceMonitor.INSTANCE.sendSnapshot(player);
            RebornAddonNetwork.setPerformanceOverlay(player, enabled);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                    + "Performance hotspot overlay " + (enabled ? "enabled." : "disabled.")));
            return;
        }
        if ("tp".equals(action)) {
            if (args.length < 3) throw new CommandException("Usage: /reborntps tp <hotspot|chunk> <number>");
            boolean chunk = "chunk".equalsIgnoreCase(args[1]);
            if (!chunk && !"hotspot".equalsIgnoreCase(args[1])) {
                throw new CommandException("Choose hotspot or chunk.");
            }
            int index;
            try {
                index = Integer.parseInt(args[2]) - 1;
            } catch (NumberFormatException invalid) {
                throw new CommandException("The result number must be a whole number.");
            }
            PerformanceMonitor.INSTANCE.teleportToHotspot(player, index, chunk);
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args,
                "status", "profile", "overlay", "tp");
        if (args.length == 2 && "profile".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "5s", "30s", "1m", "5m", "1h");
        }
        if (args.length == 2 && "overlay".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 2 && "tp".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "hotspot", "chunk");
        }
        return Collections.emptyList();
    }
}
