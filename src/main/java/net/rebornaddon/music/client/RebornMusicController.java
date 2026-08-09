package net.rebornaddon.music.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.music.RebornMusic;
import net.rebornaddon.config.RebornAddonConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class RebornMusicController {
    public static final RebornMusicController INSTANCE = new RebornMusicController();

    private Field vanillaMusicField;
    private Field vanillaDelayField;
    private ISound currentTrack;
    private int trackIndex = -1;
    private int trackTicks;
    private int jukeboxCheckTicks;
    private boolean jukeboxNearby;
    private final List<ISound> activeRecords = new ArrayList<ISound>();

    private RebornMusicController() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        suppressVanillaTicker(minecraft);
        updateJukeboxState(minecraft);
        if (minecraft.gameSettings.getSoundLevel(SoundCategory.MASTER) <= 0.0F
                || minecraft.gameSettings.getSoundLevel(SoundCategory.MUSIC) <= 0.0F
                || jukeboxNearby) {
            stopCurrent(minecraft.getSoundHandler());
            return;
        }

        SoundHandler sounds = minecraft.getSoundHandler();
        if (currentTrack == null) {
            playNext(sounds);
            return;
        }

        trackTicks++;
        if (trackTicks > 20 && !sounds.isSoundPlaying(currentTrack)) {
            currentTrack = null;
            playNext(sounds);
        }
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getSound();
        if (sound != null && sound.getCategory() == SoundCategory.RECORDS) {
            activeRecords.add(sound);
            jukeboxNearby = true;
        }
    }

    private void updateJukeboxState(Minecraft minecraft) {
        if (minecraft.world == null || minecraft.player == null) {
            jukeboxNearby = false;
            jukeboxCheckTicks = 0;
            activeRecords.clear();
            return;
        }
        if (jukeboxCheckTicks++ % 10 != 0) {
            return;
        }

        double radius = RebornAddonConfig.jukeboxMusicRadius;
        double maxDistance = radius * radius;
        jukeboxNearby = false;
        for (Iterator<ISound> iterator = activeRecords.iterator(); iterator.hasNext();) {
            ISound sound = iterator.next();
            if (!minecraft.getSoundHandler().isSoundPlaying(sound)) {
                iterator.remove();
                continue;
            }
            double dx = sound.getXPosF() - minecraft.player.posX;
            double dy = sound.getYPosF() - minecraft.player.posY;
            double dz = sound.getZPosF() - minecraft.player.posZ;
            if (dx * dx + dy * dy + dz * dz <= maxDistance) {
                jukeboxNearby = true;
            }
        }
    }

    private void playNext(SoundHandler sounds) {
        if (RebornMusic.TRACKS.length == 0) {
            return;
        }
        trackIndex = (trackIndex + 1) % RebornMusic.TRACKS.length;
        currentTrack = PositionedSoundRecord.getMusicRecord(RebornMusic.TRACKS[trackIndex].sound);
        trackTicks = 0;
        sounds.playSound(currentTrack);
    }

    private void stopCurrent(SoundHandler sounds) {
        if (currentTrack != null) {
            sounds.stopSound(currentTrack);
            currentTrack = null;
            trackTicks = 0;
        }
    }

    private void suppressVanillaTicker(Minecraft minecraft) {
        MusicTicker ticker = minecraft.getMusicTicker();
        if (ticker == null) {
            return;
        }
        try {
            resolveTickerFields(ticker.getClass());
            if (vanillaDelayField != null) {
                vanillaDelayField.setInt(ticker, Integer.MAX_VALUE);
            }
            if (vanillaMusicField != null) {
                Object sound = vanillaMusicField.get(ticker);
                if (sound instanceof ISound) {
                    minecraft.getSoundHandler().stopSound((ISound) sound);
                    vanillaMusicField.set(ticker, null);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void resolveTickerFields(Class<?> tickerClass) {
        if (vanillaMusicField != null && vanillaDelayField != null) {
            return;
        }
        for (Field field : tickerClass.getDeclaredFields()) {
            if (field.getType() == Integer.TYPE) {
                field.setAccessible(true);
                vanillaDelayField = field;
            } else if (ISound.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                vanillaMusicField = field;
            }
        }
    }
}
