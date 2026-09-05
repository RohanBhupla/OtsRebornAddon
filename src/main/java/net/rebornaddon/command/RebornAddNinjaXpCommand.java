package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.narutomod.PlayerTracker;
import net.rebornaddon.advancement.RebornAdvancementService;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** Authoritative replacement for NarutoMod's legacy-advancement-only XP command. */
public final class RebornAddNinjaXpCommand extends CommandBase {
    @Override
    public String getName() {
        return "addninjaxp";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/addninjaxp <target> <amount>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException(getUsage(sender));
        }

        EntityPlayerMP target = getPlayer(server, sender, args[0]);
        double amount = parseDouble(args[1]);
        if (!RebornAdvancementService.INSTANCE.ensureNinja(target)) {
            throw new CommandException(target.getName() + " is not a ninja.");
        }

        PlayerTracker.addBattleXp(target, amount);
        sender.sendMessage(new TextComponentString("Added " + formatAmount(amount)
                + " ninja XP to " + target.getName() + "."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }

    private static String formatAmount(double amount) {
        long whole = (long) amount;
        return amount == whole ? Long.toString(whole) : Double.toString(amount);
    }
}
