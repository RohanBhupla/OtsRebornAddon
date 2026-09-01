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
import net.rebornaddon.jutsu.JutsuConfigurationService;
import net.rebornaddon.jutsu.JutsuDefinition;
import net.rebornaddon.jutsu.JutsuPluginBridge;
import net.rebornaddon.jutsu.JutsuRegistry;
import net.rebornaddon.jutsu.JutsuRuntimeHooks;
import net.rebornaddon.jutsu.JutsuValueParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RebornJutsuCommand extends CommandBase {
    private static final List<String> ROOTS = Arrays.asList(
            "status", "list", "search", "get", "set", "reset", "resetall", "reload",
            "clearcooldowns", "gui");

    @Override
    public String getName() {
        return "rebornjutsu";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornjutsu <status|list|search|get|set|reset|resetall|reload|clearcooldowns|gui>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        if (sender.canUseCommand(2, getName())) {
            return true;
        }
        return JutsuPluginBridge.canAdmin(sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            help(sender);
            return;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(root)) {
            status(sender);
            return;
        }
        if ("clearcooldowns".equals(root)) {
            clearCooldowns(server, sender, args);
            return;
        }
        if (!JutsuPluginBridge.isAvailable()) {
            error(sender, "The hosted server jutsu configuration plugin is unavailable. Mod defaults remain active.");
            return;
        }

        if ("list".equals(root)) {
            list(sender, args.length > 1 ? args[1] : "");
        } else if ("search".equals(root)) {
            search(sender, join(args, 1));
        } else if ("get".equals(root)) {
            if (args.length < 2) {
                error(sender, "Usage: /rebornjutsu get <jutsu>");
            } else {
                show(sender, args[1]);
            }
        } else if ("set".equals(root)) {
            set(sender, args);
        } else if ("reset".equals(root)) {
            reset(sender, args);
        } else if ("resetall".equals(root)) {
            respond(sender, JutsuConfigurationService.INSTANCE.resetAll(sender));
        } else if ("reload".equals(root)) {
            respond(sender, JutsuConfigurationService.INSTANCE.reload(sender));
        } else if ("gui".equals(root)) {
            if (!(sender instanceof EntityPlayerMP)) {
                error(sender, "The jutsu administration GUI can only be opened by a player.");
            } else {
                JutsuConfigurationService.INSTANCE.openAdmin((EntityPlayerMP) sender);
            }
        } else {
            help(sender);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (!checkPermission(server, sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ROOTS);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if ("clearcooldowns".equals(root)) {
                List<String> targets = new ArrayList<String>();
                targets.add("all");
                targets.addAll(Arrays.asList(server.getOnlinePlayerNames()));
                return getListOfStringsMatchingLastWord(args, targets);
            }
            if ("list".equals(root)) {
                return getListOfStringsMatchingLastWord(args, JutsuRegistry.INSTANCE.namespaces());
            }
            if ("get".equals(root) || "set".equals(root) || "reset".equals(root)) {
                return getListOfStringsMatchingLastWord(args, JutsuRegistry.INSTANCE.ids());
            }
        }
        if (args.length == 3 && ("set".equals(root) || "reset".equals(root))) {
            JutsuDefinition definition = JutsuRegistry.INSTANCE.get(args[1]);
            if (definition == null) {
                return Collections.emptyList();
            }
            List<String> properties = new ArrayList<String>(definition.properties());
            if ("reset".equals(root)) {
                properties.add("all");
            }
            return getListOfStringsMatchingLastWord(args, properties);
        }
        if (args.length == 4 && "set".equals(root)) {
            if ("enabled".equalsIgnoreCase(args[2])
                    || "negative-effects-enabled".equalsIgnoreCase(args[2])) {
                return getListOfStringsMatchingLastWord(args, "true", "false");
            }
            if ("cooldown".equalsIgnoreCase(args[2])) {
                return getListOfStringsMatchingLastWord(args, "0s", "20s", "5m", "1h30m");
            }
            if ("charge-time".equalsIgnoreCase(args[2])) {
                return getListOfStringsMatchingLastWord(args, "instant", "1s", "5s", "30s");
            }
            return getListOfStringsMatchingLastWord(args, "0", "1", "10", "25");
        }
        return Collections.emptyList();
    }

    private static void status(ICommandSender sender) {
        boolean plugin = JutsuPluginBridge.isAvailable();
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Reborn jutsu configuration"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Server plugin: "
                + (plugin ? TextFormatting.GREEN + "available" : TextFormatting.RED + "unavailable")));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Catalog entries: "
                + TextFormatting.WHITE + JutsuRegistry.INSTANCE.all().size()));
        if (!plugin) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                    + "All jutsus are using their original mod behavior."));
        }
    }

    private static void list(ICommandSender sender, String namespace) {
        String filter = namespace == null ? "" : namespace.toLowerCase(Locale.ROOT);
        int shown = 0;
        int total = 0;
        for (JutsuDefinition definition : JutsuRegistry.INSTANCE.all()) {
            if (filter.length() > 0 && !definition.source().equals(filter)) {
                continue;
            }
            total++;
            if (shown < 100) {
                sender.sendMessage(new TextComponentString(TextFormatting.GRAY + definition.id()
                        + TextFormatting.DARK_GRAY + " - " + definition.displayName()));
                shown++;
            }
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Found " + total
                + " jutsu entries" + (total > shown ? "; showing the first " + shown : "") + "."));
    }

    private static void search(ICommandSender sender, String query) {
        String filter = query.toLowerCase(Locale.ROOT).trim();
        if (filter.length() == 0) {
            error(sender, "Usage: /rebornjutsu search <text>");
            return;
        }
        int count = 0;
        for (JutsuDefinition definition : JutsuRegistry.INSTANCE.all()) {
            if (definition.id().contains(filter)
                    || definition.displayName().toLowerCase(Locale.ROOT).contains(filter)) {
                sender.sendMessage(new TextComponentString(TextFormatting.GRAY + definition.id()
                        + TextFormatting.DARK_GRAY + " - " + definition.displayName()));
                if (++count >= 100) {
                    break;
                }
            }
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Matches shown: " + count + "."));
    }

    private static void show(ICommandSender sender, String id) {
        JutsuDefinition definition = JutsuRegistry.INSTANCE.get(id);
        if (definition == null) {
            error(sender, "Unknown jutsu: " + id);
            return;
        }
        String[] snapshot = JutsuConfigurationService.INSTANCE.adminSnapshot(sender);
        if (!JutsuPluginBridge.successful(snapshot)) {
            respond(sender, snapshot);
            return;
        }
        String overrides = overridesFor(snapshot, definition.id());
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + definition.displayName()
                + TextFormatting.DARK_GRAY + " (" + definition.id() + ")"));
        for (String property : definition.properties()) {
            String override = findValue(overrides, property);
            String original = definition.defaultValue(property);
            String value = override == null ? original : override;
            String display = value == null || value.length() == 0 ? "mod default" : format(property, value);
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + property + ": "
                    + TextFormatting.WHITE + display + (override == null ? "" : TextFormatting.AQUA + " (override)")));
        }
    }

    private static void set(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            error(sender, "Usage: /rebornjutsu set <jutsu> <property> <value>");
            return;
        }
        JutsuDefinition definition = JutsuRegistry.INSTANCE.get(args[1]);
        String property = args[2].toLowerCase(Locale.ROOT).replace('_', '-');
        if (definition == null) {
            error(sender, "Unknown jutsu: " + args[1]);
            return;
        }
        if (!definition.supports(property)) {
            error(sender, "That jutsu does not support " + property + ".");
            return;
        }

        String value;
        try {
            value = JutsuValueParser.normalize(property, args[3]);
        } catch (IllegalArgumentException exception) {
            error(sender, exception.getMessage());
            return;
        }
        respond(sender, JutsuConfigurationService.INSTANCE.update(sender,
                definition.id(), property, value));
    }

    private static void reset(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "Usage: /rebornjutsu reset <jutsu> <property|all>");
            return;
        }
        JutsuDefinition definition = JutsuRegistry.INSTANCE.get(args[1]);
        if (definition == null) {
            error(sender, "Unknown jutsu: " + args[1]);
            return;
        }
        respond(sender, JutsuConfigurationService.INSTANCE.reset(sender,
                definition.id(), args[2].toLowerCase(Locale.ROOT).replace('_', '-')));
    }

    private static void clearCooldowns(MinecraftServer server, ICommandSender sender,
                                       String[] args) throws CommandException {
        if (args.length < 2) {
            error(sender, "Usage: /rebornjutsu clearcooldowns <player|all>");
            return;
        }
        if ("all".equalsIgnoreCase(args[1])) {
            int count = JutsuRuntimeHooks.clearAllCooldowns(server);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                    + "Cleared current cooldowns for all players; " + count
                    + (count == 1 ? " online player was" : " online players were")
                    + " updated immediately."));
            return;
        }
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(args[1]);
        GameProfile profile = target == null ? server.getPlayerProfileCache()
                .getGameProfileForUsername(args[1]) : target.getGameProfile();
        if (profile == null || profile.getId() == null) {
            error(sender, "That player could not be found.");
            return;
        }
        JutsuRuntimeHooks.clearAllCooldowns(server, profile.getId());
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                + "Cleared all current cooldowns for " + profile.getName() + "."));
    }

    private static String format(String property, String value) {
        if ("cooldown".equals(property) || "charge-time".equals(property)
                || "max-duration".equals(property)) {
            try {
                long ticks = Long.parseLong(value);
                return "charge-time".equals(property) && ticks == 0L
                        ? "instant" : JutsuRuntimeHooks.formatDuration(ticks);
            } catch (NumberFormatException ignored) {
            }
        }
        return value;
    }

    private static String overridesFor(String[] snapshot, String id) {
        for (int i = 2; i < snapshot.length; i++) {
            String[] fields = snapshot[i].split("\\t", -1);
            if (fields.length >= 7 && id.equals(fields[0])) {
                return fields.length >= 9 ? fields[8] : fields[6];
            }
        }
        return "";
    }

    private static String findValue(String values, String property) {
        for (String entry : values.split(";")) {
            int separator = entry.indexOf('=');
            if (separator > 0 && property.equals(entry.substring(0, separator))) {
                return entry.substring(separator + 1);
            }
        }
        return null;
    }

    private static void respond(ICommandSender sender, String[] response) {
        boolean success = JutsuPluginBridge.successful(response);
        sender.sendMessage(new TextComponentString((success ? TextFormatting.GREEN : TextFormatting.RED)
                + JutsuPluginBridge.message(response)));
    }

    private static void help(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Reborn jutsu administration"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornjutsu list [namespace], search <text>, get <jutsu>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornjutsu set <jutsu> <property> <value>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornjutsu reset <jutsu> <property|all>, resetall, reload, gui"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornjutsu clearcooldowns <player|all>"));
    }

    private static void error(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(TextFormatting.RED + message));
    }

    private static String join(String[] values, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < values.length; i++) {
            if (result.length() > 0) result.append(' ');
            result.append(values[i]);
        }
        return result.toString();
    }
}
