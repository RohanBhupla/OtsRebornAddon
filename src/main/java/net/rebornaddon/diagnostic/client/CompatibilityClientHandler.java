package net.rebornaddon.diagnostic.client;

import net.minecraft.nbt.NBTTagCompound;
import net.rebornaddon.diagnostic.CompatibilityDoctor;
import net.rebornaddon.village.network.RebornAddonNetwork;

public final class CompatibilityClientHandler {
    private CompatibilityClientHandler() {
    }

    public static void handle(final NBTTagCompound serverManifest) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                NBTTagCompound clientManifest = CompatibilityDoctor.buildManifest();
                RebornAddonNetwork.sendCompatibilityReport(
                        CompatibilityDoctor.compare(serverManifest, clientManifest));
            }
        }, "RebornAddon client compatibility scan");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }
}
