package com.hogwartsmagic.block;

import com.hogwartsmagic.block.entity.TombMarkerBlockEntity;
import com.hogwartsmagic.tomb.TombCurse;
import com.hogwartsmagic.tomb.TombCurses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.joml.Vector3f;

/**
 * A hieroglyph tile with a curse laid on it. It looks exactly like the plain Hieroglyph Sandstone
 * round it, so the only way to tell is Revelio: cast close by, and every cursed tile in range shows
 * red for a minute.
 *
 * <ul>
 *   <li><b>Unrevealed</b>, a player who steps on it or mines it sets the curse off, and the tile goes
 *       back to plain sandstone. A curse goes off once.</li>
 *   <li><b>Revealed</b>, it is safe to walk on, and mining it breaks the curse: the player gets
 *       experience and the Curse-Breaker advancement, and the tile drops as plain sandstone.</li>
 * </ul>
 *
 * <p>Creative and spectator players never set a curse off, so a builder can walk the tomb.
 */
public class CursedGlyphBlock extends Block implements EntityBlock {

    public static final BooleanProperty REVEALED = ConcealedRockBlock.REVEALED;
    public static final EnumProperty<TombCurse> CURSE = EnumProperty.create("curse", TombCurse.class);

    private static final DustParticleOptions CURSE_RED = new DustParticleOptions(new Vector3f(0.85F, 0.12F, 0.08F), 1.2F);

    public CursedGlyphBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(REVEALED, false).setValue(CURSE, TombCurse.COLLAPSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(REVEALED, CURSE);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player
                && !state.getValue(REVEALED) && TombCurses.canTrigger(player)) {
            TombCurses.trigger(server, pos, state.getValue(CURSE), player);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && TombCurses.canTrigger(sp)) {
            if (state.getValue(REVEALED)) {
                TombCurses.disarm(server, pos, sp);
            } else {
                // Mined blind: the curse goes off, and the tile still breaks (it is the curse that is
                // spent, not the player's pick).
                TombCurses.trigger(server, pos, state.getValue(CURSE), sp);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(REVEALED)) {
            level.setBlock(pos, state.setValue(REVEALED, false), Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(REVEALED) || random.nextInt(2) != 0) {
            return;
        }
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        level.addParticle(CURSE_RED, x, pos.getY() + 1.05, z, 0.0, 0.02, 0.0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TombMarkerBlockEntity(pos, state);
    }
}
