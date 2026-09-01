package net.rebornaddon.asm;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;

public class ArmorWolfSpawnTransformerTest {
    @Test
    public void preparesEveryWrapperConstructorBeforeItReturns() {
        byte[] transformed = new ArmorWolfSpawnTransformer().transform(
                "com.armourwolfmod.entity.EntitySkinWolf",
                "com.armourwolfmod.entity.EntitySkinWolf", fixture());

        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int constructors = 0;
        int prepareHooks = 0;
        int ownerHooks = 0;
        int removalHooks = 0;
        for (MethodNode method : node.methods) {
            if ("<init>".equals(method.name)) constructors++;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"net/rebornaddon/mount/ArmorWolfCompatibilityHandler".equals(call.owner)) {
                    continue;
                }
                if ("prepareExplicitSpawn".equals(call.name)) prepareHooks++;
                if ("handleUnresolvedOwner".equals(call.name)) ownerHooks++;
                if ("recordRemoval".equals(call.name)) removalHooks++;
            }
        }
        assertEquals(2, constructors);
        assertEquals(2, prepareHooks);
        assertEquals(1, ownerHooks);
        assertEquals(1, removalHooks);
    }

    @Test
    public void routesTheRealWorldSpawnResultThroughDiagnostics() {
        byte[] transformed = new ArmorWolfSpawnTransformer().transform(
                "com.armourwolfmod.event.CompanionKeybindHandler",
                "com.armourwolfmod.event.CompanionKeybindHandler", handlerFixture());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int hooks = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("net/rebornaddon/mount/ArmorWolfCompatibilityHandler".equals(call.owner)
                        && "spawnExplicit".equals(call.name)) hooks++;
            }
        }
        assertEquals(1, hooks);
    }

    @Test
    public void instrumentsBukkitListenerCancellationChanges() {
        byte[] transformed = new ArmorWolfSpawnTransformer().transform(
                "org.bukkit.plugin.RegisteredListener",
                "org.bukkit.plugin.RegisteredListener", bukkitListenerFixture());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int begin = 0;
        int finish = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"net/rebornaddon/mount/ArmorWolfCompatibilityHandler".equals(call.owner)) {
                    continue;
                }
                if ("beginBukkitSpawnListener".equals(call.name)) begin++;
                if ("finishBukkitSpawnListener".equals(call.name)) finish++;
            }
        }
        assertEquals(1, begin);
        assertEquals(2, finish);
    }

    @Test
    public void instrumentsForgeListenerCancellationChanges() {
        byte[] transformed = new ArmorWolfSpawnTransformer().transform(
                "net.minecraftforge.fml.common.eventhandler.ASMEventHandler",
                "net.minecraftforge.fml.common.eventhandler.ASMEventHandler", forgeListenerFixture());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int begin = 0;
        int finish = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"net/rebornaddon/mount/ArmorWolfCompatibilityHandler".equals(call.owner)) {
                    continue;
                }
                if ("beginForgeJoinListener".equals(call.name)) begin++;
                if ("finishForgeJoinListener".equals(call.name)) finish++;
            }
        }
        assertEquals(1, begin);
        assertEquals(1, finish);
    }

    private static byte[] fixture() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = "com/armourwolfmod/entity/EntitySkinWolf";
        node.superName = "java/lang/Object";
        constructor(node, "()V", 1);
        constructor(node, "(Ljava/lang/Object;)V", 2);
        MethodNode dead = new MethodNode(Opcodes.ACC_PUBLIC, "func_70106_y", "()V", null, null);
        dead.instructions.add(new InsnNode(Opcodes.RETURN));
        dead.maxStack = 0;
        dead.maxLocals = 1;
        node.methods.add(dead);
        MethodNode update = new MethodNode(Opcodes.ACC_PUBLIC, "func_70636_d", "()V", null, null);
        update.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        update.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name,
                "func_70106_y", "()V", false));
        update.instructions.add(new InsnNode(Opcodes.RETURN));
        update.maxStack = 1;
        update.maxLocals = 1;
        node.methods.add(update);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] handlerFixture() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = "com/armourwolfmod/event/CompanionKeybindHandler";
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "spawn", "(Lnet/minecraft/world/WorldServer;Lnet/minecraft/entity/Entity;)V",
                null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/WorldServer", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] bukkitListenerFixture() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = "org/bukkit/plugin/RegisteredListener";
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "callEvent",
                "(Lorg/bukkit/event/Event;)V", null,
                new String[] {"org/bukkit/event/EventException"});
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.maxStack = 1;
        method.maxLocals = 2;
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] forgeListenerFixture() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = "net/minecraftforge/fml/common/eventhandler/ASMEventHandler";
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "invoke",
                "(Lnet/minecraftforge/fml/common/eventhandler/Event;)V", null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = 2;
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void constructor(ClassNode node, String descriptor, int locals) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 1;
        method.maxLocals = locals;
        node.methods.add(method);
    }
}
