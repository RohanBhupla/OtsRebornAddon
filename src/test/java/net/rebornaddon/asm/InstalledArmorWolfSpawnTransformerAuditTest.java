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

import static org.junit.Assert.assertTrue;

public class InstalledArmorWolfSpawnTransformerAuditTest {
    @Test
    public void patchesInstalledServerWrapperConstructor() throws Exception {
        String configured = System.getenv("REBORNADDON_TEST_ARMOR_WOLF_JAR");
        Assume.assumeTrue(configured != null && !configured.trim().isEmpty());
        File file = new File(configured);
        Assume.assumeTrue(file.isFile());

        byte[] original;
        byte[] handler;
        ZipFile jar = new ZipFile(file);
        try {
            ZipEntry entry = jar.getEntry("com/armourwolfmod/entity/EntitySkinWolf.class");
            Assume.assumeTrue(entry != null);
            original = read(jar.getInputStream(entry));
            ZipEntry handlerEntry = jar.getEntry(
                    "com/armourwolfmod/event/CompanionKeybindHandler.class");
            Assume.assumeTrue(handlerEntry != null);
            handler = read(jar.getInputStream(handlerEntry));
        } finally {
            jar.close();
        }

        byte[] transformed = new ArmorWolfSpawnTransformer().transform(
                "com.armourwolfmod.entity.EntitySkinWolf",
                "com.armourwolfmod.entity.EntitySkinWolf", original);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int constructors = 0;
        int patched = 0;
        int ownerGuards = 0;
        int removalHooks = 0;
        for (MethodNode method : node.methods) {
            if ("<init>".equals(method.name)) {
                constructors++;
                if (hasHook(method, "prepareExplicitSpawn")) patched++;
            }
            if (hasHook(method, "handleUnresolvedOwner")) ownerGuards++;
            if (hasHook(method, "recordRemoval")) removalHooks++;
        }
        assertTrue("Armor Wolf has no constructors", constructors > 0);
        assertTrue("Not every Armor Wolf constructor was prepared", patched == constructors);
        assertTrue("Armor Wolf owner self-removal was not guarded", ownerGuards > 0);
        assertTrue("Armor Wolf removal diagnostics were not installed", removalHooks > 0);

        byte[] transformedHandler = new ArmorWolfSpawnTransformer().transform(
                "com.armourwolfmod.event.CompanionKeybindHandler",
                "com.armourwolfmod.event.CompanionKeybindHandler", handler);
        ClassNode handlerNode = new ClassNode();
        new ClassReader(transformedHandler).accept(handlerNode, 0);
        int spawnHooks = 0;
        for (MethodNode method : handlerNode.methods) {
            if (hasHook(method, "spawnExplicit")) spawnHooks++;
        }
        assertTrue("Armor Wolf spawn results are still discarded", spawnHooks > 0);
    }

    private static boolean hasHook(MethodNode method, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("net/rebornaddon/mount/ArmorWolfCompatibilityHandler".equals(call.owner)
                    && name.equals(call.name)) return true;
        }
        return false;
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
