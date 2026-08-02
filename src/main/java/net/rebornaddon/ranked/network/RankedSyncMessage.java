package net.rebornaddon.ranked.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Pushed from server to client roughly once a second, carrying the receiving player's
 * own stats/queue state plus a leaderboard snapshot. Replaces the old scoreboard-based
 * sync entirely - this is a direct, reliable packet now that both sides live in one mod.
 */
public class RankedSyncMessage implements IMessage {

    private int elo;
    private int wins;
    private int losses;
    private int draws;
    private int winStreak;
    private int peakElo;
    private int matchState; // 0 = idle, 1 = queued, 2 = in an active match
    private int queuedModeNetId; // only meaningful when matchState == 1; -1 otherwise
    private List<String> leaderboardNames = new ArrayList<>();
    private List<Integer> leaderboardElos = new ArrayList<>();

    public RankedSyncMessage() {} // required no-arg constructor

    public RankedSyncMessage(int elo, int wins, int losses, int draws, int winStreak, int peakElo,
                              int matchState, int queuedModeNetId,
                              List<String> leaderboardNames, List<Integer> leaderboardElos) {
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.winStreak = winStreak;
        this.peakElo = peakElo;
        this.matchState = matchState;
        this.queuedModeNetId = queuedModeNetId;
        this.leaderboardNames = leaderboardNames;
        this.leaderboardElos = leaderboardElos;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(elo);
        buf.writeInt(wins);
        buf.writeInt(losses);
        buf.writeInt(draws);
        buf.writeInt(winStreak);
        buf.writeInt(peakElo);
        buf.writeInt(matchState);
        buf.writeInt(queuedModeNetId);
        buf.writeInt(leaderboardNames.size());
        for (int i = 0; i < leaderboardNames.size(); i++) {
            ByteBufUtils.writeUTF8String(buf, leaderboardNames.get(i));
            buf.writeInt(leaderboardElos.get(i));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        elo = buf.readInt();
        wins = buf.readInt();
        losses = buf.readInt();
        draws = buf.readInt();
        winStreak = buf.readInt();
        peakElo = buf.readInt();
        matchState = buf.readInt();
        queuedModeNetId = buf.readInt();
        int count = buf.readInt();
        leaderboardNames = new ArrayList<>();
        leaderboardElos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            leaderboardNames.add(ByteBufUtils.readUTF8String(buf));
            leaderboardElos.add(buf.readInt());
        }
    }

    /** Deliberately NOT SideOnly, and deliberately doesn't reference Minecraft or
     *  RankedClientData directly - delegating through RebornAddonMod.proxy is what
     *  makes this class genuinely safe to register from common code on both sides
     *  (needed so the server can actually encode/send this message type at all). */
    public static class Handler implements IMessageHandler<RankedSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(RankedSyncMessage message, MessageContext ctx) {
            RebornAddonMod.proxy.handleRankedSync(message);
            return null;
        }
    }

    public int getElo() { return elo; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }
    public int getWinStreak() { return winStreak; }
    public int getPeakElo() { return peakElo; }
    public int getMatchState() { return matchState; }
    public int getQueuedModeNetId() { return queuedModeNetId; }
    public List<String> getLeaderboardNames() { return leaderboardNames; }
    public List<Integer> getLeaderboardElos() { return leaderboardElos; }
}
