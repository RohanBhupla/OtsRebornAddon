package net.rebornaddon.music.client;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@SideOnly(Side.CLIENT)
final class MusicChannelControl {
    private static Field soundManagerField;
    private static Field channelMapField;
    private static Field soundSystemField;
    private static Field stopTimeMapField;
    private static Field playTimeField;
    private static Method pauseMethod;
    private static Method playMethod;

    private MusicChannelControl() {
    }

    static boolean pause(SoundHandler handler, ISound sound) {
        return invoke(handler, sound, "pause");
    }

    static boolean resume(SoundHandler handler, ISound sound) {
        return invoke(handler, sound, "play");
    }

    static void keepAlive(SoundHandler handler, ISound sound) {
        try {
            SoundManager manager = manager(handler);
            String channel = channel(manager, sound);
            if (manager == null || channel == null) {
                return;
            }
            int playTime = playTime(manager);
            Map<Object, Object> stopTimes = stopTimes(manager, channel);
            if (stopTimes != null) {
                stopTimes.put(channel, Integer.valueOf(playTime + 40));
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean invoke(SoundHandler handler, ISound sound, String methodName) {
        if (handler == null || sound == null) {
            return false;
        }
        try {
            SoundManager manager = manager(handler);
            String channel = channel(manager, sound);
            Object soundSystem = soundSystem(manager);
            if (channel == null || soundSystem == null) {
                return false;
            }
            Method method = method(soundSystem, methodName);
            method.invoke(soundSystem, channel);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Method method(Object soundSystem, String name) throws NoSuchMethodException {
        if ("pause".equals(name)) {
            if (pauseMethod == null) {
                pauseMethod = soundSystem.getClass().getMethod(name, String.class);
            }
            return pauseMethod;
        }
        if (playMethod == null) {
            playMethod = soundSystem.getClass().getMethod(name, String.class);
        }
        return playMethod;
    }

    private static SoundManager manager(SoundHandler handler) throws ReflectiveOperationException {
        if (soundManagerField == null) {
            for (Field field : SoundHandler.class.getDeclaredFields()) {
                if (SoundManager.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    soundManagerField = field;
                    break;
                }
            }
        }
        return soundManagerField == null ? null : (SoundManager) soundManagerField.get(handler);
    }

    private static String channel(SoundManager manager, ISound sound) throws IllegalAccessException {
        if (manager == null) {
            return null;
        }
        if (channelMapField != null) {
            Object value = ((Map<?, ?>) channelMapField.get(manager)).get(sound);
            if (value instanceof String) {
                return (String) value;
            }
        }
        for (Field field : SoundManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object map = field.get(manager);
            if (!(map instanceof Map)) {
                continue;
            }
            Object value = ((Map<?, ?>) map).get(sound);
            if (value instanceof String) {
                channelMapField = field;
                return (String) value;
            }
        }
        return null;
    }

    private static Object soundSystem(SoundManager manager) throws IllegalAccessException {
        if (manager == null) {
            return null;
        }
        if (soundSystemField != null) {
            return soundSystemField.get(manager);
        }
        for (Field field : SoundManager.class.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(manager);
            if (value == null) {
                continue;
            }
            try {
                value.getClass().getMethod("pause", String.class);
                value.getClass().getMethod("play", String.class);
                soundSystemField = field;
                return value;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static int playTime(SoundManager manager) throws IllegalAccessException {
        if (playTimeField == null) {
            for (Field field : SoundManager.class.getDeclaredFields()) {
                if (field.getType() == Integer.TYPE) {
                    field.setAccessible(true);
                    playTimeField = field;
                    break;
                }
            }
        }
        return playTimeField == null ? 0 : playTimeField.getInt(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> stopTimes(SoundManager manager, String channel)
            throws IllegalAccessException {
        if (stopTimeMapField != null) {
            return (Map<Object, Object>) stopTimeMapField.get(manager);
        }
        for (Field field : SoundManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(manager);
            if (!(value instanceof Map)) {
                continue;
            }
            Object stopTime = ((Map<?, ?>) value).get(channel);
            if (stopTime instanceof Integer) {
                stopTimeMapField = field;
                return (Map<Object, Object>) value;
            }
        }
        return null;
    }
}
