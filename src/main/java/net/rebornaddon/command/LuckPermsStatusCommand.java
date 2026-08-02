package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.village.LuckPermsBridge;
import net.rebornaddon.village.VillageSelectionHandler;

import java.util.Arrays;
import java.util.List;

public class LuckPermsStatusCommand extends CommandBase {
    @Override
    public String getName() {
        return "rebornlp";
    }

    public String func_71517_b() {
        return getName();
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("rebornluckperms", "rlp");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornlp [reset [player]]";
    }

    public String func_71518_a(ICommandSender sender) {
        return getUsage(sender);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args != null && args.length > 0 && "reset".equalsIgnoreCase(args[0])) {
            EntityPlayerMP target = null;
            if (args.length > 1) {
                target = server.getPlayerList().getPlayerByUsername(args[1]);
            } else if (sender instanceof EntityPlayerMP) {
                target = (EntityPlayerMP) sender;
            }

            if (target == null) {
                sender.sendMessage(new TextComponentString("Player must be online: /rebornlp reset <player>"));
                return;
            }

            VillageSelectionHandler.INSTANCE.resetSelection(target, true, true);
            sender.sendMessage(new TextComponentString("Village selection reset for " + target.getName()));
            return;
        }

        LuckPermsBridge bridge = LuckPermsBridge.INSTANCE;
        sender.sendMessage(new TextComponentString("LuckPerms detected: " + bridge.isAvailable(server)));
        sender.sendMessage(new TextComponentString("Provider: " + bridge.providerName()));
        sender.sendMessage(new TextComponentString("Bukkit plugin: " + bridge.hasBukkitPlugin()));
        sender.sendMessage(new TextComponentString("Village groups: stone, leaf, cloud, sand, mist"));
        sender.sendMessage(new TextComponentString("Reset command: /rebornlp reset [player]"));
    }

    public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        execute(server, sender, args);
    }
}
