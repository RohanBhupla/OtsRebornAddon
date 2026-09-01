package net.rebornaddon.store;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class RyoCurrency {
    private static final Denomination[] DENOMINATIONS = new Denomination[]{
            new Denomination("narutomod:ryo_1_m", 1000000),
            new Denomination("narutomod:ryo_100000", 100000),
            new Denomination("narutomod:ryo_10000", 10000),
            new Denomination("narutomod:ryo_1000", 1000),
            new Denomination("narutomod:ryo_100", 100),
            new Denomination("narutomod:ryo_10", 10)
    };

    private RyoCurrency() {
    }

    public static long balance(InventoryPlayer inventory) {
        if (inventory == null) {
            return 0L;
        }
        long total = 0L;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            int value = value(stack);
            if (value > 0) {
                total += (long) value * stack.getCount();
            }
        }
        return total;
    }

    public static boolean withdraw(EntityPlayerMP player, int amount) {
        if (player == null || amount < 0) {
            return false;
        }
        InventoryPlayer inventory = player.inventory;
        long balance = balance(inventory);
        if (balance < amount) {
            return false;
        }

        List<ItemStack> before = snapshot(inventory);

        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (value(inventory.getStackInSlot(slot)) > 0) {
                inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
            }
        }
        if (!deposit(inventory, balance - amount)) {
            restore(inventory, before);
            inventory.markDirty();
            return false;
        }
        inventory.markDirty();
        return true;
    }

    public static void credit(EntityPlayerMP player, long amount) {
        if (player == null || amount <= 0L) return;
        List<ItemStack> before = snapshot(player.inventory);
        if (!deposit(player.inventory, amount)) restore(player.inventory, before);
        player.inventory.markDirty();
    }

    private static boolean deposit(InventoryPlayer inventory, long amount) {
        long remaining = amount;
        for (Denomination denomination : DENOMINATIONS) {
            Item item = denomination.item();
            if (item == null || remaining < denomination.value) {
                continue;
            }
            long count = remaining / denomination.value;
            remaining %= denomination.value;
            int maxStack = Math.max(1, item.getItemStackLimit());
            while (count > 0) {
                int stackSize = (int) Math.min(count, maxStack);
                ItemStack change = new ItemStack(item, stackSize);
                inventory.addItemStackToInventory(change);
                if (!change.isEmpty()) return false;
                count -= stackSize;
            }
        }
        return remaining == 0L;
    }

    private static List<ItemStack> snapshot(InventoryPlayer inventory) {
        List<ItemStack> values = new ArrayList<ItemStack>(inventory.getSizeInventory());
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            values.add(inventory.getStackInSlot(slot).copy());
        }
        return values;
    }

    private static void restore(InventoryPlayer inventory, List<ItemStack> values) {
        for (int slot = 0; slot < inventory.getSizeInventory() && slot < values.size(); slot++) {
            inventory.setInventorySlotContents(slot, values.get(slot).copy());
        }
    }

    private static int value(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        for (Denomination denomination : DENOMINATIONS) {
            if (item == denomination.item()) {
                return denomination.value;
            }
        }
        return 0;
    }

    private static final class Denomination {
        private final ResourceLocation registryName;
        private final int value;
        private Item item;

        private Denomination(String registryName, int value) {
            this.registryName = new ResourceLocation(registryName);
            this.value = value;
        }

        private Item item() {
            if (item == null) {
                item = ForgeRegistries.ITEMS.getValue(registryName);
            }
            return item;
        }
    }
}
