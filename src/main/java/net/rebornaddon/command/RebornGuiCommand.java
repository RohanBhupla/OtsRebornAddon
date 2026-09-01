package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.rebornaddon.village.network.RebornAddonNetwork;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RebornGuiCommand extends CommandBase {
    private static final List<String> PANELS = Arrays.asList("village", "discord", "discordcode");

    @Override
    public String getName() {
        return "reborngui";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("previewgui");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/reborngui <village|discord|discordcode>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 1 || args.length > 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String panel = args[0].toLowerCase(Locale.ROOT);
        if ("village".equals(panel)) {
            RebornAddonNetwork.openVillageGui(player);
            return;
        }
        if ("discord".equals(panel)) {
            RebornAddonNetwork.openDiscordLinkPrompt(player);
            return;
        }
        if ("discordcode".equals(panel)) {
            String code = args.length == 2 ? cleanCode(args[1]) : "TEST12";
            if (code.isEmpty()) {
                throw new WrongUsageException("/reborngui discordcode [code]");
            }
            RebornAddonNetwork.openDiscordLinkCode(player, code, "/link",
                    "Preview only. Use the generated code from the server plugin when linking.");
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, PANELS);
        }
        if (args.length == 2 && "discordcode".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, Collections.singletonList("TEST12"));
        }
        return Collections.emptyList();
    }

    private static String cleanCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (code.length() < 4 || code.length() > 16) {
            return "";
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isLetterOrDigit(code.charAt(i))) {
                return "";
            }
        }
        return code;
    }
}
