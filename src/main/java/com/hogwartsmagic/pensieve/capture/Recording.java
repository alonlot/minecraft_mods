package com.hogwartsmagic.pensieve.capture;

import java.util.List;
import java.util.UUID;

/**
 * A captured memory: the island's blocks plus everything that moved or sounded inside the window.
 * Positions are relative to {@code centre}, in blocks; ticks run from 0 at the window's start. Pure data.
 * Records holding arrays compare those by reference, so tests compare field by field.
 */
public record Recording(UUID id, UUID owner, String ownerName, String title, long day, String dimension,
                        int[] centre, int radius, int length, long dayTime, Blocks blocks,
                        List<Track> tracks, List<Beam> beams, List<Cast> casts, List<Sound> sounds,
                        List<Particle> particles, List<LevelEvt> levelEvents, List<Chat> chat) {
    public static final int FORMAT = 1;

    public static final int REMOVAL_NONE = 0;
    public static final int REMOVAL_KILLED = 1;
    public static final int REMOVAL_GONE = 2;

    public static final int ACT_SWING_MAIN = 0;
    public static final int ACT_SWING_OFF = 1;
    public static final int ACT_HURT = 2;
    public static final int ACT_DIE = 3;

    /**
     * The island. {@code palette} holds block-state strings in {@code BlockStateParser.serialize} form with index 0
     * always {@code minecraft:air}. {@code cells} is dense over the cube [-size, size]^3, x fastest, then z, then y;
     * -1 means "nothing, do not place".
     */
    public record Blocks(List<String> palette, int size, int[] cells, List<BlockEntityData> blockEntities,
                         List<BlockChange> changes) {
        public static int index(int x, int y, int z, int size) {
            int side = 2 * size + 1;
            return ((y + size) * side + (z + size)) * side + (x + size);
        }
    }

    public record BlockEntityData(int x, int y, int z, String snbt) {
    }

    public record BlockChange(int tick, int x, int y, int z, int state) {
    }

    /**
     * One recorded entity. The float arrays have length {@code last - first + 1}. {@code data} values are one synched
     * data value as written by {@code DataValue#write} without the id byte; {@code equipment} stacks are written with
     * {@code ItemStack.OPTIONAL_STREAM_CODEC} and {@code slot} is the {@code EquipmentSlot} ordinal.
     */
    public record Track(int actor, String type, UUID uuid, String name, boolean player, String skinValue,
                        String skinSignature, int first, int last, int removal,
                        float[] x, float[] y, float[] z, float[] yaw, float[] headYaw, float[] bodyYaw, float[] pitch,
                        List<DataPatch> data, List<EquipPatch> equipment, List<Act> acts) {
    }

    public record DataPatch(int tick, int key, byte[] value) {
    }

    public record EquipPatch(int tick, int slot, byte[] stack) {
    }

    public record Act(int tick, int kind) {
    }

    /** {@code caster} is -1 when the caster is not recorded. */
    public record Beam(int from, int to, String spell, float[] start, float[] end, int caster) {
    }

    public record Cast(int tick, int actor, String spell, float[] target) {
    }

    public record Sound(int tick, String id, float x, float y, float z, float volume, float pitch) {
    }

    public record Particle(int tick, float x, float y, float z, float dx, float dy, float dz, float speed,
                           int count, byte[] options) {
    }

    public record LevelEvt(int tick, int type, int x, int y, int z, int data) {
    }

    /** {@code sender} is null for a system line. */
    public record Chat(int tick, String sender, String text) {
    }
}
