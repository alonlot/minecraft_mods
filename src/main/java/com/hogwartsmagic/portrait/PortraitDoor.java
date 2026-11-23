package com.hogwartsmagic.portrait;

import com.hogwartsmagic.spell.voice.IncantationMatcher;
import com.hogwartsmagic.worldgen.mechanism.CastleMechanisms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Fat Lady as a door: she guards the passage behind her canvas with a password the players give her.
 *
 * <ul>
 *   <li><b>Recording:</b> right-click her holding paper; she takes the slip and the next thing that player says in
 *       chat (or aloud, through wand voice casting) within 30 s becomes her password.</li>
 *   <li><b>Opening:</b> saying the password within {@link #HEAR_RANGE} blocks in front of her swings the canvas
 *       out on its left edge and opens the wall behind it, up to {@link #MAX_WALL} blocks deep, down to the first
 *       layer you can stand in (the passage). Without such a layer she still swings, but the wall stays.</li>
 *   <li><b>Asking:</b> right-click her empty-handed and she asks "Password?"; a wrong answer in the next 15 s is
 *       turned away. Other chat is never touched.</li>
 *   <li><b>Closing:</b> after {@link #OPEN_TICKS} she swings back once nobody stands in the opened wall, and every
 *       block she took out is put back (only where it is still air). From inside the passage she opens for whoever
 *       stands right behind her facing out, without a password.</li>
 * </ul>
 *
 * <p>Blocks are taken out and put back without neighbour or shape updates, so torches and signs on either face of
 * the wall stay put; blocks with a block entity and unbreakable blocks are never taken. What she took is saved with
 * her, and put back if she is broken or killed while open. Server only; the entity syncs one "open" flag.
 */
final class PortraitDoor {
    static final int OPEN_TICKS = 100;
    static final int MAX_WALL = 3;
    static final int RECORD_WINDOW = 600;
    static final int ASK_WINDOW = 300;
    static final double HEAR_RANGE = 5.0;
    static final int MAX_PASSWORD = 40;

    private static final String[] CORRECT = {
            "Correct! In you go, dear. Mind the step.",
            "Quite right. Off you pop.",
            "That's the one! Do hurry, there's a terrible draught.",
    };

    private record Carved(BlockPos pos, BlockState state) {
    }

    private final PortraitEntity portrait;
    @Nullable
    private String password;
    @Nullable
    private UUID recorder;
    private long recordUntil;
    private final Map<UUID, Long> asked = new HashMap<>();
    private final List<Carved> carved = new ArrayList<>();
    private boolean open;
    private long openedAt;

    PortraitDoor(PortraitEntity portrait) {
        this.portrait = portrait;
    }

    boolean isOpen() {
        return open;
    }

    // ---------------------------------------------------------------- talking to her

    /** A right-click on her. Server only. */
    void interact(ServerPlayer player, InteractionHand hand) {
        long now = player.serverLevel().getGameTime();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.PAPER)) {
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            recorder = player.getUUID();
            recordUntil = now + RECORD_WINDOW;
            asked.remove(player.getUUID());
            portrait.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
            say(player, password == null
                    ? "A password? Ooh, how exciting. Go on, dear, tell me what it is."
                    : "A new password? Very well. Tell me what it is, dear.");
            return;
        }
        if (open) {
            say(player, "I'm already open, dear. In or out, make up your mind.");
        } else if (password == null) {
            say(player, "Password? Oh. Nobody has given me one yet. Bring me a slip of paper, dear.");
        } else {
            asked.put(player.getUUID(), now + ASK_WINDOW);
            say(player, "Password?");
        }
    }

    /** Whether she wants to hear what this player says aloud (their voice client is told so). */
    boolean listensTo(ServerPlayer player, long now) {
        if (player.getUUID().equals(recorder) && now <= recordUntil) {
            return true;
        }
        return !open && password != null && inEarshot(player);
    }

    /**
     * {@code player} said {@code said}, in chat or ({@code spoken}) aloud. True when it was meant for her (a new
     * password, the password, or an answer to her "Password?"); the caller then keeps it out of the public chat.
     */
    boolean heard(ServerPlayer player, String said, boolean spoken) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        UUID id = player.getUUID();
        if (id.equals(recorder)) {
            if (now <= recordUntil) {
                String clean = said.strip().replaceAll("[\\s.!?,]+$", "");
                if (CastleMechanisms.normalise(clean).isEmpty()) {
                    return false;
                }
                if (clean.length() > MAX_PASSWORD) {
                    say(player, "Goodness, that's far too long to remember! Something shorter, dear.");
                    return true;
                }
                password = clean;
                recorder = null;
                portrait.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.8F, 1.2F);
                say(player, "\"" + clean + "\". Lovely. I shan't forget it, and don't you go shouting it about.");
                return true;
            }
            recorder = null;
        }
        asked.values().removeIf(until -> now > until);
        if (open || password == null || !inEarshot(player)) {
            return false;
        }
        boolean right = spoken
                ? IncantationMatcher.saysPhrase(said, password)
                : CastleMechanisms.normalise(said).equals(CastleMechanisms.normalise(password));
        if (right) {
            asked.remove(id);
            open(level, player, false);
            return true;
        }
        if (asked.remove(id) != null) {
            say(player, "Wrong! No password, no entry.");
            return true;
        }
        return false;
    }

    private boolean inEarshot(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        if (eye.subtract(portrait.position()).dot(portrait.frontVector()) <= 0.0) {
            return false;
        }
        AABB box = portrait.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz <= HEAR_RANGE * HEAR_RANGE;
    }

    private void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(portrait.kind().displayName() + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\"" + text + "\"").withStyle(ChatFormatting.WHITE)), true);
    }

    // ---------------------------------------------------------------- opening and closing

    /** Every 10 ticks from the entity. */
    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (recorder != null && now > recordUntil) {
            recorder = null;
        }
        if (open) {
            if (now - openedAt >= OPEN_TICKS && !occupied(level)) {
                close(level);
            }
            return;
        }
        // from inside: whoever stands right behind her, facing out, is let out
        Vec3 front = portrait.frontVector();
        List<ServerPlayer> behind = level.getPlayers(p -> !p.isSpectator() && p.isAlive()
                && p.distanceToSqr(portrait.position()) <= 36.0
                && p.position().subtract(portrait.position()).dot(front) < 0.0
                && p.getLookAngle().dot(front) > 0.3);
        if (behind.isEmpty()) {
            return;
        }
        int depth = passageDepth(level);
        if (depth <= 0) {
            return;
        }
        AABB zone = layerBox(depth);
        for (ServerPlayer p : behind) {
            if (p.getBoundingBox().intersects(zone)) {
                open(level, null, true);
                return;
            }
        }
    }

    private void open(ServerLevel level, @Nullable ServerPlayer who, boolean fromInside) {
        if (open) {
            return;
        }
        int depth = passageDepth(level);
        for (int d = 0; d < depth; d++) {
            for (BlockPos pos : layer(d)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }
                carved.add(new Carved(pos.immutable(), state));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        open = true;
        openedAt = level.getGameTime();
        portrait.setOpenSynced(true);
        level.playSound(null, portrait.getX(), portrait.getY(), portrait.getZ(), SoundEvents.WOODEN_DOOR_OPEN,
                SoundSource.BLOCKS, 1.0F, 0.6F);
        if (who != null && !fromInside) {
            say(who, depth < 0 ? "Correct! Oh... somebody has bricked up my passage. How rude."
                    : CORRECT[level.getRandom().nextInt(CORRECT.length)]);
        }
    }

    private void close(ServerLevel level) {
        restore(level);
        open = false;
        portrait.setOpenSynced(false);
        level.playSound(null, portrait.getX(), portrait.getY(), portrait.getZ(), SoundEvents.WOODEN_DOOR_CLOSE,
                SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /** Puts back what she took out, where it is still air. Also used when she is broken while open. */
    void restore(ServerLevel level) {
        for (Carved c : carved) {
            if (level.isLoaded(c.pos()) && level.getBlockState(c.pos()).isAir()) {
                level.setBlock(c.pos(), c.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        carved.clear();
    }

    /** Somebody (or something alive) is standing where a block would go back. */
    private boolean occupied(ServerLevel level) {
        if (carved.isEmpty()) {
            return false;
        }
        AABB box = null;
        for (Carved c : carved) {
            AABB cell = new AABB(c.pos());
            box = box == null ? cell : box.minmax(cell);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            for (Carved c : carved) {
                if (e.getBoundingBox().intersects(new AABB(c.pos()))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the wall behind her

    /**
     * How many layers of wall stand between her and the passage: the first layer behind the canvas (0 = the block
     * she hangs on) whose two bottom rows you could stand in. -1 when there is none within {@link #MAX_WALL}.
     */
    private int passageDepth(ServerLevel level) {
        for (int d = 0; d <= MAX_WALL; d++) {
            boolean walkable = true;
            for (BlockPos pos : layer(d)) {
                int row = pos.getY() - bottomY();
                if (row <= 1 && (!level.isLoaded(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())) {
                    walkable = false;
                    break;
                }
            }
            if (walkable) {
                return d;
            }
        }
        return -1;
    }

    private int bottomY() {
        int h = portrait.kind().heightBlocks();
        return portrait.tilePos().getY() - (h - 1) / 2;
    }

    /**
     * The cells of wall layer {@code d} behind the canvas, one per canvas cell (layout as in
     * {@code PortraitPlacement.canvasCell}).
     */
    private List<BlockPos> layer(int d) {
        PortraitKind kind = portrait.kind();
        Direction facing = portrait.getDirection();
        Direction right = facing.getCounterClockWise();
        BlockPos base = portrait.tilePos().relative(facing.getOpposite(), d + 1);
        List<BlockPos> out = new ArrayList<>(kind.widthBlocks() * kind.heightBlocks());
        for (int col = 0; col < kind.widthBlocks(); col++) {
            for (int row = 0; row < kind.heightBlocks(); row++) {
                out.add(base.relative(right, col - (kind.widthBlocks() - 1) / 2)
                        .above(row - (kind.heightBlocks() - 1) / 2));
            }
        }
        return out;
    }

    private AABB layerBox(int d) {
        AABB box = null;
        for (BlockPos pos : layer(d)) {
            AABB cell = new AABB(pos);
            box = box == null ? cell : box.minmax(cell);
        }
        return box;
    }

    // ---------------------------------------------------------------- save data

    void save(CompoundTag tag) {
        CompoundTag door = new CompoundTag();
        if (password != null) {
            door.putString("password", password);
        }
        door.putBoolean("open", open);
        door.putLong("openedAt", openedAt);
        ListTag list = new ListTag();
        for (Carved c : carved) {
            CompoundTag e = new CompoundTag();
            e.putInt("x", c.pos().getX());
            e.putInt("y", c.pos().getY());
            e.putInt("z", c.pos().getZ());
            e.put("state", NbtUtils.writeBlockState(c.state()));
            list.add(e);
        }
        door.put("carved", list);
        tag.put("door", door);
    }

    void load(CompoundTag tag) {
        if (!tag.contains("door", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag door = tag.getCompound("door");
        password = door.contains("password", Tag.TAG_STRING) ? door.getString("password") : null;
        open = door.getBoolean("open");
        openedAt = door.getLong("openedAt");
        carved.clear();
        ListTag list = door.getList("carved", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), e.getCompound("state"));
            carved.add(new Carved(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")), state));
        }
    }
}
