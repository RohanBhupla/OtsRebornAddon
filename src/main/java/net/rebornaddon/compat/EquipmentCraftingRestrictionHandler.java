package net.rebornaddon.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.store.StoreCatalog;

public final class EquipmentCraftingRestrictionHandler {
    public static final EquipmentCraftingRestrictionHandler INSTANCE = new EquipmentCraftingRestrictionHandler();

    private EquipmentCraftingRestrictionHandler() {
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        if (event.phase != TickEvent.Phase.END || player == null || player.world.isRemote) {
            return;
        }
        Container container = player.openContainer;
        if (container == null || container.inventorySlots.isEmpty()) {
            return;
        }
        Slot result = container.inventorySlots.get(0);
        if (!(result instanceof SlotCrafting)) {
            return;
        }
        ItemStack output = result.getStack();
        if (StoreCatalog.isRestrictedCraftingOutput(output)) {
            result.putStack(ItemStack.EMPTY);
            container.detectAndSendChanges();
        }
    }
}
