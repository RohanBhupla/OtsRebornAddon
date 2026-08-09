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
import net.rebornaddon.client.ClientLocalization;

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
    private boolean paused;
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
        if (paused) {
            if (currentTrack != null) {
                MusicChannelControl.keepAlive(sounds, currentTrack);
                MusicChannelControl.pause(sounds, currentTrack);
            }
            return;
        }
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

    public void togglePaused() {
        Minecraft minecraft = Minecraft.getMinecraft();
        SoundHandler sounds = minecraft.getSoundHandler();
        paused = !paused;

        if (paused) {
            if (currentTrack != null && !MusicChannelControl.pause(sounds, currentTrack)) {
                stopCurrent(sounds);
            }
            showStatus(minecraft, ClientLocalization.format(
                    "message.rebornaddon.music_paused", "Music paused"));
            return;
        }

        if (currentTrack != null && !MusicChannelControl.resume(sounds, currentTrack)) {
            stopCurrent(sounds);
        }
        if (currentTrack == null && canPlay(minecraft)) {
            if (trackIndex >= 0) {
                playCurrent(sounds);
            } else {
                playNext(sounds);
            }
        }
        showStatus(minecraft, ClientLocalization.format(
                "message.rebornaddon.music_resumed", "Music resumed"));
    }

    public void skipTrack() {
        Minecraft minecraft = Minecraft.getMinecraft();
        stopCurrent(minecraft.getSoundHandler());
        if (RebornMusic.TRACKS.length == 0) {
            return;
        }
        trackIndex = (trackIndex + 1) % RebornMusic.TRACKS.length;
        if (!paused && canPlay(minecraft)) {
            playCurrent(minecraft.getSoundHandler());
        }
        showStatus(minecraft, ClientLocalization.format(
                "message.rebornaddon.music_track", "Now playing: %s",
                RebornMusic.TRACKS[trackIndex].title));
    }

    public boolean isPaused() {
        return paused;
    }

    public String getCurrentTrackTitle() {
        if (RebornMusic.TRACKS.length == 0) {
            return null;
        }
        int selectedTrack = trackIndex < 0 ? 0 : trackIndex;
        return RebornMusic.TRACKS[selectedTrack].title;
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
        playCurrent(sounds);
    }

    private void playCurrent(SoundHandler sounds) {
        if (trackIndex < 0 || trackIndex >= RebornMusic.TRACKS.length) {
            return;
        }
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

    private boolean canPlay(Minecraft minecraft) {
        return minecraft.gameSettings.getSoundLevel(SoundCategory.MASTER) > 0.0F
                && minecraft.gameSettings.getSoundLevel(SoundCategory.MUSIC) > 0.0F
                && !jukeboxNearby;
    }

    private void showStatus(Minecraft minecraft, String message) {
        if (minecraft.ingameGUI != null && minecraft.world != null) {
            minecraft.ingameGUI.setOverlayMessage(message, false);
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
