package net.rebornaddon.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.rebornaddon.content.NativeContentObjects;
import net.rebornaddon.content.NativeContentService;
import net.rebornaddon.content.NativeContentPluginBridge;
import net.rebornaddon.content.migration.CustomNpcMigrationImporter;
import net.rebornaddon.village.network.RebornAddonNetwork;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RebornContentCommand extends CommandBase {
    private static final List<String> ROOT = Arrays.asList("editor", "refresh", "mailbox", "pathtool", "migrate");

    @Override
    public String getName() { return "reborncontent"; }

    @Override
    public List<String> getAliases() { return Arrays.asList("rcontent", "npccontent"); }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/reborncontent <editor|refresh|mailbox|pathtool <pathId>|migrate <preview|run|undo>>";
    }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        if ("refresh".equalsIgnoreCase(args[0])) {
            boolean success = NativeContentService.INSTANCE.refreshRuntime();
            sender.sendMessage(new TextComponentString(success
                    ? "Native server content refreshed." : "The hosted content plugin is unavailable."));
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if ("editor".equalsIgnoreCase(args[0])) {
            NativeContentService.INSTANCE.sendAdminSnapshot(player);
            RebornAddonNetwork.sendNativeContentSync(player, "interaction", "{\"route\":\"world\"}");
            return;
        }
        if ("mailbox".equalsIgnoreCase(args[0]) && args.length == 1) {
            ItemStack mailbox = new ItemStack(NativeContentObjects.MAILBOX_ITEM);
            if (!player.inventory.addItemStackToInventory(mailbox)) {
                throw new CommandException("Make room in your inventory first.");
            }
            sender.sendMessage(new TextComponentString("Added a placeable Reborn mailbox."));
            return;
        }
        if ("pathtool".equalsIgnoreCase(args[0]) && args.length == 2) {
            ItemStack tool = new ItemStack(NativeContentObjects.PATH_TOOL);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("PathId", args[1]);
            tool.setTagCompound(tag);
            tool.setStackDisplayName("NPC Path Tool: " + args[1]);
            if (!player.inventory.addItemStackToInventory(tool)) {
                throw new CommandException("Make room in your inventory first.");
            }
            sender.sendMessage(new TextComponentString("Path tool bound to '" + args[1]
                    + "'. Right-click adds a point; sneak-right-click removes the last point."));
            return;
        }
        if ("migrate".equalsIgnoreCase(args[0]) && args.length == 2) {
            if ("preview".equalsIgnoreCase(args[1])) {
                CustomNpcMigrationImporter.Result result = CustomNpcMigrationImporter.inspect(server);
                if (!result.ok()) throw new CommandException(result.error());
                sender.sendMessage(new TextComponentString("Migration preview: " + result.quests()
                        + " quests, " + result.dialogs() + " dialogues, " + result.templates()
                        + " loaded NPC templates, " + result.actors() + " actors, "
                        + result.paths() + " paths, and " + result.warnings() + " review warnings."));
                sender.sendMessage(new TextComponentString("Source: " + result.source()));
                return;
            }
            if ("run".equalsIgnoreCase(args[1])) {
                String[] response = CustomNpcMigrationImporter.importToPlugin(server, player);
                if (!NativeContentPluginBridge.successful(response)) {
                    throw new CommandException(NativeContentPluginBridge.message(response));
                }
                sender.sendMessage(new TextComponentString(NativeContentPluginBridge.message(response)));
                NativeContentService.INSTANCE.refreshRuntime();
                NativeContentService.INSTANCE.sendAdminSnapshot(player);
                return;
            }
            if ("undo".equalsIgnoreCase(args[1])) {
                String[] response = NativeContentPluginBridge.adminAction(player,
                        "import.customnpcs.undo", "{}");
                if (!NativeContentPluginBridge.successful(response)) {
                    throw new CommandException(NativeContentPluginBridge.message(response));
                }
                sender.sendMessage(new TextComponentString(NativeContentPluginBridge.message(response)));
                NativeContentService.INSTANCE.refreshRuntime();
                NativeContentService.INSTANCE.sendAdminSnapshot(player);
                return;
            }
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, ROOT);
        if (args.length == 2 && "pathtool".equalsIgnoreCase(args[0])
                && sender instanceof EntityPlayerMP) {
            return getListOfStringsMatchingLastWord(args,
                    NativeContentService.INSTANCE.pathIds((EntityPlayerMP) sender));
        }
        if (args.length == 2 && "migrate".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "preview", "run", "undo");
        }
        return Collections.emptyList();
    }
}
