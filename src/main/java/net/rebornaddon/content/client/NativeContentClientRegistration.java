package net.rebornaddon.content.client;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.rebornaddon.content.entity.EntityMissionNpc;

public final class NativeContentClientRegistration {
    private NativeContentClientRegistration() {
    }

    public static void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityMissionNpc.class,
                new IRenderFactory<EntityMissionNpc>() {
                    @Override
                    public Render<? super EntityMissionNpc> createRenderFor(RenderManager manager) {
                        return new RenderMissionNpc(manager);
                    }
                });
    }
}
