package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.support.LumosRules;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The light Lumos leaves in the world. Two kinds, by {@link #TIMED}:
 *
 * <ul>
 *   <li><b>Follow light</b> (above a Lumos holder's head). Every {@link LumosRules#FOLLOW_RECHECK_TICKS}
 *       ticks a scheduled block tick looks for a living Lumos holder within
 *       {@link LumosRules#FOLLOW_SEARCH_RADIUS} blocks and removes the block if there is none. Random
 *       ticks run the same check, so a light whose scheduled tick was lost still goes.</li>
 *   <li><b>Timed light</b> (Lumos Maxima orbs, Revelio highlights). One scheduled tick at placement
 *       removes it. Sparkles on the client so an orb reads as an orb.</li>
 * </ul>
 *
 * <p>Scheduled block ticks are saved with the chunk, so neither kind can outlive an unload, a logout,
 * a death or a restart. That is the bug this block replaces: vanilla light blocks tracked only in a
 * static map. Not an {@code air()} block on purpose: chunk sections only random-tick non-air blocks.
 */
public class LumosLightBlock extends Block {
    /**
     * Later expiry for a timed light that was refreshed while its removal tick was pending. Transient on
     * purpose: after a restart the saved tick simply fires at the original (earlier) time, which is the safe
     * direction. Only ever read by the tick that removes the light, so it cannot keep one alive. Cleared per
     * dimension on level unload and wholly on server stop, so it never carries into another world.
     */
    private static final Map<GlobalPos, Long> REFRESHED_UNTIL = new ConcurrentHashMap<>();

    public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;
    public static final BooleanProperty TIMED = BooleanProperty.create("timed");

    public LumosLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LIGHT, 15).setValue(TIMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT, TIMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Timed lights are scheduled by placeTimedLight with their own delay.
        if (!level.isClientSide && !state.getValue(TIMED)) {
            level.scheduleTick(pos, this, LumosRules.FOLLOW_RECHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TIMED)) {
            // A refresh while a tick was already pending could not move it (vanilla keeps one tick per block
            // and position), so the new expiry waits in REFRESHED_UNTIL; honour it by rescheduling.
            Long until = REFRESHED_UNTIL.remove(GlobalPos.of(level.dimension(), pos));
            long now = level.getGameTime();
            if (until != null && until > now) {
                REFRESHED_UNTIL.put(GlobalPos.of(level.dimension(), pos), until);
                level.scheduleTick(pos, this, (int) Math.min(Integer.MAX_VALUE, until - now));
                return;
            }
            level.removeBlock(pos, false);
            return;
        }
        if (!hasLumosHolderNear(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, LumosRules.FOLLOW_RECHECK_TICKS);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(TIMED);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasLumosHolderNear(level, pos)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(TIMED) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    0.0, 0.005, 0.0);
        }
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ExampleMod.MODID,
            bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Cleanup {
        private Cleanup() {
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
            if (event.getLevel() instanceof ServerLevel level) {
                REFRESHED_UNTIL.keySet().removeIf(k -> k.dimension().equals(level.dimension()));
            }
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
            REFRESHED_UNTIL.clear();
        }
    }

    private static boolean hasLumosHolderNear(ServerLevel level, BlockPos pos) {
        Holder<MobEffect> lumos = ExampleMod.LUMOS_EFFECT.getHolder().orElse(null);
        if (lumos == null) {
            return false;
        }
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(LumosRules.FOLLOW_SEARCH_RADIUS),
                e -> e.isAlive() && e.hasEffect(lumos)).isEmpty();
    }

    /**
     * Puts (or re-levels) a follow light at {@code pos}. Takes empty space, an existing follow light, or a
     * vanilla light block (the old Lumos code adopted those too, which also clears lights it leaked).
     */
    public static boolean placeFollowLight(ServerLevel level, BlockPos pos, int light) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        boolean ours = current.is(ExampleMod.LUMOS_LIGHT.get()) && !current.getValue(TIMED);
        if (!ours && !current.isAir() && !current.is(Blocks.LIGHT)) {
            return false;
        }
        BlockState wanted = ExampleMod.LUMOS_LIGHT.get().defaultBlockState()
                .setValue(LIGHT, Mth.clamp(light, 0, 15))
                .setValue(TIMED, false);
        if (!current.equals(wanted)) {
            level.setBlock(pos, wanted, Block.UPDATE_ALL);
        }
        return true;
    }

    public static void removeFollowLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current.is(ExampleMod.LUMOS_LIGHT.get()) && !current.getValue(TIMED)) {
            level.removeBlock(pos, false);
        }
    }

    /** A light that removes itself after {@code ticks}. Takes empty space or any light block. */
    public static boolean placeTimedLight(ServerLevel level, BlockPos pos, int light, int ticks) {
        if (ticks <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.is(Blocks.LIGHT) && !current.is(ExampleMod.LUMOS_LIGHT.get())) {
            return false;
        }
        BlockState wanted = ExampleMod.LUMOS_LIGHT.get().defaultBlockState()
                .setValue(LIGHT, Mth.clamp(light, 0, 15))
                .setValue(TIMED, true);
        GlobalPos key = GlobalPos.of(level.dimension(), pos);
        long expiry = level.getGameTime() + ticks;
        if (current.is(ExampleMod.LUMOS_LIGHT.get())) {
            // Refresh (or a follow light converted to a timed one, whose pending 10-tick recheck would
            // otherwise remove it early): never shorten, and a pending tick cannot be moved, so record the
            // later expiry; the tick that fires reschedules itself to it.
            REFRESHED_UNTIL.merge(key, expiry, Math::max);
            if (REFRESHED_UNTIL.size() > 1024) {
                long now = level.getGameTime();
                REFRESHED_UNTIL.entrySet().removeIf(e -> e.getValue() <= now);
            }
        } else {
            REFRESHED_UNTIL.remove(key);
        }
        if (!current.equals(wanted)) {
            level.setBlock(pos, wanted, Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, ExampleMod.LUMOS_LIGHT.get(), ticks); // no-op if one is already pending
        return true;
    }

    public static boolean isTimedLight(BlockState state) {
        return state.is(ExampleMod.LUMOS_LIGHT.get()) && state.getValue(TIMED);
    }
}
