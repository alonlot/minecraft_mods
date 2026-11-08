package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModParticles;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Real fog in places that should feel enclosed (the Forbidden Forest, vanilla dark forests, swamps...).
 *
 * <p>Vanilla fog is linear - clear, then a ramp to solid - which reads as a short render distance, not weather.
 * Four things here make it read as fog:
 * <ul>
 *   <li><b>Exponential haze.</b> The fog start is sent as a code (see {@code assets/minecraft/shaders/include/
 *       fog.glsl}) that switches the shader to {@code 1 - exp(-depth * d / far)}: a tree ten blocks off is already
 *       softened, as in real air, and the far wall dissolves instead of cutting off.</li>
 *   <li><b>Pale, lit colour.</b> Fog scatters light, so by day it is brighter than the shade under the trees.
 *       Overworld entries take their brightness from vanilla's own fog colour, so they still go dark at night and
 *       pick up sunrise.</li>
 *   <li><b>Ground mist.</b> Big, faint, slow wisps drifting a block or two off the ground, thickest at dawn.</li>
 *   <li><b>A veiled sky.</b> The sun, moon and stars fade behind the fog (via {@code SkyFogVeilMixin}).</li>
 * </ul>
 * The biome is sampled in a ring around the camera and each entry's share eases in, so the fog thickens over a
 * couple of seconds as you walk in rather than snapping at the border. Never touches fog underwater, in lava or
 * powder snow, or under Blindness/Darkness. Client only; every value here is cosmetic.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BiomeFogClient {

    /**
     * One biome's fog.
     *
     * @param tint  true: {@code r,g,b} is the colour at full daylight and follows vanilla's fog brightness through
     *              the day; false: a fixed colour (underground places and the volcanic glow)
     * @param far   where the fog is solid, in blocks (never pushed past the player's own render distance)
     * @param haze  optical depth reached at {@code far}: how thick the air is on the way there (~1.5 light,
     *              ~3 heavy)
     * @param mist  how much of {@code far} the dawn/night/rain mist can take away at its peak (0..1)
     * @param wisps ground-mist wisps per tick at full share (0 = none)
     */
    private record Entry(ResourceKey<Biome> biome, float r, float g, float b, boolean tint,
                         float far, float haze, float mist, float wisps) {}

    private static final Entry[] ENTRIES = {
            // Old and unfriendly: a grey-green murk you can see a little way into, and something could be in it.
            new Entry(ModBiomes.FORBIDDEN_FOREST, 0.46F, 0.53F, 0.50F, true, 80.0F, 2.4F, 0.30F, 0.9F),
            // Cold grey murk sitting on the water. The thickest overworld fog -- it hides the Dementors until they
            // are already close.
            new Entry(ModBiomes.DEMENTOR_FEN, 0.58F, 0.62F, 0.63F, true, 34.0F, 3.0F, 0.20F, 1.3F),
            // Volcanic haze, glowing day and night. Kept far back so the crags still read as vast.
            new Entry(ModBiomes.HORNTAIL_PEAKS, 0.42F, 0.29F, 0.20F, false, 128.0F, 1.5F, 0.0F, 0.0F),
            // Cold blue-grey mountain air: thin, so a giant reads as a shape on the far slope long before he
            // reads as a giant. Mist only at dawn, in the valley floors.
            new Entry(ModBiomes.GIANTS_HIGHLAND, 0.70F, 0.76F, 0.82F, true, 112.0F, 1.6F, 0.35F, 0.4F),
            // Sickly green and tight: in the Chamber you see the next pillar and not the thing behind it.
            new Entry(ModBiomes.CHAMBER_DEPTHS, 0.05F, 0.11F, 0.07F, false, 24.0F, 2.5F, 0.0F, 0.3F),
            // Forge-lit soot. Loose enough to still read the shape of a vault.
            new Entry(ModBiomes.GOBLIN_UNDERCITY, 0.20F, 0.13F, 0.06F, false, 40.0F, 2.0F, 0.0F, 0.0F),

            // ---- vanilla ----
            // Grey-green mist between the trunks: a few trees deep by day, close and black by night.
            new Entry(Biomes.DARK_FOREST, 0.58F, 0.64F, 0.60F, true, 48.0F, 2.6F, 0.35F, 1.0F),
            // Low wet haze over the water; at dawn it sits so thick you lose the far bank.
            new Entry(Biomes.SWAMP, 0.66F, 0.70F, 0.62F, true, 60.0F, 2.4F, 0.40F, 1.2F),
            new Entry(Biomes.MANGROVE_SWAMP, 0.62F, 0.67F, 0.60F, true, 44.0F, 2.6F, 0.40F, 1.1F),
            // Pale mountain mist between the giant trunks -- light and far back, heavy only at dawn.
            new Entry(Biomes.OLD_GROWTH_PINE_TAIGA, 0.78F, 0.82F, 0.82F, true, 96.0F, 1.8F, 0.45F, 0.6F),
            new Entry(Biomes.OLD_GROWTH_SPRUCE_TAIGA, 0.78F, 0.82F, 0.82F, true, 96.0F, 1.8F, 0.45F, 0.6F),
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
    private static final float VANILLA_HUE = 0.25F;
    /** The fog start that tells fog.glsl to draw real fog: {@code CODE_BASE - CODE_SCALE * depth}. */
    private static final float CODE_BASE = -1000.0F;
    private static final float CODE_SCALE = 100.0F;
    /** How far "behind the fog" the sun is judged to be when veiling it, in blocks. */
    private static final float SKY_VEIL_DISTANCE = 40.0F;

    private static final float[] weights = new float[ENTRIES.length];
    private static final float[] targets = new float[ENTRIES.length];
    private static float totalWeight;
    private static float mist;
    private static float skyVeil;
    private static Level lastLevel;
    private static BlockPos lastPos;
    private static float wispDebt;

    private BiomeFogClient() {}

    // ---- state -------------------------------------------------------------------------------------------------

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

        float veil = 0.0F;
        for (int i = 0; i < ENTRIES.length; i++) {
            if (weights[i] > 0.0F) {
                Entry e = ENTRIES[i];
                veil += weights[i] * (1.0F - (float) Math.exp(-e.haze() * SKY_VEIL_DISTANCE / effectiveFar(e)));
            }
        }
        skyVeil = Math.min(0.92F, veil);

        spawnWisps(mc);
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
        skyVeil = 0.0F;
        wispDebt = 0.0F;
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

    private static float effectiveFar(Entry e) {
        return Math.max(4.0F, e.far() * (1.0F - e.mist() * mist));
    }

    private static boolean vanillaOwnsFog() {
        Minecraft mc = Minecraft.getInstance();
        return mc.cameraEntity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    // ---- fog ---------------------------------------------------------------------------------------------------

    /** Switches the fog to real fog. Returns true when it changed the event (the caller then cancels it). */
    public static boolean renderFog(ViewportEvent.RenderFog event) {
        if (totalWeight <= 0.0F || event.getType() != FogType.NONE || vanillaOwnsFog()) {
            return false;
        }
        float vanillaFar = event.getFarPlaneDistance();
        float far = (1.0F - totalWeight) * vanillaFar;
        float depth = 0.0F;
        for (int i = 0; i < ENTRIES.length; i++) {
            float w = weights[i];
            if (w <= 0.0F) {
                continue;
            }
            Entry e = ENTRIES[i];
            // min() so a player on a low render distance never has the fog pushed *further* out than vanilla
            // would have drawn it.
            far += w * Math.min(vanillaFar, effectiveFar(e));
            depth += w * e.haze();
        }
        event.setNearPlaneDistance(CODE_BASE - CODE_SCALE * depth);
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
        float light = Math.min(1.15F, (0.299F * vr + 0.587F * vg + 0.114F * vb) / DAYLIGHT_LUMA);
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

    /** 0..0.92: how much of the sun, moon and stars the fog hides. Read by SkyFogVeilMixin. */
    public static float skyVeil() {
        return vanillaOwnsFog() ? 0.0F : skyVeil;
    }

    /** The fog colour of the strongest tinted entry here, at daylight, for the mist wisps. */
    public static float[] wispColour() {
        float best = 0.0F;
        Entry pick = null;
        for (int i = 0; i < ENTRIES.length; i++) {
            if (weights[i] > best) {
                best = weights[i];
                pick = ENTRIES[i];
            }
        }
        if (pick == null || !pick.tint()) {
            return new float[] {0.82F, 0.84F, 0.82F};
        }
        // the mist itself is whiter than the fog behind it: it is lit from all round, not seen through
        return new float[] {Mth.lerp(0.5F, pick.r(), 1.0F), Mth.lerp(0.5F, pick.g(), 1.0F), Mth.lerp(0.5F, pick.b(), 1.0F)};
    }

    // ---- ground mist -------------------------------------------------------------------------------------------

    private static void spawnWisps(Minecraft mc) {
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        float rate = 0.0F;
        for (int i = 0; i < ENTRIES.length; i++) {
            rate += weights[i] * ENTRIES[i].wisps();
        }
        if (rate <= 0.01F) {
            wispDebt = 0.0F;
            return;
        }
        rate *= 0.55F + mist * 0.9F;
        if (mc.options.particles().get() == ParticleStatus.DECREASED) {
            rate *= 0.5F;
        }
        wispDebt += rate;
        RandomSource random = mc.level.random;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        while (wispDebt >= 1.0F) {
            wispDebt -= 1.0F;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 5.0D + Math.sqrt(random.nextDouble()) * 27.0D;   // even over the area, none in your face
            int x = Mth.floor(cam.x + Math.cos(angle) * dist);
            int z = Mth.floor(cam.z + Math.sin(angle) * dist);
            double ground = groundBelow(mc.level, x, Mth.floor(cam.y) + 6, z);
            if (Double.isNaN(ground)) {
                continue;
            }
            double y = ground + 0.25D + random.nextDouble() * random.nextDouble() * 2.2D;   // hugs the ground
            mc.particleEngine.createParticle(ModParticles.GROUND_MIST.get(),
                    x + random.nextDouble(), y, z + random.nextDouble(), 0.0D, 0.0D, 0.0D);
        }
    }

    /** The top of the first solid, non-leaf block at or below {@code fromY} (within 20), or NaN. */
    private static double groundBelow(Level level, int x, int fromY, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, fromY, z);
        if (!level.hasChunkAt(p)) {
            return Double.NaN;
        }
        for (int i = 0; i < 20; i++, p.move(0, -1, 0)) {
            BlockState state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty()) {
                return p.getY() + 1.0D;                     // mist lies on water too
            }
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES)) {
                return i == 0 ? Double.NaN : p.getY() + 1.0D;
            }
        }
        return Double.NaN;
    }
}
