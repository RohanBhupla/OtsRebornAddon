package net.rebornaddon.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.narutomod.entity.EntityNinjaMob;
import net.narutomod.item.ItemBakuton;

public final class ExplosiveClayCompatibility {
    private ExplosiveClayCompatibility() {
    }

    public static ItemStack normalizeExplosiveClayStack(ItemStack supplied,
                                                        EntityLivingBase caster) {
        if (isExplosiveClayItem(supplied)) {
            return supplied;
        }
        if (caster instanceof EntityNinjaMob.Base) {
            EntityNinjaMob.Base ninja = (EntityNinjaMob.Base) caster;
            ItemStack equipped = findEquipped(ninja);
            if (isExplosiveClayItem(equipped)) {
                return equipped;
            }
            for (int slot = 0; slot < ninja.getInventorySize(); slot++) {
                ItemStack candidate = ninja.getItemFromInventory(slot);
                if (isExplosiveClayItem(candidate)) {
                    return candidate;
                }
            }
        }
        Item fallback = ItemBakuton.block;
        if (fallback instanceof ItemBakuton.RangedItem) {
            return new ItemStack(fallback);
        }
        return supplied == null ? ItemStack.EMPTY : supplied;
    }

    private static ItemStack findEquipped(EntityNinjaMob.Base ninja) {
        ItemStack offhand = ninja.getHeldItemOffhand();
        if (isExplosiveClayItem(offhand)) {
            return offhand;
        }
        return ninja.getHeldItemMainhand();
    }

    private static boolean isExplosiveClayItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ItemBakuton.RangedItem;
    }
}
