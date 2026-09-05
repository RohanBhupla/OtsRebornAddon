package net.rebornaddon.mount.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.mount.ArmorWolfMountJumpHandler;
import net.rebornaddon.mount.network.MountJumpMessage;
import net.rebornaddon.village.network.RebornAddonNetwork;

/** Sends one request when the rider presses jump on an Armor Wolf mount. */
@SideOnly(Side.CLIENT)
public final class ArmorWolfMountJumpInput {
    public static final ArmorWolfMountJumpInput INSTANCE = new ArmorWolfMountJumpInput();
    private static final String WRAPPER_ID = "armourwolfmod:skin_wolf";
    private static final int JUMP_BUFFER_TICKS = 6;
    private boolean jumpWasDown;
    private int jumpBufferTicks;

    private ArmorWolfMountJumpInput() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity mount = minecraft.player == null ? null : minecraft.player.getRidingEntity();
        boolean ridingMount = minecraft.currentScreen == null && isArmorWolfMount(mount);
        boolean jumpDown = ridingMount
                && minecraft.currentScreen == null
                && minecraft.gameSettings.keyBindJump.isKeyDown();
        if (jumpDown && !jumpWasDown) jumpBufferTicks = JUMP_BUFFER_TICKS;
        if (!ridingMount) jumpBufferTicks = 0;
        if (jumpBufferTicks > 0) {
            if (ArmorWolfMountJumpHandler.tryClientJump(minecraft.player)) {
                RebornAddonNetwork.CHANNEL.sendToServer(new MountJumpMessage());
                jumpBufferTicks = 0;
            } else {
                jumpBufferTicks--;
            }
        }
        jumpWasDown = jumpDown;
    }

    private static boolean isArmorWolfMount(Entity entity) {
        ResourceLocation id = entity == null ? null : EntityList.getKey(entity);
        return id != null && WRAPPER_ID.equals(id.toString());
    }
}
