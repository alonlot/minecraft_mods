package com.hogwartsmagic.pensieve.capture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingAssemblyTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0001-0000-000000000001");
    private static final int TICKS = 600;

    private static Recording.Track track(Random rnd, int actor, UUID uuid, double distance) {
        float[][] lanes = new float[7][TICKS];
        for (int i = 0; i < TICKS; i++) {
            lanes[0][i] = (float) (distance + rnd.nextGaussian() * 3);
            lanes[1][i] = (float) (rnd.nextGaussian() * 3);
            lanes[2][i] = (float) (rnd.nextGaussian() * 3);
            lanes[3][i] = rnd.nextFloat() * 360 - 180;
            lanes[4][i] = rnd.nextFloat() * 360 - 180;
            lanes[5][i] = rnd.nextFloat() * 360 - 180;
            lanes[6][i] = rnd.nextFloat() * 180 - 90;
        }
        return new Recording.Track(actor, "minecraft:zombie", uuid, "", false, null, null, 0, TICKS - 1,
                Recording.REMOVAL_NONE, lanes[0], lanes[1], lanes[2], lanes[3], lanes[4], lanes[5], lanes[6],
                List.of(), List.of(), List.of());
    }

    private static Recording noisy(int tracks, int particles) {
        Random rnd = new Random(7);
        List<Recording.Track> list = new ArrayList<>();
        list.add(track(rnd, 0, OWNER, 0));
        for (int i = 1; i <= tracks; i++) {
            // actor i sits about 2*i blocks away, so a higher actor is farther
            list.add(track(rnd, i, UUID.randomUUID(), 2.0 * i));
        }
        List<Recording.Particle> ps = new ArrayList<>();
        for (int i = 0; i < particles; i++) {
            ps.add(new Recording.Particle(rnd.nextInt(TICKS), rnd.nextFloat() * 60 - 30, rnd.nextFloat() * 60 - 30,
                    rnd.nextFloat() * 60 - 30, rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(),
                    1 + rnd.nextInt(8), new byte[] {(byte) rnd.nextInt(), (byte) rnd.nextInt(), (byte) rnd.nextInt()}));
        }
        Recording.Blocks blocks = new Recording.Blocks(List.of("minecraft:air", "minecraft:stone"), 1,
                new int[27], List.of(), List.of());
        return new Recording(UUID.randomUUID(), OWNER, "Harry", "Test", 3, "minecraft:overworld", new int[] {0, 64, 0},
                8, TICKS, 6000, blocks, list, List.of(), List.of(), List.of(), ps, List.of(), List.of());
    }

    private static int size(Recording r) {
        return RecordingCodec.write(r, false).length;
    }

    @Test
    void thinsToFitCap() {
        Recording big = noisy(50, 10_000);
        Recording withoutParticles = new Recording(big.id(), big.owner(), big.ownerName(), big.title(), big.day(),
                big.dimension(), big.centre(), big.radius(), big.length(), big.dayTime(), big.blocks(), big.tracks(),
                big.beams(), big.casts(), big.sounds(), List.of(), big.levelEvents(), big.chat());
        int cap = size(withoutParticles) / 2; // particles alone cannot make it fit, so tracks must go too
        assertTrue(size(big) > cap);

        Recording thin = RecordingAssembly.thin(big, cap);

        assertTrue(size(thin) <= cap, "fits the cap");
        assertTrue(thin.particles().isEmpty(), "particles go first");
        assertTrue(thin.tracks().stream().anyMatch(t -> OWNER.equals(t.uuid())), "the owner stays");
        List<Integer> dropped = RecordingAssembly.droppedActors(big, thin);
        assertFalse(dropped.isEmpty());
        int nearestDropped = dropped.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int farthestKept = thin.tracks().stream().filter(t -> !OWNER.equals(t.uuid()))
                .mapToInt(Recording.Track::actor).max().orElse(0);
        assertTrue(nearestDropped > farthestKept, "everything dropped is farther than everything kept");
        assertEquals(51 - dropped.size(), thin.tracks().size());
    }

    @Test
    void dropsOnlyParticlesWhenThatIsEnough() {
        Recording big = noisy(5, 20_000);
        Recording thin = RecordingAssembly.thin(big, size(big) - 1);
        assertTrue(thin.particles().isEmpty());
        assertEquals(6, thin.tracks().size());
    }

    @Test
    void leavesAFittingRecordingAlone() {
        Recording small = noisy(2, 10);
        assertEquals(small, RecordingAssembly.thin(small, size(small) + 10));
    }

    @Test
    void throwsWhenTheOwnerAloneIsTooBig() {
        Recording big = noisy(3, 10);
        RecordingAssembly.CaptureException e = assertThrows(RecordingAssembly.CaptureException.class,
                () -> RecordingAssembly.thin(big, 100));
        assertEquals(RecordingAssembly.TOO_BIG, e.key);
    }

    @Test
    void farPlayersAreNotCandidatesButTheOwnerAlwaysIs() {
        List<EntitySelect.Candidate> all = List.of(
                new EntitySelect.Candidate(0, true, true, 500),
                new EntitySelect.Candidate(1, true, false, 3000),
                new EntitySelect.Candidate(2, true, false, 32 + RecordingAssembly.CANDIDATE_MARGIN),
                new EntitySelect.Candidate(3, false, false, 5),
                new EntitySelect.Candidate(4, false, false, 32 + RecordingAssembly.CANDIDATE_MARGIN + 1));
        List<EntitySelect.Candidate> near = RecordingAssembly.withinReach(all, 32);
        assertEquals(List.of(0, 2, 3), near.stream().map(EntitySelect.Candidate::actor).toList());
        // With room for one, the nearby mob is no longer evicted by the player 3000 blocks away.
        assertEquals(List.of(0), EntitySelect.pick(near, 1));
        assertEquals(List.of(0, 2), EntitySelect.pick(near, 2));
    }
}
