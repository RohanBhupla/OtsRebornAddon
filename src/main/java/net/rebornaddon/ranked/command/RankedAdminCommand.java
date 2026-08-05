package net.rebornaddon.ranked.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.ranked.RankedLocation;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.arena.Arena;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RankedAdminCommand extends CommandBase {

    @Override
    public String getName() {
        return "rankedadmin";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rankedadmin <pos1|pos2|create|addspawn|clearspawns|resize|list|remove|forceend|setloserspawn>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // op level, same as the original plugin.yml permission default
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("pos1", "pos2", "create", "addspawn",
                    "clearspawns", "resize", "list", "remove", "forceend", "setloserspawn", "season"));
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            if ("addspawn".equals(subcommand) || "clearspawns".equals(subcommand) || "resize".equals(subcommand)
                    || "remove".equals(subcommand)) {
                return getListOfStringsMatchingLastWord(args, arenaIds());
            }

            if ("forceend".equals(subcommand)) {
                return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            }

            if ("season".equals(subcommand)) {
                return getListOfStringsMatchingLastWord(args, Arrays.asList("info", "end", "setreward"));
            }
        }

        if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            if ("addspawn".equals(subcommand) || "clearspawns".equals(subcommand)) {
                return getListOfStringsMatchingLastWord(args, "0", "1");
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString("This command can only be used in-game."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        RankedLocation here = new RankedLocation(player.dimension, player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch);

        switch (args[0].toLowerCase()) {
            case "pos1":
                RankedSystem.arenaManager.setPos1(player.getUniqueID(), here);
                player.sendMessage(msg(TextFormatting.GREEN + "Position 1 set at your location."));
                return;

            case "pos2":
                RankedSystem.arenaManager.setPos2(player.getUniqueID(), here);
                player.sendMessage(msg(TextFormatting.GREEN + "Position 2 set at your location."));
                return;

            case "create":
                if (args.length < 2) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin create <arenaId>"));
                    return;
                }
                Arena arena = RankedSystem.arenaManager.createFromPending(player.getUniqueID(), args[1]);
                if (arena == null) {
                    player.sendMessage(msg(TextFormatting.RED + "Set both pos1 and pos2 first!"));
                } else {
                    player.sendMessage(msg(TextFormatting.GREEN + "Arena '" + args[1] + "' created. Now add spawns with "
                            + "/rankedadmin addspawn " + args[1] + " <0|1>"));
                }
                return;

            case "resize":
                if (args.length < 2) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin resize <arenaId>"));
                    return;
                }
                boolean resized = RankedSystem.arenaManager.resizeFromPending(player.getUniqueID(), args[1]);
                if (resized) {
                    player.sendMessage(msg(TextFormatting.GREEN + "Arena '" + args[1] + "' corners updated. Spawns unchanged."));
                } else {
                    player.sendMessage(msg(TextFormatting.RED + "Either that arena doesn't exist, or pos1/pos2 aren't both set."));
                }
                return;

            case "addspawn":
                if (args.length < 3) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin addspawn <arenaId> <0|1>"));
                    return;
                }
                int team;
                try {
                    team = Integer.parseInt(args[2]);
                    if (team != 0 && team != 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(msg(TextFormatting.RED + "Team must be 0 or 1."));
                    return;
                }
                boolean added = RankedSystem.arenaManager.addSpawn(args[1], team, here);
                if (added) {
                    player.sendMessage(msg(TextFormatting.GREEN + "Spawn added to team " + team + " of arena '" + args[1] + "'."));
                } else {
                    player.sendMessage(msg(TextFormatting.RED + "No arena found with id '" + args[1] + "'."));
                }
                return;

            case "clearspawns":
                if (args.length < 2) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin clearspawns <arenaId> [0|1]"));
                    return;
                }
                int teamToClear = -1;
                if (args.length >= 3) {
                    try {
                        teamToClear = Integer.parseInt(args[2]);
                        if (teamToClear != 0 && teamToClear != 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage(msg(TextFormatting.RED + "Team must be 0 or 1 (or omit it to clear both)."));
                        return;
                    }
                }
                boolean cleared = RankedSystem.arenaManager.clearSpawns(args[1], teamToClear);
                player.sendMessage(cleared
                        ? msg(TextFormatting.GREEN + "Spawns cleared for arena '" + args[1] + "'.")
                        : msg(TextFormatting.RED + "No arena found with id '" + args[1] + "'."));
                return;

            case "list":
                player.sendMessage(msg(TextFormatting.GOLD + "=== Arenas ==="));
                for (Arena a : RankedSystem.arenaManager.all()) {
                    String size = "not set";
                    if (a.corner1 != null && a.corner2 != null) {
                        int dx = (int) Math.abs(a.corner1.x - a.corner2.x);
                        int dy = (int) Math.abs(a.corner1.y - a.corner2.y);
                        int dz = (int) Math.abs(a.corner1.z - a.corner2.z);
                        size = dx + "x" + dy + "x" + dz;
                    }
                    player.sendMessage(msg(TextFormatting.GRAY + "- " + a.id
                            + " | team0 spawns: " + a.team0Spawns.size()
                            + " | team1 spawns: " + a.team1Spawns.size()
                            + " | in use: " + a.inUse + " | size: " + size));

                    for (int t = 0; t <= 1; t++) {
                        for (int i = 0; i < a.getTeamSpawns(t).size(); i++) {
                            if (!a.contains(a.getTeamSpawns(t).get(i))) {
                                player.sendMessage(msg(TextFormatting.RED + "  spawn OUTSIDE bounds: team" + t + " #" + (i + 1)));
                            }
                        }
                    }
                }
                return;

            case "remove":
                if (args.length < 2) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin remove <arenaId>"));
                    return;
                }
                boolean removed = RankedSystem.arenaManager.remove(args[1]);
                player.sendMessage(removed ? msg(TextFormatting.GREEN + "Arena removed.")
                        : msg(TextFormatting.RED + "No arena found with that id."));
                return;

            case "forceend":
                if (args.length < 2) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin forceend <player>"));
                    return;
                }
                EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(args[1]);
                if (target == null) {
                    player.sendMessage(msg(TextFormatting.RED + "Player not found (must be online)."));
                    return;
                }
                boolean ended = RankedSystem.matchManager.forceEndDraw(target.getUniqueID());
                player.sendMessage(ended ? msg(TextFormatting.GREEN + "Match ended.")
                        : msg(TextFormatting.RED + "That player isn't in a match."));
                return;

            case "setloserspawn":
                RankedSystem.matchManager.setLoserSpawn(here);
                player.sendMessage(msg(TextFormatting.GREEN + "Loser spawn point set at your location."));
                return;

            case "season":
                handleSeasonSubcommand(player, args);
                return;

            default:
                sendUsage(player);
        }
    }

    private void handleSeasonSubcommand(EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg(TextFormatting.GOLD + "=== Season Commands ==="));
            player.sendMessage(msg(TextFormatting.GRAY + "/rankedadmin season info"));
            player.sendMessage(msg(TextFormatting.GRAY + "/rankedadmin season end  (ends now, distributes rewards, hard-resets ELO, starts next season)"));
            player.sendMessage(msg(TextFormatting.GRAY + "/rankedadmin season setreward <tierName>  (hold the item first)"));
            player.sendMessage(msg(TextFormatting.DARK_GRAY + "Tier names: Academy Student, Genin, Chunin, Jonin, Elite Jonin, Kage"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "info": {
                net.rebornaddon.ranked.season.SeasonManager sm = RankedSystem.seasonManager;
                long remainingMs = sm.getTimeRemainingMillis();
                long days = remainingMs / (24L * 60 * 60 * 1000);
                long hours = (remainingMs / (60L * 60 * 1000)) % 24;

                player.sendMessage(msg(TextFormatting.GOLD + "=== Season " + sm.getSeasonNumber() + " ==="));
                player.sendMessage(msg(TextFormatting.GRAY + "Time remaining: " + TextFormatting.WHITE + days + "d " + hours + "h"));
                player.sendMessage(msg(TextFormatting.GRAY + "Configured rewards: " + TextFormatting.WHITE
                        + (sm.getConfiguredTierNames().isEmpty() ? "none set" : String.join(", ", sm.getConfiguredTierNames().keySet()))));
                return;
            }

            case "end": {
                net.rebornaddon.ranked.season.SeasonManager sm = RankedSystem.seasonManager;
                sm.endSeasonAndDistribute(
                        RankedSystem.eloManager,
                        this::grantItemOrQueue,
                        uuid -> player.getServer().getPlayerList().getPlayerByUUID(uuid) != null,
                        sm.getSeasonDurationMillis()
                );
                player.sendMessage(msg(TextFormatting.GREEN + "Season ended. Rewards distributed, ELO reset, season "
                        + sm.getSeasonNumber() + " has begun."));
                return;
            }

            case "setreward": {
                if (args.length < 3) {
                    player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin season setreward <tierName>  (hold the item first)"));
                    return;
                }
                String tierName = args[2];
                net.minecraft.item.ItemStack held = player.getHeldItemMainhand();
                if (held == null || held.isEmpty()) {
                    player.sendMessage(msg(TextFormatting.RED + "Hold the item you want to set as the reward first."));
                    return;
                }
                RankedSystem.seasonManager.setReward(tierName, held.copy());
                player.sendMessage(msg(TextFormatting.GREEN + "Reward for '" + tierName + "' set to your held item."));
                return;
            }

            default:
                player.sendMessage(msg(TextFormatting.RED + "Usage: /rankedadmin season <info|end|setreward>"));
        }
    }

    /** Used as the "online player" branch when distributing season rewards - gives the
     *  item directly, and if their inventory can't fully hold it, queues the remainder
     *  as a pending reward rather than letting it vanish. */
    private void grantItemOrQueue(java.util.UUID uuid, net.minecraft.item.ItemStack item) {
        EntityPlayerMP p = net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getPlayerList().getPlayerByUUID(uuid);
        if (p == null) {
            RankedSystem.seasonManager.queuePendingReward(uuid, item);
            return;
        }
        net.minecraft.item.ItemStack toGive = item.copy();
        p.inventory.addItemStackToInventory(toGive);
        if (!toGive.isEmpty()) {
            RankedSystem.seasonManager.queuePendingReward(uuid, toGive);
        }
    }

    private void sendUsage(EntityPlayerMP player) {
        player.sendMessage(msg(TextFormatting.GOLD + "=== RankedDuels Arena Setup ==="));
        player.sendMessage(msg(TextFormatting.GRAY + "1. Stand at one corner: /rankedadmin pos1"));
        player.sendMessage(msg(TextFormatting.GRAY + "2. Stand at the opposite corner: /rankedadmin pos2"));
        player.sendMessage(msg(TextFormatting.GRAY + "3. Create the arena: /rankedadmin create <id>"));
        player.sendMessage(msg(TextFormatting.GRAY + "   (to fix corners later: pos1/pos2 again, then /rankedadmin resize <id>)"));
        player.sendMessage(msg(TextFormatting.GRAY + "4. Add spawns: /rankedadmin addspawn <id> <0|1>"));
        player.sendMessage(msg(TextFormatting.GRAY + "/rankedadmin list, remove <id>, clearspawns <id> [0|1]"));
        player.sendMessage(msg(TextFormatting.GRAY + "/rankedadmin forceend <player>, setloserspawn"));
        player.sendMessage(msg(TextFormatting.GRAY + "/rankedadmin season <info|end|setreward>"));
    }

    private TextComponentString msg(String s) {
        return new TextComponentString(s);
    }

    private static List<String> arenaIds() {
        if (RankedSystem.arenaManager == null) {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<String>();
        for (Arena arena : RankedSystem.arenaManager.all()) {
            ids.add(arena.id);
        }
        return ids;
    }
}
