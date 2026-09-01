package net.rebornaddon.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownTrackerCompatibility {
    private static final Map<Class<?>, Accessor> ACCESSORS =
            new ConcurrentHashMap<Class<?>, Accessor>();

    private CooldownTrackerCompatibility() {
    }

    public static boolean clear(EntityPlayer player, Item item) {
        if (player == null || item == null) return false;
        return clearTracker(player.getCooldownTracker(), item);
    }

    static boolean clearTracker(Object tracker, Item item) {
        if (tracker == null || item == null) return false;
        Accessor accessor = ACCESSORS.get(tracker.getClass());
        if (accessor == null) {
            accessor = resolve(tracker.getClass());
            ACCESSORS.put(tracker.getClass(), accessor);
        }
        return accessor.clear(tracker, item);
    }

    public static boolean hasCooldown(EntityPlayer player, Item item) {
        if (player == null || item == null) return false;
        return hasCooldown(player.getCooldownTracker(), item);
    }

    static boolean hasCooldown(Object tracker, Item item) {
        if (tracker == null || item == null) return false;
        Accessor accessor = ACCESSORS.get(tracker.getClass());
        if (accessor == null) {
            accessor = resolve(tracker.getClass());
            ACCESSORS.put(tracker.getClass(), accessor);
        }
        return accessor.has(tracker, item);
    }

    private static Accessor resolve(Class<?> type) {
        Method remove = null;
        Method set = null;
        Method has = null;
        Field cooldowns = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!Modifier.isPublic(method.getModifiers())) continue;
                if (remove == null && method.getReturnType() == Void.TYPE
                        && parameters.length == 1 && parameters[0] == Item.class) {
                    remove = accessible(method);
                } else if (set == null && method.getReturnType() == Void.TYPE
                        && parameters.length == 2 && parameters[0] == Item.class
                        && (parameters[1] == Integer.TYPE || parameters[1] == Integer.class)) {
                    set = accessible(method);
                } else if (has == null && (method.getReturnType() == Boolean.TYPE
                        || method.getReturnType() == Boolean.class)
                        && parameters.length == 1 && parameters[0] == Item.class) {
                    has = accessible(method);
                }
            }
            if (cooldowns == null) {
                for (Field field : current.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        cooldowns = accessible(field);
                        break;
                    }
                }
            }
        }
        return new Accessor(remove, set, has, cooldowns);
    }

    private static Method accessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (Throwable ignored) {
        }
        return method;
    }

    private static Field accessible(Field field) {
        try {
            field.setAccessible(true);
        } catch (Throwable ignored) {
        }
        return field;
    }

    private static final class Accessor {
        private final Method remove;
        private final Method set;
        private final Method has;
        private final Field cooldowns;

        private Accessor(Method remove, Method set, Method has, Field cooldowns) {
            this.remove = remove;
            this.set = set;
            this.has = has;
            this.cooldowns = cooldowns;
        }

        private boolean has(Object tracker, Item item) {
            if (has != null) {
                try {
                    Object value = has.invoke(tracker, item);
                    if (value instanceof Boolean) return ((Boolean) value).booleanValue();
                } catch (Throwable ignored) {
                }
            }
            if (cooldowns != null) {
                try {
                    Object value = cooldowns.get(tracker);
                    if (value instanceof Map) return ((Map<?, ?>) value).containsKey(item);
                } catch (Throwable ignored) {
                }
            }
            return false;
        }

        private boolean clear(Object tracker, Item item) {
            if (invoke(remove, tracker, item)) return true;
            if (invoke(set, tracker, item, Integer.valueOf(0))) return true;
            if (cooldowns == null) return false;
            try {
                Object value = cooldowns.get(tracker);
                if (value instanceof Map) {
                    ((Map<?, ?>) value).remove(item);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        private static boolean invoke(Method method, Object target, Object... arguments) {
            if (method == null) return false;
            try {
                method.invoke(target, arguments);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
