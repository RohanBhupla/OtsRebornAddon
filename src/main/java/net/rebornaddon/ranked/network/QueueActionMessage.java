package net.rebornaddon.ranked.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.elo.PlayerStats;
import net.rebornaddon.ranked.match.MatchMode;

/**
 * Sent by the hub GUI when a player clicks Queue 1v1/2v2/3v3, Leave Queue, or Forfeit.
 * This is what replaced faking a "/ranked 1v1" chat command from the old two-mod setup -
 * a real packet now that client and server logic live in the same mod.
 */
public class QueueActionMessage implements IMessage {

    public static final int ACTION_QUEUE = 0;
    public static final int ACTION_LEAVE = 1;
    public static final int ACTION_FORFEIT = 2;

    private int action;
    private int modeNetId; // only meaningful when action == ACTION_QUEUE

    public QueueActionMessage() {} // required no-arg constructor for IMessage

    public QueueActionMessage(int action, int modeNetId) {
        this.action = action;
        this.modeNetId = modeNetId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(modeNetId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        modeNetId = buf.readInt();
    }

    public static class Handler implements IMessageHandler<QueueActionMessage, IMessage> {
        @Override
        public IMessage onMessage(QueueActionMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // Network handlers run off the main thread - anything touching game state
            // needs to be scheduled back onto it.
            player.getServerWorld().addScheduledTask(() -> handle(message, player));
            return null;
        }

        private void handle(QueueActionMessage message, EntityPlayerMP player) {
            if (!RankedSystem.isReady()) return;

            switch (message.action) {
                case ACTION_QUEUE:
                    if (RankedSystem.matchManager.isInMatch(player.getUniqueID())) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                net.minecraft.util.text.TextFormatting.RED + "You're already in a match!"));
                        return;
                    }
                    MatchMode mode = MatchMode.fromNetId(message.modeNetId);
                    PlayerStats stats = RankedSystem.eloManager.getStats(player.getUniqueID(), player.getName());
                    RankedSystem.queueManager.join(mode, player.getUniqueID(), player.getName(), stats.elo);
                    break;
                case ACTION_LEAVE:
                    RankedSystem.queueManager.leaveAll(player.getUniqueID());
                    break;
                case ACTION_FORFEIT:
                    RankedSystem.matchManager.forfeit(player.getUniqueID());
                    break;
                default:
                    break;
            }
        }
    }
}
