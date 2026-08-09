package net.rebornaddon.chakra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.network.ChakraModeEffectMessage;
import net.rebornaddon.client.render.SolidColorTexture;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Field;

@SideOnly(Side.CLIENT)
public final class ClientChakraModeOverlay {
    private static final float MODEL_SCALE = 0.0625F;
    private static final float HEIGHT_SCALE = 0.94F;
    private static final float FEET_ANCHOR_OFFSET = 1.5F * (1.0F - HEIGHT_SCALE);
    private static final long HEARTBEAT_TIMEOUT = 30L;
    private static final Field HEADWEAR_FIELD = findHeadwearField();

    public static final ClientChakraModeOverlay INSTANCE = new ClientChakraModeOverlay();

    private final Map<Integer, ActiveOverlay> active = new HashMap<Integer, ActiveOverlay>();

    private final ModelPlayer normalSurface = new ModelPlayer(0.0F, false);
    private final ModelPlayer slimSurface = new ModelPlayer(0.0F, true);
    private final ModelPlayer normalShell = new ModelPlayer(0.004F, false);
    private final ModelPlayer slimShell = new ModelPlayer(0.004F, true);

    private ClientChakraModeOverlay() {
        hideOuterLayers(normalSurface);
        hideOuterLayers(slimSurface);
        hideOuterLayers(normalShell);
        hideOuterLayers(slimShell);
    }

    public void update(ChakraModeEffectMessage message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ChakraMode mode = ChakraMode.byId(message.getModeId());
        if (minecraft.world == null || mode == null) {
            return;
        }
        if (!message.isActive()) {
            active.remove(message.getEntityId());
            return;
        }
        active.put(message.getEntityId(), new ActiveOverlay(mode,
                minecraft.world.getTotalWorldTime() + HEARTBEAT_TIMEOUT));
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (!(event.getEntityPlayer() instanceof AbstractClientPlayer)) {
            return;
        }
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntityPlayer();
        ActiveOverlay overlay = active.get(player.getEntityId());
        if (overlay == null || player.isInvisible()) {
            return;
        }

        long now = player.world.getTotalWorldTime();
        if (overlay.expiresAt < now) {
            active.remove(player.getEntityId());
            return;
        }

        float partialTicks = event.getPartialRenderTick();
        float bodyYaw = interpolate(player.prevRenderYawOffset, player.renderYawOffset, partialTicks);
        float headYaw = interpolate(player.prevRotationYawHead, player.rotationYawHead, partialTicks) - bodyYaw;
        float headPitch = player.prevRotationPitch
                + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        float limbSwingAmount = player.prevLimbSwingAmount
                + (player.limbSwingAmount - player.prevLimbSwingAmount) * partialTicks;
        float limbSwing = player.limbSwing - player.limbSwingAmount * (1.0F - partialTicks);
        float age = player.ticksExisted + partialTicks;
        float pulse = 0.88F + 0.12F * Math.abs((float) Math.sin(age * 0.22F));
        boolean slim = "slim".equals(player.getSkinType());
        ModelPlayer source = (ModelPlayer) event.getRenderer().getMainModel();
        ModelPlayer surface = slim ? slimSurface : normalSurface;
        ModelPlayer shell = slim ? slimShell : normalShell;
        copyPose(source, surface);
        copyPose(source, shell);

        long textureBinding = SolidColorTexture.NO_BINDING;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) event.getX(), (float) event.getY(), (float) event.getZ());
            GlStateManager.rotate(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-1.0F, -1.0F, 1.0F);
            GlStateManager.translate(0.0F, -1.501F, 0.0F);
            GlStateManager.translate(0.0F, FEET_ANCHOR_OFFSET, 0.0F);
            GlStateManager.scale(1.0F, HEIGHT_SCALE, 1.0F);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            textureBinding = SolidColorTexture.bind();
            GlStateManager.depthMask(false);
            GlStateManager.enablePolygonOffset();
            GlStateManager.doPolygonOffset(-1.0F, -10.0F);

            OverlayColor color = colorFor(overlay.mode);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            render(surface, player, limbSwing, limbSwingAmount, age, headYaw, headPitch,
                    color.red, color.green, color.blue, color.surfaceAlpha);

            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            render(shell, player, limbSwing, limbSwingAmount, age, headYaw, headPitch,
                    color.shellRed, color.shellGreen, color.shellBlue, color.shellAlpha * pulse);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.doPolygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            SolidColorTexture.restore(textureBinding);
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            active.clear();
        }
    }

    private static void render(ModelPlayer model, AbstractClientPlayer player, float limbSwing,
                               float limbSwingAmount, float age, float headYaw, float headPitch,
                               float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
        model.setLivingAnimations(player, limbSwing, limbSwingAmount, 0.0F);
        model.render(player, limbSwing, limbSwingAmount, age, headYaw, headPitch, MODEL_SCALE);
    }

    private static void copyPose(ModelPlayer source, ModelPlayer target) {
        target.setModelAttributes(source);
        hideOuterLayers(target);
    }

    private static void hideOuterLayers(ModelPlayer model) {
        hide(model.bipedBodyWear);
        hide(model.bipedLeftArmwear);
        hide(model.bipedRightArmwear);
        hide(model.bipedLeftLegwear);
        hide(model.bipedRightLegwear);
        if (HEADWEAR_FIELD == null) {
            return;
        }
        try {
            Object headwear = HEADWEAR_FIELD.get(model);
            if (headwear instanceof ModelRenderer) {
                hide((ModelRenderer) headwear);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static void hide(ModelRenderer renderer) {
        if (renderer != null) {
            renderer.showModel = false;
        }
    }

    private static Field findHeadwearField() {
        try {
            return ReflectionHelper.findField(ModelBiped.class, "bipedHeadwear", "field_178720_f");
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static float interpolate(float previous, float current, float partialTicks) {
        float difference = current - previous;
        while (difference < -180.0F) {
            difference += 360.0F;
        }
        while (difference >= 180.0F) {
            difference -= 360.0F;
        }
        return previous + difference * partialTicks;
    }

    private static OverlayColor colorFor(ChakraMode mode) {
        if (mode == ChakraMode.RAIN) {
            return new OverlayColor(0.18F, 0.32F, 0.62F, 0.25F,
                    0.42F, 0.58F, 0.9F, 0.17F);
        }
        if (mode == ChakraMode.EARTH) {
            return new OverlayColor(0.38F, 0.29F, 0.18F, 0.26F,
                    0.68F, 0.56F, 0.34F, 0.18F);
        }
        return new OverlayColor(0.16F, 0.62F, 0.82F, 0.24F,
                0.5F, 0.9F, 1.0F, 0.16F);
    }

    private static final class ActiveOverlay {
        private final ChakraMode mode;
        private final long expiresAt;

        private ActiveOverlay(ChakraMode mode, long expiresAt) {
            this.mode = mode;
            this.expiresAt = expiresAt;
        }
    }

    private static final class OverlayColor {
        private final float red;
        private final float green;
        private final float blue;
        private final float surfaceAlpha;
        private final float shellRed;
        private final float shellGreen;
        private final float shellBlue;
        private final float shellAlpha;

        private OverlayColor(float red, float green, float blue, float surfaceAlpha,
                             float shellRed, float shellGreen, float shellBlue, float shellAlpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.surfaceAlpha = surfaceAlpha;
            this.shellRed = shellRed;
            this.shellGreen = shellGreen;
            this.shellBlue = shellBlue;
            this.shellAlpha = shellAlpha;
        }
    }
}
