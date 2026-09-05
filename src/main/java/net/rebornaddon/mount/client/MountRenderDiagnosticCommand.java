package net.rebornaddon.mount.client;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;

/** Client-only access to the latest Armor Wolf render fallback evidence. */
@SideOnly(Side.CLIENT)
public final class MountRenderDiagnosticCommand extends CommandBase {
    @Override
    public String getName() {
        return "mountdiagnose";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("rebornmountdiagnose", "mountdiag");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/mountdiagnose [last|watch|clear]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            throw new CommandException("This diagnostic is only available in game.");
        }
        if (arguments.length > 1) throw new CommandException(getUsage(sender));
        ArmorWolfRenderDiagnostics.report((EntityPlayer) sender,
                arguments.length == 0 ? "" : arguments[0]);
    }
}
