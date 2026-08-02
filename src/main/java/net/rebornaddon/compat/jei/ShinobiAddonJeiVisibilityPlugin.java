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
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

            for (ItemStack stack : stacksFor(item)) {
                ingredientBlacklist.addIngredientToBlacklist(stack);
                itemBlacklist.addItemToBlacklist(stack);
            }
        }
    }

    private List<ItemStack> stacksFor(Item item) {
        NonNullList<ItemStack> found = NonNullList.create();
        Set<String> seen = new HashSet<String>();
        List<ItemStack> stacks = new ArrayList<ItemStack>();

        addSubItems(item, CreativeTabs.SEARCH, found);
        for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (tab != null) {
                addSubItems(item, tab, found);
            }
        }

        for (ItemStack stack : found) {
            addStack(stacks, seen, stack);
        }

        addStack(stacks, seen, new ItemStack(item));
        addStack(stacks, seen, new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));
        for (int meta = 0; meta <= 15; meta++) {
            addStack(stacks, seen, new ItemStack(item, 1, meta));
        }

        return stacks;
    }

    private static void addSubItems(Item item, CreativeTabs tab, NonNullList<ItemStack> stacks) {
        try {
            item.getSubItems(tab, stacks);
        } catch (RuntimeException ignored) {
        }
    }

    private static void addStack(List<ItemStack> stacks, Set<String> seen, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return;
        }

        String key = name.toString() + ":" + stack.getItemDamage();
        if (seen.add(key)) {
            stacks.add(stack);
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
