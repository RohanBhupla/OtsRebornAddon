package net.rebornaddon.asm;

import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;

public class InstalledExplosiveClaySafetyTransformerAuditTest {
    @Test
    public void patchesTheInstalledNarutoModExplosiveClayImplementation() throws Exception {
        String configured = System.getenv("REBORNADDON_TEST_NARUTO_JAR");
        Assume.assumeTrue(configured != null && !configured.trim().isEmpty());
        File file = new File(configured);
        Assume.assumeTrue(file.isFile());

        byte[] original;
        try (ZipFile jar = new ZipFile(file)) {
            ZipEntry entry = jar.getEntry(
                    "net/narutomod/item/ItemBakuton$ExplosiveClay$Jutsu.class");
            Assume.assumeTrue(entry != null);
            original = read(jar.getInputStream(entry));
        }

        byte[] transformed = new ExplosiveClaySafetyTransformer().transform(
                ExplosiveClaySafetyTransformer.TARGET,
                ExplosiveClaySafetyTransformer.TARGET,
                original);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int hooks = 0;
        for (MethodNode method : node.methods) {
            if (!ExplosiveClaySafetyTransformer.METHOD.equals(method.name)
                    || !ExplosiveClaySafetyTransformer.DESCRIPTOR.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (ExplosiveClaySafetyTransformer.HOOK.equals(call.owner)
                        && "normalizeExplosiveClayStack".equals(call.name)) {
                    hooks++;
                }
            }
        }
        assertEquals(1, hooks);
    }

    private static byte[] read(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
