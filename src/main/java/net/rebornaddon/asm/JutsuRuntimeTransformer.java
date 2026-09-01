package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class JutsuRuntimeTransformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Jutsu Transformer");
    private static final String TARGET = "net.narutomod.item.ItemJutsu$Base";
    private static final String SHARINGAN_SETTINGS =
            "net.decentstudio.narutoaddon.util.JutsuSettings";
    private static final String JUTSU_ADDON_SETTINGS =
            "net.decentstudio.jutsuaddon.util.JutsuSettings";
    private static final String HOOKS = "net/rebornaddon/jutsu/JutsuRuntimeHooks";

    private static final String EXECUTE_DESC =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;F)Z";
    private static final String POWER_DESC =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;I)F";
    private static final String RIGHT_CLICK_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String targetName = transformedName == null ? name : transformedName;
        try {
            if (TARGET.equals(name) || TARGET.equals(transformedName)) {
                return patchBase(basicClass);
            }
            if (SHARINGAN_SETTINGS.equals(targetName) || JUTSU_ADDON_SETTINGS.equals(targetName)
                    || SHARINGAN_SETTINGS.equals(name) || JUTSU_ADDON_SETTINGS.equals(name)) {
                return patchExternalSettings(basicClass);
            }
            if (isJutsuAddonClass(targetName)) {
                return patchJutsuClass(basicClass);
            }
            return basicClass;
        } catch (Throwable throwable) {
            if (isRelevantClass(name) || isRelevantClass(transformedName)) {
                LOGGER.error("Could not apply jutsu configuration hooks to {}.", targetName, throwable);
            }
            return basicClass;
        }
    }

    private static boolean isRelevantClass(String name) {
        return TARGET.equals(name) || SHARINGAN_SETTINGS.equals(name)
                || JUTSU_ADDON_SETTINGS.equals(name) || isJutsuAddonClass(name);
    }

    private byte[] patchJutsuClass(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("net/narutomod/item/ItemJutsu$Base".equals(node.superName)
                    && "getPower".equals(method.name) && POWER_DESC.equals(method.desc)) {
                decoratePowerReturns(method);
                changed = true;
            }
            changed |= decorateCooldownMessages(method);
        }
        if (!changed) return basicClass;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isJutsuAddonClass(String name) {
        return name != null && (name.startsWith("net.mcreator.ahznbcursemarkaddon.")
                || name.startsWith("net.mcreator.kabutoaddon.")
                || name.startsWith("net.kgtweaks.")
                || name.startsWith("net.decentstudio.jutsuaddon.")
                || name.startsWith("net.decentstudio.narutoaddon.")
                || name.startsWith("net.decentstudio.rinneganaddon.")
                || name.startsWith("com.leolifeless.shinobiaddon.")
                || name.startsWith("net.narutomod."));
    }

    private static boolean decorateCooldownMessages(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/entity/player/EntityPlayer".equals(call.owner)
                    && ("func_146105_b".equals(call.name) || "sendStatusMessage".equals(call.name))
                    && "(Lnet/minecraft/util/text/ITextComponent;Z)V".equals(call.desc)) {
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = HOOKS;
                call.name = "sendStatusMessage";
                call.desc = "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/util/text/ITextComponent;Z)V";
                call.itf = false;
                changed = true;
            }
        }
        return changed;
    }

    private byte[] patchBase(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("executeJutsu".equals(method.name) && EXECUTE_DESC.equals(method.desc)) {
                replaceExecute(method);
                changed = true;
            } else if ("getPower".equals(method.name) && POWER_DESC.equals(method.desc)) {
                decoratePowerReturns(method);
                changed = true;
            } else if (("func_77659_a".equals(method.name) || "onItemRightClick".equals(method.name))
                    && RIGHT_CLICK_DESC.equals(method.desc)) {
                replaceRightClick(method);
                changed = true;
            }
        }
        if (!changed) {
            LOGGER.error("NarutoMod ItemJutsu.Base did not contain the expected jutsu methods; server overrides are inactive.");
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchExternalSettings(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            String property = null;
            if ("getDuration".equals(method.name) && "()I".equals(method.desc)) {
                property = "max-duration";
            } else if ("getCooldown".equals(method.name) && "()I".equals(method.desc)) {
                property = "cooldown";
            } else if ("getDamage".equals(method.name) && "()I".equals(method.desc)) {
                property = "damage";
            }
            if (property != null) {
                decorateIntReturns(method, property);
                changed = true;
            } else if ("getChakraCost".equals(method.name)
                    && "(Lnet/minecraft/entity/EntityLivingBase;)D".equals(method.desc)) {
                decorateChakraReturns(method);
                changed = true;
            }
        }
        if (!changed) {
            LOGGER.warn("Jutsu settings class {} did not contain compatible getters.", node.name);
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void decorateIntReturns(MethodNode method, String property) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.IRETURN) continue;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new org.objectweb.asm.tree.LdcInsnNode(property));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/rebornaddon/jutsu/ExternalJutsuSettings", "intValue",
                    "(ILjava/lang/Object;Ljava/lang/String;)I", false));
            method.instructions.insertBefore(instruction, hook);
        }
        method.maxStack = Math.max(method.maxStack + 2, 3);
    }

    private static void decorateChakraReturns(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.DRETURN) continue;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/rebornaddon/jutsu/ExternalJutsuSettings", "chakraCost",
                    "(DLjava/lang/Object;Lnet/minecraft/entity/EntityLivingBase;)D", false));
            method.instructions.insertBefore(instruction, hook);
        }
        method.maxStack = Math.max(method.maxStack + 2, 4);
    }

    private static void replaceExecute(MethodNode method) {
        clear(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "executeJutsu",
                "(Lnet/narutomod/item/ItemJutsu$Base;Lnet/minecraft/item/ItemStack;"
                        + "Lnet/minecraft/entity/EntityLivingBase;F)Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 4;
        method.maxLocals = 4;
    }

    private static void replaceRightClick(MethodNode method) {
        clear(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "onItemRightClick",
                "(Lnet/narutomod/item/ItemJutsu$Base;Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;)"
                        + "Lnet/minecraft/util/ActionResult;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 4;
        method.maxLocals = 4;
    }

    private static void decoratePowerReturns(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.FRETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
            hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "resolvePower",
                    "(FLnet/narutomod/item/ItemJutsu$Base;Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/entity/EntityLivingBase;I)F", false));
            method.instructions.insertBefore(instruction, hook);
        }
        method.maxStack = Math.max(method.maxStack + 4, 6);
    }

    private static void clear(MethodNode method) {
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
