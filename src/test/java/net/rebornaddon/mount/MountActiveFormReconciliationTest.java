package net.rebornaddon.mount;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MountActiveFormReconciliationTest {
    @Test
    public void selectedKokuoReplacesAnActiveWolfFormImmediately() throws Exception {
        FakeWrapper wrapper = new FakeWrapper();
        FakeCapability data = new FakeCapability(0.10F);

        MountAdministrationService.applySelectedWrapperState(
                wrapper, data, "narutomod:five_tails", true);

        assertEquals("narutomod:five_tails", wrapper.form);
        assertEquals(0.10F, wrapper.scale, 0.0001F);
        assertEquals("narutomod:five_tails", data.requestedId);
        assertEquals(Boolean.TRUE, data.requestedMount);
    }

    public static final class FakeWrapper {
        private String form;
        private float scale = 1.0F;

        public String getCompanionForm() {
            return form;
        }

        public void setCompanionForm(String form) {
            this.form = form;
        }

        public void setCompanionScale(float scale) {
            this.scale = scale;
        }
    }

    public static final class FakeCapability {
        private final float scale;
        private String requestedId;
        private Boolean requestedMount;

        private FakeCapability(float scale) {
            this.scale = scale;
        }

        public float getEntityScale(String id, boolean mount) {
            requestedId = id;
            requestedMount = Boolean.valueOf(mount);
            return scale;
        }
    }
}
