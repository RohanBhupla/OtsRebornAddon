package net.rebornaddon.armor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OtsutsukiArmorResourcesTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/rebornaddon");
    private static final List<String> SETS = Arrays.asList(
            "hagoromo", "hamura", "isshiki", "kaguya", "kinshiki",
            "momoshiki", "shibai", "toneri", "urashiki");
    private static final List<String> PIECES = Arrays.asList(
            "helmet", "chestplate", "leggings", "boots");

    @Test
    public void everySetHasFourItemsAndTwoWearableModels() throws IOException {
        assertEquals(9, OtsutsukiArmorSet.values().length);
        String language = read(ASSETS.resolve("lang/en_us.lang"));
        for (String armorSet : SETS) {
            for (String piece : PIECES) {
                String id = armorSet + "_" + piece;
                assertTrue(Files.isRegularFile(ASSETS.resolve("textures/items/" + id + ".png")));
                assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json")));
                assertTrue(language.contains("item.rebornaddon." + id + ".name="));
            }
            assertModel(armorSet, "main");
            assertModel(armorSet, "leggings");
        }
    }

    @Test
    public void roundedFeaturesRemainIdentifiableInCompactGeometry() throws IOException {
        int orbCount = 0;
        int haloCount = 0;
        for (String armorSet : SETS) {
            JsonObject model = parse(ASSETS.resolve(
                    "armor_models/otsutsuki/" + armorSet + "_main.json"));
            for (JsonElement entry : model.getAsJsonArray("elements")) {
                JsonObject element = entry.getAsJsonObject();
                assertVector(element.getAsJsonArray("origin"));
                assertVector(element.getAsJsonArray("rotation"));
                String name = element.get("name").getAsString();
                if (name.startsWith("Orb")) orbCount++;
                if (name.equals("Halo")) haloCount++;
            }
            for (JsonElement entry : model.getAsJsonArray("groups")) {
                JsonObject group = entry.getAsJsonObject();
                assertVector(group.getAsJsonArray("origin"));
                assertVector(group.getAsJsonArray("rotation"));
            }
        }
        assertEquals(40, orbCount);
        assertEquals(1, haloCount);
    }

    @Test
    public void minecraftGsonCanDeserializeEveryRuntimeModel() throws Exception {
        Class<?> projectData = Class.forName(
                "net.rebornaddon.armor.client.OtsutsukiArmorModel$ProjectData");
        Gson gson = new Gson();
        Path directory = ASSETS.resolve("armor_models/otsutsuki");
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                assertTrue(gson.fromJson(read(file), projectData) != null);
            }
        }
    }

    @Test
    public void runtimeGeometryContainsNoWorkstationPathsOrProjectHistory() throws IOException {
        Path directory = ASSETS.resolve("armor_models/otsutsuki");
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String json = read(file);
                assertFalse(json.contains("C:\\"));
                assertFalse(json.contains("D:\\"));
                assertFalse(json.contains("backup"));
                assertFalse(json.contains("history"));
                assertFalse(json.contains("data:image"));
            }
        }
    }

    @Test
    public void kinshikiHaloUsesTheVisibleTextureFace() throws IOException {
        JsonObject model = parse(ASSETS.resolve("armor_models/otsutsuki/kinshiki_main.json"));
        JsonObject halo = null;
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            if ("Halo".equals(element.get("name").getAsString())) {
                halo = element;
                break;
            }
        }
        assertTrue(halo != null);
        JsonObject faces = halo.getAsJsonObject("faces");
        JsonArray north = faces.getAsJsonObject("north").getAsJsonArray("uv");
        JsonArray south = faces.getAsJsonObject("south").getAsJsonArray("uv");
        BufferedImage texture = ImageIO.read(ASSETS.resolve(
                "textures/models/armor/otsutsuki/kinshiki_main.png").toFile());
        assertTrue(opaquePixels(texture, south) > opaquePixels(texture, north) * 4);
        assertEquals(0xFFF44066, texture.getRGB(47, 32));
        assertEquals(0xFFF97596, texture.getRGB(49, 32));
        assertEquals(0xFFFEA9C6, texture.getRGB(48, 33));
        assertEquals(0xFFFFBDD3, texture.getRGB(49, 33));
    }

    @Test
    public void everyOrbUsesTheAuthoredOpaqueCrimsonPalette() throws IOException {
        Set<Integer> expected = new HashSet<>(Arrays.asList(
                0xFF910E1C, 0xFFA01116, 0xFFC21A1A, 0xFFD54424));
        int orbCount = 0;
        for (String armorSet : SETS) {
            JsonObject model = parse(ASSETS.resolve(
                    "armor_models/otsutsuki/" + armorSet + "_main.json"));
            BufferedImage texture = ImageIO.read(ASSETS.resolve(
                    "textures/models/armor/otsutsuki/" + armorSet + "_main.png").toFile());
            for (JsonElement entry : model.getAsJsonArray("elements")) {
                JsonObject element = entry.getAsJsonObject();
                if (!element.get("name").getAsString().startsWith("Orb")) {
                    continue;
                }
                orbCount++;
                Set<Integer> colors = new HashSet<>();
                for (Map.Entry<String, JsonElement> faceEntry
                        : element.getAsJsonObject("faces").entrySet()) {
                    JsonArray uv = faceEntry.getValue().getAsJsonObject().getAsJsonArray("uv");
                    int minX = Math.min(uv.get(0).getAsInt(), uv.get(2).getAsInt());
                    int maxX = Math.max(uv.get(0).getAsInt(), uv.get(2).getAsInt());
                    int minY = Math.min(uv.get(1).getAsInt(), uv.get(3).getAsInt());
                    int maxY = Math.max(uv.get(1).getAsInt(), uv.get(3).getAsInt());
                    for (int y = minY; y < maxY; y++) {
                        for (int x = minX; x < maxX; x++) {
                            int color = texture.getRGB(x, y);
                            assertEquals(0xFF, color >>> 24);
                            colors.add(color);
                        }
                    }
                }
                assertEquals(expected, colors);
            }
        }
        assertEquals(40, orbCount);
    }

    private static void assertModel(String armorSet, String variant) throws IOException {
        Path geometry = ASSETS.resolve(
                "armor_models/otsutsuki/" + armorSet + "_" + variant + ".json");
        Path texture = ASSETS.resolve(
                "textures/models/armor/otsutsuki/" + armorSet + "_" + variant + ".png");
        assertTrue(Files.isRegularFile(geometry));
        assertTrue(Files.isRegularFile(texture));
        JsonObject model = parse(geometry);
        assertTrue(model.getAsJsonArray("elements").size() > 0);
        assertTrue(model.getAsJsonArray("groups").size() > 0);
        assertTrue(model.getAsJsonArray("outliner").size() > 0);
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            assertVector(element.getAsJsonArray("origin"));
            assertVector(element.getAsJsonArray("rotation"));
            assertEquals(2, element.getAsJsonArray("uvOffset").size());
            assertTrue(element.has("mirror"));
        }
        for (JsonElement entry : model.getAsJsonArray("groups")) {
            JsonObject group = entry.getAsJsonObject();
            assertVector(group.getAsJsonArray("origin"));
            assertVector(group.getAsJsonArray("rotation"));
        }
    }

    private static JsonObject parse(Path path) throws IOException {
        return new JsonParser().parse(read(path)).getAsJsonObject();
    }

    private static void assertVector(JsonArray vector) {
        assertEquals(3, vector.size());
        for (JsonElement coordinate : vector) {
            assertFalse(coordinate.isJsonNull());
            coordinate.getAsFloat();
        }
    }

    private static int opaquePixels(BufferedImage image, JsonArray uv) {
        int minX = Math.max(0, Math.min(uv.get(0).getAsInt(), uv.get(2).getAsInt()));
        int maxX = Math.min(image.getWidth() - 1,
                Math.max(uv.get(0).getAsInt(), uv.get(2).getAsInt()));
        int minY = Math.max(0, Math.min(uv.get(1).getAsInt(), uv.get(3).getAsInt()));
        int maxY = Math.min(image.getHeight() - 1,
                Math.max(uv.get(1).getAsInt(), uv.get(3).getAsInt()));
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
