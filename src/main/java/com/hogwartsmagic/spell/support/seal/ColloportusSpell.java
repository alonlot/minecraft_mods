package com.hogwartsmagic.spell.support.seal;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.support.lock.CircuitTracer;
import com.hogwartsmagic.spell.support.lock.LockRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Colloportus, the locking charm. A beam at a door, trapdoor or fence gate, or at any block of a piston door (a
 * door piston within LockRules.DOOR_RADIUS), seals every block within the rank's radius against magic. An
 * Alohomora there is refused, or, cast at a strictly higher rank, breaks the seal instead of opening. The sealer
 * passes their own seal and lifts it by casting Colloportus inside it again. Hands, keys and redstone are
 * untouched. Seals last until broken or lifted (SealData). Players only, never through a puppet. No protection
 * checks, as Alohomora. Server only.
 */
public final class ColloportusSpell {
    /** What a Colloportus beam did: made a seal (pays XP), lifted the caster's own (no XP), or nothing (fizzle). */
    public enum Cast { SEALED, LIFTED, NOTHING }

    /** What the seals over a lock do to one Alohomora: let it through, refuse it, or lose one seal to it. */
    public enum Gate { PASS, REFUSED, BROKEN }

    private static final int MAX_REVEAL_LINES = 4;
    /** The piston scan reaches DOOR_RADIUS from the struck block, and a door's other half is one block further. */
    private static final int LOCK_MARGIN = LockRules.DOOR_RADIUS + 1;

    private ColloportusSpell() {
    }

    /** The Colloportus beam struck pos (a loaded block). */
    public static Cast onBlockHit(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster, boolean reflected) {
        if (reflected || MindControl.hitPuppeteer() != null
                || !(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return Cast.NOTHING; // players only, never a reflected beam, never through a puppet
        }
        SealData data = SealData.get(level);
        List<Seal> mine = new ArrayList<>();
        for (Seal seal : data.covering(List.of(pos))) {
            if (seal.owner().equals(player.getUUID())) {
                mine.add(seal);
            }
        }
        if (!mine.isEmpty()) {
            mine.forEach(data::remove);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.3f);
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
            tell(player, "lifted", ChatFormatting.GRAY);
            return Cast.LIFTED;
        }
        if (!isLock(level, pos, state)) {
            tell(player, "no_lock", ChatFormatting.GOLD);
            return Cast.NOTHING;
        }
        MinecraftServer server = level.getServer();
        int rank = DisableSupport.casterRank(player, SpellType.COLLOPORTUS);
        int cap = SealRules.cap(rank);
        int evicted = evictOldest(server, player.getUUID(), SealRules.toEvict(countAll(server, player.getUUID()), cap));
        // Anchors: the door pistons of this lock (nearest first, at most MAX_ANCHORS), so the seal covers them too.
        List<BlockPos> pistons = CircuitTracer.doorPistons(level, pos);
        int anchorCount = Math.min(pistons.size(), SealData.MAX_ANCHORS);
        long[] anchors = new long[anchorCount];
        for (int i = 0; i < anchorCount; i++) {
            anchors[i] = Seal.pack(pistons.get(i).getX(), pistons.get(i).getY(), pistons.get(i).getZ());
        }
        Seal seal = new Seal(player.getUUID(), player.getGameProfile().getName(), rank,
                pos.getX(), pos.getY(), pos.getZ(), SealRules.radius(rank), level.getGameTime(), anchors);
        data.add(seal);
        sealEffects(level, seal);
        player.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus." + (evicted > 0 ? "replaced" : "sealed"),
                countAll(server, player.getUUID()), cap).withStyle(ChatFormatting.AQUA));
        return Cast.SEALED;
    }

    /** A door, trapdoor or fence gate, or a block with a door piston near it (the piston-lock search). */
    public static boolean isLock(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || CircuitTracer.hasDoorPiston(level, pos);
    }

    /**
     * The lock an Alohomora, Revelio or /lockinfo at hit works on: hit itself, the other half when hit is a door
     * (a door is sealed if either half is covered), and the door pistons near it.
     */
    public static List<BlockPos> lockPositions(ServerLevel level, BlockPos hit) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(hit.immutable());
        BlockState state = level.isLoaded(hit) ? level.getBlockState(hit) : null;
        if (state != null && state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            positions.add(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? hit.above() : hit.below());
        }
        positions.addAll(CircuitTracer.doorPistons(level, hit));
        return positions;
    }

    /**
     * Before an Alohomora at hit touches the lock (its mana is already spent). PASS: no seal, or only the caster's
     * own. REFUSED: the caster was told. BROKEN: the highest seal it beat is gone, and this cast does nothing else.
     * Through a puppet there is no owner bypass and no break.
     */
    public static Gate alohomoraGate(ServerLevel level, BlockPos hit, @Nullable Entity caster, int rank) {
        SealData data = SealData.get(level);
        if (data.isEmpty() || !anySealNear(data, hit) || !level.isLoaded(hit)) {
            return Gate.PASS; // no seal near: no piston scan at all
        }
        if (!isLock(level, hit, level.getBlockState(hit))) {
            return Gate.PASS; // a plain wall in a seal is not a lock: Alohomora does what it normally does
        }
        List<Seal> covering = data.covering(lockPositions(level, hit));
        if (covering.isEmpty()) {
            return Gate.PASS;
        }
        boolean puppet = MindControl.hitPuppeteer() != null;
        Entity responsible = CombatEffects.responsible(caster);
        SealRules.Decision decision = SealRules.decide(covering, bypassId(caster), rank, !puppet);
        if (decision.verdict() == SealRules.Verdict.PASS) {
            return Gate.PASS;
        }
        ServerPlayer player = responsible instanceof ServerPlayer sp ? sp : null;
        if (decision.verdict() == SealRules.Verdict.REFUSE) {
            level.playSound(null, hit, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 0.6f);
            if (player != null) {
                tell(player, "refused", ChatFormatting.GOLD);
            }
            return Gate.REFUSED;
        }
        Seal seal = covering.get(decision.target());
        data.remove(seal);
        BlockPos centre = new BlockPos(seal.x(), seal.y(), seal.z());
        level.playSound(null, centre, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.playSound(null, centre, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.7f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5,
                24, 0.5, 0.5, 0.5, 0.15);
        if (player != null) {
            tell(player, "broken", ChatFormatting.AQUA);
        }
        ServerPlayer sealer = level.getServer().getPlayerList().getPlayer(seal.owner());
        if (sealer != null && sealer != player) {
            sealer.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus.broken_yours",
                    responsible != null ? responsible.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.GOLD));
        }
        return Gate.BROKEN;
    }

    /**
     * Cheap test before any piston scan: some seal in the dimension has its centre or an anchor within its radius
     * plus the lock margin of hit. False means no seal can cover the lock at hit.
     */
    private static boolean anySealNear(SealData data, BlockPos hit) {
        for (Seal seal : data.seals()) {
            if (SealRules.near(seal, hit.getX(), hit.getY(), hit.getZ(), LOCK_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    /** The player whose own seals let this cast through; null for an NPC or a puppet cast. */
    @Nullable
    public static UUID bypassId(@Nullable Entity caster) {
        Entity responsible = CombatEffects.responsible(caster);
        return MindControl.hitPuppeteer() == null && responsible instanceof Player p ? p.getUUID() : null;
    }

    /** Alohomora's Expert area: a seal not owned by bypass covers pos (the area never passes or breaks one). */
    public static boolean sealedAgainst(SealData data, BlockPos pos, @Nullable UUID bypass) {
        for (Seal seal : data.seals()) {
            if (!seal.owner().equals(bypass) && SealRules.covers(seal, pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** Revelio at hit: outlines every seal over this lock and names its sealer and rank. False if there is none. */
    public static boolean revealSeals(ServerLevel level, BlockPos hit, ServerPlayer viewer) {
        SealData data = SealData.get(level);
        if (data.isEmpty() || !anySealNear(data, hit)) {
            return false;
        }
        List<Seal> covering = data.covering(lockPositions(level, hit));
        if (covering.isEmpty()) {
            return false;
        }
        for (int i = 0; i < covering.size(); i++) {
            Seal seal = covering.get(i);
            if (i < MAX_REVEAL_LINES) { // the outline only for the seals that also get a line
                outline(level, seal);
                viewer.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus.revealed",
                        seal.ownerName(), Component.translatable(MasteryAwards.rankKey(seal.rank())))
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        level.playSound(null, hit, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.4f);
        return true;
    }

    /** The highest seal over the lock at hit (the oldest of equals), or null: for /lockinfo. */
    @Nullable
    public static Seal strongestSeal(ServerLevel level, BlockPos hit) {
        SealData data = SealData.get(level);
        if (data.isEmpty() || !anySealNear(data, hit)) {
            return null;
        }
        Seal best = null;
        for (Seal seal : data.covering(lockPositions(level, hit))) {
            if (best == null || seal.rank() > best.rank()) {
                best = seal;
            }
        }
        return best;
    }

    private static int countAll(MinecraftServer server, UUID owner) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += SealData.get(level).ownedBy(owner).size();
        }
        return count;
    }

    /**
     * Removes the owner's count oldest seals, in any dimension (all dimensions share one game time). Equal
     * createdAt in one dimension: the earlier in its list (the older) goes first, because the comparison is strict.
     */
    private static int evictOldest(MinecraftServer server, UUID owner, int count) {
        int removed = 0;
        for (int i = 0; i < count; i++) {
            SealData oldestData = null;
            Seal oldest = null;
            for (ServerLevel level : server.getAllLevels()) {
                SealData data = SealData.get(level);
                for (Seal seal : data.ownedBy(owner)) {
                    if (oldest == null || seal.createdAt() < oldest.createdAt()) {
                        oldest = seal;
                        oldestData = data;
                    }
                }
            }
            if (oldest == null) {
                break;
            }
            oldestData.remove(oldest);
            removed++;
        }
        return removed;
    }

    /** A new seal: an iron door shuts and a chain settles, and a ring shows how far it reaches. */
    private static void sealEffects(ServerLevel level, Seal seal) {
        BlockPos pos = new BlockPos(seal.x(), seal.y(), seal.z());
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.9f, 0.8f);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
        double cx = seal.x() + 0.5;
        double cy = seal.y() + 0.5;
        double cz = seal.z() + 0.5;
        int points = 16 + 8 * seal.radius();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            level.sendParticles(ParticleTypes.WAX_ON, cx + Math.cos(a) * seal.radius(), cy, cz + Math.sin(a) * seal.radius(),
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 20, 0.3, 0.4, 0.3, 0.4);
    }

    /** Revelio: one burst of points spread evenly over the seal's sphere (a Fibonacci sphere). */
    private static void outline(ServerLevel level, Seal seal) {
        int r = seal.radius();
        int points = 24 + 12 * r * r;
        double cx = seal.x() + 0.5;
        double cy = seal.y() + 0.5;
        double cz = seal.z() + 0.5;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / points;
            double ring = Math.sqrt(1.0 - y * y);
            double a = golden * i;
            level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * ring * r, cy + y * r, cz + Math.sin(a) * ring * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting colour) {
        player.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus." + key).withStyle(colour));
    }
}
