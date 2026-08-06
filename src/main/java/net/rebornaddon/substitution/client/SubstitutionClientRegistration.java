package net.rebornaddon.substitution.client;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.substitution.EntitySubstitutionDecoy;

@SideOnly(Side.CLIENT)
public final class SubstitutionClientRegistration {
    private SubstitutionClientRegistration() {
    }

    public static void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntitySubstitutionDecoy.class,
                new IRenderFactory<EntitySubstitutionDecoy>() {
                    @Override
                    public Render<? super EntitySubstitutionDecoy> createRenderFor(RenderManager manager) {
                        return new RenderSubstitutionDecoy(manager);
                    }
                });
    }
}
