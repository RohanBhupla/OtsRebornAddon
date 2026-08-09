package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.quest.CustomNpcQuestService;
import net.rebornaddon.quest.QuestTabDefinition;
import net.rebornaddon.quest.QuestTabStore;
import net.rebornaddon.village.Village;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RebornQuestsCommand extends CommandBase {
    private static final List<String> ROOT = Arrays.asList("sync", "refresh", "tabs");
    private static final List<String> TAB_ACTIONS = Arrays.asList("list", "create", "createvillage", "remove");
    private static final List<String> VILLAGES = Arrays.asList("stone", "leaf", "cloud", "sand", "mist", "rain");

    @Override
    public String getName() {
        return "rebornquests";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("rq", "questtabs");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornquests <sync|refresh|tabs>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        if ("sync".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            CustomNpcQuestService.send(player);
            sender.sendMessage(new TextComponentString("Quest screen data refreshed."));
            return;
        }

        requireAdmin(server, sender);
        if ("refresh".equalsIgnoreCase(args[0])) {
            int count = CustomNpcQuestService.refresh();
            CustomNpcQuestService.sendAll(server);
            sender.sendMessage(new TextComponentString("Loaded " + count + " CustomNPC quests into the mission tabs."));
            return;
        }

        if (!"tabs".equalsIgnoreCase(args[0]) || args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        handleTabs(server, sender, args);
    }

    private void handleTabs(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        QuestTabStore store = QuestTabStore.get(server);
        if (store == null) {
            throw new CommandException("The quest tab data is not available yet.");
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            if (store.all().isEmpty()) {
                sender.sendMessage(new TextComponentString("No custom quest tabs have been created."));
            } else {
                for (QuestTabDefinition tab : store.all()) {
                    String scope = tab.getVillage().isEmpty() ? "all villages" : tab.getVillage();
                    sender.sendMessage(new TextComponentString(tab.getId() + ": " + tab.getTitle()
                            + " | category '" + tab.getCategoryFilter() + "' | " + scope));
                }
            }
            return;
        }

        if ("remove".equals(action)) {
            if (args.length != 3) {
                throw new WrongUsageException("/rebornquests tabs remove <id>");
            }
            if (!store.remove(args[2])) {
                throw new CommandException("No custom quest tab exists with id '" + args[2] + "'.");
            }
            CustomNpcQuestService.sendAll(server);
            sender.sendMessage(new TextComponentString("Removed quest tab '" + args[2] + "'."));
            return;
        }

        if ("create".equals(action)) {
            if (args.length < 4) {
                throw new WrongUsageException("/rebornquests tabs create <id> <title>|<CustomNPC category>");
            }
            create(store, args[2], "", join(args, 3));
        } else if ("createvillage".equals(action)) {
            if (args.length < 5) {
                throw new WrongUsageException("/rebornquests tabs createvillage <id> <village> <title>|<CustomNPC category>");
            }
            Village village = Village.byGroup(args[3]);
            if (village == null) {
                throw new CommandException("Unknown village. Use stone, leaf, cloud, sand, mist, or rain.");
            }
            create(store, args[2], village.group(), join(args, 4));
        } else {
            throw new WrongUsageException("/rebornquests tabs <list|create|createvillage|remove>");
        }

        CustomNpcQuestService.sendAll(server);
        sender.sendMessage(new TextComponentString("Created quest tab '" + args[2] + "'."));
    }

    private void create(QuestTabStore store, String id, String village, String payload) throws CommandException {
        String normalizedId = QuestTabStore.normalizeId(id);
        if (!normalizedId.matches("[a-z0-9_-]{1,24}")) {
            throw new CommandException("Tab ids may only contain a-z, 0-9, underscores, and hyphens (24 characters max).");
        }

        int separator = payload.indexOf('|');
        if (separator < 1 || separator == payload.length() - 1) {
            throw new CommandException("Separate the tab title and CustomNPC category with |.");
        }
        String title = payload.substring(0, separator).trim();
        String filter = payload.substring(separator + 1).trim();
        if (title.isEmpty() || title.length() > 32 || filter.isEmpty() || filter.length() > 80) {
            throw new CommandException("Titles must be 1-32 characters and category filters must be 1-80 characters.");
        }

        if (!store.put(new QuestTabDefinition(normalizedId, title, filter, village))) {
            throw new CommandException("That id already exists or the " + QuestTabStore.MAX_TABS + " tab limit was reached.");
        }
    }

    private void requireAdmin(MinecraftServer server, ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(2, getName())) {
            throw new CommandException("You do not have permission to manage quest tabs.");
        }
    }

    private static String join(String[] args, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(args[i]);
        }
        return result.toString();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ROOT);
        }
        if (args.length == 2 && "tabs".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, TAB_ACTIONS);
        }
        if (args.length == 4 && "tabs".equalsIgnoreCase(args[0])
                && "createvillage".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, VILLAGES);
        }
        if (args.length == 3 && "tabs".equalsIgnoreCase(args[0]) && "remove".equalsIgnoreCase(args[1])) {
            QuestTabStore store = QuestTabStore.get(server);
            if (store != null) {
                String[] ids = new String[store.all().size()];
                for (int i = 0; i < store.all().size(); i++) {
                    ids[i] = store.all().get(i).getId();
                }
                return getListOfStringsMatchingLastWord(args, ids);
            }
        }
        return Collections.emptyList();
    }
}
