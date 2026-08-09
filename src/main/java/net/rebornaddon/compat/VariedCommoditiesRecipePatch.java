package net.rebornaddon.compat;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreIngredient;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;

public final class VariedCommoditiesRecipePatch {
    public static final VariedCommoditiesRecipePatch INSTANCE = new VariedCommoditiesRecipePatch();

    private VariedCommoditiesRecipePatch() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
        registerCompatibilityRecipes(event.getRegistry());

        if (Loader.isModLoaded("variedcommodities")) {
            for (int i = 0; i < 5; i++) {
                remove(event.getRegistry(), new ResourceLocation("variedcommodities", "wall_banner_" + i));
                remove(event.getRegistry(), new ResourceLocation("variedcommodities", "banner_" + i));
            }
        }

        for (IRecipe recipe : new ArrayList<IRecipe>(event.getRegistry().getValuesCollection())) {
            try {
                ResourceLocation name = recipe.getRegistryName();
                boolean oversizedVariedRecipe = name != null
                        && "variedcommodities".equals(name.getResourceDomain())
                        && recipe.getIngredients().size() > 9;
                if (oversizedVariedRecipe
                        || ShinobiAddonRestrictionHandler.shouldBlockRecipeOutput(recipe.getRecipeOutput())) {
                    remove(event.getRegistry(), recipe.getRegistryName());
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void registerCompatibilityRecipes(IForgeRegistry<IRecipe> registry) {
        if (Loader.isModLoaded("futuremc")) {
            Item soulSoil = ForgeRegistries.ITEMS.getValue(new ResourceLocation("futuremc", "soul_soil"));
            Item soulTorch = ForgeRegistries.ITEMS.getValue(new ResourceLocation("futuremc", "soul_fire_torch"));
            if (soulSoil != null && soulTorch != null) {
                registerShaped(registry, new ResourceLocation("futuremc", "else/soul_torch"),
                        new ItemStack(soulTorch, 4),
                        Ingredient.fromStacks(new ItemStack(Items.COAL, 1, 0),
                                new ItemStack(Items.COAL, 1, 1)),
                        Ingredient.fromItem(Items.STICK),
                        Ingredient.fromStacks(new ItemStack(Blocks.SOUL_SAND), new ItemStack(soulSoil)));
            }
        }

        if (Loader.isModLoaded("nanexscherrybiome")) {
            Item slab = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("nanexscherrybiome", "cherryplanksslab"));
            Item jar = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("nanexscherrybiome", "cherrysaplingjar"));
            if (slab != null && jar != null) {
                registerShaped(registry,
                        new ResourceLocation("nanexscherrybiome", "cherrysaplingjarcraft"),
                        new ItemStack(jar),
                        Ingredient.fromItem(Item.getItemFromBlock(Blocks.GLASS)),
                        new OreIngredient("cherrysapling"),
                        Ingredient.fromStacks(new ItemStack(slab, 1, 0)));
            }
        }
    }

    private static void registerShaped(IForgeRegistry<IRecipe> registry, ResourceLocation name,
                                       ItemStack output, Ingredient first, Ingredient second,
                                       Ingredient third) {
        if (registry.containsKey(name)) {
            return;
        }
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(first);
        ingredients.add(second);
        ingredients.add(third);
        ShapedRecipes recipe = new ShapedRecipes("", 1, 3, ingredients, output);
        recipe.setRegistryName(name);
        registry.register(recipe);
    }

    @SuppressWarnings("unchecked")
    private static void remove(IForgeRegistry<IRecipe> registry, ResourceLocation name) {
        if (name == null || !registry.containsKey(name)) {
            return;
        }

        try {
            if (registry instanceof ForgeRegistry) {
                ((ForgeRegistry<IRecipe>) registry).remove(name);
            }
        } catch (Throwable ignored) {
        }
    }
}
