package net.rebornaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.keybind.KeyBindings;

@SideOnly(Side.CLIENT)
public final class ClientZoomHandler {
    public static final ClientZoomHandler INSTANCE = new ClientZoomHandler();
    private static final float ZOOM_MULTIPLIER = 0.25F;

    private boolean zooming;
    private boolean previousSmoothCamera;

    private ClientZoomHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!active(minecraft)) return;
        begin(minecraft);
        event.setFOV(zoomedFov(event.getFOV()));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (active(minecraft)) begin(minecraft);
        else end(minecraft);
    }

    private static boolean active(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null && minecraft.currentScreen == null
                && KeyBindings.zoom != null && KeyBindings.zoom.isKeyDown();
    }

    private void begin(Minecraft minecraft) {
        if (zooming) return;
        previousSmoothCamera = minecraft.gameSettings.smoothCamera;
        minecraft.gameSettings.smoothCamera = true;
        zooming = true;
    }

    private void end(Minecraft minecraft) {
        if (!zooming || minecraft == null) return;
        minecraft.gameSettings.smoothCamera = previousSmoothCamera;
        zooming = false;
    }

    static float zoomedFov(float fov) {
        return fov * ZOOM_MULTIPLIER;
    }
}
