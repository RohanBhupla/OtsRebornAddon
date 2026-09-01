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
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Optional audit for the actual installed addon classes used by the testing instance. */
public class InstalledAdvancementTransformerAuditTest {
    @Test
    public void rewritesEveryInstalledLegacyAdvancementCall() throws Exception {
        String configured = System.getenv("REBORNADDON_TEST_MODS_DIR");
        Assume.assumeTrue(configured != null && !configured.trim().isEmpty());
        File directory = new File(configured);
        Assume.assumeTrue(directory.isDirectory());

        AdvancementSafetyTransformer transformer = new AdvancementSafetyTransformer();
        int classes = 0;
        int calls = 0;
        File[] files = directory.listFiles();
        if (files == null) files = new File[0];
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar")) continue;
            ZipFile jar;
            try {
                jar = new ZipFile(file);
            } catch (Exception ignored) {
                continue;
            }
            try {
                Enumeration<? extends ZipEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                    byte[] original = read(jar.getInputStream(entry));
                    ClassNode before;
                    try {
                        before = node(original);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    String className = before.name.replace('/', '.');
                    if (className.startsWith("net.minecraft.")
                            || className.startsWith("net.rebornaddon.")) continue;
                    int expected = legacyCalls(before);
                    if (expected == 0) continue;

                    ClassNode after = node(transformer.transform(className, className, original));
                    assertEquals(file.getName() + "!" + entry.getName(),
                            expected, compatibilityCalls(after));
                    classes++;
                    calls += expected;
                }
            } finally {
                jar.close();
            }
        }
        assertTrue("No installed advancement consumers were audited", classes > 0);
        assertTrue("No installed advancement calls were audited", calls > 0);
    }

    private static int legacyCalls(ClassNode node) {
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("net/minecraft/advancements/PlayerAdvancements".equals(call.owner)
                        && isSupported(call.name, call.desc)) count++;
            }
        }
        return count;
    }

    private static int compatibilityCalls(ClassNode node) {
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode
                        && "net/rebornaddon/compat/NarutoAddonAdvancementCompatibility"
                        .equals(((MethodInsnNode) instruction).owner)) count++;
            }
        }
        return count;
    }

    private static boolean isSupported(String name, String descriptor) {
        if (("getProgress".equals(name) || "func_192747_a".equals(name))
                && "(Lnet/minecraft/advancements/Advancement;)"
                .concat("Lnet/minecraft/advancements/AdvancementProgress;").equals(descriptor)) {
            return true;
        }
        return ("grantCriterion".equals(name) || "func_192750_a".equals(name)
                || "revokeCriterion".equals(name) || "func_192744_b".equals(name))
                && "(Lnet/minecraft/advancements/Advancement;Ljava/lang/String;)Z".equals(descriptor);
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] read(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
