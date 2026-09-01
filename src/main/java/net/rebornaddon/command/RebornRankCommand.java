package net.rebornaddon.command;

import com.google.gson.JsonObject;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.exam.ExamPluginBridge;
import net.rebornaddon.exam.ExamService;
import net.rebornaddon.exam.RankService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RebornRankCommand extends CommandBase {
    @Override
    public String getName() {
        return "rebornrank";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornrank <get|set|list> [player] [rank]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            sender.sendMessage(message(TextFormatting.GOLD + "Configured ninja ranks:"));
            for (RankService.Rank rank : RankService.INSTANCE.ranks()) {
                sender.sendMessage(message(TextFormatting.GRAY + rank.name + " - "
                        + rank.xpCap + " ninja XP (group: " + rank.lpGroup + ")"));
            }
            return;
        }
        if ("get".equals(action) && args.length == 2) {
            EntityPlayerMP target = getPlayer(server, sender, args[1]);
            String rankId = RankService.INSTANCE.rankId(target);
            RankService.Rank rank = RankService.INSTANCE.rank(rankId);
            sender.sendMessage(message(TextFormatting.GOLD + target.getName() + ": "
                    + (rank == null ? rankId : rank.name) + " (cap "
                    + RankService.INSTANCE.cap(target) + ")"));
            return;
        }
        if ("set".equals(action) && args.length == 3) {
            EntityPlayerMP target = getPlayer(server, sender, args[1]);
            RankService.Rank rank = RankService.INSTANCE.rank(args[2]);
            if (rank == null) throw new CommandException("Unknown rank: " + args[2]);
            if (!ExamPluginBridge.isAvailable()) {
                if (!server.isSinglePlayer()) {
                    throw new CommandException("The hosted exam and rank plugin is unavailable.");
                }
                RankService.INSTANCE.applyRank(target, rank.id, rank.xpCap);
            } else {
                String[] response = ExamPluginBridge.setRank(sender, target, rank.id);
                if (!ExamPluginBridge.successful(response)) {
                    throw new CommandException(ExamPluginBridge.message(response));
                }
                long cap = rank.xpCap;
                String acceptedId = rank.id;
                try {
                    JsonObject payload = ExamService.parseObject(ExamPluginBridge.payload(response));
                    if (payload.has("id")) acceptedId = payload.get("id").getAsString();
                    if (payload.has("xpCap")) cap = payload.get("xpCap").getAsLong();
                } catch (Throwable ignored) {
                }
                RankService.INSTANCE.applyRank(target, acceptedId, cap);
            }
            RankService.INSTANCE.notifyAssigned(target, rank.id);
            sender.sendMessage(message(TextFormatting.GREEN + "Set " + target.getName()
                    + " to " + rank.name + "."));
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("get", "set", "list"));
        }
        if (args.length == 2 && ("get".equalsIgnoreCase(args[0])
                || "set".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (RankService.Rank rank : RankService.INSTANCE.ranks()) ids.add(rank.id);
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }

    private static TextComponentString message(String text) {
        return new TextComponentString(text);
    }
}
