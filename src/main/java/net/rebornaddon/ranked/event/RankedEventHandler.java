package net.rebornaddon.ranked.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.ranked.RankedLocation;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.match.MatchManager;
import net.rebornaddon.ranked.network.RankedNetwork;
import net.rebornaddon.ranked.network.RankedSyncMessage;
import net.rebornaddon.ranked.queue.ProposedMatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The server-side glue between actual Forge events and MatchManager/QueueManager.
 * MatchManager itself has no event dependencies at all - this class is the only
 * thing that knows about LivingDeathEvent, PlayerRespawnEvent, etc., and just calls
 * straight into the manager methods.
 */
public class RankedEventHandler {

    private int tickCounter = 0;

    // Players whose respawn needs finalizing (gamemode/inventory/teleport) on the
    // NEXT tick after they actually respawn - mirrors the original plugin's "don't
    // touch a dead player" fix, adapted since Forge has no scheduleSyncDelayedTask.
    private final Set<UUID> pendingFinalizeNextTick = new HashSet<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!RankedSystem.isReady()) return;

        MatchManager matchManager = RankedSystem.matchManager;

        // Finalize any respawns queued up from last tick, now that the respawn itself
        // has actually completed.
        if (!pendingFinalizeNextTick.isEmpty()) {
            for (UUID uuid : new ArrayList<>(pendingFinalizeNextTick)) {
                matchManager.finalizePendingRespawn(uuid);
            }
            pendingFinalizeNextTick.clear();
        }

        // Boundary/countdown-freeze check every tick for every match participant.
        for (EntityPlayerMP p : net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getPlayerList().getPlayers()) {
            if (matchManager.isInMatch(p.getUniqueID())) {
                matchManager.tickPlayerBoundary(p);
            }
        }

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            matchManager.tickSecond();

            for (ProposedMatch proposal : RankedSystem.queueManager.tick()) {
                boolean started = matchManager.startMatch(proposal);
                if (!started) {
                    // No free arena right now - requeue everyone rather than losing their spot,
                    // and actually tell them why nothing happened instead of leaving them guessing.
                    // Re-add the ORIGINAL groups, not flattened individuals, so a party that got
                    // matched together doesn't get silently split apart by this failure path.
                    for (net.rebornaddon.ranked.queue.QueuedGroup g : proposal.getTeam0Groups()) {
                        RankedSystem.queueManager.joinGroup(proposal.getMode(), g);
                    }
                    for (net.rebornaddon.ranked.queue.QueuedGroup g : proposal.getTeam1Groups()) {
                        RankedSystem.queueManager.joinGroup(proposal.getMode(), g);
                    }

                    java.util.List<net.rebornaddon.ranked.queue.QueuedPlayer> all = new ArrayList<>(proposal.getTeam0());
                    all.addAll(proposal.getTeam1());
                    for (net.rebornaddon.ranked.queue.QueuedPlayer qp : all) {
                        EntityPlayerMP p = net.minecraftforge.fml.common.FMLCommonHandler.instance()
                                .getMinecraftServerInstance().getPlayerList().getPlayerByUUID(qp.getUuid());
                        if (p != null) {
                            p.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    net.minecraft.util.text.TextFormatting.RED
                                    + "A match was found but no arena is free right now - you've been re-queued."));
                        }
                    }
                }
            }

            syncAllPlayers();
            checkSeasonExpiry();
            RankedSystem.partyManager.cleanupExpiredInvites();
        }
    }

    private void checkSeasonExpiry() {
        if (!RankedSystem.seasonManager.isSeasonExpired()) return;

        int endingSeasonNumber = RankedSystem.seasonManager.getSeasonNumber();
        RankedSystem.seasonManager.endSeasonAndDistribute(
                RankedSystem.eloManager,
                this::grantItemOrQueue,
                uuid -> net.minecraftforge.fml.common.FMLCommonHandler.instance()
                        .getMinecraftServerInstance().getPlayerList().getPlayerByUUID(uuid) != null,
                RankedSystem.seasonManager.getSeasonDurationMillis()
        );

        String msg = net.minecraft.util.text.TextFormatting.GOLD + "Season " + endingSeasonNumber
                + " has ended! Rewards distributed, ELO reset. Season "
                + RankedSystem.seasonManager.getSeasonNumber() + " begins now.";
        for (EntityPlayerMP p : net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getPlayerList().getPlayers()) {
            p.sendMessage(new net.minecraft.util.text.TextComponentString(msg));
        }
    }

    /** Same logic as the admin command's manual season-end - kept here too since the
     *  automatic timer-based end needs it without going through a command sender. */
    private void grantItemOrQueue(UUID uuid, net.minecraft.item.ItemStack item) {
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

    private void syncAllPlayers() {
        List<PlayerStats> top = RankedSystem.eloManager.getTop(10);
        List<String> names = new ArrayList<>();
        List<Integer> elos = new ArrayList<>();
        for (PlayerStats s : top) {
            names.add(s.name);
            elos.add(s.elo);
        }

        List<EntityPlayerMP> allOnline = net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getPlayerList().getPlayers();
        List<String> allOnlineNames = new ArrayList<>();
        for (EntityPlayerMP p : allOnline) allOnlineNames.add(p.getName());

        for (EntityPlayerMP p : allOnline) {
            PlayerStats stats = RankedSystem.eloManager.getStats(p.getUniqueID(), p.getName());
            int state;
            int queuedModeNetId = -1;
            if (RankedSystem.matchManager.isInMatch(p.getUniqueID())) {
                state = 2;
            } else {
                net.rebornaddon.ranked.match.MatchMode queuedMode = RankedSystem.queueManager.getQueuedMode(p.getUniqueID());
                if (queuedMode != null) {
                    state = 1;
                    queuedModeNetId = queuedMode.getNetId();
                } else {
                    state = 0;
                }
            }

            long remainingMs = RankedSystem.seasonManager.getTimeRemainingMillis();
            int seasonDaysRemaining = (int) (remainingMs / (24L * 60 * 60 * 1000));

            net.rebornaddon.ranked.party.Party party = RankedSystem.partyManager.getParty(p.getUniqueID());
            boolean inParty = party != null;
            boolean isLeader = inParty && party.isLeader(p.getUniqueID());
            List<String> partyMemberNames = inParty ? party.getMemberNames() : java.util.Collections.emptyList();
            String pendingInviteFrom = RankedSystem.partyManager.getPendingInviteFromName(p.getUniqueID());
            if (pendingInviteFrom == null) pendingInviteFrom = "";

            long teleportRemainingMs = RankedSystem.partyManager.getTeleportCooldownRemainingMillis(p.getUniqueID());
            int teleportCooldownSeconds = (int) ((teleportRemainingMs + 999) / 1000); // round up

            List<String> invitablePlayers = new ArrayList<>();
            for (String name : allOnlineNames) {
                if (name.equals(p.getName())) continue;
                if (inParty && partyMemberNames.contains(name)) continue;
                invitablePlayers.add(name);
            }

            RankedNetwork.CHANNEL.sendTo(new RankedSyncMessage(stats.elo, stats.wins, stats.losses,
                    stats.draws, stats.currentWinStreak, stats.peakElo, state, queuedModeNetId,
                    RankedSystem.seasonManager.getSeasonNumber(), seasonDaysRemaining, names, elos,
                    inParty, isLeader, partyMemberNames, pendingInviteFrom,
                    teleportCooldownSeconds, invitablePlayers), p);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer)) return;
        if (!RankedSystem.isReady()) return;
        UUID uuid = event.getEntity().getUniqueID();
        if (!RankedSystem.matchManager.isInMatch(uuid)) return;

        RankedSystem.matchManager.onPlayerDeath(uuid);
        // Don't force an instant respawn - let the normal death screen/animation play,
        // same reasoning as the original plugin. PlayerRespawnEvent below redirects
        // where they land once they actually respawn.
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer)) return;
        if (!RankedSystem.isReady()) return;
        if (RankedSystem.matchManager.isInMatch(event.getEntity().getUniqueID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer)) return;
        if (!RankedSystem.isReady()) return;
        if (RankedSystem.matchManager.shouldBlockDamage(event.getEntity().getUniqueID())) {
            event.setCanceled(true);
            return;
        }

        // Party friendly-fire prevention - applies everywhere, not just in a ranked
        // match, since party members are never supposed to end up fighting each other
        // (matchmaking never splits a party across opposing teams either).
        net.minecraft.entity.Entity attacker = event.getSource().getTrueSource();
        if (attacker instanceof EntityPlayer) {
            UUID attackerUuid = attacker.getUniqueID();
            UUID victimUuid = event.getEntity().getUniqueID();
            if (!attackerUuid.equals(victimUuid)) {
                net.rebornaddon.ranked.party.Party attackerParty = RankedSystem.partyManager.getParty(attackerUuid);
                if (attackerParty != null && attackerParty.contains(victimUuid)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!RankedSystem.isReady()) return;
        UUID uuid = event.player.getUniqueID();
        RankedLocation redirect = RankedSystem.matchManager.getPendingRespawnLocation(uuid);
        if (redirect == null) return;

        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) event.player;
            if (p.dimension != redirect.dimensionId) {
                p.changeDimension(redirect.dimensionId);
            }
            p.connection.setPlayerLocation(redirect.x, redirect.y, redirect.z, redirect.yaw, redirect.pitch);
        }

        // Finalize gamemode/inventory next tick, once the respawn has actually completed.
        pendingFinalizeNextTick.add(uuid);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!RankedSystem.isReady()) return;
        UUID uuid = event.player.getUniqueID();
        RankedSystem.queueManager.leaveAll(uuid);
        RankedSystem.matchManager.onPlayerQuit(uuid);
        RankedSystem.partyManager.handleDisconnect(uuid);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!RankedSystem.isReady()) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) event.player;

        List<net.minecraft.item.ItemStack> pending = RankedSystem.seasonManager.takePendingRewards(p.getUniqueID());
        if (pending.isEmpty()) return;

        p.sendMessage(new net.minecraft.util.text.TextComponentString(
                net.minecraft.util.text.TextFormatting.GOLD + "You have " + pending.size()
                + " season reward(s) waiting - check your inventory!"));
        for (net.minecraft.item.ItemStack item : pending) {
            net.minecraft.item.ItemStack toGive = item.copy();
            p.inventory.addItemStackToInventory(toGive);
            if (!toGive.isEmpty()) {
                // Still couldn't fit - re-queue rather than lose it.
                RankedSystem.seasonManager.queuePendingReward(p.getUniqueID(), toGive);
            }
        }
    }
}
