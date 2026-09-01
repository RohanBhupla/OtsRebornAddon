package net.rebornaddon.exam.client;

import net.rebornaddon.exam.network.ExamSyncMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClientExamDataTest {
    @Before
    public void setUp() {
        ClientExamData.reset();
    }

    @After
    public void tearDown() {
        ClientExamData.reset();
    }

    @Test
    public void rankCatalogUpdateKeepsExamScheduleSnapshot() {
        handle("snapshot", 1,
                "{\"available\":true,\"exams\":[{\"id\":\"chunin\"}],"
                        + "\"rankCatalog\":[{\"id\":\"genin\"}]}");
        handle("rank_catalog", 2,
                "{\"available\":true,\"rankCatalog\":[{\"id\":\"jonin\"}]}");

        assertEquals("chunin", ClientExamData.snapshot().getAsJsonArray("exams")
                .get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("jonin", ClientExamData.rankCatalogSnapshot().getAsJsonArray("rankCatalog")
                .get(0).getAsJsonObject().get("id").getAsString());
        assertTrue(ClientExamData.rankCatalogSnapshot().get("available").getAsBoolean());
    }

    private static void handle(String kind, int transferId, String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ClientExamData.handle(new ExamSyncMessage(kind, transferId, 0, 1, payload));
    }
}
