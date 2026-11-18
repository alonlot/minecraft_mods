package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client side of blast debris and Reparo. A Bombarda blast throws textured pieces of every block it breaks
 * out from the impact; they lie around as rubble. When Reparo puts a block back, that block's pieces lift
 * and fly home into it (or, if this client has none, a few new pieces fly in from where the server's
 * repair flight starts). Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class BlockDebrisClient {

    /** Most debris pieces alive at once; a big blast gets fewer pieces per block. */
    private static final int MAX_PIECES = 600;
    /** How far Reparo looks for pieces of the same block when none came from the exact position. */
    private static final double BORROW_RADIUS = 6.0;
    /** Flight time home, about the server's own repair flight, so a piece lands as the block reappears. */
    private static final int HOME_TICKS = 13;

    private static final List<BlockDebrisParticle> LIVE = new ArrayList<>();

    private BlockDebrisClient() {
    }

    public static void acceptDebris(SpellPackets.BlockDebrisPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        prune();
        Vec3 impact = new Vec3(packet.x(), packet.y(), packet.z());
        Vec3 out = new Vec3(packet.outX(), packet.outY(), packet.outZ());
        int count = Math.min(packet.positions().length, packet.states().length);
        int perBlock = count > 60 ? 1 : count > 20 ? 2 : 3;
        RandomSource random = level.getRandom();
        for (int i = 0; i < count && LIVE.size() < MAX_PIECES; i++) {
            BlockPos pos = BlockPos.of(packet.positions()[i]);
            BlockState state = Block.stateById(packet.states()[i]);
            if (state.isAir()) {
                continue;
            }
            for (int n = 0; n < perBlock; n++) {
                Vec3 start = Vec3.atCenterOf(pos).add((random.nextDouble() - 0.5) * 0.6,
                        (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
                Vec3 away = start.subtract(impact);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
                // Mostly blown back out of the struck face, spread by where in the blast the block was.
                away = away.scale(0.6).add(out).normalize();
                double force = 0.2 + random.nextDouble() * 0.3;
                float size = 0.18F + random.nextFloat() * 0.16F;
                BlockDebrisParticle piece = new BlockDebrisParticle(level, start.x, start.y - size * 0.5, start.z,
                        state, pos, size);
                piece.launch(away.x * force + (random.nextDouble() - 0.5) * 0.1,
                        away.y * force * 0.6 + 0.15 + random.nextDouble() * 0.2,
                        away.z * force + (random.nextDouble() - 0.5) * 0.1);
                minecraft.particleEngine.add(piece);
                LIVE.add(piece);
            }
        }
    }

    public static void acceptAssemble(SpellPackets.ReparoAssemblePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        prune();
        BlockPos target = packet.target();
        BlockState state = Block.stateById(packet.stateId());
        List<BlockDebrisParticle> pieces = new ArrayList<>();
        for (BlockDebrisParticle piece : LIVE) {
            if (!piece.isReturning() && piece.origin.equals(target)) {
                pieces.add(piece);
            }
        }
        if (pieces.isEmpty()) {
            // Borrow up to three resting pieces of the same block from nearby rubble.
            Vec3 centre = Vec3.atCenterOf(target);
            for (BlockDebrisParticle piece : LIVE) {
                if (pieces.size() >= 3) {
                    break;
                }
                if (!piece.isReturning() && piece.state == state
                        && centre.distanceToSqr(piece.getPos()) < BORROW_RADIUS * BORROW_RADIUS) {
                    pieces.add(piece);
                }
            }
        }
        RandomSource random = level.getRandom();
        if (pieces.isEmpty() && !state.isAir()) {
            Vec3 from = new Vec3(packet.fromX(), packet.fromY(), packet.fromZ());
            for (int n = 0; n < 3; n++) {
                float size = 0.16F + random.nextFloat() * 0.1F;
                BlockDebrisParticle piece = new BlockDebrisParticle(level,
                        from.x + (random.nextDouble() - 0.5) * 0.5, from.y + (random.nextDouble() - 0.5) * 0.3,
                        from.z + (random.nextDouble() - 0.5) * 0.5, state, target, size);
                minecraft.particleEngine.add(piece);
                LIVE.add(piece);
                pieces.add(piece);
            }
        }
        for (BlockDebrisParticle piece : pieces) {
            Vec3 into = Vec3.atCenterOf(target).add((random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.4 - 0.15, (random.nextDouble() - 0.5) * 0.4);
            piece.flyHome(into, HOME_TICKS - 2 + random.nextInt(4));
        }
    }

    private static void prune() {
        for (Iterator<BlockDebrisParticle> it = LIVE.iterator(); it.hasNext(); ) {
            if (!it.next().isAlive()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !LIVE.isEmpty() && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getGameTime() % 40 == 0) {
            prune();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LIVE.clear();
    }
}
