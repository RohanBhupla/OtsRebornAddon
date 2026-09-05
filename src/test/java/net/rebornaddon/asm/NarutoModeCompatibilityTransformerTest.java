package net.rebornaddon.asm;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.Type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NarutoModeCompatibilityTransformerTest {
    private static final String CLASS_NAME = "net.narutomod.item.ItemSenjutsu$RangedItem";
    private static final String UPDATE_DESC =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/Entity;IZ)V";

    @Test
    public void redirectsSenjutsuFoodClampToCompatibilityHelper() {
        ClassNode input = new ClassNode();
        input.version = Opcodes.V1_8;
        input.access = Opcodes.ACC_PUBLIC;
        input.name = CLASS_NAME.replace('.', '/');
        input.superName = "java/lang/Object";
        MethodNode update = new MethodNode(Opcodes.ACC_PUBLIC, "func_77663_a", UPDATE_DESC, null, null);
        update.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        update.instructions.add(new InsnNode(Opcodes.ICONST_5));
        update.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/util/FoodStats", "func_75114_a", "(I)V", false));
        update.instructions.add(new InsnNode(Opcodes.RETURN));
        update.maxStack = 2;
        update.maxLocals = 6;
        input.methods.add(update);
        ClassWriter writer = new ClassWriter(0);
        input.accept(writer);

        byte[] transformed = new NarutoModeCompatibilityTransformer().transform(
                CLASS_NAME, CLASS_NAME, writer.toByteArray());
        ClassNode output = new ClassNode();
        new ClassReader(transformed).accept(output, 0);

        boolean redirected = false;
        for (MethodNode method : output.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("net/rebornaddon/compat/SenjutsuFoodCompatibility".equals(call.owner)) {
                    assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                    assertEquals("ignoreInventoryTick", call.name);
                    redirected = true;
                }
            }
        }
        assertTrue(redirected);
    }

    @Test
    public void movesSenjutsuFoodStateToActivationAndDeactivation() {
        assertLifecycleHook("net.narutomod.item.ItemSenjutsu$SageMode", "createJutsu",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;F)Z",
                "captureActivationFood", true);
        assertLifecycleHook("net.narutomod.item.ItemSenjutsu", "deactivateSageMode",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
                "applyDeactivationCost", false);
    }

    @Test
    public void suppressesEveryNarutoAddonOptionsAndRerollProcedure() {
        assertProcedureSuppressed("narutomodaddon.procedure.ProcedureShowOptionsWhenPlayerJoins");
        assertProcedureSuppressed("narutomodaddon.procedure.ProcedureTriggerReroll");
        assertProcedureSuppressed("narutomodaddon.procedure.ProcedureAddonSettingsCommandExecuted");
    }

    @Test
    public void replacesNativeWaterAndWallProcedureButKeepsBaseHelper() {
        String className = "net.narutomod.procedure.ProcedureBasicNinjaSkills";
        MethodNode method = transformProcedure(className);
        MethodInsnNode helper = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) helper = (MethodInsnNode) instruction;
        }
        assertTrue(helper != null);
        assertEquals("net/rebornaddon/compat/ChakraControlCompatibility", helper.owner);
        assertEquals("applyBaseNinjaSkills", helper.name);
    }

    @Test
    public void redirectsNarutoLevelGateWithoutChangingRealExperience() {
        String className = NarutoModeCompatibilityTransformer.CHAKRA_PLAYER_HOOK;
        ClassNode input = classWithMethod(className, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "gate", "(Lnet/minecraft/entity/player/EntityPlayer;)I");
        MethodNode method = input.methods.get(0);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "net/minecraft/entity/player/EntityPlayer", "field_71068_ca", "I"));
        method.instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 10));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 1;

        MethodNode output = transform(className, input);
        int helpers = 0;
        for (AbstractInsnNode instruction = output.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && NarutoModeCompatibilityTransformer.NINJA_ACCESS_HELPER
                    .equals(((MethodInsnNode) instruction).owner)
                    && "ninjaAccessLevel".equals(((MethodInsnNode) instruction).name)) {
                helpers++;
            }
        }
        assertEquals(1, helpers);
    }

    @Test
    public void hooksWoodReleaseInventoryTick() {
        String className = "net.narutomod.item.ItemMokuton$ItemCustom";
        ClassNode input = classWithMethod(className, Opcodes.ACC_PUBLIC, "func_77663_a", UPDATE_DESC);
        MethodNode update = input.methods.get(0);
        update.instructions.add(new InsnNode(Opcodes.RETURN));
        update.maxLocals = 6;
        MethodNode output = transform(className, input);
        boolean found = false;
        for (AbstractInsnNode instruction = output.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && "maintainWoodReleaseNutrition".equals(((MethodInsnNode) instruction).name)) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void registersAddonSusanooDataAgainstItsActualEntityClass() {
        String className = "net.mcreator.ahznbcursemarkaddon.entity.EntitySusanooBase";
        ClassNode input = classWithMethod(className, Opcodes.ACC_STATIC, "<clinit>", "()V");
        MethodNode initializer = input.methods.get(0);
        initializer.instructions.add(new LdcInsnNode(
                Type.getObjectType("net/narutomod/entity/EntitySusanooBase")));
        initializer.instructions.add(new InsnNode(Opcodes.POP));
        initializer.instructions.add(new LdcInsnNode(
                Type.getObjectType("net/narutomod/entity/EntitySusanooBase")));
        initializer.instructions.add(new InsnNode(Opcodes.POP));
        initializer.instructions.add(new InsnNode(Opcodes.RETURN));
        initializer.maxStack = 1;

        MethodNode output = transform(className, input);
        int corrected = 0;
        for (AbstractInsnNode instruction = output.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof LdcInsnNode)) continue;
            Object value = ((LdcInsnNode) instruction).cst;
            if (value instanceof Type && className.replace('.', '/')
                    .equals(((Type) value).getInternalName())) corrected++;
        }
        assertEquals(2, corrected);
    }

    private static void assertProcedureSuppressed(String className) {
        MethodNode output = transformProcedure(className);
        int executable = 0;
        for (AbstractInsnNode instruction = output.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                executable++;
                assertEquals(Opcodes.RETURN, instruction.getOpcode());
            }
        }
        assertEquals(1, executable);
    }

    private static MethodNode transformProcedure(String className) {
        ClassNode input = classWithMethod(className, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "executeProcedure", "(Ljava/util/Map;)V");
        MethodNode procedure = input.methods.get(0);
        procedure.instructions.add(new InsnNode(Opcodes.NOP));
        procedure.instructions.add(new InsnNode(Opcodes.RETURN));
        procedure.maxLocals = 1;
        return transform(className, input);
    }

    private static ClassNode classWithMethod(String className, int access, String name,
                                             String descriptor) {
        ClassNode input = new ClassNode();
        input.version = Opcodes.V1_8;
        input.access = Opcodes.ACC_PUBLIC;
        input.name = className.replace('.', '/');
        input.superName = "java/lang/Object";
        input.methods.add(new MethodNode(access, name, descriptor, null, null));
        return input;
    }

    private static MethodNode transform(String className, ClassNode input) {
        ClassWriter writer = new ClassWriter(0);
        input.accept(writer);
        byte[] transformed = new NarutoModeCompatibilityTransformer().transform(
                className, className, writer.toByteArray());
        ClassNode output = new ClassNode();
        new ClassReader(transformed).accept(output, 0);
        return output.methods.get(0);
    }

    private static void assertLifecycleHook(String className, String methodName, String descriptor,
                                            String helperName, boolean returnsBoolean) {
        ClassNode input = new ClassNode();
        input.version = Opcodes.V1_8;
        input.access = Opcodes.ACC_PUBLIC;
        input.name = className.replace('.', '/');
        input.superName = "java/lang/Object";
        int access = returnsBoolean ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        MethodNode method = new MethodNode(access, methodName, descriptor, null, null);
        if (returnsBoolean) method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(returnsBoolean ? Opcodes.IRETURN : Opcodes.RETURN));
        method.maxStack = returnsBoolean ? 1 : 0;
        method.maxLocals = returnsBoolean ? 4 : 2;
        input.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        input.accept(writer);

        byte[] transformed = new NarutoModeCompatibilityTransformer().transform(
                className, className, writer.toByteArray());
        ClassNode output = new ClassNode();
        new ClassReader(transformed).accept(output, 0);

        boolean found = false;
        for (MethodNode candidate : output.methods) {
            for (AbstractInsnNode instruction = candidate.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode
                        && helperName.equals(((MethodInsnNode) instruction).name)) found = true;
            }
        }
        assertTrue(found);
    }
}
