package net.rebornaddon.performance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ClientPerformanceOverlay {
    public static final ClientPerformanceOverlay INSTANCE = new ClientPerformanceOverlay();
    private static final double MAX_DISTANCE_SQ = 96.0D * 96.0D;
    private static final int MAX_WORLD_MARKERS = 24;
    private static final int MAX_TEXT_ROWS = 8;
    private volatile boolean enabled;

    private ClientPerformanceOverlay() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        enabled = !enabled;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity viewer = minecraft.getRenderViewEntity();
        if (!enabled || viewer == null || minecraft.world == null) return;
        List<Row> rows = nearby(viewer);
        if (rows.isEmpty()) return;
        double partial = event.getPartialTicks();
        double viewX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partial;
        double viewY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partial;
        double viewZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partial;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(2.0F);
        for (int i = 0; i < Math.min(MAX_WORLD_MARKERS, rows.size()); i++) {
            Row row = rows.get(i);
            AxisAlignedBB box = new AxisAlignedBB(row.x, row.y, row.z,
                    row.x + 1.0D, row.y + 1.0D, row.z + 1.0D)
                    .offset(-viewX, -viewY, -viewZ).grow(0.01D);
            float heat = Math.min(1.0F, 0.25F + (float) row.percent / 25.0F);
            RenderGlobal.drawSelectionBoundingBox(box, 1.0F, 1.0F - heat * 0.75F,
                    0.15F, 0.9F);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity viewer = minecraft.getRenderViewEntity();
        if (!enabled || viewer == null || minecraft.world == null) return;
        List<Row> rows = nearby(viewer);
        event.getLeft().add(TextFormatting.GOLD + "Nearby tick hotspots"
                + TextFormatting.GRAY + " (last profile)");
        if (rows.isEmpty()) {
            event.getLeft().add(TextFormatting.GRAY + "No captured hotspots within 96 blocks.");
            return;
        }
        for (int i = 0; i < Math.min(MAX_TEXT_ROWS, rows.size()); i++) {
            Row row = rows.get(i);
            event.getLeft().add(TextFormatting.YELLOW + Integer.toString(i + 1) + ". "
                    + TextFormatting.WHITE + shortName(row.name) + TextFormatting.GRAY + " @ "
                    + row.x + ", " + row.y + ", " + row.z + "  " + row.micros + " us");
        }
    }

    private static List<Row> nearby(Entity viewer) {
        NBTTagCompound snapshot = ClientPerformanceData.snapshot();
        NBTTagList values = snapshot.getTagList("Hotspots", 10);
        List<Row> result = new ArrayList<Row>();
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            if (value.getInteger("Dimension") != viewer.dimension) continue;
            int x = value.getInteger("X");
            int y = value.getInteger("Y");
            int z = value.getInteger("Z");
            double dx = x + 0.5D - viewer.posX;
            double dy = y + 0.5D - viewer.posY;
            double dz = z + 0.5D - viewer.posZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > MAX_DISTANCE_SQ) continue;
            result.add(new Row(value.getString("Name"), x, y, z, value.getLong("Micros"),
                    value.getDouble("Percent"), distanceSq));
        }
        Collections.sort(result, new Comparator<Row>() {
            @Override
            public int compare(Row first, Row second) {
                int timing = Long.compare(second.micros, first.micros);
                return timing != 0 ? timing : Double.compare(first.distanceSq, second.distanceSq);
            }
        });
        return result;
    }

    private static String shortName(String value) {
        if (value == null) return "unknown";
        int colon = value.indexOf(':');
        return colon >= 0 && colon + 1 < value.length() ? value.substring(colon + 1) : value;
    }

    private static final class Row {
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        private final long micros;
        private final double percent;
        private final double distanceSq;

        private Row(String name, int x, int y, int z, long micros, double percent,
                    double distanceSq) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.micros = micros;
            this.percent = percent;
            this.distanceSq = distanceSq;
        }
    }
}
