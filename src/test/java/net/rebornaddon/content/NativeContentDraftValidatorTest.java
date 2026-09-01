package net.rebornaddon.content;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeContentDraftValidatorTest {
    @Test
    public void acceptsValidTemplateDraft() {
        JsonObject record = new JsonObject();
        record.addProperty("id", "onoki_dust_boss");
        record.addProperty("name", "Onoki");
        record.addProperty("skin", "https://skins.example.net/onoki.png");
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "templates");
        payload.add("record", record);
        assertTrue(NativeContentDraftValidator.validateUpsert(payload.toString()).isEmpty());
    }

    @Test
    public void rejectsInsecureRemoteSkinAndInvalidId() {
        JsonObject record = new JsonObject();
        record.addProperty("id", "Bad ID");
        record.addProperty("skin", "http://localhost/skin.png");
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "templates");
        payload.add("record", record);
        assertFalse(NativeContentDraftValidator.validateUpsert(payload.toString()).isEmpty());
    }

    @Test
    public void rejectsUnboundedActivationRadius() {
        JsonObject record = new JsonObject();
        record.addProperty("id", "boss_spawn");
        record.addProperty("activationRadius", 1000000);
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "bosses");
        payload.add("record", record);
        assertFalse(NativeContentDraftValidator.validateUpsert(payload.toString()).isEmpty());
    }
}
