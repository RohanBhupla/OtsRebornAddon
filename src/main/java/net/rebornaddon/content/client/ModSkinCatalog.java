package net.rebornaddon.content.client;

import com.google.gson.JsonObject;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class ModSkinCatalog {
    private static final int MAX_ENTRIES = 2048;
    private static List<JsonObject> cached;

    private ModSkinCatalog() {
    }

    static synchronized List<JsonObject> values() {
        if (cached == null) cached = scan();
        return new ArrayList<JsonObject>(cached);
    }

    private static List<JsonObject> scan() {
        Map<String, JsonObject> found = new LinkedHashMap<String, JsonObject>();
        for (ModContainer mod : Loader.instance().getModList()) {
            if (mod == null || mod.getSource() == null || !mod.getSource().exists()) continue;
            File source = mod.getSource();
            String sourceName = cleanName(mod.getName(), mod.getModId());
            if (source.isFile()) scanJar(source, sourceName, found);
            else if (source.isDirectory()) scanDirectory(source.toPath(), sourceName, found);
            if (found.size() >= MAX_ENTRIES) break;
        }
        List<JsonObject> result = new ArrayList<JsonObject>(found.values());
        Collections.sort(result, new Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject left, JsonObject right) {
                String leftSource = value(left, "source");
                String rightSource = value(right, "source");
                int sourceCompare = leftSource.compareToIgnoreCase(rightSource);
                return sourceCompare != 0 ? sourceCompare
                        : value(left, "name").compareToIgnoreCase(value(right, "name"));
            }
        });
        return result;
    }

    private static void scanJar(File file, String sourceName, Map<String, JsonObject> found) {
        JarFile jar = null;
        try {
            jar = new JarFile(file);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements() && found.size() < MAX_ENTRIES) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String path = normalize(entry.getName());
                if (!candidatePath(path)) continue;
                InputStream input = jar.getInputStream(entry);
                try {
                    addIfSkin(path, sourceName, input, found);
                } finally {
                    try { input.close(); }
                    catch (IOException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (jar != null) try { jar.close(); }
            catch (IOException ignored) {
            }
        }
    }

    private static void scanDirectory(final Path root, final String sourceName,
                                      final Map<String, JsonObject> found) {
        Stream<Path> stream = null;
        try {
            stream = Files.walk(root);
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (found.size() >= MAX_ENTRIES || !Files.isRegularFile(file)) continue;
                String path = normalize(root.relativize(file).toString());
                if (!candidatePath(path)) continue;
                InputStream input = Files.newInputStream(file);
                try {
                    addIfSkin(path, sourceName, input, found);
                } finally {
                    try { input.close(); }
                    catch (IOException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (stream != null) stream.close();
        }
    }

    private static void addIfSkin(String assetPath, String sourceName, InputStream input,
                                  Map<String, JsonObject> found) {
        BufferedImage image = image(input);
        if (image == null || !playerSkin(image)) return;
        String resource = resourceLocation(assetPath);
        if (resource.isEmpty() || found.containsKey(resource)) return;
        JsonObject entry = new JsonObject();
        entry.addProperty("id", resource);
        entry.addProperty("name", title(assetPath) + " (" + sourceName + ")");
        entry.addProperty("skin", resource);
        entry.addProperty("source", sourceName);
        entry.addProperty("slim", slim(image));
        found.put(resource, entry);
    }

    private static BufferedImage image(InputStream input) {
        try {
            return ImageIO.read(input);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean playerSkin(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 64 || width > 256 || width % 64 != 0) return false;
        int scale = width / 64;
        if (!(height == 32 * scale || height == 64 * scale)) return false;
        if (coverage(image, 8, 8, 16, 16) < 0.70D) return false;
        if (coverage(image, 20, 20, 28, 32) < 0.70D) return false;
        if (coverage(image, 4, 20, 12, 32) < 0.45D) return false;
        return colorVariety(image) >= 8;
    }

    private static double coverage(BufferedImage image, int fromX, int fromY, int toX, int toY) {
        int scale = image.getWidth() / 64;
        int opaque = 0;
        int total = 0;
        for (int y = fromY * scale; y < toY * scale && y < image.getHeight(); y++) {
            for (int x = fromX * scale; x < toX * scale && x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 24) opaque++;
                total++;
            }
        }
        return total == 0 ? 0.0D : opaque / (double) total;
    }

    private static int colorVariety(BufferedImage image) {
        java.util.Set<Integer> colors = new java.util.HashSet<Integer>();
        int step = Math.max(1, image.getWidth() / 64);
        for (int y = 0; y < image.getHeight() && colors.size() < 8; y += step) {
            for (int x = 0; x < image.getWidth() && colors.size() < 8; x += step) {
                int color = image.getRGB(x, y);
                if ((color >>> 24) > 24) colors.add(color & 0x00FFFFFF);
            }
        }
        return colors.size();
    }

    private static boolean slim(BufferedImage image) {
        if (image.getHeight() < image.getWidth()) return false;
        int scale = image.getWidth() / 64;
        int transparent = 0;
        int checked = 0;
        int[] columns = {54, 55};
        for (int column : columns) {
            for (int y = 20; y < 32; y++) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int alpha = image.getRGB(column * scale + sx, y * scale + sy) >>> 24;
                        if (alpha < 16) transparent++;
                        checked++;
                    }
                }
            }
        }
        return checked > 0 && transparent >= checked * 3 / 4;
    }

    private static boolean candidatePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("assets/") || !lower.endsWith(".png")) return false;
        int marker = lower.indexOf("/textures/");
        if (marker < 0) return false;
        String rest = lower.substring(marker + "/textures/".length());
        if (rest.startsWith("blocks/") || rest.startsWith("items/")
                || rest.startsWith("gui/") || rest.startsWith("font/")
                || rest.startsWith("particle/") || rest.startsWith("particles/")
                 || rest.startsWith("models/") || rest.startsWith("model/")) {
            return false;
        }
        String file = rest.substring(rest.lastIndexOf('/') + 1);
        if (contains(file, "armor", "armour", "robe", "cloak", "overlay", "layer",
                "effect", "particle", "icon", "logo", "banner", "wing", "tail",
                "weapon", "sword", "scroll", "mask", "hat", "helmet", "chestplate",
                "leggings", "boots", "eyes", "eye_")) return false;
        if (rest.indexOf('/') < 0) return true;
        return rest.startsWith("entity/") || rest.startsWith("entities/")
                || rest.startsWith("skin/") || rest.startsWith("skins/")
                || rest.startsWith("npc/") || rest.startsWith("npcs/");
    }

    private static boolean contains(String value, String... parts) {
        for (String part : parts) if (value.contains(part)) return true;
        return false;
    }

    private static String resourceLocation(String path) {
        String normalized = normalize(path);
        if (!normalized.startsWith("assets/")) return "";
        int domainStart = "assets/".length();
        int domainEnd = normalized.indexOf('/', domainStart);
        if (domainEnd <= domainStart || domainEnd + 1 >= normalized.length()) return "";
        return normalized.substring(domainStart, domainEnd) + ":"
                + normalized.substring(domainEnd + 1);
    }

    private static String title(String path) {
        String normalized = normalize(path);
        int slash = normalized.lastIndexOf('/');
        String file = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (file.endsWith(".png")) file = file.substring(0, file.length() - 4);
        file = file.replace('_', ' ').replace('-', ' ').trim();
        if (file.isEmpty()) return "Skin";
        StringBuilder result = new StringBuilder();
        for (String part : file.split(" +")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.length() == 0 ? "Skin" : result.toString();
    }

    private static String cleanName(String name, String fallback) {
        String value = name == null || name.trim().isEmpty() ? fallback : name;
        return value == null || value.trim().isEmpty() ? "Mod" : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
