package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.cosy.AmbienceRules;
import com.hogwartsmagic.cosy.HearthBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Indoor ambience, all on the client. Every ten ticks the player is looked over once: is it raining here while
 * they stand under a roof, with glass nearby that the rain can reach (the patter on the window); is a lit hearth
 * within twelve blocks (the crackle, fading with distance). Each answer drives one looping sound that fades in and
 * out. Registered by the Forge bus subscriber annotation, and referenced from nothing on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class IndoorAmbience {
    private IndoorAmbience() {
    }

    private static final int LOOK_INTERVAL = 10;
    private static final float RAIN_VOLUME = 0.55f;
    private static final float CRACKLE_VOLUME = 0.8f;
    private static final TagKey<Block> GLASS_BLOCKS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));

    private static FadingLoopSound rain;
    private static FadingLoopSound crackle;
    private static boolean rainWanted;
    private static boolean crackleWanted;
    private static float crackleScale;
    private static int counter;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            reset();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        if (++counter >= LOOK_INTERVAL) {
            counter = 0;
            look(level, player);
        }
        rain = drive(rain, ModSounds.RAIN_WINDOW_LOOP.get(), RAIN_VOLUME, rainWanted, 1.0f);
        crackle = drive(crackle, ModSounds.HEARTH_CRACKLE_LOOP.get(), CRACKLE_VOLUME, crackleWanted, crackleScale);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        rainWanted = false;
        crackleWanted = false;
        rain = null;
        crackle = null;
        counter = 0;
    }

    /** Keeps one instance per loop: starts it when wanted, tells it what is wanted, and lets go once it has stopped. */
    private static FadingLoopSound drive(FadingLoopSound sound, net.minecraft.sounds.SoundEvent event, float volume,
                                         boolean wanted, float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        if (sound != null && sound.isStopped()) {
            sound = null;
        }
        if (sound == null && wanted) {
            sound = new FadingLoopSound(event, volume);
            minecraft.getSoundManager().play(sound);
        }
        if (sound != null) {
            sound.want(wanted, scale);
        }
        return sound;
    }

    private static void look(ClientLevel level, LocalPlayer player) {
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        boolean raining = level.isRaining() && level.dimensionType().hasSkyLight()
                && level.getBiome(eye).value().getPrecipitationAt(eye) == Biome.Precipitation.RAIN;
        // Under a roof: something motion-blocking (glass included) is above the eyes, so a conservatory counts.
        boolean underRoof = level.getHeight(Heightmap.Types.MOTION_BLOCKING, eye.getX(), eye.getZ()) > eye.getY();
        // Only worth scanning for glass when the rest already holds.
        rainWanted = raining && underRoof && AmbienceRules.rainOnWindow(true, true, exposedGlassNearby(level, eye));

        double nearest = Double.MAX_VALUE;
        Vec3 at = player.position();
        int reach = (int) AmbienceRules.CRACKLE_RANGE;
        int minChunkX = (eye.getX() - reach) >> 4;
        int maxChunkX = (eye.getX() + reach) >> 4;
        int minChunkZ = (eye.getZ() - reach) >> 4;
        int maxChunkZ = (eye.getZ() + reach) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof HearthBlockEntity && be.getBlockState().getValue(CampfireBlock.LIT)) {
                        double d = Math.sqrt(Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(at));
                        nearest = Math.min(nearest, d);
                    }
                }
            }
        }
        crackleWanted = nearest < AmbienceRules.CRACKLE_RANGE;
        crackleScale = crackleWanted ? AmbienceRules.crackleScale(nearest) : 0.0f;
    }

    /** Glass or a pane within five blocks that the rain reaches: a window the rain is falling on. */
    private static boolean exposedGlassNearby(ClientLevel level, BlockPos centre) {
        int r = AmbienceRules.GLASS_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (isGlass(level.getBlockState(pos)) && rainReaches(level, pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Rain is on this glass if sky can be seen from just above it (a skylight or glass roof), from the glass itself,
     * or from an open-air cell beside it (the outside face of a window in a wall).
     */
    private static boolean rainReaches(ClientLevel level, BlockPos glass) {
        if (level.canSeeSky(glass.above()) || level.canSeeSky(glass)) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos outside = glass.relative(side);
            if (level.isEmptyBlock(outside) && level.canSeeSky(outside)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGlass(BlockState state) {
        return state.is(GLASS_BLOCKS) || state.is(GLASS_PANES)
                || state.getBlock() instanceof TransparentBlock
                || state.getBlock() instanceof StainedGlassPaneBlock
                || state.is(Blocks.GLASS_PANE);
    }
}
