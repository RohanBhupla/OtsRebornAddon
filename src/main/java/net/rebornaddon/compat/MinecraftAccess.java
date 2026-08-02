package net.rebornaddon.compat;

import net.minecraft.entity.Entity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;

import java.lang.reflect.Method;

public final class MinecraftAccess {
    private MinecraftAccess() {
    }

    public static World world(Entity entity) {
        return entity == null ? null : entity.getEntityWorld();
    }

    public static boolean isRemote(Entity entity) {
        World world = world(entity);
        return world == null || world.isRemote;
    }

    public static void sendMessage(Object target, ITextComponent message) {
        invoke(target, new Class<?>[] {ITextComponent.class}, new Object[] {message},
                "func_145747_a", "sendMessage");
    }

    public static Style style(ITextComponent component) {
        Object style = invoke(component, new Class<?>[0], new Object[0],
                "func_150256_b", "getStyle");
        return style instanceof Style ? (Style) style : new Style();
    }

    public static void appendSibling(ITextComponent component, ITextComponent sibling) {
        invoke(component, new Class<?>[] {ITextComponent.class}, new Object[] {sibling},
                "func_150257_a", "appendSibling");
    }

    public static void setColor(Style style, TextFormatting color) {
        invoke(style, new Class<?>[] {TextFormatting.class}, new Object[] {color},
                "func_150238_a", "setColor");
    }

    public static void setUnderlined(Style style, Boolean underlined) {
        invoke(style, new Class<?>[] {Boolean.class}, new Object[] {underlined},
                "func_150228_d", "setUnderlined");
    }

    public static void setClickEvent(Style style, ClickEvent event) {
        invoke(style, new Class<?>[] {ClickEvent.class}, new Object[] {event},
                "func_150241_a", "setClickEvent");
    }

    public static void setHoverEvent(Style style, HoverEvent event) {
        invoke(style, new Class<?>[] {HoverEvent.class}, new Object[] {event},
                "func_150209_a", "setHoverEvent");
    }

    private static Object invoke(Object target, Class<?>[] parameterTypes, Object[] args, String... names) {
        if (target == null) {
            return null;
        }

        Class<?> type = target.getClass();
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
