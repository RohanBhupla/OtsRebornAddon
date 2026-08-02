package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.compat.CustomNpcQuestImporter;

import java.util.Arrays;
import java.util.List;

public class CustomNpcQuestImportCommand extends CommandBase {
    @Override
    public String getName() {
        return "importcustomnpcquests";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/importcustomnpcquests [preview] [keepcustomnpcs]";
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
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
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
                + ", chapters created " + result.chaptersCreated
                + ", dialog tasks " + result.dialogTasks
                + ", dependencies " + result.dependencies
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
}
