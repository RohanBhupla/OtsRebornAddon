package net.rebornaddon.substitution.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.substitution.EntitySubstitutionDecoy;
import net.rebornaddon.village.Village;

import java.util.UUID;

@SideOnly(Side.CLIENT)
public class RenderSubstitutionDecoy extends Render<EntitySubstitutionDecoy> {
    private static final float MODEL_SCALE = 0.0625F;
    private static final float PLAYER_SIZE = 0.9375F;

    private final ModelPlayer normalModel = new ModelPlayer(0.0F, false);
    private final ModelPlayer slimModel = new ModelPlayer(0.0F, true);
    private final ModelPlayer normalShell = new ModelPlayer(0.035F, false);
    private final ModelPlayer slimShell = new ModelPlayer(0.035F, true);

    public RenderSubstitutionDecoy(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.0F;
        prepareModel(normalModel);
        prepareModel(slimModel);
        prepareModel(normalShell);
        prepareModel(slimShell);
    }

    @Override
    public void doRender(EntitySubstitutionDecoy entity, double x, double y, double z, float entityYaw,
                         float partialTicks) {
        UUID owner = entity.getOwnerId();
        boolean slim = isSlim(owner);
        ModelPlayer model = slim ? slimModel : normalModel;
        ModelPlayer shell = slim ? slimShell : normalShell;
        Village village = entity.getVillage();
        CloneStyle style = styleFor(village);
        float maxAge = entity.getMaxAge();
        float age = Math.min(maxAge, entity.ticksExisted + partialTicks);
        float opacity = lifeOpacity(age, maxAge);
        float yaw = interpolate(entity.prevRotationYaw, entity.rotationYaw, partialTicks);
        float pulse = 0.88F + 0.12F * Math.abs((float) Math.sin(age * style.pulseSpeed));
        float horizontalScale = horizontalScale(village, age, maxAge);
        float verticalScale = verticalScale(village, age, maxAge);
        float yOffset = verticalOffset(village, age, maxAge);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y + yOffset, (float) z);
            GlStateManager.rotate(180.0F - yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(horizontalScale * PLAYER_SIZE, verticalScale * PLAYER_SIZE,
                    horizontalScale * PLAYER_SIZE);
            GlStateManager.scale(-1.0F, -1.0F, 1.0F);
            GlStateManager.translate(0.0F, -1.501F, 0.0F);

            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(true);

            bindTexture(skinFor(owner));
            renderModel(model, entity, age, 1.0F, 1.0F, 1.0F, style.skinAlpha * opacity);

            GlStateManager.disableTexture2D();
            renderModel(model, entity, age, style.red, style.green, style.blue,
                    style.materialAlpha * opacity);

            if (style.additiveShell) {
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE);
                GlStateManager.depthMask(false);
            }

            renderModel(shell, entity, age, style.shellRed, style.shellGreen, style.shellBlue,
                    style.shellAlpha * opacity * pulse);
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(EntitySubstitutionDecoy entity) {
        return skinFor(entity.getOwnerId());
    }

    private static void renderModel(ModelPlayer model, EntitySubstitutionDecoy entity, float age,
                                    float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
        model.render(entity, 0.0F, 0.0F, age, 0.0F, 0.0F, MODEL_SCALE);
    }

    private static void prepareModel(ModelPlayer model) {
        model.isChild = false;
        model.isRiding = false;
        model.swingProgress = 0.0F;
    }

    private static ResourceLocation skinFor(UUID owner) {
        NetworkPlayerInfo info = playerInfo(owner);
        if (info != null) {
            return info.getLocationSkin();
        }
        return owner == null ? DefaultPlayerSkin.getDefaultSkinLegacy() : DefaultPlayerSkin.getDefaultSkin(owner);
    }

    private static boolean isSlim(UUID owner) {
        NetworkPlayerInfo info = playerInfo(owner);
        if (info != null) {
            return "slim".equals(info.getSkinType());
        }
        return owner != null && "slim".equals(DefaultPlayerSkin.getSkinType(owner));
    }

    private static NetworkPlayerInfo playerInfo(UUID owner) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return owner != null && minecraft.getConnection() != null
                ? minecraft.getConnection().getPlayerInfo(owner)
                : null;
    }

    private static float lifeOpacity(float age, float maxAge) {
        float forming = smoothStep(clamp(age / 3.0F));
        float fading = smoothStep(clamp((maxAge - age) / 5.0F));
        return Math.min(forming, fading);
    }

    private static float verticalScale(Village village, float age, float maxAge) {
        float ending = smoothStep(clamp((age - (maxAge - 6.0F)) / 6.0F));
        if (village == Village.SAND) {
            return 1.0F - ending * 0.2F;
        }
        if (village == Village.RAIN) {
            return 1.0F + ending * 0.06F;
        }
        return 1.0F;
    }

    private static float horizontalScale(Village village, float age, float maxAge) {
        float scale = 0.94F + 0.06F * smoothStep(clamp(age / 3.0F));
        float ending = smoothStep(clamp((age - (maxAge - 7.0F)) / 7.0F));
        if (village == Village.MIST) {
            return scale + ending * 0.07F;
        }
        if (village == Village.RAIN) {
            return scale - ending * 0.035F;
        }
        return scale;
    }

    private static float verticalOffset(Village village, float age, float maxAge) {
        if (village == Village.MIST) {
            return 0.025F + (float) Math.sin(age * 0.42F) * 0.018F;
        }
        if (village == Village.RAIN) {
            return -smoothStep(clamp((age - (maxAge - 8.0F)) / 8.0F)) * 0.06F;
        }
        if (village == Village.CLOUD) {
            return (float) Math.sin(age * 1.8F) * 0.006F;
        }
        return 0.0F;
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

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static CloneStyle styleFor(Village village) {
        if (village == Village.SAND) {
            return new CloneStyle(0.4F, 0.83F, 0.65F, 0.36F, 0.52F,
                    0.96F, 0.82F, 0.53F, 0.22F, 0.55F, false);
        }
        if (village == Village.CLOUD) {
            return new CloneStyle(0.28F, 0.42F, 0.78F, 1.0F, 0.34F,
                    1.0F, 0.9F, 0.4F, 0.34F, 1.9F, true);
        }
        if (village == Village.MIST) {
            return new CloneStyle(0.28F, 0.82F, 0.94F, 0.98F, 0.26F,
                    0.94F, 0.99F, 1.0F, 0.13F, 0.42F, false);
        }
        if (village == Village.RAIN) {
            return new CloneStyle(0.46F, 0.22F, 0.42F, 0.78F, 0.43F,
                    0.38F, 0.64F, 1.0F, 0.24F, 0.78F, false);
        }
        if (village == Village.STONE) {
            return new CloneStyle(0.28F, 0.48F, 0.48F, 0.46F, 0.58F,
                    0.7F, 0.67F, 0.6F, 0.28F, 0.35F, false);
        }
        return new CloneStyle(0.62F, 0.32F, 0.7F, 0.25F, 0.24F,
                0.55F, 0.35F, 0.16F, 0.2F, 0.62F, false);
    }

    private static final class CloneStyle {
        private final float skinAlpha;
        private final float red;
        private final float green;
        private final float blue;
        private final float materialAlpha;
        private final float shellRed;
        private final float shellGreen;
        private final float shellBlue;
        private final float shellAlpha;
        private final float pulseSpeed;
        private final boolean additiveShell;

        private CloneStyle(float skinAlpha, float red, float green, float blue, float materialAlpha,
                           float shellRed, float shellGreen, float shellBlue, float shellAlpha,
                           float pulseSpeed, boolean additiveShell) {
            this.skinAlpha = skinAlpha;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.materialAlpha = materialAlpha;
            this.shellRed = shellRed;
            this.shellGreen = shellGreen;
            this.shellBlue = shellBlue;
            this.shellAlpha = shellAlpha;
            this.pulseSpeed = pulseSpeed;
            this.additiveShell = additiveShell;
        }
    }
}
