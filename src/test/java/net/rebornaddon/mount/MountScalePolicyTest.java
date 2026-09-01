package net.rebornaddon.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MountScalePolicyTest {
    @Test
    public void oversizedFormsHaveSeparateCompanionAndMountDefaults() {
        MountScalePolicy policy = new MountScalePolicy();
        assertEquals(0.055F, policy.scale("narutomod:one_tail", false), 0.0001F);
        assertEquals(0.11F, policy.scale("narutomod:one_tail", true), 0.0001F);
        assertEquals(0.021F, policy.scale("narutomod:ten_tails", false), 0.0001F);
        assertEquals(0.042F, policy.scale("narutomod:ten_tails", true), 0.0001F);
        assertEquals(0.11F, policy.scale("exampleaddon:one_tail", true), 0.0001F);
        assertEquals(1.0F, policy.scale("narutomod:toad", false), 0.0001F);
    }

    @Test
    public void hostedPolicyOverridesEachRoleIndependentlyAndPersistsInMemory() {
        MountScalePolicy policy = new MountScalePolicy();
        JsonObject root = new JsonObject();
        JsonArray policies = new JsonArray();
        JsonObject shukaku = new JsonObject();
        shukaku.addProperty("entityId", "narutomod:one_tail");
        shukaku.addProperty("companionScale", 0.075F);
        shukaku.addProperty("mountScale", 0.3F);
        policies.add(shukaku);
        root.add("scalePolicies", policies);

        assertTrue(policy.accept(root));
        assertFalse(policy.accept(root));
        assertEquals(0.075F, policy.scale("narutomod:one_tail", false), 0.0001F);
        assertEquals(0.3F, policy.scale("narutomod:one_tail", true), 0.0001F);

        policy.set("narutomod:one_tail", false, 0.08F);
        assertEquals(0.08F, policy.scale("narutomod:one_tail", false), 0.0001F);
        assertEquals(0.3F, policy.scale("narutomod:one_tail", true), 0.0001F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidScaleIsRejected() {
        MountScalePolicy.checked(0.0F);
    }
}
