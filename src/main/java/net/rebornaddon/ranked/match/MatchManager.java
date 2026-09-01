package net.rebornaddon.ranked.match;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.rebornaddon.ranked.RankedLocation;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.arena.Arena;
import net.rebornaddon.ranked.arena.ArenaManager;
import net.rebornaddon.ranked.elo.EloManager;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.queue.ProposedMatch;
import net.rebornaddon.ranked.queue.QueuedPlayer;
import net.rebornaddon.gameplay.GameplaySystemsService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

public class MatchManager {

    private final ArenaManager arenaManager;
    private final EloManager eloManager;

    private final int startCountdownSeconds;
    private final int timeLimitSeconds;
    private RankedLocation loserSpawn; // null = losers just go back to their original spot too

    private final List<Match> activeMatches = new ArrayList<>();
    private final Map<UUID, Match> playerToMatch = new HashMap<>();
    private final Map<UUID, Integer> countdownRemaining = new HashMap<>(); // matchId hash -> seconds, tracked per-match below instead
    private final Map<Match, Integer> matchCountdown = new HashMap<>();
    private final Map<UUID, SpectatorSession> spectators = new HashMap<>();
    private static final String SPECTATOR_STATE = "RebornRankedSpectator";

    // Deferred restore/spectate to apply once a player actually respawns (mirrors the
    // original plugin's approach - never touch a player's position/gamemode while
    // they're still on the death screen, or it silently fails).
    private final Map<UUID, PendingAction> pendingActions = new HashMap<>();

    // Last position confirmed to be legitimately inside the arena (or the frozen
    // countdown spot) - Forge has no PlayerMoveEvent equivalent that hands this to us,
    // so we track it ourselves rather than making every caller supply it.
    private final Map<UUID, double[]> lastGoodPosition = new HashMap<>();

    private static class PendingAction {
        final boolean restoreOriginal;
        final boolean isLoser;
        final Match match;

        PendingAction(boolean restoreOriginal, boolean isLoser, Match match) {
            this.restoreOriginal = restoreOriginal;
            this.isLoser = isLoser;
            this.match = match;
        }
    }

    public MatchManager(ArenaManager arenaManager, EloManager eloManager,
                         int startCountdownSeconds, int timeLimitSeconds) {
        this.arenaManager = arenaManager;
        this.eloManager = eloManager;
        this.startCountdownSeconds = startCountdownSeconds;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public void setLoserSpawn(RankedLocation loc) { this.loserSpawn = loc; }
    public RankedLocation getLoserSpawn() { return loserSpawn; }

    public boolean isInMatch(UUID uuid) { return playerToMatch.containsKey(uuid); }
    public Match getMatch(UUID uuid) { return playerToMatch.get(uuid); }

    private EntityPlayerMP getPlayer(UUID uuid) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return null;
        return server.getPlayerList().getPlayerByUUID(uuid);
    }

    // ------------------------------------------------------------------
    // Starting a match
    // ------------------------------------------------------------------

    public boolean startMatch(ProposedMatch proposal) {
        Arena arena = arenaManager.findFreeArena(proposal.getMode().getTeamSize());
        if (arena == null) return false;

        int teamSize = proposal.getMode().getTeamSize();
        if (!spawnsAreValid(arena, teamSize)) {
            System.err.println("[RebornAddon] Arena '" + arena.id + "' has a spawn outside its own "
                    + "corners - skipping it for this match. Fix with /ranked resize.");
            return false;
        }

        List<UUID> team0 = new ArrayList<>();
        for (QueuedPlayer qp : proposal.getTeam0()) team0.add(qp.getUuid());
        List<UUID> team1 = new ArrayList<>();
        for (QueuedPlayer qp : proposal.getTeam1()) team1.add(qp.getUuid());

        Match match = new Match(proposal.getMode(), arena, team0, team1, timeLimitSeconds);
        arena.inUse = true;
        activeMatches.add(match);
        matchCountdown.put(match, startCountdownSeconds);
        for (UUID u : match.allPlayers()) playerToMatch.put(u, match);

        List<RankedLocation> spawns0 = arena.getTeamSpawns(0);
        List<RankedLocation> spawns1 = arena.getTeamSpawns(1);

        for (int i = 0; i < team0.size(); i++) preparePlayer(team0.get(i), match, spawns0.get(i));
        for (int i = 0; i < team1.size(); i++) preparePlayer(team1.get(i), match, spawns1.get(i));

        broadcastToMatch(match, TextFormatting.GREEN + "Match found! " + match.getMode().getLabel()
                + " - fight starts in " + startCountdownSeconds + " seconds.");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("matchId", match.getMatchId().toString());
        evidence.addProperty("mode", match.getMode().name());
        evidence.addProperty("arena", match.getArena().id);
        evidence.add("teamOne", names(match.getTeam0()));
        evidence.add("teamTwo", names(match.getTeam1()));
        GameplaySystemsService.INSTANCE.recordEvent(match.getMatchId().toString(),
                "ranked.match-started", null, match.getArena().id, evidence.toString());
        return true;
    }

    private boolean spawnsAreValid(Arena arena, int teamSize) {
        for (int team = 0; team <= 1; team++) {
            List<RankedLocation> spawns = arena.getTeamSpawns(team);
            for (int i = 0; i < teamSize; i++) {
                if (!arena.contains(spawns.get(i))) return false;
            }
        }
        return true;
    }

    private void preparePlayer(UUID uuid, Match match, RankedLocation spawn) {
        EntityPlayerMP p = getPlayer(uuid);
        if (p == null) return;

        match.saveOriginalLocation(uuid, new RankedLocation(p.dimension, p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch));
        match.saveOriginalInventory(uuid, p.inventory.mainInventory.toArray(new ItemStack[0]));
        match.saveOriginalArmor(uuid, p.inventory.armorInventory.toArray(new ItemStack[0]));
        match.saveOriginalHealth(uuid, p.getHealth());
        match.saveOriginalFood(uuid, p.getFoodStats().getFoodLevel());
        match.saveOriginalSaturation(uuid, p.getFoodStats().getSaturationLevel());

        p.setHealth(p.getMaxHealth());
        p.getFoodStats().setFoodLevel(20);
        p.extinguish();
        for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(effect.getPotion());
        }

        teleport(p, spawn);
        p.setGameType(GameType.SURVIVAL);
        lastGoodPosition.put(uuid, new double[]{spawn.x, spawn.y, spawn.z});
    }

    private void teleport(EntityPlayerMP p, RankedLocation loc) {
        if (p.dimension != loc.dimensionId) {
            // Cross-dimension teleport - untested edge case. Vanilla 1.12.2's
            // changeDimension() can make the old EntityPlayerMP reference stale, and
            // all your actual arenas are expected to live in the same dimension anyway,
            // so this path likely never actually runs in practice. Worth a specific
            // look if you ever do build a cross-dimension arena.
            p.changeDimension(loc.dimensionId);
        }
        p.connection.setPlayerLocation(loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
    }

    // ------------------------------------------------------------------
    // Per-tick driving (called from the server tick event handler)
    // ------------------------------------------------------------------

    /** Call once per second (i.e. gate every-20th-tick in the actual event handler). */
    public void tickSecond() {
        for (Match match : new ArrayList<>(activeMatches)) {
            if (match.getState() == Match.State.COUNTDOWN) {
                int remaining = matchCountdown.getOrDefault(match, 0) - 1;
                matchCountdown.put(match, remaining);
                if (remaining <= 0) {
                    match.setState(Match.State.ACTIVE);
                    broadcastToMatch(match, TextFormatting.RED + "" + TextFormatting.BOLD + "FIGHT!");
                } else if (remaining <= 3) {
                    broadcastToMatch(match, TextFormatting.YELLOW + "" + remaining + "...");
                }
            } else if (match.getState() == Match.State.ACTIVE) {
                match.decrementTime();
                sendActionBarTimer(match);
                if (match.getTimeRemainingSeconds() <= 0) {
                    endMatchByTimeLimit(match);
                }
            }
        }
        tickSpectators();
    }

    /** Call every tick, for every online player currently in a match, to enforce the
     *  countdown freeze and arena boundaries. Forge has no direct equivalent to Bukkit's
     *  cancelable PlayerMoveEvent, so this uses continuous position-checking instead -
     *  functionally the same anti-escape effect, just reactive rather than preemptive. */
    public void tickPlayerBoundary(EntityPlayerMP p) {
        UUID uuid = p.getUniqueID();
        Match match = playerToMatch.get(uuid);
        if (match == null) {
            lastGoodPosition.remove(uuid);
            return;
        }

        double[] last = lastGoodPosition.get(uuid);
        if (last == null) {
            // Shouldn't normally happen (set on match start), but fail safe rather than NPE.
            last = new double[]{p.posX, p.posY, p.posZ};
            lastGoodPosition.put(uuid, last);
            return;
        }

        if (match.getState() == Match.State.COUNTDOWN) {
            boolean moved = p.posX != last[0] || p.posY != last[1] || p.posZ != last[2];
            if (moved) {
                p.connection.setPlayerLocation(last[0], last[1], last[2], p.rotationYaw, p.rotationPitch);
            }
            return;
        }

        if (match.getState() != Match.State.ACTIVE) return;

        RankedLocation current = new RankedLocation(p.dimension, p.posX, p.posY, p.posZ);
        if (!match.getArena().contains(current)) {
            p.connection.setPlayerLocation(last[0], last[1], last[2], p.rotationYaw, p.rotationPitch);
        } else {
            last[0] = p.posX;
            last[1] = p.posY;
            last[2] = p.posZ;
        }
    }

    /** Cancel damage to a match participant before the fight actually starts. */
    public boolean shouldBlockDamage(UUID uuid) {
        Match match = playerToMatch.get(uuid);
        return match != null && match.getState() == Match.State.COUNTDOWN;
    }

    // ------------------------------------------------------------------
    // Death / respawn / quit
    // ------------------------------------------------------------------

    public void onPlayerDeath(UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match == null || match.getState() != Match.State.ACTIVE) return;

        match.eliminate(uuid);
        int aliveTeam0 = match.countAlive(0);
        int aliveTeam1 = match.countAlive(1);

        if (aliveTeam0 == 0 && aliveTeam1 == 0) {
            finishMatch(match, 0.5);
        } else if (aliveTeam0 == 0) {
            finishMatch(match, 0.0);
        } else if (aliveTeam1 == 0) {
            finishMatch(match, 1.0);
        } else {
            pendingActions.put(uuid, new PendingAction(false, false, match));
        }
    }

    public void onPlayerQuit(UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match != null && match.isAlive(uuid)) {
            onPlayerDeath(uuid);
        }
    }

    /** Returns where this respawn should be redirected to, or null if nothing pending. */
    public RankedLocation getPendingRespawnLocation(UUID uuid) {
        PendingAction action = pendingActions.get(uuid);
        if (action == null) return null;
        return action.restoreOriginal
                ? destinationFor(uuid, action.match, action.isLoser)
                : action.match.getArena().getCenter();
    }

    /** True if this player's pending respawn is "become a spectator" rather than "match
     *  ended, restore them" - kept for callers that want to know without triggering it. */
    public boolean isPendingSpectate(UUID uuid) {
        PendingAction action = pendingActions.get(uuid);
        return action != null && !action.restoreOriginal;
    }

    public void finalizePendingRespawn(UUID uuid) {
        PendingAction action = pendingActions.remove(uuid);
        if (action == null) return;
        EntityPlayerMP p = getPlayer(uuid);
        if (p == null) return;

        if (action.restoreOriginal) {
            restorePlayer(uuid, action.match, action.isLoser);
        } else {
            p.setGameType(GameType.SPECTATOR);
        }
    }

    // ------------------------------------------------------------------
    // Admin / player actions
    // ------------------------------------------------------------------

    public boolean forceEndDraw(UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match == null) return false;
        broadcastToMatch(match, TextFormatting.RED + "This match was force-ended. Result: draw.");
        finishMatch(match, 0.5);
        return true;
    }

    public boolean forfeit(UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match == null) return false;
        int team = match.getTeamOf(uuid);
        if (team == -1) return false;

        String name = nameOf(uuid);
        broadcastToMatch(match, TextFormatting.RED + name + " forfeited the match!");
        finishMatch(match, team == 0 ? 0.0 : 1.0);
        return true;
    }

    public JsonObject spectatorSnapshot(EntityPlayerMP viewer) {
        JsonObject root = new JsonObject();
        root.addProperty("available", true);
        root.addProperty("title", "Ranked and Exam Spectating");
        root.addProperty("message", activeMatches.isEmpty()
                ? "There are no active ranked matches to watch."
                : "Spectators are isolated from combat and restored when they leave or the match ends.");
        JsonArray entries = new JsonArray();
        for (Match match : activeMatches) {
            if (match.getState() == Match.State.ENDED || match.allPlayers().contains(viewer.getUniqueID())) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", match.getMatchId().toString());
            row.addProperty("title", match.getMode().getLabel() + " - " + match.getArena().id);
            row.addProperty("subtitle", teamLabel(match.getTeam0()) + " vs " + teamLabel(match.getTeam1()));
            row.addProperty("detail", match.getState().name() + " | "
                    + Math.max(0, match.getTimeRemainingSeconds()) + " seconds remaining");
            row.addProperty("action", "ranked.spectate");
            row.addProperty("actionLabel", "Spectate Match");
            entries.add(row);
        }
        if (spectators.containsKey(viewer.getUniqueID())) {
            JsonObject leave = new JsonObject();
            leave.addProperty("id", "current");
            leave.addProperty("title", "Leave current match view");
            leave.addProperty("subtitle", "Return to your previous location and game mode");
            leave.addProperty("action", "ranked.leave");
            leave.addProperty("actionLabel", "Leave Spectating");
            entries.add(leave);
        }
        root.add("entries", entries);
        return root;
    }

    public boolean spectate(EntityPlayerMP player, String matchId) {
        if (player == null || isInMatch(player.getUniqueID())) return false;
        Match selected = null;
        for (Match match : activeMatches) {
            if (match.getMatchId().toString().equals(matchId) && match.getState() != Match.State.ENDED) {
                selected = match;
                break;
            }
        }
        if (selected == null) return false;
        leaveSpectator(player);
        RankedLocation original = new RankedLocation(player.dimension, player.posX, player.posY,
                player.posZ, player.rotationYaw, player.rotationPitch);
        SpectatorSession session = new SpectatorSession(selected, original, player.interactionManager.getGameType());
        spectators.put(player.getUniqueID(), session);
        saveSpectatorState(player, session);
        RankedLocation center = selected.getArena().getCenter();
        teleport(player, new RankedLocation(center.dimensionId, center.x, center.y + 5.0D, center.z));
        player.setGameType(GameType.SPECTATOR);
        player.sendMessage(new TextComponentString(TextFormatting.AQUA
                + "Now spectating " + selected.getMode().getLabel() + "."));
        return true;
    }

    public boolean leaveSpectator(EntityPlayerMP player) {
        if (player == null) return false;
        SpectatorSession session = spectators.remove(player.getUniqueID());
        if (session == null) return false;
        player.setGameType(session.gameType);
        teleport(player, session.original);
        clearSpectatorState(player);
        return true;
    }

    public void onSpectatorQuit(EntityPlayerMP player) {
        leaveSpectator(player);
    }

    public void recoverSpectator(EntityPlayerMP player) {
        if (player == null || !persisted(player).hasKey(SPECTATOR_STATE, 10)) return;
        NBTTagCompound state = persisted(player).getCompoundTag(SPECTATOR_STATE);
        GameType type = GameType.getByID(state.getInteger("GameType"));
        player.setGameType(type == GameType.SPECTATOR ? GameType.SURVIVAL : type);
        teleport(player, new RankedLocation(state.getInteger("Dimension"), state.getDouble("X"),
                state.getDouble("Y"), state.getDouble("Z"), state.getFloat("Yaw"),
                state.getFloat("Pitch")));
        clearSpectatorState(player);
    }

    private void tickSpectators() {
        for (Map.Entry<UUID, SpectatorSession> entry : new ArrayList<Map.Entry<UUID, SpectatorSession>>(
                spectators.entrySet())) {
            EntityPlayerMP player = getPlayer(entry.getKey());
            SpectatorSession session = entry.getValue();
            if (player == null) continue;
            if (!activeMatches.contains(session.match) || session.match.getState() == Match.State.ENDED) {
                leaveSpectator(player);
                continue;
            }
            RankedLocation center = session.match.getArena().getCenter();
            double dx = player.posX - center.x;
            double dz = player.posZ - center.z;
            if (player.dimension != center.dimensionId || dx * dx + dz * dz > 16384.0D) {
                teleport(player, new RankedLocation(center.dimensionId, center.x, center.y + 5.0D, center.z));
            }
        }
    }

    // ------------------------------------------------------------------
    // Ending a match
    // ------------------------------------------------------------------

    private void endMatchByTimeLimit(Match match) {
        int aliveTeam0 = match.countAlive(0);
        int aliveTeam1 = match.countAlive(1);

        double result;
        if (aliveTeam0 > aliveTeam1) {
            result = 1.0;
        } else if (aliveTeam1 > aliveTeam0) {
            result = 0.0;
        } else {
            double hp0 = totalHealth(match, 0);
            double hp1 = totalHealth(match, 1);
            if (hp0 > hp1) result = 1.0;
            else if (hp1 > hp0) result = 0.0;
            else result = 0.5;
        }
        finishMatch(match, result);
    }

    private double totalHealth(Match match, int team) {
        double sum = 0;
        for (UUID u : match.getTeam(team)) {
            if (!match.isAlive(u)) continue;
            EntityPlayerMP p = getPlayer(u);
            if (p != null) sum += p.getHealth();
        }
        return sum;
    }

    private void finishMatch(Match match, double team0Result) {
        if (match.getState() == Match.State.ENDED) return;
        match.setState(Match.State.ENDED);
        JsonObject evidence = new JsonObject();
        evidence.addProperty("matchId", match.getMatchId().toString());
        evidence.addProperty("result", team0Result);
        evidence.addProperty("remainingSeconds", match.getTimeRemainingSeconds());
        GameplaySystemsService.INSTANCE.recordEvent(match.getMatchId().toString(),
                "ranked.match-ended", null, match.getArena().id, evidence.toString());

        List<PlayerStats> team0Stats = new ArrayList<>();
        Map<UUID, Integer> beforeElo = new HashMap<>();
        for (UUID u : match.getTeam0()) {
            PlayerStats s = eloManager.getStats(u, nameOf(u));
            beforeElo.put(u, s.elo);
            team0Stats.add(s);
        }
        List<PlayerStats> team1Stats = new ArrayList<>();
        for (UUID u : match.getTeam1()) {
            PlayerStats s = eloManager.getStats(u, nameOf(u));
            beforeElo.put(u, s.elo);
            team1Stats.add(s);
        }

        eloManager.applyMatchResult(team0Stats, team1Stats, team0Result);
        if (RankedSystem.seasonManager != null) {
            RankedSystem.seasonManager.syncPodium(eloManager, true);
        }

        String resultLabel = team0Result == 0.5 ? TextFormatting.YELLOW + "Draw!" :
                (team0Result == 1.0 ? TextFormatting.GREEN + "Team 1 wins!" : TextFormatting.GREEN + "Team 2 wins!");
        broadcastToMatch(match, resultLabel);

        for (PlayerStats s : team0Stats) announceEloChange(s, beforeElo.get(s.uuid));
        for (PlayerStats s : team1Stats) announceEloChange(s, beforeElo.get(s.uuid));

        for (UUID u : match.allPlayers()) {
            int team = match.getTeamOf(u);
            boolean isLoser = (team0Result == 1.0 && team == 1) || (team0Result == 0.0 && team == 0);

            EntityPlayerMP p = getPlayer(u);
            if (p != null && p.isDead) {
                pendingActions.put(u, new PendingAction(true, isLoser, match));
            } else {
                restorePlayer(u, match, isLoser);
            }
            playerToMatch.remove(u);
            lastGoodPosition.remove(u);
        }

        match.getArena().inUse = false;
        activeMatches.remove(match);
        matchCountdown.remove(match);
        for (Map.Entry<UUID, SpectatorSession> entry : new ArrayList<Map.Entry<UUID, SpectatorSession>>(
                spectators.entrySet())) {
            if (entry.getValue().match.equals(match)) {
                EntityPlayerMP spectator = getPlayer(entry.getKey());
                if (spectator != null) leaveSpectator(spectator);
            }
        }
    }

    private void announceEloChange(PlayerStats stats, int before) {
        EntityPlayerMP p = getPlayer(stats.uuid);
        if (p == null) return;
        int delta = stats.elo - before;
        String sign = delta >= 0 ? "+" : "";
        p.sendStatusMessage(new TextComponentString(TextFormatting.GRAY + "Your ELO: " + before + " -> "
                + stats.elo + " (" + sign + delta + ")"), false);
    }

    private RankedLocation destinationFor(UUID uuid, Match match, boolean isLoser) {
        if (isLoser && loserSpawn != null) return loserSpawn;
        return match.getOriginalLocation(uuid);
    }

    private void restorePlayer(UUID uuid, Match match, boolean isLoser) {
        EntityPlayerMP p = getPlayer(uuid);
        if (p == null) return;

        p.setGameType(GameType.SURVIVAL);

        p.inventory.mainInventory.clear();
        ItemStack[] inv = match.getOriginalInventory(uuid);
        if (inv != null) {
            for (int i = 0; i < inv.length && i < p.inventory.mainInventory.size(); i++) {
                p.inventory.mainInventory.set(i, inv[i]);
            }
        }
        ItemStack[] armor = match.getOriginalArmor(uuid);
        if (armor != null) {
            for (int i = 0; i < armor.length && i < p.inventory.armorInventory.size(); i++) {
                p.inventory.armorInventory.set(i, armor[i]);
            }
        }

        RankedLocation destination = destinationFor(uuid, match, isLoser);
        if (destination != null) teleport(p, destination);

        Float origHealth = match.getOriginalHealth(uuid);
        Integer origFood = match.getOriginalFood(uuid);

        p.setHealth(origHealth != null ? Math.min(origHealth, p.getMaxHealth()) : p.getMaxHealth());
        if (origFood != null) p.getFoodStats().setFoodLevel(origFood);
        // Note: exact saturation restoration was dropped here - FoodStats doesn't expose
        // a reliable public setter for it in vanilla 1.12.2 (unlike Bukkit's API, which
        // did). Food level restoration covers the important part; saturation will just
        // settle naturally from there. If this matters to you, it's worth flagging when
        // you review this - happy to add it back via reflection if needed.

        p.extinguish();
        for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(effect.getPotion());
        }
    }

    private void sendActionBarTimer(Match match) {
        int minutes = Math.max(0, match.getTimeRemainingSeconds()) / 60;
        int seconds = Math.max(0, match.getTimeRemainingSeconds()) % 60;
        String text = TextFormatting.YELLOW + String.format("%d:%02d", minutes, seconds);
        ITextComponent component = new TextComponentString(text);
        for (UUID u : match.allPlayers()) {
            EntityPlayerMP p = getPlayer(u);
            if (p != null) p.sendStatusMessage(component, true); // true = action bar
        }
    }

    private void broadcastToMatch(Match match, String message) {
        for (UUID u : match.allPlayers()) {
            EntityPlayerMP p = getPlayer(u);
            if (p != null) p.sendMessage(new TextComponentString(message));
        }
    }

    private String nameOf(UUID uuid) {
        EntityPlayerMP p = getPlayer(uuid);
        if (p != null) return p.getName();
        return uuid.toString().substring(0, 8); // offline fallback, no profile cache lookup wired up here
    }

    private JsonArray names(List<UUID> players) {
        JsonArray names = new JsonArray();
        for (UUID player : players) names.add(nameOf(player));
        return names;
    }

    private String teamLabel(List<UUID> players) {
        StringBuilder value = new StringBuilder();
        for (UUID player : players) {
            if (value.length() > 0) value.append(", ");
            value.append(nameOf(player));
        }
        return value.toString();
    }

    private static void saveSpectatorState(EntityPlayerMP player, SpectatorSession session) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Dimension", session.original.dimensionId);
        state.setDouble("X", session.original.x);
        state.setDouble("Y", session.original.y);
        state.setDouble("Z", session.original.z);
        state.setFloat("Yaw", session.original.yaw);
        state.setFloat("Pitch", session.original.pitch);
        state.setInteger("GameType", session.gameType.getID());
        persisted(player).setTag(SPECTATOR_STATE, state);
    }

    private static void clearSpectatorState(EntityPlayerMP player) {
        persisted(player).removeTag(SPECTATOR_STATE);
    }

    private static NBTTagCompound persisted(EntityPlayerMP player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayerMP.PERSISTED_NBT_TAG, 10)) {
            root.setTag(EntityPlayerMP.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return root.getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG);
    }

    private static final class SpectatorSession {
        private final Match match;
        private final RankedLocation original;
        private final GameType gameType;

        private SpectatorSession(Match match, RankedLocation original, GameType gameType) {
            this.match = match;
            this.original = original;
            this.gameType = gameType;
        }
    }
}
