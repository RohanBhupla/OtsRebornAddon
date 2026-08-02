package net.rebornaddon.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class NarutoProgressionHandler {
    public static final NarutoProgressionHandler INSTANCE = new NarutoProgressionHandler();

    private static final int[] GATE_XP = new int[] {0, 8000, 8750, 9250, 10750, 11250, 12000, 12500, 13000};
    private static final int MAX_GATE = 3;
    private static final int MAX_GATE_XP = GATE_XP[MAX_GATE + 1] * 2 - 1;
    private static boolean patched;

    private NarutoProgressionHandler() {
    }

    public static void apply() {
        patchGateRequirements();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        World world = MinecraftAccess.world(event.player);
        if (event.phase != TickEvent.Phase.END || world == null || world.isRemote) {
            return;
        }

        patchGateRequirements();
        cleanHeldItems(event.player);

        if (world.getTotalWorldTime() % 20L == 0L) {
            cleanPlayer(event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Entity entity = event.getEntityPlayer();

        if (isEightGates(event.getItemStack()) && entity.isSneaking()
                && getGateOpened(event.getItemStack()) >= MAX_GATE) {
            event.setCanceled(true);
        }
    }

    private static void patchGateRequirements() {
        if (patched) {
            return;
        }

        try {
            Class<?> itemClass = Class.forName("net.narutomod.item.ItemEightGates");
            Field blockField = itemClass.getDeclaredField("block");
            blockField.setAccessible(true);
            Object item = blockField.get(null);

            if (item == null) {
                return;
            }

            Field gateField = item.getClass().getDeclaredField("GATE");
            gateField.setAccessible(true);
            Object[] gates = (Object[]) gateField.get(item);

            if (gates == null || gates.length < GATE_XP.length) {
                return;
            }

            Field xpField = gates[1].getClass().getDeclaredField("xpRequired");
            makeWritable(xpField);

            for (int i = 1; i < GATE_XP.length; i++) {
                xpField.setInt(gates[i], GATE_XP[i] * 2);
            }

            patched = true;
        } catch (Throwable ignored) {
        }
    }

    private static void makeWritable(Field field) {
        field.setAccessible(true);

        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Throwable ignored) {
        }
    }

    private static void cleanHeldItems(EntityPlayer player) {
        EntityLivingBase living = player;
        cleanStack(living.getHeldItemMainhand());
        cleanStack(living.getHeldItemOffhand());
    }

    private static void cleanPlayer(EntityPlayer player) {
        clean(player.inventory.mainInventory);
        clean(player.inventory.armorInventory);
        clean(player.inventory.offHandInventory);
    }

    private static void clean(NonNullList<ItemStack> items) {
        for (ItemStack stack : items) {
            cleanStack(stack);
        }
    }

    private static void cleanStack(ItemStack stack) {
        if (!isEightGates(stack)) {
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }

        if (tag.getFloat("gateOpened") > MAX_GATE) {
            tag.setFloat("gateOpened", MAX_GATE);
        }

        if (tag.getInteger("battleExperience") > MAX_GATE_XP) {
            tag.setInteger("battleExperience", MAX_GATE_XP);
        }
    }

    private static float getGateOpened(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0.0F : tag.getFloat("gateOpened");
    }

    private static boolean isEightGates(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        return name != null && "narutomod:eightgates".equals(name.toString());
    }
}
