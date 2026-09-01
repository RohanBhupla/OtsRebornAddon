package net.rebornaddon.jutsu;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ExternalJutsuSettings {
    private static final Map<Object, JutsuDefinition> DEFINITIONS = Collections.synchronizedMap(
            new IdentityHashMap<Object, JutsuDefinition>());

    private ExternalJutsuSettings() {
    }

    static void register(Object settings, JutsuDefinition definition) {
        if (settings != null && definition != null) {
            DEFINITIONS.put(settings, definition);
        }
    }

    static void clear() {
        DEFINITIONS.clear();
    }

    public static int intValue(int original, Object settings, String property) {
        JutsuDefinition definition = DEFINITIONS.get(settings);
        if (definition == null || JutsuSettingsCache.INSTANCE.override(definition, property) == null) {
            return original;
        }
        if ("cooldown".equals(property) || "max-duration".equals(property)) {
            return (int) Math.min(Integer.MAX_VALUE,
                    JutsuSettingsCache.INSTANCE.ticks(definition, property, original));
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(
                JutsuSettingsCache.INSTANCE.decimal(definition, property, original)));
    }

    public static double chakraCost(double original, Object settings, EntityLivingBase user) {
        JutsuDefinition definition = DEFINITIONS.get(settings);
        if (definition == null) {
            return original;
        }
        if (!JutsuSettingsCache.INSTANCE.enabled(definition)) {
            if (user instanceof EntityPlayer && !user.world.isRemote) {
                ((EntityPlayer) user).sendStatusMessage(new TextComponentString(TextFormatting.RED
                        + definition.displayName() + " is disabled on this server."), true);
            }
            return Double.MAX_VALUE;
        }
        return JutsuSettingsCache.INSTANCE.decimal(definition, "chakra", original);
    }
}
