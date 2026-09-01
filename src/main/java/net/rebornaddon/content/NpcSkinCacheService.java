package net.rebornaddon.content;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.rebornaddon.config.RebornAddonConfig;
import net.rebornaddon.content.entity.EntityMissionNpc;
import net.rebornaddon.village.network.RebornAddonNetwork;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NpcSkinCacheService {
    public static final NpcSkinCacheService INSTANCE = new NpcSkinCacheService();
    private static final int MAX_REDIRECTS = 3;
    private static final int MEMORY_ENTRIES = 128;
    private static final long REQUEST_COOLDOWN_MILLIS = 1000L;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "RebornAddon NPC skin cache");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Map<String, byte[]> memory = Collections.synchronizedMap(
            new LinkedHashMap<String, byte[]>(MEMORY_ENTRIES + 1, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MEMORY_ENTRIES;
                }
            });
    private final Map<String, List<UUID>> pending = new HashMap<String, List<UUID>>();
    private final Map<UUID, Long> lastRequest = new HashMap<UUID, Long>();
    private MinecraftServer server;
    private File cacheDirectory;

    private NpcSkinCacheService() {
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        cacheDirectory = new File(server.getDataDirectory(), "rebornaddon/skin-cache");
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
        workers.execute(new Runnable() {
            @Override
            public void run() {
                pruneDiskCache();
            }
        });
    }

    public synchronized void reset() {
        server = null;
        synchronized (pending) {
            pending.clear();
        }
        lastRequest.clear();
        memory.clear();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) return;
        UUID player = event.player.getUniqueID();
        synchronized (this) {
            lastRequest.remove(player);
        }
        synchronized (pending) {
            for (List<UUID> waiters : pending.values()) waiters.remove(player);
        }
    }

    public void request(EntityPlayerMP player, String requestedUrl) {
        if (player == null || requestedUrl == null || requestedUrl.length() > 1024) return;
        String url = requestedUrl.trim();
        if (!isConfigured(url) && !player.canUseCommand(2, "reborncontent")) {
            reject(player, "That NPC skin is not part of the active server catalog.");
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            Long previous = lastRequest.get(player.getUniqueID());
            if (previous != null && now - previous.longValue() < REQUEST_COOLDOWN_MILLIS) return;
            lastRequest.put(player.getUniqueID(), Long.valueOf(now));
        }
        String key = sha256(url);
        byte[] cached = memory.get(key);
        if (cached == null) cached = readCached(key);
        if (cached != null) {
            send(player, url, key, cached);
            return;
        }
        boolean start = false;
        synchronized (pending) {
            List<UUID> waiters = pending.get(key);
            if (waiters == null) {
                waiters = new ArrayList<UUID>();
                pending.put(key, waiters);
                start = true;
            }
            if (!waiters.contains(player.getUniqueID())) waiters.add(player.getUniqueID());
        }
        if (start) download(url, key);
    }

    private void download(final String url, final String key) {
        workers.execute(new Runnable() {
            @Override
            public void run() {
                byte[] bytes = null;
                String failure = "The remote NPC skin could not be loaded.";
                try {
                    bytes = fetch(url);
                    writeCached(key, bytes);
                    memory.put(key, bytes);
                } catch (Exception problem) {
                    failure = problem.getMessage() == null ? failure : problem.getMessage();
                }
                final byte[] result = bytes;
                final String message = failure;
                final MinecraftServer current = server;
                if (current != null) current.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        List<UUID> waiters;
                        synchronized (pending) {
                            waiters = pending.remove(key);
                        }
                        if (waiters == null) return;
                        for (UUID id : waiters) {
                            EntityPlayerMP player = current.getPlayerList().getPlayerByUUID(id);
                            if (player == null) continue;
                            if (result == null) reject(player, message);
                            else send(player, url, key, result);
                        }
                    }
                });
            }
        });
    }

    private static byte[] fetch(String start) throws Exception {
        URI current = validateUri(new URI(start));
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateAddress(current.getHost());
            HttpsURLConnection connection = (HttpsURLConnection) current.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "RebornAddon-NpcSkinCache/1.0");
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.8");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || redirect == MAX_REDIRECTS) throw new IllegalArgumentException("The NPC skin URL redirected too many times.");
                current = validateUri(current.resolve(location));
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IllegalArgumentException("The NPC skin host returned HTTP " + status + ".");
            }
            int declared = connection.getContentLength();
            if (declared > RebornAddonConfig.npcSkinMaximumBytes) {
                connection.disconnect();
                throw new IllegalArgumentException("The NPC skin exceeds the server size limit.");
            }
            InputStream input = connection.getInputStream();
            byte[] encoded;
            try {
                encoded = readBounded(input, RebornAddonConfig.npcSkinMaximumBytes);
            } finally {
                input.close();
                connection.disconnect();
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
            if (image == null || image.getWidth() != 64
                    || image.getHeight() != 64 && image.getHeight() != 32) {
                throw new IllegalArgumentException("NPC skins must be 64x64 or legacy 64x32 PNG images.");
            }
            ByteArrayOutputStream normalized = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", normalized)) throw new IllegalArgumentException("The NPC skin could not be normalized.");
            byte[] result = normalized.toByteArray();
            if (result.length > RebornAddonConfig.npcSkinMaximumBytes) throw new IllegalArgumentException("The normalized NPC skin exceeds the server size limit.");
            return result;
        }
        throw new IllegalArgumentException("The NPC skin URL could not be resolved.");
    }

    private static URI validateUri(URI value) throws Exception {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getPort() != -1 && value.getPort() != 443) {
            throw new IllegalArgumentException("Remote NPC skins must use a standard public HTTPS URL.");
        }
        String host = value.getHost().toLowerCase(Locale.ROOT);
        String[] allowed = RebornAddonConfig.npcSkinAllowedHosts;
        if (allowed != null && allowed.length > 0) {
            boolean match = false;
            for (String entry : allowed) {
                String candidate = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
                if (!candidate.isEmpty() && (host.equals(candidate) || host.endsWith("." + candidate))) {
                    match = true;
                    break;
                }
            }
            if (!match) throw new IllegalArgumentException("That NPC skin host is not allowed by the server.");
        }
        return value;
    }

    private static void validateAddress(String host) throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new IllegalArgumentException("The NPC skin host did not resolve.");
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || privateRange(address)) {
                throw new IllegalArgumentException("The NPC skin host resolves to a private or unsafe address.");
            }
        }
    }

    private static boolean privateRange(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            return first == 0 || first == 10 || first == 127 || first == 169 && second == 254
                    || first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168
                    || first == 100 && second >= 64 && second <= 127;
        }
        return address instanceof Inet6Address && ((bytes[0] & 0xfe) == 0xfc);
    }

    private boolean isConfigured(String url) {
        if (NativeContentService.INSTANCE.isConfiguredSkin(url)) return true;
        MinecraftServer current = server;
        if (current == null || current.worlds == null) return false;
        for (net.minecraft.world.WorldServer world : current.worlds) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityMissionNpc && url.equals(((EntityMissionNpc) entity).getSkin())) return true;
            }
        }
        return false;
    }

    private byte[] readCached(String key) {
        File file = cacheFile(key);
        if (file == null || !file.isFile() || file.length() > RebornAddonConfig.npcSkinMaximumBytes) return null;
        try {
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream input = new FileInputStream(file);
            try {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) return null;
                    offset += read;
                }
            } finally {
                input.close();
            }
            file.setLastModified(System.currentTimeMillis());
            memory.put(key, bytes);
            return bytes;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCached(String key, byte[] bytes) throws Exception {
        File destination = cacheFile(key);
        if (destination == null) return;
        File temporary = new File(cacheDirectory, key + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File cacheFile(String key) {
        return cacheDirectory == null ? null : new File(cacheDirectory, key + ".png");
    }

    private void pruneDiskCache() {
        File directory = cacheDirectory;
        if (directory == null) return;
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) return;
        List<File> values = new ArrayList<File>();
        Collections.addAll(values, files);
        Collections.sort(values, Comparator.comparingLong(File::lastModified));
        long maximum = RebornAddonConfig.npcSkinCacheMegabytes * 1024L * 1024L;
        long total = 0L;
        for (File value : values) total += value.length();
        for (File value : values) {
            if (total <= maximum) break;
            long length = value.length();
            if (value.delete()) total -= length;
        }
    }

    private static byte[] readBounded(InputStream input, int maximum) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 65536));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) throw new IllegalArgumentException("The NPC skin exceeds the server size limit.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void send(EntityPlayerMP player, String url, String key, byte[] bytes) {
        RebornAddonNetwork.sendNpcSkin(player, url, key, bytes);
    }

    private static void reject(EntityPlayerMP player, String message) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED + message), true);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte part : digest) result.append(String.format("%02x", part & 255));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
