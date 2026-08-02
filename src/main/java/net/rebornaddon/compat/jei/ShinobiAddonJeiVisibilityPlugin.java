package net.rebornaddon.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IItemBlacklist;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.IIngredientRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;

import java.util.ArrayList;
import java.util.List;

@JEIPlugin
public class ShinobiAddonJeiVisibilityPlugin implements IModPlugin {

    private IIngredientRegistry ingredientRegistry;

    @Override
    public void register(IModRegistry registry) {
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
        ingredientRegistry = registry.getIngredientRegistry();

        IIngredientBlacklist ingredientBlacklist = registry.getJeiHelpers().getIngredientBlacklist();
        IItemBlacklist itemBlacklist = registry.getJeiHelpers().getItemBlacklist();

        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation name = item.getRegistryName();
            if (!ShinobiAddonRestrictionHandler.shouldRestrictName(name)) {
                continue;
            }

            for (ItemStack stack : ShinobiAddonRestrictionHandler.visibilityStacksFor(item)) {
                ingredientBlacklist.addIngredientToBlacklist(stack);
                itemBlacklist.addItemToBlacklist(stack);
            }
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (ingredientRegistry == null) {
            return;
        }

        ShinobiAddonRestrictionHandler.applyVisibilityRules();

        List<ItemStack> restricted = new ArrayList<ItemStack>();
        for (ItemStack stack : ingredientRegistry.getIngredients(ItemStack.class)) {
            if (ShinobiAddonRestrictionHandler.shouldRestrict(stack)) {
                restricted.add(stack);
            }
        }

        if (!restricted.isEmpty()) {
            ingredientRegistry.removeIngredientsAtRuntime(ItemStack.class, restricted);
        }
    }
}
