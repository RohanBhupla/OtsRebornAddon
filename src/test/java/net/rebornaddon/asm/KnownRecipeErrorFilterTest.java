package net.rebornaddon.asm;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnownRecipeErrorFilterTest {
    @Test
    public void filtersDedicatedServerClientModelSuperclassNoise() {
        String message = "Unable to get superclass as resource: "
                + "net/minecraft/client/model/ModelBase (net/minecraft/client/model/ModelBase) "
                + "Do you have a broken installation? It is referenced in "
                + "net/narutomod/entity/EntityTemari$ModelTemari "
                + "(net/narutomod/entity/EntityTemari$ModelTemari)";

        assertTrue(KnownRecipeErrorFilter.isKnownHarmless("TickCentral", message));
    }

    @Test
    public void doesNotHideServerSuperclassFailuresOrOtherLoggers() {
        String serverMessage = "Unable to get superclass as resource: "
                + "net/minecraft/entity/EntityLivingBase (net/minecraft/entity/EntityLivingBase) "
                + "Do you have a broken installation? It is referenced in example/BrokenEntity";
        String clientMessage = "Unable to get superclass as resource: "
                + "net/minecraft/client/model/ModelBase (net/minecraft/client/model/ModelBase) "
                + "Do you have a broken installation? It is referenced in example/ClientModel";

        assertFalse(KnownRecipeErrorFilter.isKnownHarmless("TickCentral", serverMessage));
        assertFalse(KnownRecipeErrorFilter.isKnownHarmless("OtherMod", clientMessage));
        assertFalse(KnownRecipeErrorFilter.isKnownHarmless("", "java.lang.NullPointerException"));
    }
}
