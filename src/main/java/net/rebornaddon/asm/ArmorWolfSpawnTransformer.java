package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Marks Armor Wolf wrappers as explicit summons before Forge evaluates spawn cancellation. */
public final class ArmorWolfSpawnTransformer implements IClassTransformer {
    private static final String WRAPPER = "com.armourwolfmod.entity.EntitySkinWolf";
    private static final String HANDLER = "com.armourwolfmod.event.CompanionKeybindHandler";
    private static final String RENDERER = "com.armourwolfmod.client.render.RenderCompanionWolf";
    private static final String BUKKIT_LISTENER = "org.bukkit.plugin.RegisteredListener";
    private static final String FORGE_LISTENER =
            "net.minecraftforge.fml.common.eventhandler.ASMEventHandler";
    private static final String WRAPPER_INTERNAL = WRAPPER.replace('.', '/');
    private static final String HOOK = "net/rebornaddon/mount/ArmorWolfCompatibilityHandler";
    private static final String RENDER_HOOK =
            "net/rebornaddon/mount/client/ArmorWolfRenderDiagnostics";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String className = transformedName == null ? name : transformedName;
        boolean wrapper = WRAPPER.equals(className) || WRAPPER.equals(name);
        boolean handler = HANDLER.equals(className) || HANDLER.equals(name);
        boolean renderer = RENDERER.equals(className) || RENDERER.equals(name);
        boolean bukkitListener = BUKKIT_LISTENER.equals(className)
                || BUKKIT_LISTENER.equals(name);
        boolean forgeListener = FORGE_LISTENER.equals(className) || FORGE_LISTENER.equals(name);
        if (!wrapper && !handler && !renderer && !bukkitListener && !forgeListener) {
            return basicClass;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            int constructors = 0;
            int ownerGuards = 0;
            int removalHooks = 0;
            int spawnCalls = 0;
            int rendererFallbacks = 0;
            int bukkitListenerMethods = 0;
            int forgeListenerMethods = 0;
            for (MethodNode method : node.methods) {
                if (renderer && isRendererFallback(method)) {
                    InsnList diagnostic = new InsnList();
                    diagnostic.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    diagnostic.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    diagnostic.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER_HOOK,
                            "recordFallback",
                            "(Ljava/lang/Object;Lnet/minecraft/entity/Entity;)V", false));
                    method.instructions.insert(diagnostic);
                    rendererFallbacks++;
                }
                if (bukkitListener && isBukkitCallEvent(method)) {
                    InsnList begin = new InsnList();
                    begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    begin.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                            "beginBukkitSpawnListener",
                            "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    method.instructions.insert(begin);
                    for (AbstractInsnNode instruction = method.instructions.getFirst();
                         instruction != null; ) {
                        AbstractInsnNode next = instruction.getNext();
                        if (instruction.getOpcode() == Opcodes.RETURN
                                || instruction.getOpcode() == Opcodes.ATHROW) {
                            InsnList finish = new InsnList();
                            finish.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            finish.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                                    "finishBukkitSpawnListener",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                            method.instructions.insertBefore(instruction, finish);
                        }
                        instruction = next;
                    }
                    bukkitListenerMethods++;
                }
                if (forgeListener && isForgeInvoke(method)) {
                    InsnList begin = new InsnList();
                    begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    begin.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                            "beginForgeJoinListener",
                            "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    method.instructions.insert(begin);
                    for (AbstractInsnNode instruction = method.instructions.getFirst();
                         instruction != null; ) {
                        AbstractInsnNode next = instruction.getNext();
                        if (instruction.getOpcode() == Opcodes.RETURN
                                || instruction.getOpcode() == Opcodes.ATHROW) {
                            InsnList finish = new InsnList();
                            finish.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            finish.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                                    "finishForgeJoinListener",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                            method.instructions.insertBefore(instruction, finish);
                        }
                        instruction = next;
                    }
                    forgeListenerMethods++;
                }
                if (wrapper && isSetDeadMethod(method)) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                            "recordRemoval", "(Lnet/minecraft/entity/Entity;)V", false));
                    method.instructions.insert(hook);
                    removalHooks++;
                }
                boolean patchedConstructor = false;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; ) {
                    AbstractInsnNode next = instruction.getNext();
                    if (wrapper && "<init>".equals(method.name)
                            && instruction.getOpcode() == Opcodes.RETURN) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                                "prepareExplicitSpawn",
                                "(Lnet/minecraft/entity/Entity;)V", false));
                        method.instructions.insertBefore(instruction, hook);
                        patchedConstructor = true;
                    } else if (wrapper && isLivingUpdate(method)
                            && isWrapperSetDead(instruction)) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                HOOK, "handleUnresolvedOwner",
                                "(Lnet/minecraft/entity/Entity;)V", false));
                        ownerGuards++;
                    } else if (handler && isWorldSpawn(instruction)) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                HOOK, "spawnExplicit",
                                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)Z",
                                false));
                        spawnCalls++;
                    }
                    instruction = next;
                }
                if (patchedConstructor) constructors++;
            }
            if (wrapper && (constructors == 0 || ownerGuards == 0 || removalHooks == 0)) {
                return basicClass;
            }
            if (handler && spawnCalls == 0) {
                return basicClass;
            }
            if (renderer && rendererFallbacks == 0) {
                return basicClass;
            }
            if (bukkitListener && bukkitListenerMethods == 0) {
                return basicClass;
            }
            if (forgeListener && forgeListenerMethods == 0) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            return basicClass;
        }
    }

    private static boolean isLivingUpdate(MethodNode method) {
        return "onLivingUpdate".equals(method.name) || "func_70636_d".equals(method.name);
    }

    private static boolean isSetDeadMethod(MethodNode method) {
        return "()V".equals(method.desc)
                && ("setDead".equals(method.name) || "func_70106_y".equals(method.name));
    }

    private static boolean isWrapperSetDead(AbstractInsnNode instruction) {
        if (!(instruction instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && WRAPPER_INTERNAL.equals(call.owner)
                && "()V".equals(call.desc)
                && ("setDead".equals(call.name) || "func_70106_y".equals(call.name));
    }

    private static boolean isWorldSpawn(AbstractInsnNode instruction) {
        if (!(instruction instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && ("net/minecraft/world/World".equals(call.owner)
                || "net/minecraft/world/WorldServer".equals(call.owner))
                && "(Lnet/minecraft/entity/Entity;)Z".equals(call.desc)
                && ("spawnEntity".equals(call.name) || "func_72838_d".equals(call.name));
    }

    private static boolean isBukkitCallEvent(MethodNode method) {
        return "callEvent".equals(method.name)
                && "(Lorg/bukkit/event/Event;)V".equals(method.desc);
    }

    private static boolean isForgeInvoke(MethodNode method) {
        return "invoke".equals(method.name)
                && "(Lnet/minecraftforge/fml/common/eventhandler/Event;)V".equals(method.desc);
    }

    private static boolean isRendererFallback(MethodNode method) {
        return "renderWithFallback".equals(method.name)
                && ("(Lcom/armourwolfmod/entity/EntitySkinWolf;DDDFFF)V".equals(method.desc)
                || "(Lnet/minecraft/entity/Entity;DDDFFF)V".equals(method.desc));
    }
}
