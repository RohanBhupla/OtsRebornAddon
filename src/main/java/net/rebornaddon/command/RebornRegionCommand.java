package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.region.RegionPolicyService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RebornRegionCommand extends CommandBase {
    private static final Map<UUID, BlockPos> FIRST = new HashMap<UUID, BlockPos>();
    private static final Map<UUID, BlockPos> SECOND = new HashMap<UUID, BlockPos>();
    private static final String[] RULES = {"pvp", "jutsu", "modes", "weapons", "armor", "break",
            "place", "interact", "containers", "redstone", "teleport", "quests", "music"};

    @Override
    public String getName() {
        return "rebornregion";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornregion <pos1|pos2|create|delete|set|list|here|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new CommandException(getUsage(sender));
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            List<RegionPolicyService.RegionDefinition> values = RegionPolicyService.INSTANCE.regions();
            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Configured regions: " + values.size()));
            for (RegionPolicyService.RegionDefinition region : values) sender.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "- " + region.id + " dim " + region.dimension + " ["
                            + region.minX + "," + region.minY + "," + region.minZ + "] to ["
                            + region.maxX + "," + region.maxY + "," + region.maxZ + "]"));
            return;
        }
        if ("reload".equals(action)) {
            sender.sendMessage(new TextComponentString(RegionPolicyService.INSTANCE.reload()
                    ? TextFormatting.GREEN + "Region policies reloaded."
                    : TextFormatting.RED + "Region policies could not be reloaded; the previous snapshot is still active."));
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if ("pos1".equals(action) || "pos2".equals(action)) {
            BlockPos pos = player.getPosition();
            ("pos1".equals(action) ? FIRST : SECOND).put(player.getUniqueID(), pos);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + action + " set to "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return;
        }
        if ("here".equals(action)) {
            List<RegionPolicyService.RegionDefinition> values = RegionPolicyService.INSTANCE.at(player);
            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Regions here: " + values.size()));
            for (RegionPolicyService.RegionDefinition region : values) {
                sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "- " + region.id + " " + region.rules));
            }
            return;
        }
        if ("create".equals(action)) {
            if (args.length < 2) throw new CommandException("/rebornregion create <id> [priority]");
            BlockPos first = FIRST.get(player.getUniqueID());
            BlockPos second = SECOND.get(player.getUniqueID());
            if (first == null || second == null) throw new CommandException("Set pos1 and pos2 first.");
            RegionPolicyService.RegionDefinition region = new RegionPolicyService.RegionDefinition();
            region.id = args[1];
            region.dimension = player.dimension;
            region.minX = first.getX(); region.minY = first.getY(); region.minZ = first.getZ();
            region.maxX = second.getX(); region.maxY = second.getY(); region.maxZ = second.getZ();
            region.priority = args.length > 2 ? parseInt(args[2], -100000, 100000) : 0;
            RegionPolicyService.INSTANCE.put(region);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Region saved: " + region.id));
            return;
        }
        if ("delete".equals(action)) {
            if (args.length < 2) throw new CommandException("/rebornregion delete <id>");
            sender.sendMessage(new TextComponentString(RegionPolicyService.INSTANCE.remove(args[1])
                    ? TextFormatting.GREEN + "Region deleted."
                    : TextFormatting.YELLOW + "No matching region."));
            return;
        }
        if ("set".equals(action)) {
            if (args.length < 4) throw new CommandException("/rebornregion set <id> <rule> <allow|deny|default>");
            sender.sendMessage(new TextComponentString(RegionPolicyService.INSTANCE.setRule(args[1], args[2], args[3])
                    ? TextFormatting.GREEN + "Region rule updated."
                    : TextFormatting.RED + "Region or rule was invalid."));
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args,
                "pos1", "pos2", "create", "delete", "set", "list", "here", "reload");
        if (args.length == 2 && ("delete".equalsIgnoreCase(args[0]) || "set".equalsIgnoreCase(args[0]))) {
            java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
            for (RegionPolicyService.RegionDefinition region : RegionPolicyService.INSTANCE.regions()) ids.add(region.id);
            return getListOfStringsMatchingLastWord(args, ids);
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, RULES);
        if (args.length == 4 && "set".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args,
                "allow", "deny", "default");
        return Collections.emptyList();
    }
}
