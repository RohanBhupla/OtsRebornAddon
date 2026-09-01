package net.rebornaddon.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.ThreadLocalRandom;

public final class KibaBladeCompatibilityHandler {
    public static final KibaBladeCompatibilityHandler INSTANCE = new KibaBladeCompatibilityHandler();
    private static final ResourceLocation KIBA_BLADES = new ResourceLocation("narutomod", "kiba_blades");

    private KibaBladeCompatibilityHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        promoteStandaloneBlade(event.player);
    }

    static void promoteStandaloneBlade(EntityPlayer player) {
        ItemStack candidate = ItemStack.EMPTY;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!isKibaBlade(stack)) continue;
            if (isPrimary(stack)) return;
            if (candidate.isEmpty()) candidate = stack;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (!isKibaBlade(stack)) continue;
            if (isPrimary(stack)) return;
            if (candidate.isEmpty()) candidate = stack;
        }
        if (!candidate.isEmpty()) markPrimary(candidate);
    }

    static boolean isPrimary(ItemStack stack) {
        if (!isKibaBlade(stack) || !stack.hasTagCompound()) return false;
        return hasPrimaryIdentity(stack.getTagCompound());
    }

    static boolean hasPrimaryIdentity(NBTTagCompound root) {
        if (root == null || !root.hasKey("id", 10) || !root.hasKey("id1", 3)) return false;
        return root.getCompoundTag("id").hashCode() == root.getInteger("id1");
    }

    static void markPrimary(ItemStack stack) {
        if (!isKibaBlade(stack)) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        writePrimaryIdentity(stack.getTagCompound());
    }

    static void writePrimaryIdentity(NBTTagCompound root) {
        if (root == null) return;
        NBTTagCompound identity = new NBTTagCompound();
        identity.setLong("id", ThreadLocalRandom.current().nextLong());
        root.setTag("id", identity);
        root.setInteger("id1", identity.hashCode());
    }

    private static boolean isKibaBlade(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem().getRegistryName() != null
                && KIBA_BLADES.equals(stack.getItem().getRegistryName());
    }
}
