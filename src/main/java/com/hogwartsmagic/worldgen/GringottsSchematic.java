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
 * Loads {@code gringotts.schem} - the imported Gringotts bank - once and caches
 * the decoded block grid.
 *
 * <p>The build was authored in a Minecraft 1.12 world and lifted out by
 * {@code build_scripts/extract_gringotts.py}, which translates the old numeric
 * block IDs to modern blockstate strings and writes a Sponge v2 schematic. Same
 * on-disk format as the Mini Hogwarts castle: a palette of blockstate strings
 * plus a flat LEB128 varint stream indexed {@code x + z*width + y*width*length}.
 */
public final class GringottsSchematic {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Gringotts");
    private static final String RESOURCE = "/data/hogwartsmagic/schematic/gringotts.schem";

    private static GringottsSchematic instance;
    private static boolean loadFailed;

    private final int width;
    private final int height;
    private final int length;
    private final BlockState[] palette;
    private final short[] cells;

    private GringottsSchematic(int width, int height, int length, BlockState[] palette, short[] cells) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.cells = cells;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int length() {
        return this.length;
    }

    public int volume() {
        return this.width * this.height * this.length;
    }

    public int indexOf(int x, int y, int z) {
        return x + z * this.width + y * this.width * this.length;
    }

    /** Block state at a flat index; never null (air fallback). */
    public BlockState blockAt(int index) {
        int paletteId = this.cells[index] & 0xFFFF;
        BlockState state = paletteId < this.palette.length ? this.palette[paletteId] : null;
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    public boolean isAir(int index) {
        return this.cells[index] == 0;
    }

    /** Loads (once) and caches. Returns null if the file is missing or unparseable. */
    public static synchronized GringottsSchematic get() {
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
            LOGGER.error("Failed to load gringotts schematic", e);
        }
        return instance;
    }

    private static GringottsSchematic load() throws Exception {
        CompoundTag root;
        try (InputStream in = GringottsSchematic.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Schematic resource not found on classpath: " + RESOURCE);
            }
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        CompoundTag schem = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        int w = schem.getShort("Width") & 0xFFFF;
        int h = schem.getShort("Height") & 0xFFFF;
        int l = schem.getShort("Length") & 0xFFFF;

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

        LOGGER.info("Loaded gringotts schematic {}x{}x{} ({} cells, palette size {})",
                w, h, l, expected, palette.length);
        return new GringottsSchematic(w, h, l, palette, cells);
    }

    private static BlockState parseState(String stateString) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), stateString, false).blockState();
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Unknown block in gringotts palette, using air: {}", stateString);
            return Blocks.AIR.defaultBlockState();
        }
    }
}
