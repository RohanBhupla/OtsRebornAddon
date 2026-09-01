package net.rebornaddon.region;

import net.minecraft.util.math.BlockPos;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionPolicyServiceTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void indexesDimensionAndBoundsWithoutLoadingChunks() throws Exception {
        RegionPolicyService service = RegionPolicyService.INSTANCE;
        service.initialize(temporary.newFolder("server"));
        RegionPolicyService.RegionDefinition region = new RegionPolicyService.RegionDefinition();
        region.id = "exam_arena";
        region.dimension = 0;
        region.minX = -32;
        region.minY = 40;
        region.minZ = -32;
        region.maxX = 32;
        region.maxY = 100;
        region.maxZ = 32;
        region.rules.put("pvp", "deny");
        service.put(region);
        assertFalse(service.allows("pvp", 0, new BlockPos(0, 64, 0), true));
        assertTrue(service.allows("pvp", 0, new BlockPos(100, 64, 100), true));
        assertTrue(service.allows("pvp", 1, new BlockPos(0, 64, 0), true));
        service.reset();
    }
}
