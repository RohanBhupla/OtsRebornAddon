package net.rebornaddon.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SenjutsuFoodCompatibility {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon");
    private static final String PREVIOUS_FOOD = "prevFoodStat";
    private static final Field FOOD_LEVEL_FIELD = findField(int.class, 0,
            "foodLevel", "field_75127_a");
    private static final Field SATURATION_LEVEL_FIELD = findField(float.class, 0,
            "foodSaturationLevel", "field_75125_b");
    private static final AtomicBoolean ACCESS_WARNING_REPORTED = new AtomicBoolean();

    private SenjutsuFoodCompatibility() {
    }

    public static void ignoreInventoryTick(FoodStats food, int requestedLevel, ItemStack learner) {
        if (learner == null || learner.isEmpty()) return;
        ignoreInventoryTick(food, requestedLevel, learner.getTagCompound());
    }

    static void ignoreInventoryTick(FoodStats food, int requestedLevel, NBTTagCompound tag) {
        if (tag != null) tag.removeTag(PREVIOUS_FOOD);
    }

    public static void captureActivationFood(ItemStack learner, EntityLivingBase entity) {
        if (learner == null || learner.isEmpty() || !(entity instanceof EntityPlayer)) return;
        if (!learner.hasTagCompound()) learner.setTagCompound(new NBTTagCompound());
        learner.getTagCompound().setInteger(PREVIOUS_FOOD,
                ((EntityPlayer) entity).getFoodStats().getFoodLevel());
    }

    public static void applyDeactivationCost(ItemStack learner, EntityLivingBase entity) {
        if (learner == null || learner.isEmpty() || !(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        if (hasWoodRelease(player)) {
            maintainFullNutrition(player.getFoodStats());
            if (learner.hasTagCompound()) learner.getTagCompound().removeTag(PREVIOUS_FOOD);
            return;
        }
        applyDeactivationCost(player.getFoodStats(), learner.getTagCompound());
    }

    public static void maintainWoodReleaseNutrition(ItemStack stack, World world, Entity entity) {
        if (stack == null || stack.isEmpty() || world == null || world.isRemote
                || !(entity instanceof EntityPlayer) || !isWoodRelease(stack)) return;
        maintainFullNutrition(((EntityPlayer) entity).getFoodStats());
    }

    static void maintainFullNutrition(FoodStats food) {
        if (food == null) return;
        boolean foodUpdated = setInt(FOOD_LEVEL_FIELD, food, 20);
        boolean saturationUpdated = setFloat(SATURATION_LEVEL_FIELD, food, 20.0F);
        if ((!foodUpdated || !saturationUpdated) && ACCESS_WARNING_REPORTED.compareAndSet(false, true)) {
            LOGGER.error("Unable to maintain wood-release nutrition on this server implementation "
                    + "(foodLevel={}, saturation={}). The compatibility hook has been disabled safely "
                    + "for inaccessible values instead of crashing player ticks.",
                    foodUpdated, saturationUpdated);
        }
    }

    static void applyDeactivationCost(FoodStats food, NBTTagCompound tag) {
        if (food == null) return;
        if (tag == null || !tag.hasKey(PREVIOUS_FOOD, 3)) return;
        int requestedLevel = Math.max(0, tag.getInteger(PREVIOUS_FOOD) - 5);
        boolean updated = setInt(FOOD_LEVEL_FIELD, food,
                Math.min(food.getFoodLevel(), requestedLevel));
        if (!updated && ACCESS_WARNING_REPORTED.compareAndSet(false, true)) {
            LOGGER.error("Unable to apply the senjutsu food cost on this server implementation. "
                    + "The inaccessible value was left unchanged instead of crashing the player tick.");
        }
        tag.removeTag(PREVIOUS_FOOD);
    }

    private static boolean hasWoodRelease(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isWoodRelease(stack)) return true;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isWoodRelease(stack)) return true;
        }
        return false;
    }

    private static boolean isWoodRelease(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = stack.getItem().getRegistryName();
        return id != null && "narutomod".equals(id.getResourceDomain())
                && "mokuton".equals(id.getResourcePath());
    }

    private static Field findField(Class<?> expectedType, int ordinal, String... names) {
        try {
            return ReflectionHelper.findField(FoodStats.class, names);
        } catch (RuntimeException ignored) {
            int found = 0;
            for (Field field : FoodStats.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != expectedType) continue;
                if (found++ != ordinal) continue;
                try {
                    field.setAccessible(true);
                    return field;
                } catch (RuntimeException inaccessible) {
                    return null;
                }
            }
            return null;
        }
    }

    private static boolean setInt(Field field, FoodStats food, int value) {
        if (field == null) return false;
        try {
            field.setInt(food, value);
            return true;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean setFloat(Field field, FoodStats food, float value) {
        if (field == null) return false;
        try {
            field.setFloat(food, value);
            return true;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }
}
