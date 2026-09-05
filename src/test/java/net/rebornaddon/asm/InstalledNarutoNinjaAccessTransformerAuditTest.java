package net.rebornaddon.asm;

import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;

public class InstalledNarutoNinjaAccessTransformerAuditTest {
    @Test
    public void patchesEveryInstalledLevelGateButPreservesTheExperiencePacket() throws Exception {
        String configured = System.getenv("REBORNADDON_TEST_NARUTO_JAR");
        Assume.assumeTrue(configured != null && !configured.trim().isEmpty());
        File file = new File(configured);
        Assume.assumeTrue(file.isFile());

        assertTransformed(file, NarutoModeCompatibilityTransformer.PLAYER_NINJA_SKILL_TICK,
                2, 0);
        assertTransformed(file, NarutoModeCompatibilityTransformer.CHAKRA_PLAYER_HOOK,
                2, 1);
    }

    private static void assertTransformed(File file, String className,
                                          int expectedHooks, int expectedRawReads) throws Exception {
        byte[] original;
        try (ZipFile jar = new ZipFile(file)) {
            ZipEntry entry = jar.getEntry(className.replace('.', '/') + ".class");
            Assume.assumeTrue(entry != null);
            original = read(jar.getInputStream(entry));
        }
        byte[] transformed = new NarutoModeCompatibilityTransformer().transform(
                className, className, original);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int hooks = 0;
        int rawReads = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && NarutoModeCompatibilityTransformer.NINJA_ACCESS_HELPER
                            .equals(call.owner)
                            && "ninjaAccessLevel".equals(call.name)) hooks++;
                } else if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (field.getOpcode() == Opcodes.GETFIELD
                            && "net/minecraft/entity/player/EntityPlayer".equals(field.owner)
                            && "field_71068_ca".equals(field.name)) rawReads++;
                }
            }
        }
        assertEquals(className + " Ninja access hooks", expectedHooks, hooks);
        assertEquals(className + " real XP reads", expectedRawReads, rawReads);
    }

    private static byte[] read(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}
