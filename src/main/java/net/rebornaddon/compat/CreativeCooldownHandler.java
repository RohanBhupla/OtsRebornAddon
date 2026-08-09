package net.rebornaddon.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

public final class CreativeCooldownHandler {
    public static final CreativeCooldownHandler INSTANCE = new CreativeCooldownHandler();

    private Field externalCooldowns;
    private boolean externalCooldownsResolved;

    private CreativeCooldownHandler() {
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        if (event.phase != TickEvent.Phase.END || player == null || !player.capabilities.isCreativeMode) {
            return;
        }

        clear(player, player.inventory.mainInventory);
        clear(player, player.inventory.offHandInventory);
        clear(player, player.inventory.armorInventory);

        if (MinecraftAccess.isRemote(player)) {
            clearExternalClientCooldowns();
        }
    }

    private static void clear(EntityPlayer player, NonNullList<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            if (player.getCooldownTracker().hasCooldown(stack.getItem())) {
                player.getCooldownTracker().setCooldown(stack.getItem(), 0);
            }
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || tag.hasNoTags()) {
                continue;
            }

            for (String key : new ArrayList<String>(tag.getKeySet())) {
                if (key.startsWith("JutsuCDMapKey")) {
                    tag.setLong(key, 0L);
                }
            }
        }
    }

    private void clearExternalClientCooldowns() {
        resolveExternalCooldowns();
        if (externalCooldowns == null) {
            return;
        }

        try {
            Object value = externalCooldowns.get(null);
            if (value instanceof Map) {
                ((Map<?, ?>) value).clear();
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private void resolveExternalCooldowns() {
        if (externalCooldownsResolved) {
            return;
        }
        externalCooldownsResolved = true;

        try {
            Class<?> helper = Class.forName("net.jutsu.cooldown.asm.CooldownHelper");
            Field field = helper.getDeclaredField("JUTSU_COOLDOWN_EXPIRY");
            field.setAccessible(true);
            externalCooldowns = field;
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
