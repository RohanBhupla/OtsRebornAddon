package net.rebornaddon.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IItemBlacklist;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;

import java.util.ArrayList;
import java.util.List;

@JEIPlugin
public class ShinobiAddonJeiVisibilityPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        removeOversizedVariedCommoditiesRecipes();
        IIngredientBlacklist ingredientBlacklist = registry.getJeiHelpers().getIngredientBlacklist();
        IItemBlacklist itemBlacklist = registry.getJeiHelpers().getItemBlacklist();

        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation name = item.getRegistryName();
            if (!ShinobiAddonRestrictionHandler.shouldRestrictName(name)) {
                continue;
            }

            NonNullList<ItemStack> stacks = NonNullList.create();
            item.getSubItems(CreativeTabs.SEARCH, stacks);

            if (stacks.isEmpty()) {
                stacks.add(new ItemStack(item));
            }

            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue;
                }

                ingredientBlacklist.addIngredientToBlacklist(stack);
                itemBlacklist.addItemToBlacklist(stack);
            }
        }
    }

    private void removeOversizedVariedCommoditiesRecipes() {
        List<IRecipe> replacements = new ArrayList<IRecipe>();

        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ResourceLocation name = recipe.getRegistryName();
            if (name == null || !"variedcommodities".equals(name.getResourceDomain())) {
                continue;
            }

            if (recipe.getIngredients().size() > 9) {
                replacements.add(recipe);
            }
        }

        for (IRecipe recipe : replacements) {
            ResourceLocation name = recipe.getRegistryName();
            int id = CraftingManager.REGISTRY.getIDForObject(recipe);
            if (name != null && id >= 0) {
                CraftingManager.REGISTRY.register(id, name, new EmptyRecipe(name));
            }
        }
    }

    private static class EmptyRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

        private EmptyRecipe(ResourceLocation name) {
            setRegistryName(name);
        }

        @Override
        public boolean matches(InventoryCrafting inv, World worldIn) {
            return false;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canFit(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.create();
        }
    }
}
