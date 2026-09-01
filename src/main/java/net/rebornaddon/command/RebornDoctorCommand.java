package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.rebornaddon.diagnostic.CompatibilityDoctor;

import java.util.Collections;
import java.util.List;

public final class RebornDoctorCommand extends CommandBase {
    @Override
    public String getName() {
        return "reborndoctor";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/reborndoctor [player|refresh]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) throw new CommandException("commands.generic.player.unspecified");
        EntityPlayerMP viewer = (EntityPlayerMP) sender;
        EntityPlayerMP target = viewer;
        if (args.length > 0 && "refresh".equalsIgnoreCase(args[0])) {
            CompatibilityDoctor.INSTANCE.request(viewer, true);
        } else if (args.length > 0) {
            target = getPlayer(server, sender, args[0]);
        }
        CompatibilityDoctor.INSTANCE.sendReport(viewer, target);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length != 1) return Collections.emptyList();
        String[] players = server.getOnlinePlayerNames();
        String[] values = new String[players.length + 1];
        values[0] = "refresh";
        System.arraycopy(players, 0, values, 1, players.length);
        return getListOfStringsMatchingLastWord(args, values);
    }
}
