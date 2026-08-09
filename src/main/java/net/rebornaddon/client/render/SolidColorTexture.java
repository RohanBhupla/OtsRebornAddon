package net.rebornaddon.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

@SideOnly(Side.CLIENT)
public final class SolidColorTexture {
    public static final long NO_BINDING = -1L;

    private static ResourceLocation textureLocation;

    private SolidColorTexture() {
    }

    public static long bind() {
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture());
        return ((long) activeUnit << 32) | (boundTexture & 0xFFFFFFFFL);
    }

    public static void restore(long binding) {
        if (binding == NO_BINDING) {
            return;
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture((int) binding);
        GlStateManager.setActiveTexture((int) (binding >>> 32));
    }

    private static ResourceLocation texture() {
        if (textureLocation == null) {
            DynamicTexture texture = new DynamicTexture(1, 1);
            texture.getTextureData()[0] = 0xFFFFFFFF;
            texture.updateDynamicTexture();
            textureLocation = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("rebornaddon_solid_color", texture);
        }
        return textureLocation;
    }
}
