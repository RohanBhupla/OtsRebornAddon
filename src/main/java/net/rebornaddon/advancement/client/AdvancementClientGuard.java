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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps the vanilla advancement and tutorial surfaces limited to RebornAddon content. */
@SideOnly(Side.CLIENT)
public final class AdvancementClientGuard {
    public static final AdvancementClientGuard INSTANCE = new AdvancementClientGuard();
    private static final List<ResourceLocation> ROOT_ORDER = Arrays.asList(
            new ResourceLocation("rebornaddon", "ninja"),
            new ResourceLocation("rebornaddon", "natures"),
            new ResourceLocation("rebornaddon", "kekkei_genkai"),
            new ResourceLocation("rebornaddon", "clan"),
            new ResourceLocation("rebornaddon", "modes"),
            new ResourceLocation("rebornaddon", "store"));

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
                reorderRoots(manager);
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

    @SuppressWarnings("unchecked")
    private static void reorderRoots(ClientAdvancementManager manager) {
        Iterable<Advancement> iterable = manager.getAdvancementList().getRoots();
        if (!(iterable instanceof Set)) {
            return;
        }
        Set<Advancement> roots = (Set<Advancement>) iterable;
        Map<ResourceLocation, Advancement> byId =
                new LinkedHashMap<ResourceLocation, Advancement>();
        List<ResourceLocation> currentIds = new ArrayList<ResourceLocation>();
        for (Advancement root : roots) {
            if (root != null && root.getId() != null) {
                byId.put(root.getId(), root);
                currentIds.add(root.getId());
            }
        }
        roots.clear();
        for (ResourceLocation id : orderedRootIds(currentIds)) {
            Advancement root = byId.get(id);
            if (root != null) {
                roots.add(root);
            }
        }
    }

    static List<ResourceLocation> orderedRootIds(Iterable<ResourceLocation> currentIds) {
        List<ResourceLocation> current = new ArrayList<ResourceLocation>();
        for (ResourceLocation id : currentIds) {
            if (id != null && !current.contains(id)) {
                current.add(id);
            }
        }
        List<ResourceLocation> ordered = new ArrayList<ResourceLocation>();
        for (ResourceLocation id : ROOT_ORDER) {
            if (current.contains(id)) {
                ordered.add(id);
            }
        }
        for (ResourceLocation id : current) {
            if (!ordered.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
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
