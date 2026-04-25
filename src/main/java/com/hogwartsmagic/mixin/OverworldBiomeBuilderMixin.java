package com.hogwartsmagic.mixin;

import com.hogwartsmagic.worldgen.ForbiddenForestClimateZones;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Registers {@code hogwartsmagic:forbidden_forest} as additional overworld climate targets.
 * Vanilla {@link Biomes#DARK_FOREST} (and all other vanilla cells) are unchanged — these are
 * extra hypercubes in the multi-noise parameter space for the mod biome only.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {

    private static final ResourceKey<Biome> FORBIDDEN_FOREST = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "forbidden_forest"));

    /** HEAD so these entries register before vanilla slices (helps on equal multi-noise fitness). */
    @Inject(
            method = "addBiomes(Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            remap = false)
    private void hogwartsmagic$prependForbiddenForestParameterPoints(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
            CallbackInfo ci) {
        for (Climate.ParameterPoint point : ForbiddenForestClimateZones.POINTS) {
            consumer.accept(Pair.of(point, FORBIDDEN_FOREST));
        }
    }
}
