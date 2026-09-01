package net.rebornaddon.command;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.jutsu.JutsuRuntimeHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ClearCooldownsCommand extends CommandBase {
    @Override
    public String getName() {
        return "clearcooldowns";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/clearcooldowns <player|all>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender,
                        String[] args) throws CommandException {
        if (args.length != 1) throw new CommandException(getUsage(sender));
        if ("all".equalsIgnoreCase(args[0])) {
            int count = JutsuRuntimeHooks.clearAllCooldowns(server);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                    + "Cleared current cooldowns for all players; " + count
                    + (count == 1 ? " online player was" : " online players were")
                    + " updated immediately."));
            return;
        }

        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(args[0]);
        GameProfile profile = online == null ? server.getPlayerProfileCache()
                .getGameProfileForUsername(args[0]) : online.getGameProfile();
        if (profile == null || profile.getId() == null) {
            throw new CommandException("That player could not be found.");
        }
        JutsuRuntimeHooks.clearAllCooldowns(server, profile.getId());
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                + "Cleared all current cooldowns for " + profile.getName() + "."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length != 1) return Collections.emptyList();
        List<String> values = new ArrayList<String>();
        values.add("all");
        values.addAll(Arrays.asList(server.getOnlinePlayerNames()));
        return getListOfStringsMatchingLastWord(args, values);
    }
}
