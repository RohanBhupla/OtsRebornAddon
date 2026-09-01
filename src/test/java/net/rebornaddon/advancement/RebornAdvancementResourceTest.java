package net.rebornaddon.advancement;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
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
