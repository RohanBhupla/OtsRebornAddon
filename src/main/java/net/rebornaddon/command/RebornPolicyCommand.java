package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RebornPolicyCommand extends CommandBase {
    @Override
    public String getName() {
        return "rebornpolicy";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornpolicy <list|check|deny|allow|reload> [action] [registry-pattern]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new CommandException(getUsage(sender));
        if ("reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString(ContentPolicyService.INSTANCE.reload()
                    ? TextFormatting.GREEN + "Content policy reloaded. Legacy blockers remain active."
                    : TextFormatting.RED + "Content policy could not be reloaded; the last valid snapshot remains active."));
            return;
        }
        if (args.length < 2) throw new CommandException(getUsage(sender));
        PolicyAction action = PolicyAction.parse(args[1]);
        if (action == null) throw new CommandException("Unknown policy action: " + args[1]);
        if ("list".equalsIgnoreCase(args[0])) {
            List<String> rules = ContentPolicyService.INSTANCE.rules(action);
            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + action.name() + " custom denials: " + rules.size()));
            for (String rule : rules) sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "- " + rule));
            return;
        }
        if (args.length < 3) throw new CommandException(getUsage(sender));
        String pattern = args[2].toLowerCase(Locale.ROOT);
        if ("check".equalsIgnoreCase(args[0])) {
            boolean custom = ContentPolicyService.INSTANCE.denies(action, pattern);
            boolean baseline = ShinobiAddonRestrictionHandler.shouldRestrictName(parseResource(pattern));
            sender.sendMessage(new TextComponentString((custom || baseline ? TextFormatting.RED : TextFormatting.GREEN)
                    + pattern + " -> baseline=" + baseline + ", custom " + action.name().toLowerCase(Locale.ROOT)
                    + "=" + custom));
            return;
        }
        boolean changed;
        if ("deny".equalsIgnoreCase(args[0])) changed = ContentPolicyService.INSTANCE.deny(action, pattern);
        else if ("allow".equalsIgnoreCase(args[0])) changed = ContentPolicyService.INSTANCE.allow(action, pattern);
        else throw new CommandException(getUsage(sender));
        sender.sendMessage(new TextComponentString((changed ? TextFormatting.GREEN : TextFormatting.YELLOW)
                + (changed ? "Policy updated. " : "No policy change. ")
                + "Existing baseline blockers were not modified."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "list", "check", "deny", "allow", "reload");
        if (args.length == 2) {
            List<String> values = new ArrayList<String>();
            for (PolicyAction action : PolicyAction.values()) values.add(action.name().toLowerCase(Locale.ROOT));
            return getListOfStringsMatchingLastWord(args, values);
        }
        if (args.length == 3 && ("check".equalsIgnoreCase(args[0]) || "deny".equalsIgnoreCase(args[0])
                || "allow".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args,
                    net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getKeys());
        }
        return Collections.emptyList();
    }

    private static net.minecraft.util.ResourceLocation parseResource(String value) {
        try {
            return value.indexOf('*') >= 0 ? null : new net.minecraft.util.ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
