package com.hogwartsmagic.worldgen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/**
 * Loads {@code diagon_alley.nbt} once and caches it.
 *
 * <p>Unlike the Gringotts and Mini Hogwarts loaders, which read Sponge schematics -- a dense grid
 * with one palette index per cell -- this reads a <b>structure template</b>: the format Minecraft's
 * own structure blocks write, which is a sparse <i>list</i> of (position, palette index, optional
 * block-entity tag). Sparse is the right shape here for two reasons. Diagon Alley is a surface
 * build, so most of its bounding box is sky that must be left alone rather than filled with air;
 * and it carries baked block entities -- seventeen shop signs with their text already on them, and
 * chests with their loot tables already set -- which a dense grid has nowhere to put.
 *
 * <p>The template is written by {@code build_scripts/generate_diagon_alley.py}. Nothing here
 * assumes anything about the contents: the placer walks the list and the analysis it needs (the
 * footprint, the lowest solid block per column) is derived, not hard-coded, so the build can be
 * re-authored freely without touching Java.
 */
public final class DiagonAlleyTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");
    private static final String RESOURCE = "/data/hogwartsmagic/structure/diagon_alley.nbt";

    private static DiagonAlleyTemplate instance;
    private static boolean loadFailed;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    /** Parallel arrays, one entry per placed block. Kept as primitives -- there are ~76,000. */
    private final short[] x;
    private final short[] y;
    private final short[] z;
    private final short[] state;
    private final CompoundTag[] blockEntity;      // null for all but a few hundred
    private final BlockState[] palette;
    private final List<CompoundTag> entities;
    /**
     * CRC32 of the template's uncompressed NBT. {@link DiagonAlleyRegistry} records it against every
     * street it builds, so a street built from an older version of the file can be told apart from
     * one built from this version and rebuilt the next time somebody opens its wall.
     */
    private final int fingerprint;

    private DiagonAlleyTemplate(int sizeX, int sizeY, int sizeZ, short[] x, short[] y, short[] z,
                                short[] state, CompoundTag[] blockEntity, BlockState[] palette,
                                List<CompoundTag> entities, int fingerprint) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.state = state;
        this.blockEntity = blockEntity;
        this.palette = palette;
        this.entities = entities;
        this.fingerprint = fingerprint;
    }

    /**
     * Identifies this version of the template file. Computed once, at load.
     *
     * <p>Taken over the <i>uncompressed</i> bytes rather than the file as shipped: gzip writes a
     * modification time into its header, so a generator that regenerates an identical template would
     * otherwise change the fingerprint every run and force a pointless rebuild of every street.
     */
    public int fingerprint() {
        return this.fingerprint;
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public int count() {
        return this.x.length;
    }

    public int localX(int i) {
        return this.x[i];
    }

    public int localY(int i) {
        return this.y[i];
    }

    public int localZ(int i) {
        return this.z[i];
    }

    public BlockState stateAt(int i) {
        int id = this.state[i] & 0xFFFF;
        BlockState s = id < this.palette.length ? this.palette[id] : null;
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    /** The block-entity tag for entry {@code i}, or null. Positions inside are rewritten by the placer. */
    public CompoundTag blockEntityAt(int i) {
        return this.blockEntity[i];
    }

    public List<CompoundTag> entities() {
        return this.entities;
    }

    /** Loads (once) and caches. Returns null if the file is missing or unparseable. */
    public static synchronized DiagonAlleyTemplate get() {
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
            LOGGER.error("Failed to load the Diagon Alley template", e);
        }
        return instance;
    }

    private static DiagonAlleyTemplate load() throws Exception {
        CompoundTag root;
        int fingerprint;
        try (InputStream in = DiagonAlleyTemplate.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Template not on the classpath: " + RESOURCE);
            }
            // Decompressed by hand so the same bytes can be both fingerprinted and parsed.
            byte[] raw;
            try (GZIPInputStream unzipped = new GZIPInputStream(in)) {
                raw = unzipped.readAllBytes();
            }
            CRC32 crc = new CRC32();
            crc.update(raw, 0, raw.length);
            fingerprint = (int) crc.getValue();
            // unlimitedHeap: the default accounter caps at 2 MiB and this template is bigger than
            // that uncompressed, so the default fails with a bare "Tried to read NBT tag that was
            // too big" that says nothing about which file.
            root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)),
                    NbtAccounter.unlimitedHeap());
        }

        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        int sx = sizeTag.getInt(0);
        int sy = sizeTag.getInt(1);
        int sz = sizeTag.getInt(2);

        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = parseState(paletteTag.getCompound(i));
        }

        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        int n = blocksTag.size();
        short[] xs = new short[n];
        short[] ys = new short[n];
        short[] zs = new short[n];
        short[] st = new short[n];
        CompoundTag[] bes = new CompoundTag[n];
        for (int i = 0; i < n; i++) {
            CompoundTag entry = blocksTag.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            xs[i] = (short) pos.getInt(0);
            ys[i] = (short) pos.getInt(1);
            zs[i] = (short) pos.getInt(2);
            st[i] = (short) entry.getInt("state");
            if (entry.contains("nbt", Tag.TAG_COMPOUND)) {
                bes[i] = entry.getCompound("nbt");
            }
        }

        List<CompoundTag> ents = new ArrayList<>();
        ListTag entitiesTag = root.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entitiesTag.size(); i++) {
            CompoundTag e = entitiesTag.getCompound(i);
            if (e.contains("nbt", Tag.TAG_COMPOUND)) {
                ents.add(e.getCompound("nbt"));
            }
        }

        LOGGER.info("Loaded the Diagon Alley template: {}x{}x{}, {} blocks, {} palette entries, {} entities, fingerprint {}",
                sx, sy, sz, n, palette.length, ents.size(), Integer.toHexString(fingerprint));
        return new DiagonAlleyTemplate(sx, sy, sz, xs, ys, zs, st, bes, palette, ents, fingerprint);
    }

    /**
     * A palette entry is {@code {Name: "minecraft:oak_stairs", Properties: {facing: "north", ...}}}.
     * Rebuilding the blockstate string and handing it to the command parser is how the two existing
     * schematic loaders do it, and it means unknown blocks degrade to air with a named warning
     * rather than throwing during a placement that is already half done.
     */
    private static BlockState parseState(CompoundTag entry) {
        String name = entry.getString("Name");
        StringBuilder sb = new StringBuilder(name);
        if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag props = entry.getCompound("Properties");
            if (!props.isEmpty()) {
                sb.append('[');
                boolean first = true;
                for (String key : props.getAllKeys()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append(key).append('=').append(props.getString(key));
                }
                sb.append(']');
            }
        }
        String stateString = sb.toString();
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), stateString, false)
                    .blockState();
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Unknown block in the Diagon Alley palette, using air: {}", stateString);
            return Blocks.AIR.defaultBlockState();
        }
    }
}
