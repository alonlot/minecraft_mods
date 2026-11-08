package com.hogwartsmagic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of the Chamber of Secrets' round door leaf.
 *
 * <p>The door opens as one moving piece of stone, and Minecraft can only draw a block where its
 * block is. So while the door moves, {@link com.hogwartsmagic.client.ChamberDoorRenderer} draws the
 * whole leaf and the real blocks get out of the way <em>without</em> ceasing to exist:
 *
 * <ul>
 *   <li><b>0 - shut.</b> An ordinary block: drawn from its model, solid, opaque.</li>
 *   <li><b>1 - moving.</b> Invisible and still solid. The renderer draws this cell somewhere else,
 *       and the doorway is not open yet, so nobody may walk through it.</li>
 *   <li><b>2 - parted.</b> Invisible, no collision, no light. The renderer keeps the leaf drawn
 *       where it swung to, and the doorway is now a doorway.</li>
 * </ul>
 *
 * <p>The same three phases, through {@link #PHASE}, are on the lock in the middle.
 */
public class ChamberDoorLeafBlock extends Block {
    public static final int SHUT = 0;
    public static final int MOVING = 1;
    public static final int PARTED = 2;

    public static final IntegerProperty PHASE = IntegerProperty.create("phase", SHUT, PARTED);

    public ChamberDoorLeafBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, SHUT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return renderShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return shape(state);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return occlusion(state);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return lightBlock(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PHASE) == PARTED;
    }

    // Shared with the lock, which has the same three phases but is a block entity block.

    static RenderShape renderShape(BlockState state) {
        return state.getValue(PHASE) == SHUT ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    static VoxelShape shape(BlockState state) {
        return state.getValue(PHASE) == PARTED ? Shapes.empty() : Shapes.block();
    }

    /**
     * A full occluder only while shut. An invisible cell that still claimed to occlude would cull the
     * faces of the frame stone next to it, and the frame would show holes into the void.
     */
    static VoxelShape occlusion(BlockState state) {
        return state.getValue(PHASE) == SHUT ? Shapes.block() : Shapes.empty();
    }

    static int lightBlock(BlockState state) {
        return state.getValue(PHASE) == PARTED ? 0 : 15;
    }
}
