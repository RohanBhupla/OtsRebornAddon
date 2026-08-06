package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.village.LuckPermsBridge;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageSelectionHandler;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VillageAssignCommand extends CommandBase {
    private static final List<String> OPTIONS = Arrays.asList("stone", "leaf", "cloud", "sand", "mist", "rain", "clear");

    @Override
    public String getName() {
        return "assignvillage";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("setvillage", "testvillage");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/assignvillage <stone|leaf|cloud|sand|mist|rain|clear> [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2) {
            throw new WrongUsageException(getUsage(sender));
        }

        EntityPlayerMP target = args.length == 2 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
        boolean luckPermsAvailable = LuckPermsBridge.INSTANCE.isAvailable(server);
        if ("clear".equalsIgnoreCase(args[0])) {
            VillageSelectionHandler.INSTANCE.resetSelection(target, luckPermsAvailable, false);
            sender.sendMessage(new TextComponentString("Cleared the assigned village for " + target.getName() + "."));
            return;
        }

        Village village = Village.byGroup(args[0]);
        if (village == null) {
            throw new WrongUsageException(getUsage(sender));
        }

        VillageSelectionHandler.INSTANCE.assignLocalVillage(target, village);
        boolean luckPermsUpdated = !luckPermsAvailable || LuckPermsBridge.INSTANCE.applyVillage(target, village);
        String suffix = luckPermsAvailable
                ? (luckPermsUpdated ? " LuckPerms was updated." : " Local assignment saved; LuckPerms update failed.")
                : " Local assignment saved without LuckPerms.";
        sender.sendMessage(new TextComponentString("Assigned " + target.getName() + " to "
                + village.displayName() + "." + suffix));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, OPTIONS);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
