package net.rebornaddon.content.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.content.entity.EntityMissionNpc;

import java.io.InputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderMissionNpc extends RenderLiving<EntityMissionNpc> {
    private static final Map<String, ResourceLocation> RESOURCE_SKINS =
            new ConcurrentHashMap<String, ResourceLocation>();
    private static final ModelPlayer NORMAL = new ModelPlayer(0.0F, false);
    private static final ModelPlayer SLIM = new ModelPlayer(0.0F, true);

    public RenderMissionNpc(RenderManager manager) {
        super(manager, NORMAL, 0.45F);
        addLayer(new LayerHeldItem(this));
        addLayer(new LayerBipedArmor(this) {
            @Override
            protected void initArmor() {
                modelLeggings = new ModelPlayer(0.5F, false);
                modelArmor = new ModelPlayer(1.0F, false);
            }
        });
    }

    @Override
    public void doRender(EntityMissionNpc entity, double x, double y, double z,
                         float yaw, float partialTicks) {
        mainModel = entity.isSlim() ? SLIM : NORMAL;
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityMissionNpc entity) {
        return textureFor(entity);
    }

    public static ResourceLocation textureFor(EntityMissionNpc entity) {
        String skin = entity.getSkin();
        if (skin == null || skin.isEmpty()) {
            return DefaultPlayerSkin.getDefaultSkinLegacy();
        }
        if (skin.startsWith("http://") || skin.startsWith("https://")) {
            return ClientNpcSkinCache.texture(skin);
        }
        try {
            return resourceSkin(skin);
        } catch (Throwable ignored) {
            return DefaultPlayerSkin.getDefaultSkinLegacy();
        }
    }

    private static ResourceLocation resourceSkin(String value) {
        ResourceLocation cached = RESOURCE_SKINS.get(value);
        if (cached != null) return cached;
        ResourceLocation source = new ResourceLocation(value);
        InputStream input = null;
        try {
            input = Minecraft.getMinecraft().getResourceManager().getResource(source).getInputStream();
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                RESOURCE_SKINS.put(value, DefaultPlayerSkin.getDefaultSkinLegacy());
                return DefaultPlayerSkin.getDefaultSkinLegacy();
            }
            BufferedImage scaled = scaleSkin(image);
            BufferedImage normalized = new ImageBufferDownload().parseUserSkin(scaled);
            ResourceLocation location = new ResourceLocation("rebornaddon",
                    "npc_skin/resource_" + sha256(value));
            Minecraft.getMinecraft().getTextureManager().loadTexture(location,
                    new DynamicTexture(normalized));
            RESOURCE_SKINS.put(value, location);
            return location;
        } catch (Throwable ignored) {
            RESOURCE_SKINS.put(value, DefaultPlayerSkin.getDefaultSkinLegacy());
            return DefaultPlayerSkin.getDefaultSkinLegacy();
        } finally {
            if (input != null) try { input.close(); }
            catch (java.io.IOException ignored) {
            }
        }
    }

    private static BufferedImage scaleSkin(BufferedImage source) {
        int targetHeight = source.getHeight() * 2 == source.getWidth() ? 32 : 64;
        if (source.getWidth() == 64 && source.getHeight() == targetHeight) return source;
        BufferedImage scaled = new BufferedImage(64, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, 64, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
