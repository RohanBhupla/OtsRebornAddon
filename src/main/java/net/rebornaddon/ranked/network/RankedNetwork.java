package net.rebornaddon.ranked.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class RankedNetwork {

    private RankedNetwork() {}

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("rebornaddon_ranked");

    /**
     * Safe to call unconditionally from common preInit, on both sides. Both message
     * classes need registering on both sides regardless of which side actually sends
     * or handles them - the server needs to know how to ENCODE RankedSyncMessage even
     * though it never runs that handler's real logic, or it can't send the message at
     * all. RankedSyncMessage.Handler is deliberately written to delegate through the
     * proxy rather than touch any client-only class directly, which is what makes
     * this safe - see RankedSyncMessage.java for why.
     */
    public static void init() {
        CHANNEL.registerMessage(QueueActionMessage.Handler.class, QueueActionMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(RankedSyncMessage.Handler.class, RankedSyncMessage.class, 1, Side.CLIENT);
    }
}
