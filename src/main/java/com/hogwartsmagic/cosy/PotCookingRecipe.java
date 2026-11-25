package com.hogwartsmagic.cosy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.RecipeMatcher;

import java.util.List;

/**
 * {@code hogwartsmagic:pot_cooking}: shapeless ingredients, a cook time in ticks, and the result. Containers come
 * back (a milk bucket leaves a bucket, a honey bottle a glass bottle) by the ordinary crafting-remainder rule.
 * Never shown in the recipe book ({@link #isSpecial()}), since the pot is not a crafting grid.
 */
public class PotCookingRecipe implements Recipe<PotInput> {
    private final List<Ingredient> ingredients;
    private final int cookTicks;
    private final ItemStack result;

    public PotCookingRecipe(List<Ingredient> ingredients, int cookTicks, ItemStack result) {
        this.ingredients = List.copyOf(ingredients);
        this.cookTicks = cookTicks;
        this.result = result;
    }

    public int cookTicks() {
        return this.cookTicks;
    }

    public ItemStack result() {
        return this.result;
    }

    @Override
    public boolean matches(PotInput input, Level level) {
        return input.size() == this.ingredients.size()
                && RecipeMatcher.findMatches(input.items(), this.ingredients) != null;
    }

    @Override
    public ItemStack assemble(PotInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.ingredients.toArray(Ingredient[]::new));
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CosyKitchen.POT_COOKING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return CosyKitchen.POT_COOKING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<PotCookingRecipe> {
        private static final MapCodec<PotCookingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter(r -> r.ingredients),
                com.mojang.serialization.Codec.intRange(1, 100_000).fieldOf("cook_ticks").forGetter(r -> r.cookTicks),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result)
        ).apply(i, PotCookingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, PotCookingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.ingredients,
                ByteBufCodecs.VAR_INT, r -> r.cookTicks,
                ItemStack.STREAM_CODEC, r -> r.result,
                PotCookingRecipe::new);

        @Override
        public MapCodec<PotCookingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PotCookingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
