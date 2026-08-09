package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class NarutoModeCompatibilityTransformer implements IClassTransformer {
    private static final String ARMOR_TEXTURE_DESC =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/Entity;"
                    + "Lnet/minecraft/inventory/EntityEquipmentSlot;Ljava/lang/String;)Ljava/lang/String;";
    private static final String ARMOR_MODEL_DESC =
            "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/inventory/EntityEquipmentSlot;Lnet/minecraft/client/model/ModelBiped;)"
                    + "Lnet/minecraft/client/model/ModelBiped;";
    private static final String TEXTURE_HELPER = "net/rebornaddon/compat/BijuCloakCompatibility";

    private static final Set<String> PROGRESSION_CLOAKS = new HashSet<String>(Arrays.asList(
            "net.narutomod.item.ItemBijuCloak$1",
            "net.narutomod.item.ItemBijuCloak$2",
            "net.narutomod.item.ItemBijuCloak$3"
    ));
    private static final Set<String> NORMAL_CLOAKS = new HashSet<String>(Arrays.asList(
            "net.narutomod.item.ItemNormalBijuCloak$1",
            "net.narutomod.item.ItemNormalBijuCloak$2",
            "net.narutomod.item.ItemNormalBijuCloak$3"
    ));
    private static final Set<String> TAIL_ROOT_CLASSES = new HashSet<String>(Arrays.asList(
            "net.narutomod.item.ItemBijuCloak$2",
            "net.narutomod.item.ItemNormalBijuCloak$2"
    ));

    private static final String[] SHARED_FORM_UUIDS = new String[] {
            "c3ee1250-8b80-4668-b58a-33af5ea73ee6",
            "6d6202e1-9aac-4c3d-ba0c-6684bdd58868",
            "33b7fa14-828a-4964-b014-b61863526589",
            "74f3ab51-a73f-45e3-a4c4-aae6974b6414",
            "70e0acc2-cf75-4bbd-a21a-753088324a59"
    };
    private static final String[] KARMA_UUIDS = new String[] {
            "57121fd3-ea4f-442e-b470-f073c85a4592",
            "8db6d83e-ebeb-4d5b-852a-a25b995899c5",
            "6f2c6fdb-6296-4da7-8949-b9d57df40b73",
            "2b0b48c8-422e-4240-8677-475e7a6480e3",
            "dc1df0d3-3007-4610-9e0c-26d68fde8e85"
    };
    private static final String[] CURSE_MARK_UUIDS = new String[] {
            "1e24191e-f024-4d51-8fb3-619f550f1c16",
            "3e7818d5-6115-43ec-a51b-c67e70ef3163",
            "cc916bac-c68d-48e1-9c46-743ec08dd0bb",
            "29f0dbb3-3572-46d4-a82c-21bfb2c02171",
            "63122ae6-0743-488b-ae90-8a6d06314804"
    };
    private static final String[] BYAKUGOU_UUIDS = new String[] {
            "62809ef6-bf17-4a3b-8a2f-d876cf748012",
            "30bb77e1-862f-4ce0-9c00-29b52ef4322c",
            "be25f817-ac3f-4fd8-bbc2-e76d1283c167",
            "2ee7f004-5452-4dc4-a92f-9244121b03d6",
            "a59109da-8a4a-4001-ad9f-6f15df4d179b"
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        String className = transformedName == null ? name : transformedName;
        String[] formUuids = formUuids(className);
        if (!PROGRESSION_CLOAKS.contains(className)
                && !NORMAL_CLOAKS.contains(className)
                && formUuids == null) {
            return basicClass;
        }

        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, 0);
            boolean changed = false;

            for (MethodNode method : classNode.methods) {
                if ((PROGRESSION_CLOAKS.contains(className) || NORMAL_CLOAKS.contains(className))
                        && "getArmorTexture".equals(method.name)
                        && ARMOR_TEXTURE_DESC.equals(method.desc)) {
                    String helperMethod = PROGRESSION_CLOAKS.contains(className)
                            ? "progressionTexture"
                            : "normalTexture";
                    replaceTextureMethod(method, helperMethod);
                    changed = true;
                }

                if (TAIL_ROOT_CLASSES.contains(className)
                        && "getArmorModel".equals(method.name)
                        && ARMOR_MODEL_DESC.equals(method.desc)) {
                    changed |= keepTailRootVisible(method);
                }

                if (formUuids != null) {
                    changed |= replaceSharedUuids(method, formUuids);
                }
            }

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

    private void replaceTextureMethod(MethodNode method, String helperMethod) {
        clear(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                TEXTURE_HELPER,
                helperMethod,
                "(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
                false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = argumentSlots(method);
    }

    private boolean keepTailRootVisible(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)) {
                continue;
            }

            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.getOpcode() == Opcodes.PUTFIELD
                    && "net/minecraft/client/model/ModelRenderer".equals(field.owner)
                    && ("field_78806_j".equals(field.name) || "showModel".equals(field.name))
                    && "Z".equals(field.desc)) {
                method.instructions.insertBefore(field, new InsnNode(Opcodes.POP));
                method.instructions.insertBefore(field, new InsnNode(Opcodes.ICONST_1));
                method.maxStack = Math.max(method.maxStack, 2);
                return true;
            }
        }
        return false;
    }

    private boolean replaceSharedUuids(MethodNode method, String[] replacements) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof LdcInsnNode)) {
                continue;
            }

            LdcInsnNode constant = (LdcInsnNode) instruction;
            if (!(constant.cst instanceof String)) {
                continue;
            }

            for (int i = 0; i < SHARED_FORM_UUIDS.length; i++) {
                if (SHARED_FORM_UUIDS[i].equals(constant.cst)) {
                    constant.cst = replacements[i];
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private String[] formUuids(String className) {
        if ("net.narutomod.item.ItemKarma$RangedItem".equals(className)) {
            return KARMA_UUIDS;
        }
        if ("net.mcreator.ahznbcursemarkaddon.item.ItemCursemarkMode$RangedItem".equals(className)) {
            return CURSE_MARK_UUIDS;
        }
        if ("net.mcreator.ahznbcursemarkaddon.item.ItemByakugouJutsu$RangedItem".equals(className)) {
            return BYAKUGOU_UUIDS;
        }
        return null;
    }

    private int argumentSlots(MethodNode method) {
        int slots = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type type : Type.getArgumentTypes(method.desc)) {
            slots += type.getSize();
        }
        return slots;
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
