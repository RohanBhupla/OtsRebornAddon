package net.rebornaddon.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

public final class JutsuStackCompatibility {
    private static final String JUTSU_INDEX_KEY = "JutsuIndexKey";

    private JutsuStackCompatibility() {
    }

    public static int currentIndex(List<?> jutsus, ItemStack stack) {
        if (jutsus == null || jutsus.isEmpty()) {
            return 0;
        }

        NBTTagCompound tag = stack == null || stack.isEmpty() ? null : stack.getTagCompound();
        int selected = tag == null ? 0 : tag.getInteger(JUTSU_INDEX_KEY);
        if (selected >= 0 && selected < jutsus.size()) {
            return selected;
        }
        if (tag != null) {
            tag.setInteger(JUTSU_INDEX_KEY, 0);
        }
        return 0;
    }
}
