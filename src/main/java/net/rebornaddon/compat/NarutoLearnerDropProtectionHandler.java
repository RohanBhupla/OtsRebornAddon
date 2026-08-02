package net.rebornaddon.compat;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class NarutoLearnerDropProtectionHandler {
    public static final NarutoLearnerDropProtectionHandler INSTANCE = new NarutoLearnerDropProtectionHandler();

    private static final Set<String> PROTECTED_ITEMS = new HashSet<String>(Arrays.asList(
            "narutomod:katon",
            "narutomod:suiton",
            "narutomod:raiton",
            "narutomod:doton",
            "narutomod:futon",
            "narutomod:dojutsu",
            "narutomod:byakuganhelmet",
            "narutomod:sharinganhelmet",
            "narutomod:sharingan_1tomoehelmet",
            "narutomod:sharingan_2tomoehelmet",
            "narutomod:mangekyosharinganhelmet",
            "narutomod:mangekyosharinganobitohelmet",
            "narutomod:mangekyosharinganeternalhelmet",
            "narutomod:iryo_jutsu",
            "narutomod:hyoton",
            "narutomod:futton",
            "narutomod:bakuton",
            "narutomod:ranton",
            "narutomod:yooton",
            "narutomod:yoton",
            "narutomod:shakuton",
            "narutomod:shoton",
            "narutomod:eightgates",
            "shinobiaddon:dynamic_sharingan",
            "shinobiaddon:dynamic_byakugan",
            "shinobiaddon:dynamic_ketsuryugan",
            "shinobiaddon:dojutsu_medical_scroll",
            "narutomod:rinnegansharinganobitohelmet",
            "narutomod:rinnegantomoehelmet",
            "narutomod:mangekyosharinganitachihelmet",
            "narutomod:mangekyosharinganshisuihelmet",
            "kabutoaddon:rinne_sharingan",
            "kabutoaddon:rinne_sharingan_helmet",
            "kabutoaddon:kg_remover",
            "kabutoaddon:kg_reroll"
    ));

    private static final String[] LEARNER_MARKERS = new String[] {
            "learner",
            "genkai",
            "kekkei",
            "dojutsu",
            "sharingan",
            "rinnegan",
            "rinne_sharingan",
            "byakugan",
            "ketsuryugan",
            "tenseigan",
            "senrigan",
            "jogan",
            "mangekyo",
            "tomoe",
            "kg_",
            "_kg",
            "katon",
            "suiton",
            "raiton",
            "doton",
            "futon",
            "hyoton",
            "futton",
            "bakuton",
            "ranton",
            "yooton",
            "yoton",
            "shakuton",
            "shoton",
            "jiton",
            "mokuton",
            "jinton",
            "koton",
            "meiton",
            "inton",
            "iryo_jutsu",
            "eightgates",
            "nature"
    };

    private NarutoLearnerDropProtectionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemToss(ItemTossEvent event) {
        EntityItem entityItem = event.getEntityItem();
        if (entityItem == null || MinecraftAccess.isRemote(entityItem)) {
            return;
        }

        ItemStack stack = entityItem.getItem();
        if (!isProtectedLearner(stack)) {
            return;
        }

        event.setCanceled(true);
        restore(event.getPlayer(), stack);
        entityItem.setItem(ItemStack.EMPTY);
        entityItem.setDead();
    }

    private static void restore(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        ItemStack remaining = stack.copy();
        player.inventory.addItemStackToInventory(remaining);

        if (!remaining.isEmpty() && player.inventory.getItemStack().isEmpty()) {
            player.inventory.setItemStack(remaining.copy());
            remaining.setCount(0);
        }

        player.inventory.markDirty();

        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP mp = (EntityPlayerMP) player;
            Container inventoryContainer = getContainer(mp, "inventoryContainer", "field_71069_bz");
            Container openContainer = getContainer(mp, "openContainer", "field_71070_bA");
            if (inventoryContainer != null) {
                mp.sendContainerToPlayer(inventoryContainer);
            }
            if (openContainer != null && openContainer != inventoryContainer) {
                mp.sendContainerToPlayer(openContainer);
            }
            mp.updateHeldItem();
        }
    }

    private static Container getContainer(EntityPlayerMP player, String mappedName, String runtimeName) {
        Object value = getFieldValue(player, mappedName);
        if (!(value instanceof Container)) {
            value = getFieldValue(player, runtimeName);
        }
        return value instanceof Container ? (Container) value : null;
    }

    private static Object getFieldValue(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isProtectedLearner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        if (isProtectedName(name)) {
            return true;
        }

        return hasType(stack.getItem().getClass(), "net.narutomod.item.ItemJutsu$Base")
                || hasType(stack.getItem().getClass(), "net.narutomod.item.ItemDojutsu$Base")
                || isProtectedClassName(stack.getItem().getClass());
    }

    private static boolean isProtectedName(ResourceLocation name) {
        if (name == null) {
            return false;
        }

        String id = name.toString().toLowerCase(Locale.ROOT);
        if (PROTECTED_ITEMS.contains(id)) {
            return true;
        }

        int split = id.indexOf(':');
        String path = split >= 0 ? id.substring(split + 1) : id;
        if (isPlainScroll(path)) {
            return false;
        }
        return hasMarker(path);
    }

    private static boolean isProtectedClassName(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            String name = current.getName().toLowerCase(Locale.ROOT);
            if (isPlainScroll(name)) {
                current = current.getSuperclass();
                continue;
            }
            if (name.contains(".item.") && hasMarker(name)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isPlainScroll(String value) {
        return value.contains("scroll")
                && !value.contains("dojutsu")
                && !value.contains("learner")
                && !value.contains("genkai")
                && !value.contains("kekkei")
                && !value.contains("kg_")
                && !value.contains("_kg");
    }

    private static boolean hasMarker(String value) {
        for (String marker : LEARNER_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasType(Class<?> type, String className) {
        Class<?> current = type;
        while (current != null) {
            if (className.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
