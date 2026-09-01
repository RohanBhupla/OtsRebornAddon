package net.rebornaddon.advancement.client;

import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.advancements.GuiScreenAdvancements;
import net.minecraft.client.gui.toasts.AdvancementToast;
import net.minecraft.client.gui.toasts.TutorialToast;
import net.minecraft.client.multiplayer.ClientAdvancementManager;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Keeps the vanilla advancement and tutorial surfaces limited to RebornAddon content. */
@SideOnly(Side.CLIENT)
public final class AdvancementClientGuard {
    public static final AdvancementClientGuard INSTANCE = new AdvancementClientGuard();

    private AdvancementClientGuard() {
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiScreenAdvancements)
                || gui instanceof GuiRebornAdvancements) {
            return;
        }

        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();
        if (connection != null) {
            ClientAdvancementManager manager = connection.getAdvancementManager();
            if (manager != null) {
                event.setGui(new GuiRebornAdvancements(manager));
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getTutorial() != null) {
            minecraft.getTutorial().setStep(TutorialSteps.NONE);
        }
    }

    public static boolean isVisible(Advancement advancement) {
        return advancement != null
                && advancement.getId() != null
                && "rebornaddon".equals(advancement.getId().getResourceDomain());
    }

    /** Called by the core transformer before a toast is queued. */
    public static boolean shouldDisplayToast(Object toast) {
        if (toast == null || toast instanceof TutorialToast) {
            return false;
        }
        if (!(toast instanceof AdvancementToast)) {
            return true;
        }

        try {
            Class<?> type = AdvancementToast.class;
            while (type != null) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (!Advancement.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    return isVisible((Advancement) field.get(toast));
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        // A toast we cannot classify must not leak an external advancement.
        return false;
    }
}
