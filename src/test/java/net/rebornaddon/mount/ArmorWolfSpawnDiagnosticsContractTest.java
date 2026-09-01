package net.rebornaddon.mount;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.assertTrue;

public class ArmorWolfSpawnDiagnosticsContractTest {
    @Test
    public void failureReportKeepsEveryRequiredEvidenceCategory() throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(
                "net/rebornaddon/mount/ArmorWolfCompatibilityHandler.class");
        if (input == null) throw new AssertionError("Missing compatibility handler bytecode");
        ClassNode node = new ClassNode();
        try {
            new ClassReader(input).accept(node, 0);
        } finally {
            input.close();
        }
        StringBuilder constants = new StringBuilder();
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode
                        && ((LdcInsnNode) instruction).cst instanceof String) {
                    constants.append(((LdcInsnNode) instruction).cst).append('\n');
                }
            }
        }
        require(constants, "gamerules.doMobSpawning=");
        require(constants, "server.spawnPeacefulMobs=");
        require(constants, "server.spawnHostileMobs=");
        require(constants, "spawn.path=");
        require(constants, "chunkLoadedBefore=");
        require(constants, "chunkLoadedAfter=");
        require(constants, "insideWorldBorderBefore=");
        require(constants, "insideWorldBorderAfter=");
        require(constants, "uuidIndexedToSameEntity=");
        require(constants, "duplicateUuid=");
        require(constants, "serverTrackerIndexed=");
        require(constants, "ownerTrackingEntity=");
        require(constants, "capability.binding=");
        require(constants, "join.checkpoints=");
        require(constants, "join.cancellationCandidates=");
        require(constants, "forge.exactCancellationSource=");
        require(constants, "forge.cancellationChanges=");
        require(constants, "mohist.bukkitSpawnListenerCandidates=");
        require(constants, "bukkit.exactCancellationSource=");
        require(constants, "bukkit.cancellationChanges=");
        require(constants, "removal.source=");
        require(constants, "attempt.evidence=");
    }

    private static void require(StringBuilder constants, String value) {
        assertTrue("Missing diagnostic field " + value, constants.indexOf(value) >= 0);
    }
}
