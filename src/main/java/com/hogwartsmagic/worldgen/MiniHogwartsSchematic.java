package com.hogwartsmagic.worldgen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Loads {@code mini_hogwarts.schem} (WorldEdit / Sponge schematic, version 3) from the mod jar
 * exactly once and caches the decoded block grid for runtime placement.
 *
 * <p>The Sponge schematic format stores blocks as a flat varint stream indexed
 * {@code x + z*Width + y*Width*Length}, with a palette mapping block-state strings to ids.
 * We resolve each palette entry to a {@link BlockState} up front and keep the per-cell palette
 * ids in a compact {@code short[]} (≈3 MB for this 96×109×145 build) rather than 1.5M object refs.
 */
public final class MiniHogwartsSchematic {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");
    private static final String RESOURCE = "/data/hogwartsmagic/schematic/mini_hogwarts.schem";

    private static MiniHogwartsSchematic instance;
    private static boolean loadFailed;

    private final int width;
    private final int height;
    private final int length;
    private final BlockState[] palette;
    private final short[] cells; // palette id per cell, order x + z*width + y*width*length

    private MiniHogwartsSchematic(int width, int height, int length, BlockState[] palette, short[] cells) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.cells = cells;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public int volume() {
        return width * height * length;
    }

    /** Block state at flat index {@code x + z*width + y*width*length}; never null (AIR fallback). */
    public BlockState blockAt(int index) {
        int paletteId = cells[index] & 0xFFFF;
        BlockState state = paletteId < palette.length ? palette[paletteId] : null;
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    /** Loads (once) and caches the schematic. Returns null if the file is missing or unparseable. */
    public static synchronized MiniHogwartsSchematic get() {
        if (instance != null) {
            return instance;
        }
        if (loadFailed) {
            return null;
        }
        try {
            instance = load();
        } catch (Exception e) {
            loadFailed = true;
            LOGGER.error("Failed to load mini_hogwarts schematic", e);
        }
        return instance;
    }

    private static MiniHogwartsSchematic load() throws Exception {
        CompoundTag root;
        try (InputStream in = MiniHogwartsSchematic.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Schematic resource not found on classpath: " + RESOURCE);
            }
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        // v3 wraps everything in a "Schematic" compound; older files keep fields at the root.
        CompoundTag schem = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        int w = schem.getShort("Width") & 0xFFFF;
        int h = schem.getShort("Height") & 0xFFFF;
        int l = schem.getShort("Length") & 0xFFFF;

        // v3: Palette + Data live under a "Blocks" compound; v2: directly under the root with "BlockData".
        CompoundTag blocksTag = schem.contains("Blocks", Tag.TAG_COMPOUND) ? schem.getCompound("Blocks") : schem;
        CompoundTag paletteTag = blocksTag.getCompound("Palette");
        byte[] data = blocksTag.contains("Data") ? blocksTag.getByteArray("Data") : blocksTag.getByteArray("BlockData");

        int maxId = -1;
        for (String key : paletteTag.getAllKeys()) {
            maxId = Math.max(maxId, paletteTag.getInt(key));
        }
        BlockState[] palette = new BlockState[Math.max(1, maxId + 1)];
        for (String key : paletteTag.getAllKeys()) {
            palette[paletteTag.getInt(key)] = parseState(key);
        }

        int expected = w * h * l;
        short[] cells = new short[expected];
        int cursor = 0;
        int written = 0;
        while (written < expected && cursor < data.length) {
            // Unsigned LEB128 varint.
            int value = 0;
            int shift = 0;
            while (true) {
                byte b = data[cursor++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            cells[written++] = (short) value;
        }
        // Any trailing cells (malformed/truncated data) default to palette id 0; we just leave them.

        LOGGER.info("Loaded mini_hogwarts schematic {}x{}x{} ({} cells, palette size {})",
                w, h, l, expected, palette.length);
        return new MiniHogwartsSchematic(w, h, l, palette, cells);
    }

    private static BlockState parseState(String stateString) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), stateString, false).blockState();
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Unknown block in mini_hogwarts palette, using air: {}", stateString);
            return Blocks.AIR.defaultBlockState();
        }
    }
}
