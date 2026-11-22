package com.hogwartsmagic.worldgen.mechanism;

import com.hogwartsmagic.worldgen.MiniHogwartsAnchors;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The castle mechanisms' shared tools: block-state strings from the anchors turned into states, cells written without
 * waking their neighbours (every state is written as the castle generator worked it out, fences already joined), anyone
 * standing where a block appears lifted onto it, and the block displays that stand in for moving blocks while they move.
 */
final class MechanismBlocks {
    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Mechanisms");

    /** Written with this: clients told, no neighbour updates, no shape updates (a lantern must not drop off its post). */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The tag every mechanism display carries, so one left over from a stopped server is thrown away when it loads. */
    static final String DISPLAY_TAG = "hogwartsmagic_mechanism";
    /** The displays that belong to a running animation now; any other tagged display is a leftover. */
    static final Set<UUID> LIVE = new HashSet<>();

    private static final Map<String, BlockState> PARSED = new HashMap<>();

    private MechanismBlocks() {
    }

    static synchronized BlockState state(String text) {
        return PARSED.computeIfAbsent(text, s -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
            } catch (Exception e) {
                LOGGER.error("Mechanism block state '{}' does not parse; using air", s, e);
                return Blocks.CAVE_AIR.defaultBlockState();
            }
        });
    }

    static boolean isEmpty(BlockState s) {
        return s.isAir() || s.is(Blocks.LIGHT);
    }

    /** Write every entry (local to {@code anchor}), lifting whoever stands in a block that appears. */
    static void write(ServerLevel level, BlockPos anchor, List<MiniHogwartsAnchors.Placed> entries) {
        for (MiniHogwartsAnchors.Placed p : entries) {
            set(level, anchor.offset(p.at()), state(p.state()));
        }
    }

    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
        lift(level, pos, state);
    }

    /** Move every living thing whose box is inside the block just placed at {@code pos} up onto its top. */
    static void lift(ServerLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }
        AABB box = shape.bounds().move(pos);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e.getBoundingBox().intersects(box) && e.getY() < box.maxY) {
                e.teleportTo(e.getX(), box.maxY + 0.01D, e.getZ());
                e.resetFallDistance();
            }
        }
    }

    /**
     * How many of {@code entries} the world agrees with (same block, empty counting as empty), from 0 to 1. A castle
     * the anchors do not describe (one built before the mechanisms existed, or rebuilt by a player) scores low.
     */
    static double agreement(ServerLevel level, BlockPos anchor, List<MiniHogwartsAnchors.Placed> entries) {
        if (entries.isEmpty()) {
            return 1.0D;
        }
        int same = 0;
        for (MiniHogwartsAnchors.Placed p : entries) {
            BlockState want = state(p.state());
            BlockState have = level.getBlockState(anchor.offset(p.at()));
            if (isEmpty(want) ? isEmpty(have) : have.is(want.getBlock())) {
                same++;
            }
        }
        return same / (double) entries.size();
    }

    // ── displays ──────────────────────────────────────────────────────────────────────────

    /**
     * A block display of {@code state} at {@code (x, y, z)} turned {@code yaw}, drawn {@code offset} away from its own
     * position (so a group turns about one point), lit as {@code lightAt} is, smoothed over {@code smoothTicks}.
     */
    @Nullable
    static Display.BlockDisplay spawn(ServerLevel level, BlockState state, double x, double y, double z, float yaw,
                                      float offX, float offY, float offZ, BlockPos lightAt, int smoothTicks) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
        if (d == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(state));
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(offX, offY, offZ));
        t.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        t.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        t.put("scale", floats(1.0F, 1.0F, 1.0F));
        tag.put("transformation", t);
        tag.putInt("teleport_duration", smoothTicks);
        CompoundTag light = new CompoundTag();
        light.putInt("block", Math.max(6, level.getBrightness(LightLayer.BLOCK, lightAt)));
        light.putInt("sky", level.getBrightness(LightLayer.SKY, lightAt));
        tag.put("brightness", light);
        tag.putFloat("view_range", 2.0F);
        d.load(tag);
        d.addTag(DISPLAY_TAG);
        d.moveTo(x, y, z, yaw, 0.0F);
        LIVE.add(d.getUUID());
        if (!level.addFreshEntity(d)) {
            LIVE.remove(d.getUUID());
            return null;
        }
        return d;
    }

    static void move(Entity d, double x, double y, double z, float yaw) {
        d.moveTo(x, y, z, yaw, 0.0F);
    }

    static void discard(ServerLevel level, List<? extends Entity> displays) {
        for (Entity d : displays) {
            LIVE.remove(d.getUUID());
            d.discard();
        }
        displays.clear();
    }

    /** Throw away displays this runtime started, found by id (after a chunk reload the old objects are gone). */
    static void discardIds(ServerLevel level, Set<UUID> ids) {
        for (UUID id : ids) {
            LIVE.remove(id);
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        ids.clear();
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    /** 0..1 eased in and out (smoothstep). */
    static double ease(double u) {
        u = Math.max(0.0D, Math.min(1.0D, u));
        return u * u * (3.0D - 2.0D * u);
    }
}
