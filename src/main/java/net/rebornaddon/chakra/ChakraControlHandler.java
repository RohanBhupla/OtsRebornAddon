package net.rebornaddon.chakra;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.rebornaddon.compat.ChakraControlCompatibility;
import net.rebornaddon.village.network.RebornAddonNetwork;

public final class ChakraControlHandler {
    public static final ChakraControlHandler INSTANCE = new ChakraControlHandler();

    private ChakraControlHandler() {
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.player);
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.player);
    }

    @SubscribeEvent
    public void onCollisionBoxes(GetCollisionBoxesEvent event) {
        ChakraControlCompatibility.addWaterSurfaceCollisions(event);
    }

    private static void sync(net.minecraft.entity.player.EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            RebornAddonNetwork.sendChakraControlState(serverPlayer,
                    ChakraControlCompatibility.isEnabled(serverPlayer));
        }
    }
}
