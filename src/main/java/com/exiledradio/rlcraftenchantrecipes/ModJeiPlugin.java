package com.exiledradio.rlcraftenchantrecipes;

import mezz.jei.api.BlankModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.wrapper.IShapedCraftingRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@JEIPlugin
public class ModJeiPlugin extends BlankModPlugin {

    // Deliberately NOT registering a JEI subtype interpreter for xat:dragons_eye (or
    // any item). Tried that twice (see feedback_xat_trinket_capability_nbt memory) to
    // let a recipe slot cycle through fire/ice/lightning display stacks - both times
    // it broke "show recipe"/"show uses" on the real item elsewhere in JEI, because
    // xat's own crafting recipe is only ever indexed under ONE fixed subtype (its
    // RecipeDragonEye#getRecipeOutput() hardcodes a single element for JEI's display
    // purposes), so our fabricated variants could never all match it regardless of how
    // correct their NBT was.
    //
    // Turns out the interpreter was never actually necessary for cycling - it only
    // seemed that way because raw ShapedRecipes objects get auto-wrapped by JEI's own
    // ShapelessRecipeWrapper, whose getIngredients() runs every input through
    // StackHelper's UniqueItemStackListBuilder, which deduplicates stacks by unique ID
    // (item+damage only, without a subtype interpreter) BEFORE the cycling display ever
    // sees them - collapsing 3 NBT-distinct stacks down to 1. That dedup is specific to
    // that one auto-wrapper, not a universal step: TomeRecipeWrapper below builds its
    // own IIngredients directly (via ConfigurableTomeRecipe#buildDisplayStackLists()),
    // which JEI's CraftingRecipeCategory#setRecipe() forwards straight to the recipe
    // layout with no deduplication in between. So cycling works with zero subtype
    // registration, and can't collide with any item's real recipe lookup.
    private static final class TomeRecipeWrapper implements IShapedCraftingRecipeWrapper {
        private final ModRecipes.ConfigurableTomeRecipe recipe;

        TomeRecipeWrapper(ModRecipes.ConfigurableTomeRecipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public int getWidth() {
            return 3;
        }

        @Override
        public int getHeight() {
            return 3;
        }

        @Override
        public void getIngredients(IIngredients ingredients) {
            ingredients.setInputLists(VanillaTypes.ITEM, recipe.buildDisplayStackLists());
            ingredients.setOutput(VanillaTypes.ITEM, recipe.getRecipeOutput());
        }

        @Override
        public ResourceLocation getRegistryName() {
            return recipe.getRegistryName();
        }
    }

    @Override
    public void register(IModRegistry registry) {
        List<TomeRecipeWrapper> tomeRecipeWrappers = new ArrayList<>();
        for (ModRecipes.ConfigurableTomeRecipe recipe : ModRecipes.TOME_RECIPES) {
            tomeRecipeWrappers.add(new TomeRecipeWrapper(recipe));
        }
        registry.addRecipes(tomeRecipeWrappers, VanillaRecipeCategoryUid.CRAFTING);

        if (ModRecipes.DRAGON_HEAD_UPGRADE_RECIPE != null) {
            List<ShapedRecipes> dragonHeadDisplayRecipes = new ArrayList<>(ModRecipes.DRAGON_HEAD_UPGRADE_RECIPE.toJeiDisplayRecipes());
            registry.addRecipes(dragonHeadDisplayRecipes, VanillaRecipeCategoryUid.CRAFTING);
        }
    }
}
