package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.village.SingleplayerVillageRoles;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageLeadershipService;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SingleplayerVillageRoleCommand extends CommandBase {
    private static final List<String> VILLAGES = Arrays.asList("stone", "leaf", "cloud", "sand", "mist", "rain", "clear");

    @Override
    public String getName() {
        return "testvillagerole";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("villagetest", "testlprole");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/testvillagerole <stone|leaf|cloud|sand|mist|rain> [member|advisor|kage|anbu|captain|operator] or /testvillagerole clear";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!SingleplayerVillageRoles.isEnabled(server)) {
            throw new CommandException("This command is only available in an integrated single-player world.");
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 1 && "clear".equalsIgnoreCase(args[0])) {
            SingleplayerVillageRoles.clear(player);
            VillageLeadershipService.INSTANCE.request(player, -1, true);
            sender.sendMessage(new TextComponentString("Single-player village role cleared."));
            return;
        }
        if (args.length < 1 || args.length > 2) {
            throw new WrongUsageException(getUsage(sender));
        }

        Village village = Village.byGroup(args[0]);
        String role = args.length == 2 ? args[1] : "operator";
        if (village == null || !SingleplayerVillageRoles.roles().contains(role.toLowerCase(java.util.Locale.ROOT))) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (!SingleplayerVillageRoles.setRole(player, village, role)) {
            throw new CommandException("The single-player village role could not be applied.");
        }
        VillageLeadershipService.INSTANCE.request(player, village.id(), true);
        sender.sendMessage(new TextComponentString("Testing as " + role.toLowerCase(java.util.Locale.ROOT)
                + " for " + village.displayName() + ". Open the Reborn hub to inspect the panels."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                           @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, VILLAGES);
        }
        if (args.length == 2 && !"clear".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, SingleplayerVillageRoles.roles());
        }
        return Collections.emptyList();
    }
}
