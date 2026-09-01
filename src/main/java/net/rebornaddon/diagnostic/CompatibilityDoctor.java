package net.rebornaddon.diagnostic;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.content.NativeContentPluginBridge;
import net.rebornaddon.discord.DiscordPluginBridge;
import net.rebornaddon.exam.ExamPluginBridge;
import net.rebornaddon.jutsu.JutsuPluginBridge;
import net.rebornaddon.mode.ModePluginBridge;
import net.rebornaddon.ranked.RankedPluginBridge;
import net.rebornaddon.store.StorePluginBridge;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CompatibilityDoctor {
    public static final CompatibilityDoctor INSTANCE = new CompatibilityDoctor();
    private static final String[] TRANSFORMER_TARGETS = {
            "net.narutomod.item.ItemJutsu$Base",
            "net.narutomod.item.ItemSenjutsu$RangedItem",
            "net.narutomod.item.ItemBijuCloak$2",
            "net.narutomod.item.ItemNormalBijuCloak$2",
            "net.narutomod.item.ItemKarma$RangedItem",
            "net.minecraft.entity.EntityLivingBase",
            "net.narutomod.procedure.ProcedureParalysisPotionExpires",
            "com.leolifeless.shinobiaddon.ShinobiAddon"
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RebornAddon compatibility scan");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Map<UUID, ClientReport> reports = new HashMap<UUID, ClientReport>();
    private volatile NBTTagCompound cachedManifest;
    private volatile long manifestBuiltAt;
    private MinecraftServer server;

    private CompatibilityDoctor() {
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        rebuildManifest();
    }

    public void reset() {
        server = null;
        synchronized (reports) {
            reports.clear();
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) request((EntityPlayerMP) event.player, false);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        synchronized (reports) {
            reports.remove(event.player.getUniqueID());
        }
    }

    public void request(final EntityPlayerMP player, boolean forceRefresh) {
        if (player == null) return;
        if (forceRefresh || cachedManifest == null
                || System.currentTimeMillis() - manifestBuiltAt > 300000L) rebuildManifest();
        NBTTagCompound manifest = cachedManifest;
        if (manifest != null) {
            RebornAddonNetwork.sendCompatibilityRequest(player, manifest.copy());
            return;
        }
        worker.execute(new Runnable() {
            @Override
            public void run() {
                waitForManifest(player.getUniqueID());
            }
        });
    }

    public void acceptReport(EntityPlayerMP player, NBTTagCompound data) {
        if (player == null || data == null) return;
        ClientReport report = ClientReport.from(data);
        synchronized (reports) {
            reports.put(player.getUniqueID(), report);
        }
    }

    public void sendReport(EntityPlayerMP viewer, EntityPlayerMP target) {
        if (target != null) request(target, false);
    }

    public void sendServerChecks(EntityPlayerMP viewer) {
        // Compatibility data is intentionally silent; it is retained only for internal checks.
    }

    public static NBTTagCompound buildManifest() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList mods = new NBTTagList();
        List<ModContainer> values = new ArrayList<ModContainer>(Loader.instance().getActiveModList());
        Collections.sort(values, Comparator.comparing(ModContainer::getModId));
        for (ModContainer mod : values) {
            String id = mod.getModId();
            if ("minecraft".equals(id) || "mcp".equals(id) || "FML".equals(id) || "forge".equals(id)) continue;
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Id", id);
            row.setString("Version", clean(mod.getVersion(), 128));
            row.setString("Hash", hash(mod.getSource()));
            mods.appendTag(row);
        }
        root.setTag("Mods", mods);
        root.setString("RebornVersion", RebornAddonMod.VERSION);
        root.setLong("CreatedAt", System.currentTimeMillis());
        return root;
    }

    public static NBTTagCompound compare(NBTTagCompound serverManifest, NBTTagCompound clientManifest) {
        Map<String, ModRecord> server = records(serverManifest);
        Map<String, ModRecord> client = records(clientManifest);
        NBTTagList issues = new NBTTagList();
        for (ModRecord expected : server.values()) {
            ModRecord actual = client.get(expected.id);
            if (actual == null) {
                issues.appendTag(new net.minecraft.nbt.NBTTagString("Missing mod " + expected.id
                        + " (server " + expected.version + ")"));
            } else if (!expected.version.equals(actual.version)) {
                issues.appendTag(new net.minecraft.nbt.NBTTagString("Version mismatch " + expected.id
                        + ": server " + expected.version + ", client " + actual.version));
            } else if (!expected.hash.isEmpty() && !actual.hash.isEmpty()
                    && !expected.hash.equals(actual.hash)) {
                issues.appendTag(new net.minecraft.nbt.NBTTagString("Jar mismatch " + expected.id
                        + " " + expected.version + " (same version, different file hash)"));
            }
        }
        NBTTagCompound report = new NBTTagCompound();
        report.setBoolean("Ok", issues.tagCount() == 0);
        report.setTag("Issues", issues);
        report.setInteger("ServerMods", server.size());
        report.setInteger("ClientMods", client.size());
        return report;
    }

    private void rebuildManifest() {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                cachedManifest = buildManifest();
                manifestBuiltAt = System.currentTimeMillis();
            }
        });
    }

    private void waitForManifest(UUID playerId) {
        for (int attempt = 0; attempt < 100 && cachedManifest == null; attempt++) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        final NBTTagCompound manifest = cachedManifest;
        final MinecraftServer current = server;
        if (manifest == null || current == null) return;
        current.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                EntityPlayerMP online = current.getPlayerList().getPlayerByUUID(playerId);
                if (online != null) RebornAddonNetwork.sendCompatibilityRequest(online, manifest.copy());
            }
        });
    }

    private static List<String> missingTransformerTargets() {
        List<String> missing = new ArrayList<String>();
        ClassLoader loader = CompatibilityDoctor.class.getClassLoader();
        for (String target : TRANSFORMER_TARGETS) {
            if (loader.getResource(target.replace('.', '/') + ".class") == null) missing.add(target);
        }
        return missing;
    }

    private static Map<String, ModRecord> records(NBTTagCompound manifest) {
        Map<String, ModRecord> result = new HashMap<String, ModRecord>();
        if (manifest == null) return result;
        NBTTagList values = manifest.getTagList("Mods", 10);
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound row = values.getCompoundTagAt(i);
            ModRecord record = new ModRecord(row.getString("Id"), row.getString("Version"), row.getString("Hash"));
            result.put(record.id, record);
        }
        return result;
    }

    private static String hash(File source) {
        if (source == null || !source.isFile()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream input = new FileInputStream(source);
            try {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            } finally {
                input.close();
            }
            StringBuilder value = new StringBuilder();
            for (byte part : digest.digest()) value.append(String.format("%02x", part & 0xff));
            return value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clean(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static final class ModRecord {
        private final String id;
        private final String version;
        private final String hash;

        private ModRecord(String id, String version, String hash) {
            this.id = id;
            this.version = version;
            this.hash = hash;
        }
    }

    private static final class ClientReport {
        private final boolean ok;
        private final List<String> issues;

        private ClientReport(boolean ok, List<String> issues) {
            this.ok = ok;
            this.issues = issues;
        }

        private static ClientReport from(NBTTagCompound data) {
            List<String> issues = new ArrayList<String>();
            NBTTagList values = data.getTagList("Issues", 8);
            for (int i = 0; i < values.tagCount(); i++) issues.add(values.getStringTagAt(i));
            return new ClientReport(data.getBoolean("Ok"), issues);
        }
    }
}
