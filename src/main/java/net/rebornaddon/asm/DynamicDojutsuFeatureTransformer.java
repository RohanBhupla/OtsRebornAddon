package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DynamicDojutsuFeatureTransformer implements IClassTransformer {
    private static final String PREFIX = "com.leolifeless.shinobiaddon.";
    private static final String PACKET_HANDLER =
            "com.leolifeless.shinobiaddon.network.JutsuPacketHandler";
    private static final String PACKET_HANDLER_INTERNAL =
            "com/leolifeless/shinobiaddon/network/JutsuPacketHandler";
    private static final String NETWORK_WRAPPER_INTERNAL =
            "net/minecraftforge/fml/common/network/simpleimpl/SimpleNetworkWrapper";
    private static final String SIDE_INTERNAL = "net/minecraftforge/fml/relauncher/Side";
    private static final String REGISTER_MESSAGE_DESC =
            "(Ljava/lang/Class;Ljava/lang/Class;ILnet/minecraftforge/fml/relauncher/Side;)V";
    private static final String SUBSCRIBE_EVENT =
            "Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;";

    private static final Set<String> EVENT_EXEMPTIONS = new HashSet<String>(Arrays.asList(
            "com.leolifeless.shinobiaddon.items.ShinobiAddonItems",
            "com.leolifeless.shinobiaddon.blocks.ShinobiAddonBlocks",
            "com.leolifeless.shinobiaddon.sound.ShinobiAddonSounds",
            "com.leolifeless.shinobiaddon.proxy.ClientProxy",
            "com.leolifeless.shinobiaddon.system.SharinganProgressionSystem",
            "com.leolifeless.shinobiaddon.system.ByakuganProgressionSystem",
            "com.leolifeless.shinobiaddon.system.KetsuryuganProgressionSystem"
    ));

    private static final Set<String> COREMOD_TRANSFORMERS = new HashSet<String>(Arrays.asList(
            "com.leolifeless.shinobiaddon.coremod.SusanooPostTickTransformer",
            "com.leolifeless.shinobiaddon.coremod.SusanooWingedTransformer"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        String className = transformedName == null ? name : transformedName;
        if (className == null || !className.startsWith(PREFIX)) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            boolean changed = patch(className, classNode);
            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private boolean patch(String className, ClassNode classNode) {
        boolean changed = false;
        boolean dedicatedServer = isDedicatedServer();

        for (MethodNode method : classNode.methods) {
            if (isServerStartingMethod(className, method)) {
                keepDojutsuCommands(method);
                changed = true;
                continue;
            }

            if (dedicatedServer
                    && PACKET_HANDLER.equals(className)
                    && "register".equals(method.name)
                    && "()V".equals(method.desc)) {
                changed |= stripClientPacketRegistrations(method);
            }

            if (COREMOD_TRANSFORMERS.contains(className)
                    && "transform".equals(method.name)
                    && "(Ljava/lang/String;Ljava/lang/String;[B)[B".equals(method.desc)) {
                makePassThrough(method, 3);
                changed = true;
                continue;
            }

            if (isDedicatedServerClientGuiMethod(dedicatedServer, className, method)
                    || isLifecycleMethod(className, method)
                    || isNetworkHandler(className, method)
                    || isDynamicSusanooMethod(className, method)
                    || isBlockedEventHandler(className, method)) {
                makeNoOp(method);
                changed = true;
            }
        }

        return changed;
    }

    private boolean isServerStartingMethod(String className, MethodNode method) {
        return "com.leolifeless.shinobiaddon.ShinobiAddon".equals(className)
                && "serverStarting".equals(method.name)
                && "(Lnet/minecraftforge/fml/common/event/FMLServerStartingEvent;)V".equals(method.desc);
    }

    private void keepDojutsuCommands(MethodNode method) {
        clear(method);
        addCommandRegistration(method.instructions,
                "com/leolifeless/shinobiaddon/command/DojutsuCommand");
        addCommandRegistration(method.instructions,
                "com/leolifeless/shinobiaddon/command/DojutsuOwnerCommand");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 3;
    }

    private void addCommandRegistration(InsnList instructions, String commandClass) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new TypeInsnNode(Opcodes.NEW, commandClass));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, commandClass, "<init>", "()V", false));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraftforge/fml/common/event/FMLServerStartingEvent",
                "registerServerCommand",
                "(Lnet/minecraft/command/ICommand;)V",
                false));
    }

    private boolean isDedicatedServer() {
        try {
            Side side = FMLLaunchHandler.side();
            return side != null && side.isServer();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isDedicatedServerClientGuiMethod(boolean dedicatedServer,
                                                      String className,
                                                      MethodNode method) {
        return dedicatedServer
                && "com.leolifeless.shinobiaddon.gui.ShinobiAddonGuiHandler".equals(className)
                && "getClientGuiElement".equals(method.name);
    }

    private boolean stripClientPacketRegistrations(MethodNode method) {
        boolean changed = false;
        AbstractInsnNode node = method.instructions.getFirst();

        while (node != null) {
            AbstractInsnNode next = node.getNext();
            if (isRegisterMessageCall(node) && targetsClient(node)) {
                AbstractInsnNode start = findRegistrationStart(node);
                if (start != null) {
                    method.instructions.insertBefore(start, packetIdIncrement());
                    removeExecutableRange(method.instructions, start, node);
                    changed = true;
                }
            }
            node = next;
        }

        return changed;
    }

    private boolean isRegisterMessageCall(AbstractInsnNode node) {
        if (!(node instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) node;
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && NETWORK_WRAPPER_INTERNAL.equals(call.owner)
                && "registerMessage".equals(call.name)
                && REGISTER_MESSAGE_DESC.equals(call.desc);
    }

    private boolean targetsClient(AbstractInsnNode registerCall) {
        AbstractInsnNode node = previousExecutable(registerCall.getPrevious());
        if (!(node instanceof FieldInsnNode)) {
            return false;
        }
        FieldInsnNode side = (FieldInsnNode) node;
        return side.getOpcode() == Opcodes.GETSTATIC
                && SIDE_INTERNAL.equals(side.owner)
                && "CLIENT".equals(side.name);
    }

    private AbstractInsnNode findRegistrationStart(AbstractInsnNode registerCall) {
        for (AbstractInsnNode node = registerCall.getPrevious(); node != null; node = node.getPrevious()) {
            if (node instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) node;
                if (field.getOpcode() == Opcodes.GETSTATIC
                        && PACKET_HANDLER_INTERNAL.equals(field.owner)
                        && "INSTANCE".equals(field.name)) {
                    return node;
                }
            }
            if (isRegisterMessageCall(node)) {
                break;
            }
        }
        return null;
    }

    private InsnList packetIdIncrement() {
        InsnList instructions = new InsnList();
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, PACKET_HANDLER_INTERNAL, "packetId", "I"));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new InsnNode(Opcodes.IADD));
        instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC, PACKET_HANDLER_INTERNAL, "packetId", "I"));
        return instructions;
    }

    private void removeExecutableRange(InsnList instructions,
                                       AbstractInsnNode start,
                                       AbstractInsnNode end) {
        AbstractInsnNode node = start;
        while (node != null) {
            AbstractInsnNode next = node.getNext();
            if (isExecutable(node)) {
                instructions.remove(node);
            }
            if (node == end) {
                return;
            }
            node = next;
        }
    }

    private AbstractInsnNode previousExecutable(AbstractInsnNode node) {
        while (node != null && !isExecutable(node)) {
            node = node.getPrevious();
        }
        return node;
    }

    private boolean isExecutable(AbstractInsnNode node) {
        return !(node instanceof LabelNode)
                && !(node instanceof LineNumberNode)
                && !(node instanceof FrameNode);
    }

    private boolean isLifecycleMethod(String className, MethodNode method) {
        if ("com.leolifeless.shinobiaddon.proxy.CommonProxy".equals(className)) {
            return "preInit".equals(method.name) || "init".equals(method.name);
        }

        if (!"com.leolifeless.shinobiaddon.ShinobiAddon".equals(className)) {
            return false;
        }

        return "init".equals(method.name)
                || "postInit".equals(method.name);
    }

    private boolean isNetworkHandler(String className, MethodNode method) {
        return className.startsWith(PREFIX + "network.") && "onMessage".equals(method.name);
    }

    private boolean isDynamicSusanooMethod(String className, MethodNode method) {
        if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
            return false;
        }
        return className.contains(".susanoo.") || className.contains(".Susanoo");
    }

    private boolean isBlockedEventHandler(String className, MethodNode method) {
        return !EVENT_EXEMPTIONS.contains(className)
                && (hasAnnotation(method.visibleAnnotations) || hasAnnotation(method.invisibleAnnotations));
    }

    private boolean hasAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (SUBSCRIBE_EVENT.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    private void makePassThrough(MethodNode method, int inputIndex) {
        clear(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, inputIndex));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
    }

    private void makeNoOp(MethodNode method) {
        clear(method);
        Type returnType = Type.getReturnType(method.desc);
        switch (returnType.getSort()) {
            case Type.VOID:
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.maxStack = 0;
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                method.maxStack = 1;
                break;
            case Type.LONG:
                method.instructions.add(new InsnNode(Opcodes.LCONST_0));
                method.instructions.add(new InsnNode(Opcodes.LRETURN));
                method.maxStack = 2;
                break;
            case Type.FLOAT:
                method.instructions.add(new InsnNode(Opcodes.FCONST_0));
                method.instructions.add(new InsnNode(Opcodes.FRETURN));
                method.maxStack = 1;
                break;
            case Type.DOUBLE:
                method.instructions.add(new InsnNode(Opcodes.DCONST_0));
                method.instructions.add(new InsnNode(Opcodes.DRETURN));
                method.maxStack = 2;
                break;
            default:
                method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                method.maxStack = 1;
                break;
        }
    }

    private void clear(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        if (method.visibleLocalVariableAnnotations != null) {
            method.visibleLocalVariableAnnotations.clear();
        }
        if (method.invisibleLocalVariableAnnotations != null) {
            method.invisibleLocalVariableAnnotations.clear();
        }
    }
}
