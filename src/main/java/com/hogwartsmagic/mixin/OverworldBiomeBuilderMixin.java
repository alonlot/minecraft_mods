package com.hogwartsmagic.mixin;

import com.hogwartsmagic.worldgen.BlackLakeClimateZones;
import com.hogwartsmagic.worldgen.BlackLakeShallowsClimateZones;
import com.hogwartsmagic.worldgen.ChamberDepthsClimateZones;
import com.hogwartsmagic.worldgen.DementorFenClimateZones;
import com.hogwartsmagic.worldgen.ForbiddenForestClimateZones;
import com.hogwartsmagic.worldgen.GoblinUndercityClimateZones;
import com.hogwartsmagic.worldgen.HorntailPeaksClimateZones;
import com.hogwartsmagic.worldgen.ModBiomes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceKey;
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
 * Registers the mod's biomes as additional overworld climate targets. Vanilla
 * {@link Biomes#DARK_FOREST} (and all other vanilla cells) are unchanged — these are extra
 * hypercubes in the multi-noise parameter space for the mod biomes only.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {

    /** HEAD so these entries register before vanilla slices (helps on equal multi-noise fitness). */
    @Inject(
            method = "addBiomes(Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            remap = false)
    private void hogwartsmagic$prependModBiomeParameterPoints(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
            CallbackInfo ci) {
        add(consumer, ForbiddenForestClimateZones.POINTS, ModBiomes.FORBIDDEN_FOREST);
        add(consumer, DementorFenClimateZones.POINTS, ModBiomes.DEMENTOR_FEN);
        add(consumer, HorntailPeaksClimateZones.POINTS, ModBiomes.HORNTAIL_PEAKS);
        add(consumer, BlackLakeClimateZones.POINTS, ModBiomes.BLACK_LAKE);
        add(consumer, BlackLakeShallowsClimateZones.POINTS, ModBiomes.BLACK_LAKE_SHALLOWS);
        add(consumer, ChamberDepthsClimateZones.POINTS, ModBiomes.CHAMBER_DEPTHS);
        add(consumer, GoblinUndercityClimateZones.POINTS, ModBiomes.GOBLIN_UNDERCITY);
    }

    private static void add(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
            Climate.ParameterPoint[] points,
            ResourceKey<Biome> biome) {
        for (Climate.ParameterPoint point : points) {
            consumer.accept(Pair.of(point, biome));
        }
    }
}
