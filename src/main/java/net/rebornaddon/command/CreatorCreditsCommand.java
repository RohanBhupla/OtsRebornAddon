package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.Arrays;
import java.util.List;

public class CreatorCreditsCommand extends CommandBase {
    @Override
    public String getName() {
        return "reborncredits";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("modcredits", "credits");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/reborncredits";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("Creator credits can only be opened by a player."));
            return;
        }

        RebornAddonNetwork.openCreatorCreditsGui((EntityPlayerMP) sender);
    }
}
