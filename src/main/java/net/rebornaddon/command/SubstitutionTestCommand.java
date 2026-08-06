package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.substitution.SubstitutionHandler;
import net.rebornaddon.village.Village;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SubstitutionTestCommand extends CommandBase {
    private static final List<String> VILLAGES = Arrays.asList("stone", "leaf", "cloud", "sand", "mist", "rain");
    private static final List<String> MODES = Arrays.asList("preview", "hit", "full");

    @Override
    public String getName() {
        return "testsubstitution";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("testsub", "subtest");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/testsubstitution <village> [preview|hit|full] [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 3) {
            throw new WrongUsageException(getUsage(sender));
        }

        Village village = Village.byGroup(args[0]);
        if (village == null) {
            throw new CommandException("Unknown village. Use stone, leaf, cloud, sand, mist, or rain.");
        }

        String mode = args.length >= 2 ? args[1].toLowerCase() : "preview";
        if (!MODES.contains(mode)) {
            throw new WrongUsageException(getUsage(sender));
        }

        EntityPlayerMP target = args.length == 3 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
        SubstitutionHandler handler = SubstitutionHandler.INSTANCE;
        boolean success;
        if ("hit".equals(mode)) {
            success = handler.testHit(target, village);
        } else if ("full".equals(mode)) {
            success = handler.testFull(target, village);
        } else {
            success = handler.testPreview(target, village);
        }

        if (!success) {
            throw new CommandException("Unable to run the substitution test for that player.");
        }

        sender.sendMessage(new TextComponentString("Tested " + village.displayName() + " substitution " + mode
                + " for " + target.getName() + "."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, VILLAGES);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, MODES);
        }
        if (args.length == 3) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
