package net.rebornaddon.advancement;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RebornAdvancementResourceTest {
    @Test
    public void rootLabelsCannotRenderAsMissingTranslationKeys() throws Exception {
        List<String> roots = Arrays.asList(
                "ninja", "natures", "kekkei_genkai", "clan", "modes", "store");
        for (String root : roots) {
            String json = resource("assets/rebornaddon/advancements/" + root + ".json");
            assertTrue(root, json.contains("\"text\""));
            assertFalse(root, json.contains("\"translate\""));
        }
    }

    @Test
    public void ninjaRootMatchesNarutoModsTriggerAndRecipeRewards() throws Exception {
        String json = resource("assets/rebornaddon/advancements/ninja.json");
        assertTrue(json.contains("\"narutomod:kunai\""));
        assertTrue(json.contains("\"narutomod:shuriken\""));
        assertFalse("Raw advancement rewards cannot tolerate server-removed recipes",
                json.contains("\"rewards\""));
        assertEquals(Arrays.asList(
                "narutomod:ninja_boots_recipe",
                "narutomod:ninja_pants_recipe_konoha",
                "narutomod:ninja_vest_recipe_konoha",
                "narutomod:ninja_helmet_recipe_konoha",
                "narutomod:explosive_tag_recipe",
                "narutomod:kunai_explosive_recipe"),
                RebornAdvancementService.ninjaRecipeRewardIds());
    }

    private static String resource(String path) throws Exception {
        InputStream input = RebornAdvancementResourceTest.class.getClassLoader()
                .getResourceAsStream(path);
        if (input == null) throw new AssertionError("Missing resource " + path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
