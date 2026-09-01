package net.rebornaddon.compat;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.FoodStats;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SenjutsuFoodCompatibilityTest {
    @Test
    public void carryingUnusedLearnerDoesNotClampFood() {
        FoodStats food = new FoodStats();
        food.setFoodLevel(20);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("prevFoodStat", 20);

        SenjutsuFoodCompatibility.ignoreInventoryTick(food, 15, tag);

        assertEquals(20, food.getFoodLevel());
        assertFalse(tag.hasKey("prevFoodStat"));
    }

    @Test
    public void deactivationCostAppliesOnceAfterAnActivationMarker() {
        FoodStats food = new FoodStats();
        food.setFoodLevel(20);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("prevFoodStat", 20);

        SenjutsuFoodCompatibility.applyDeactivationCost(food, tag);
        assertEquals(15, food.getFoodLevel());
        assertFalse(tag.hasKey("prevFoodStat"));

        food.setFoodLevel(20);
        SenjutsuFoodCompatibility.applyDeactivationCost(food, tag);
        assertEquals(20, food.getFoodLevel());
    }

    @Test
    public void releaseCostNeverGrantsFood() {
        FoodStats food = new FoodStats();
        food.setFoodLevel(4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("prevFoodStat", 20);

        SenjutsuFoodCompatibility.applyDeactivationCost(food, tag);

        assertEquals(4, food.getFoodLevel());
    }

    @Test
    public void woodReleaseMaintainsFullFoodAndSaturation() {
        FoodStats food = new FoodStats();
        food.setFoodLevel(7);
        food.setFoodSaturationLevel(0.25F);

        SenjutsuFoodCompatibility.maintainFullNutrition(food);

        assertEquals(20, food.getFoodLevel());
        assertEquals(20.0F, food.getSaturationLevel(), 0.0F);
    }

    @Test
    public void compatibilityClassDoesNotLinkDirectlyToMohistOptionalSetters() throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(SenjutsuFoodCompatibility.class.getName()).accept(node, 0);

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertFalse("Nutrition hook must not invoke FoodStats setters directly: " + call.name,
                        "net/minecraft/util/FoodStats".equals(call.owner)
                                && ("setFoodLevel".equals(call.name)
                                || "setFoodSaturationLevel".equals(call.name)
                                || "func_75114_a".equals(call.name)
                                || "func_75119_b".equals(call.name)));
            }
        }
    }
}
