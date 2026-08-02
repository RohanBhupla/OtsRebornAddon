package net.rebornaddon.compat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventBus;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ShinobiAddonPerformancePatch {
    private static final Set<String> EVENT_HANDLERS = new HashSet<String>(Arrays.asList(
            "com.leolifeless.shinobiaddon.handler.steel.PlayerSteelArmorHandler",
            "com.leolifeless.shinobiaddon.handler.steel.NpcSteelArmorHandler"
    ));

    private ShinobiAddonPerformancePatch() {
    }

    public static void apply() {
        removeHandlers(MinecraftForge.EVENT_BUS);
    }

    private static void removeHandlers(EventBus bus) {
        try {
            Field field = EventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            Object value = field.get(bus);
            if (!(value instanceof Map)) {
                return;
            }

            ArrayList<Object> targets = new ArrayList<Object>();
            for (Object key : ((Map<?, ?>) value).keySet()) {
                if (key != null && EVENT_HANDLERS.contains(className(key))) {
                    targets.add(key);
                }
            }

            for (Object target : targets) {
                bus.unregister(target);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String className(Object value) {
        return value instanceof Class ? ((Class<?>) value).getName() : value.getClass().getName();
    }
}
