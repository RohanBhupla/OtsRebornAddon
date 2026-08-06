package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.compat.CustomNpcQuestImporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CustomNpcQuestImportCommand extends CommandBase {
    @Override
    public String getName() {
        return "importcustomnpcquests";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/importcustomnpcquests [preview] [keepcustomnpcs] | /importcustomnpcquests undo [latest|backup-folder]";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("npcquests2ftb", "importnpcquests");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            BlockPos targetPos) {
        if (args.length == 0 || args.length > 3) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    new ArrayList<String>(Arrays.asList("preview", "keepcustomnpcs", "keep", "undo")));
        }

        if ("undo".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return getListOfStringsMatchingLastWord(args,
                        new ArrayList<String>(Arrays.asList("latest")));
            }
            return Collections.emptyList();
        }

        List<String> options = new ArrayList<String>(Arrays.asList("preview", "keepcustomnpcs", "keep"));
        for (int i = 0; i < args.length - 1; i++) {
            String used = args[i].toLowerCase();
            if ("preview".equals(used)) {
                options.remove("preview");
            } else if ("keepcustomnpcs".equals(used) || "keep".equals(used)) {
                options.remove("keepcustomnpcs");
                options.remove("keep");
            }
        }

        return getListOfStringsMatchingLastWord(args, options);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 0 && "undo".equalsIgnoreCase(args[0])) {
            String backup = args.length <= 1 ? "latest" : joinArgs(args, 1);
            try {
                CustomNpcQuestImporter.UndoResult result = CustomNpcQuestImporter.undoConversion(backup);
                sender.sendMessage(new TextComponentString(formatUndoResult(result)));
                return;
            } catch (RuntimeException ex) {
                throw new CommandException("CustomNPC quest import undo failed: " + ex.getMessage());
            }
        }

        boolean preview = false;
        boolean keepCustomNpcRewards = false;

        for (String arg : args) {
            if ("preview".equalsIgnoreCase(arg)) {
                preview = true;
            } else if ("keepcustomnpcs".equalsIgnoreCase(arg) || "keep".equalsIgnoreCase(arg)) {
                keepCustomNpcRewards = true;
            }
        }

        try {
            CustomNpcQuestImporter.Result result = CustomNpcQuestImporter.importQuests(preview, keepCustomNpcRewards);
            sender.sendMessage(new TextComponentString(formatResult(result)));
        } catch (RuntimeException ex) {
            throw new CommandException("CustomNPC quest import failed: " + ex.getMessage());
        }
    }

    private static String formatResult(CustomNpcQuestImporter.Result result) {
        String prefix = result.preview ? "CustomNPC quest preview: " : "CustomNPC quest import: ";
        return prefix
                + "scanned " + result.scanned
                + ", imported " + result.imported
                + ", existing " + result.existing
                + ", skipped " + result.skipped
                + ", quests updated " + result.questsUpdated
                + ", quest tasks " + result.questTasks
                + ", chapters created " + result.chaptersCreated
                + ", dialog tasks " + result.dialogTasks
                + ", objective tasks " + result.objectiveTasks
                + ", dependencies " + result.dependencies
                + ", layout moved " + result.layoutUpdated
                + ", rewards " + result.rewards
                + ", reward tables " + result.rewardTables
                + ", reward table entries " + result.rewardTableEntries
                + ", descriptions updated " + result.descriptionsUpdated
                + ", CustomNPC reward sources " + result.customNpcRewardSources
                + ", CustomNPC reward fields cleared " + result.customNpcRewardsCleared
                + backupText(result)
                + ".";
    }

    private static String backupText(CustomNpcQuestImporter.Result result) {
        if (result.backupFile == null || result.backupFile.isEmpty()) {
            return result.keepCustomNpcRewards ? ", CustomNPC rewards kept" : "";
        }

        return ", backup " + result.backupFile;
    }

    private static String formatUndoResult(CustomNpcQuestImporter.UndoResult result) {
        return "CustomNPC quest import undo: FTB restored " + result.ftbRestored
                + ", CustomNPC quests restored " + result.customNpcRestored
                + ", missing " + result.customNpcMissing
                + ", backup " + result.backupFile
                + ".";
    }

    private static String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
