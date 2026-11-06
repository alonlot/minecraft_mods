package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Per-biome fog: closes the view down in places that should feel enclosed (the Forbidden Forest, vanilla
 * dark forests, swamps...).
 *
 * <p>The biome JSON's {@code fog_color} can tint distant fog but cannot pull it in - that is a render-distance
 * decision, so it happens here. Three things make it read as weather rather than a wall:
 * <ul>
 *   <li>The biome is <b>sampled around the camera</b> and each entry's weight eases toward its share, so walking
 *       into a dark forest thickens the fog over a couple of seconds instead of snapping at the border.</li>
 *   <li><b>Tinted</b> entries (the vanilla biomes) take their brightness from vanilla's own fog colour, so they
 *       follow day, night, sunrise and storms; only the mod's own biomes keep a fixed colour.</li>
 *   <li><b>Mist</b>: tinted entries thicken at night, most of all just before dawn, and in rain.</li>
 * </ul>
 * Never touches fog underwater, in lava or powder snow, or under Blindness/Darkness - vanilla's is right there.
 * Client only; every value here is cosmetic.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BiomeFogClient {

    /**
     * One biome's fog.
     *
     * @param tint  true: {@code r,g,b} is the colour at full daylight and scales with vanilla's fog brightness;
     *              false: {@code r,g,b} is the fixed fog colour, day or night
     * @param near  where the fog starts, in blocks
     * @param far   where it becomes solid, in blocks (never pushed past the player's own render distance)
     * @param mist  how much of {@code near}/{@code far} the dawn/night/rain mist can take away at its peak (0..1)
     */
    private record Entry(ResourceKey<Biome> biome, float r, float g, float b, boolean tint,
                         float near, float far, float mist) {}

    private static final Entry[] ENTRIES = {
            // Old and unfriendly, but you can see a little way down the aisles of trunks -- enough to read the
            // shape of the forest and spot what is moving in it.
            new Entry(ModBiomes.FORBIDDEN_FOREST, 0.07F, 0.09F, 0.08F, false, 12.0F, 80.0F, 0.0F),
            // Cold grey murk sitting on the water. Tighter than the forest -- the fen is meant to feel like being
            // lost, and it hides the Dementors until they are already close.
            new Entry(ModBiomes.DEMENTOR_FEN, 0.29F, 0.32F, 0.31F, false, 1.0F, 30.0F, 0.0F),
            // Volcanic haze. Kept far back so the crags still read as vast; it is atmosphere, not a wall.
            new Entry(ModBiomes.HORNTAIL_PEAKS, 0.42F, 0.29F, 0.20F, false, 12.0F, 128.0F, 0.0F),
            // Sickly green, and the tightest of the lot: in the Chamber you see the next pillar and not the thing
            // behind it.
            new Entry(ModBiomes.CHAMBER_DEPTHS, 0.05F, 0.11F, 0.07F, false, 0.5F, 24.0F, 0.0F),
            // Forge-lit soot. Loose enough to still read the shape of a vault.
            new Entry(ModBiomes.GOBLIN_UNDERCITY, 0.20F, 0.13F, 0.06F, false, 2.0F, 40.0F, 0.0F),

            // ---- vanilla ----
            // Murky green-grey under the canopy: a few trunks deep by day, close and black by night.
            new Entry(Biomes.DARK_FOREST, 0.42F, 0.50F, 0.44F, true, 4.0F, 44.0F, 0.35F),
            // Low wet haze over the water; at dawn it sits so thick you lose the far bank.
            new Entry(Biomes.SWAMP, 0.50F, 0.55F, 0.45F, true, 6.0F, 56.0F, 0.40F),
            new Entry(Biomes.MANGROVE_SWAMP, 0.46F, 0.52F, 0.44F, true, 4.0F, 40.0F, 0.40F),
            // Pale mountain mist between the giant trunks -- light and far back, heavy only at dawn.
            new Entry(Biomes.OLD_GROWTH_PINE_TAIGA, 0.70F, 0.76F, 0.76F, true, 16.0F, 96.0F, 0.45F),
            new Entry(Biomes.OLD_GROWTH_SPRUCE_TAIGA, 0.70F, 0.76F, 0.76F, true, 16.0F, 96.0F, 0.45F),
    };

    /** Sample ring: the camera's own column (weighted {@link #CENTRE_WEIGHT}) plus eight points this far out. */
    private static final int RING_RADIUS = 12;
    private static final float CENTRE_WEIGHT = 3.0F;
    private static final float TOTAL_WEIGHT = CENTRE_WEIGHT + 8.0F;
    /** Per-tick easing toward the sampled share: ~2 s to settle after crossing a border. */
    private static final float EASE = 0.06F;
    /** Vanilla's daytime fog luminance; tinted colours are authored at this brightness. */
    private static final float DAYLIGHT_LUMA = 0.80F;
    /** How much of vanilla's own hue survives under a tint, so sunrise orange still shows through. */
    private static final float VANILLA_HUE = 0.30F;

    private static final float[] weights = new float[ENTRIES.length];
    private static final float[] targets = new float[ENTRIES.length];
    private static float totalWeight;
    private static float mist;
    private static Level lastLevel;
    private static BlockPos lastPos;

    private BiomeFogClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.isPaused()) {
            return;
        }
        if (mc.level == null || mc.cameraEntity == null) {
            reset();
            return;
        }
        BlockPos pos = mc.cameraEntity.blockPosition();
        sample(mc.level, pos);

        // A new dimension, a respawn or a teleport: there is no border to have walked across, so take the
        // new fog at once instead of fading out of the old place's.
        boolean snap = mc.level != lastLevel || lastPos == null || lastPos.distSqr(pos) > 48 * 48;
        lastLevel = mc.level;
        lastPos = pos;

        float total = 0.0F;
        for (int i = 0; i < ENTRIES.length; i++) {
            weights[i] = snap ? targets[i] : weights[i] + (targets[i] - weights[i]) * EASE;
            if (weights[i] < 0.001F) {
                weights[i] = 0.0F;
            }
            total += weights[i];
        }
        totalWeight = total;

        float targetMist = mistAt(mc.level);
        mist = snap ? targetMist : mist + (targetMist - mist) * EASE;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        java.util.Arrays.fill(weights, 0.0F);
        java.util.Arrays.fill(targets, 0.0F);
        totalWeight = 0.0F;
        mist = 0.0F;
        lastLevel = null;
        lastPos = null;
    }

    private static void sample(Level level, BlockPos centre) {
        java.util.Arrays.fill(targets, 0.0F);
        addSample(level, centre, CENTRE_WEIGHT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    addSample(level, centre.offset(dx * RING_RADIUS, 0, dz * RING_RADIUS), 1.0F);
                }
            }
        }
    }

    private static void addSample(Level level, BlockPos pos, float weight) {
        var biome = level.getBiome(pos);
        for (int i = 0; i < ENTRIES.length; i++) {
            if (biome.is(ENTRIES[i].biome())) {
                targets[i] += weight / TOTAL_WEIGHT;
                return;
            }
        }
    }

    /** 0..1: how misty it is right now -- some at night, most just before sunrise, plus whatever the rain adds. */
    private static float mistAt(Level level) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0.0F;
        }
        float t = (float) Math.floorMod(level.getDayTime(), 24000L);
        float m;
        if (t >= 13000.0F && t < 21500.0F) {
            m = 0.55F;                                            // night
        } else if (t >= 21500.0F) {
            m = Mth.lerp((t - 21500.0F) / 2000.0F, 0.55F, 1.0F);  // thickening toward dawn
        } else if (t < 2500.0F) {
            m = Mth.lerp(t / 2500.0F, 1.0F, 0.0F);               // burning off after sunrise
        } else if (t > 11500.0F) {
            m = Mth.lerp((t - 11500.0F) / 1500.0F, 0.0F, 0.55F);  // settling at dusk
        } else {
            m = 0.0F;
        }
        return Mth.clamp(m + level.getRainLevel(1.0F) * 0.5F, 0.0F, 1.0F);
    }

    private static boolean vanillaOwnsFog() {
        Minecraft mc = Minecraft.getInstance();
        return mc.cameraEntity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /** Pulls the fog in. Returns true when it changed the event (the caller then cancels it). */
    public static boolean renderFog(ViewportEvent.RenderFog event) {
        if (totalWeight <= 0.0F || event.getType() != FogType.NONE || vanillaOwnsFog()) {
            return false;
        }
        float vanillaNear = event.getNearPlaneDistance();
        float vanillaFar = event.getFarPlaneDistance();
        float near = (1.0F - totalWeight) * vanillaNear;
        float far = (1.0F - totalWeight) * vanillaFar;
        for (int i = 0; i < ENTRIES.length; i++) {
            float w = weights[i];
            if (w <= 0.0F) {
                continue;
            }
            Entry e = ENTRIES[i];
            float scale = 1.0F - e.mist() * mist;
            // min() so a player on a low render distance never has the fog pushed *further* out than vanilla
            // would have drawn it.
            float entryFar = Math.min(vanillaFar, e.far() * scale);
            float entryNear = Math.min(vanillaNear, Math.min(e.near() * scale, entryFar * 0.9F));
            near += w * entryNear;
            far += w * entryFar;
        }
        event.setNearPlaneDistance(Math.min(near, far * 0.9F));
        event.setFarPlaneDistance(far);
        return true;
    }

    /** Recolours the fog. Returns true when it changed the event. */
    public static boolean fogColor(ViewportEvent.ComputeFogColor event) {
        if (totalWeight <= 0.0F || event.getCamera().getFluidInCamera() != FogType.NONE || vanillaOwnsFog()) {
            return false;
        }
        float vr = event.getRed();
        float vg = event.getGreen();
        float vb = event.getBlue();
        float light = Math.min(1.2F, (0.299F * vr + 0.587F * vg + 0.114F * vb) / DAYLIGHT_LUMA);
        float r = (1.0F - totalWeight) * vr;
        float g = (1.0F - totalWeight) * vg;
        float b = (1.0F - totalWeight) * vb;
        for (int i = 0; i < ENTRIES.length; i++) {
            float w = weights[i];
            if (w <= 0.0F) {
                continue;
            }
            Entry e = ENTRIES[i];
            if (e.tint()) {
                r += w * Mth.lerp(VANILLA_HUE, e.r() * light, vr);
                g += w * Mth.lerp(VANILLA_HUE, e.g() * light, vg);
                b += w * Mth.lerp(VANILLA_HUE, e.b() * light, vb);
            } else {
                r += w * e.r();
                g += w * e.g();
                b += w * e.b();
            }
        }
        event.setRed(Mth.clamp(r, 0.0F, 1.0F));
        event.setGreen(Mth.clamp(g, 0.0F, 1.0F));
        event.setBlue(Mth.clamp(b, 0.0F, 1.0F));
        return true;
    }
}
