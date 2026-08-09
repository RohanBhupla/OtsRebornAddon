package net.rebornaddon.music.client;

import net.minecraft.client.audio.ISound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.config.RebornAddonConfig;

@SideOnly(Side.CLIENT)
public final class AkatsukiBellLimiter {
    public static final AkatsukiBellLimiter INSTANCE = new AkatsukiBellLimiter();

    private static final ResourceLocation BELL = new ResourceLocation("narutomod", "dingding");
    private long nextAllowedAt;

    private AkatsukiBellLimiter() {
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getSound();
        if (sound == null || !BELL.equals(sound.getSoundLocation())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAllowedAt) {
            event.setResultSound(null);
            return;
        }

        nextAllowedAt = now + RebornAddonConfig.akatsukiBellMinimumSeconds * 1000L;
    }
}
