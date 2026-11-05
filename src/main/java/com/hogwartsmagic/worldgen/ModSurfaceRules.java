package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/**
 * Gives the mod's biomes their own ground blocks.
 *
 * <p>Without this a custom biome falls through to vanilla's catch-all rule and gets grass over dirt,
 * which is why {@code forbidden_forest} looks like a forest for free — and why a bog and a scorched
 * crag would not. There is no datapack hook for surface rules on a vanilla dimension, and in 1.21 the
 * overworld's rule tree is read from {@code data/minecraft/worldgen/noise_settings/*.json}, not from
 * {@code SurfaceRuleData.overworld()} (that is only called by datagen). So the rules are prepended to
 * the noise settings' rule at runtime by {@code NoiseGeneratorSettingsMixin}.
 *
 * <p>The mod rules are prepended, not merged: {@code sequence} returns the first rule that matches, so
 * ours get first refusal and every biome we do not name falls straight through to the untouched
 * vanilla tree. Every rule is guarded by its biome, so prepending to a dimension that never holds
 * these biomes changes nothing there.
 *
 * <p>Rules are guarded by {@code abovePreliminarySurface()} so they only paint the open-air surface —
 * without it the same blocks would be smeared over cave floors that happen to sit inside the biome.
 *
 * <p>Common-safe: only common worldgen classes.
 */
public final class ModSurfaceRules {

    /** The block at the very top of the ground column. */
    private static final SurfaceRules.ConditionSource ON_FLOOR =
            SurfaceRules.stoneDepthCheck(0, true, CaveSurface.FLOOR);
    /** The few blocks of topsoil beneath it. */
    private static final SurfaceRules.ConditionSource UNDER_FLOOR =
            SurfaceRules.stoneDepthCheck(0, true, 6, CaveSurface.FLOOR);

    /** Built once; the rule sources are immutable. */
    private static volatile java.util.List<SurfaceRules.RuleSource> modRules;

    /** The mod rules followed by {@code vanilla}. */
    public static SurfaceRules.RuleSource prependTo(SurfaceRules.RuleSource vanilla) {
        java.util.List<SurfaceRules.RuleSource> rules = modRules;
        if (rules == null) {
            rules = java.util.List.of(dementorFen(), horntailPeaks(), chamberDepths(), goblinUndercity());
            modRules = rules;
        }
        SurfaceRules.RuleSource[] all = new SurfaceRules.RuleSource[rules.size() + 1];
        for (int i = 0; i < rules.size(); i++) {
            all[i] = rules.get(i);
        }
        all[rules.size()] = vanilla;
        return SurfaceRules.sequence(all);
    }

    /**
     * Green, wet, worked stone. Note the deliberate absence of {@code abovePreliminarySurface()} here
     * and in the Undercity below — that guard is what stops a rule reaching underground, and these two
     * biomes <i>are</i> underground. Because they only exist in the cave depth band they never touch
     * the open sky, so {@code ON_FLOOR} means "the floor of a cavern" and nothing else.
     */
    private static SurfaceRules.RuleSource chamberDepths() {
        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(ModBiomes.CHAMBER_DEPTHS),
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(ON_FLOOR, SurfaceRules.sequence(
                                SurfaceRules.ifTrue(
                                        SurfaceRules.noiseCondition(Noises.SURFACE, 0.10D),
                                        SurfaceRules.state(Blocks.MOSSY_COBBLESTONE.defaultBlockState())),
                                SurfaceRules.state(Blocks.TUFF.defaultBlockState()))),
                        SurfaceRules.ifTrue(UNDER_FLOOR,
                                SurfaceRules.state(Blocks.TUFF.defaultBlockState()))));
    }

    /**
     * Mined-out vault floors: loose gravel spoil over cut deepslate. The blocks here deliberately match
     * the biome's stone-replacing features, so the floor you walk on and the rock you tunnel through
     * are the same material — a floor rule that disagreed with the surrounding stone would read as a
     * texture bug rather than as a place.
     */
    private static SurfaceRules.RuleSource goblinUndercity() {
        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(ModBiomes.GOBLIN_UNDERCITY),
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(ON_FLOOR, SurfaceRules.sequence(
                                SurfaceRules.ifTrue(
                                        SurfaceRules.noiseCondition(Noises.SURFACE, 0.25D),
                                        SurfaceRules.state(Blocks.GRAVEL.defaultBlockState())),
                                SurfaceRules.state(Blocks.DEEPSLATE_TILES.defaultBlockState()))),
                        SurfaceRules.ifTrue(UNDER_FLOOR,
                                SurfaceRules.state(Blocks.DEEPSLATE.defaultBlockState()))));
    }

    /**
     * A drowned bog: standing water in the hollows, mud where the water has been, dead coarse dirt on
     * the rises. Deliberately no grass block anywhere — the fen is supposed to look like nothing lives
     * in it.
     */
    private static SurfaceRules.RuleSource dementorFen() {
        // Same trick vanilla uses to flood a swamp: in the one-block band at sea level, let the swamp
        // noise decide whether the top of the column is ground or open water.
        SurfaceRules.RuleSource standingWater = SurfaceRules.ifTrue(
                SurfaceRules.yBlockCheck(VerticalAnchor.absolute(62), 0),
                SurfaceRules.ifTrue(
                        SurfaceRules.not(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(63), 0)),
                        SurfaceRules.ifTrue(
                                SurfaceRules.noiseCondition(Noises.SWAMP, 0.0D),
                                SurfaceRules.state(Blocks.WATER.defaultBlockState()))));

        SurfaceRules.RuleSource ground = SurfaceRules.ifTrue(
                SurfaceRules.abovePreliminarySurface(),
                SurfaceRules.sequence(
                        // Mud in the wet patches, dead dirt on the rest. Mud is in #minecraft:dirt, so
                        // swamp trees and grass patches still place on it.
                        SurfaceRules.ifTrue(ON_FLOOR, SurfaceRules.sequence(
                                SurfaceRules.ifTrue(
                                        SurfaceRules.noiseCondition(Noises.SURFACE, 0.15D),
                                        SurfaceRules.state(Blocks.MUD.defaultBlockState())),
                                SurfaceRules.state(Blocks.COARSE_DIRT.defaultBlockState()))),
                        SurfaceRules.ifTrue(UNDER_FLOOR,
                                SurfaceRules.state(Blocks.DIRT.defaultBlockState()))));

        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(ModBiomes.DEMENTOR_FEN),
                SurfaceRules.sequence(standingWater, ground));
    }

    /** Scorched crags: basalt on the sheer faces, blackstone on anything a dragon could land on. */
    private static SurfaceRules.RuleSource horntailPeaks() {
        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(ModBiomes.HORNTAIL_PEAKS),
                SurfaceRules.ifTrue(
                        SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(ON_FLOOR, SurfaceRules.sequence(
                                        // Cliff faces are stripped back to bare rock by the wind.
                                        SurfaceRules.ifTrue(SurfaceRules.steep(),
                                                SurfaceRules.state(Blocks.BASALT.defaultBlockState())),
                                        // Drifts of ash caught on the ledges.
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.SURFACE, 0.30D),
                                                SurfaceRules.state(Blocks.TUFF.defaultBlockState())),
                                        SurfaceRules.state(Blocks.BLACKSTONE.defaultBlockState()))),
                                SurfaceRules.ifTrue(UNDER_FLOOR,
                                        SurfaceRules.state(Blocks.BLACKSTONE.defaultBlockState())))));
    }

    private ModSurfaceRules() {}
}
