package net.rebornaddon.content.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.rebornaddon.content.NpcSkinCacheService;
import net.rebornaddon.content.network.NpcSkinDataMessage;
import net.rebornaddon.village.network.RebornAddonNetwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class ClientNpcSkinCache {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final long REQUEST_RETRY_MILLIS = 30000L;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
    private static final Map<String, Long> REQUESTED = new HashMap<String, Long>();
    private static final Map<String, Transfer> TRANSFERS = new HashMap<String, Transfer>();

    private ClientNpcSkinCache() {
    }

    public static ResourceLocation texture(String url) {
        if (url == null || url.isEmpty()) return DefaultPlayerSkin.getDefaultSkinLegacy();
        ResourceLocation loaded = TEXTURES.get(url);
        if (loaded != null) return loaded;
        String hash = NpcSkinCacheService.sha256(url);
        File file = cacheFile(hash);
        if (file.isFile() && file.length() <= MAX_BYTES) {
            try {
                loaded = install(url, hash, Files.readAllBytes(file.toPath()));
                if (loaded != null) return loaded;
            } catch (Exception ignored) {
                file.delete();
            }
        }
        long now = System.currentTimeMillis();
        Long requested = REQUESTED.get(url);
        if (requested == null || now - requested.longValue() >= REQUEST_RETRY_MILLIS) {
            REQUESTED.put(url, Long.valueOf(now));
            RebornAddonNetwork.requestNpcSkin(url);
        }
        return DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    public static void receive(NpcSkinDataMessage message) {
        if (message == null || !NpcSkinCacheService.sha256(message.url()).equals(message.hash())) return;
        String key = message.transferId() + ":" + message.hash();
        Transfer transfer = TRANSFERS.get(key);
        if (transfer == null) {
            transfer = new Transfer(message.url(), message.hash(), message.parts());
            TRANSFERS.put(key, transfer);
        }
        if (!transfer.add(message.part(), message.payload())) return;
        TRANSFERS.remove(key);
        byte[] bytes = transfer.join();
        ResourceLocation location = install(transfer.url, transfer.hash, bytes);
        if (location != null) {
            REQUESTED.remove(transfer.url);
            write(transfer.hash, bytes);
        }
    }

    public static void reset() {
        TEXTURES.clear();
        REQUESTED.clear();
        TRANSFERS.clear();
    }

    private static ResourceLocation install(String url, String hash, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) return null;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null || source.getWidth() != 64
                    || source.getHeight() != 64 && source.getHeight() != 32) return null;
            BufferedImage normalized = new ImageBufferDownload().parseUserSkin(source);
            ResourceLocation location = new ResourceLocation("rebornaddon", "npc_skin/server_" + hash);
            Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(normalized));
            TEXTURES.put(url, location);
            return location;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void write(String hash, byte[] bytes) {
        File destination = cacheFile(hash);
        File parent = destination.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        File temporary = new File(parent, hash + ".tmp");
        try {
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
        } catch (Exception ignored) {
        }
    }

    private static File cacheFile(String hash) {
        return new File(Minecraft.getMinecraft().mcDataDir,
                "rebornaddon-cache/npc-skins/" + hash + ".png");
    }

    private static final class Transfer {
        private final String url;
        private final String hash;
        private final byte[][] parts;
        private int received;
        private int total;

        private Transfer(String url, String hash, int count) {
            this.url = url;
            this.hash = hash;
            this.parts = new byte[count][];
        }

        private boolean add(int index, byte[] bytes) {
            if (index < 0 || index >= parts.length || parts[index] != null || bytes == null
                    || total + bytes.length > MAX_BYTES) return false;
            parts[index] = bytes;
            total += bytes.length;
            received++;
            return received == parts.length;
        }

        private byte[] join() {
            byte[] result = new byte[total];
            int offset = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, result, offset, part.length);
                offset += part.length;
            }
            return result;
        }
    }
}
