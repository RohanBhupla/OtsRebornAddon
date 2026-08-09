package net.rebornaddon.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.Name("RebornAddon")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(-1000)
@IFMLLoadingPlugin.TransformerExclusions({"net.rebornaddon.asm"})
public class RebornAddonLoadingPlugin implements IFMLLoadingPlugin {

    public RebornAddonLoadingPlugin() {
        KnownRecipeErrorFilter.install();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                "net.rebornaddon.asm.JutsuCooldownGuardTransformer",
                "net.rebornaddon.asm.DynamicDojutsuFeatureTransformer",
                "net.rebornaddon.asm.NarutoModeCompatibilityTransformer"
        };
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
