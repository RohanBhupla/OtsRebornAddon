package net.rebornaddon.compat.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;

import java.util.Iterator;

public class ShinobiAddonCreativeVisibilityHandler {

    public static final ShinobiAddonCreativeVisibilityHandler INSTANCE = new ShinobiAddonCreativeVisibilityHandler();

    private ShinobiAddonCreativeVisibilityHandler() {
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        prune(event.getGui());
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        prune(event.getGui());
    }

    private static void prune(GuiScreen gui) {
        if (!(gui instanceof GuiContainerCreative)) {
            return;
        }

        Container container = ((GuiContainerCreative) gui).inventorySlots;
        if (!(container instanceof GuiContainerCreative.ContainerCreative)) {
            return;
        }

        GuiContainerCreative.ContainerCreative creative = (GuiContainerCreative.ContainerCreative) container;
        NonNullList<ItemStack> items = creative.itemList;
        boolean removed = false;

        for (Iterator<ItemStack> iterator = items.iterator(); iterator.hasNext();) {
            if (ShinobiAddonRestrictionHandler.shouldHide(iterator.next())) {
                iterator.remove();
                removed = true;
            }
        }

        if (removed) {
            creative.scrollTo(0.0F);
        }
    }
}
