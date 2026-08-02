package net.jutsu.cooldown.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.lang.reflect.Method;
import java.util.Map;

@IFMLLoadingPlugin.Name("JutsuCooldownCoremod")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class JutsuCooldownCoremod implements IFMLLoadingPlugin {

    public JutsuCooldownCoremod() {
        try {
            Class<?> bootstrap = Class.forName("org.spongepowered.asm.launch.MixinBootstrap");
            Method init = bootstrap.getMethod("init");
            init.invoke(null);

            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins");
            Method addConfiguration = mixins.getMethod("addConfiguration", String.class);
            addConfiguration.invoke(null, "mixins.jutsucooldown.json");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"net.rebornaddon.asm.JutsuCooldownGuardTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
