package com.hogwartsmagic.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The furnishing layer of Mini Hogwarts: the Great Hall's house tables, benches, staff table and
 * chairs, the house banners and crests, and the mod's own decorations in the rooms they belong in.
 *
 * <p>The blocks come from {@code mini_hogwarts_decor.json}, written and checked against the
 * schematic by {@code build_scripts/generate_mini_hogwarts_decor.py}. Positions use the same
 * origin as the schematic, so the castle's placement anchor places them. Wall banners carry a
 * house name, and get that house's pattern here, because a schematic cell cannot hold one.
 */
public final class MiniHogwartsDecor {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");
    private static final String RESOURCE = "/data/hogwartsmagic/schematic/mini_hogwarts_decor.json";

    /**
     * The Great Hall's food chest: in the window bay behind the headmaster's chair, opening west
     * into the bay. The generator keeps this cell free; keep the two in step.
     */
    public static final BlockPos GREAT_HALL_CHEST = new BlockPos(70, 38, 31);

    private record Entry(BlockPos local, BlockState state, String house) {
    }

    private static List<Entry> entries;
    private static boolean loadFailed;

    private MiniHogwartsDecor() {
    }

    /** Place every decoration relative to the castle anchor. Returns how many blocks were set. */
    public static int place(ServerLevel level, BlockPos anchor) {
        List<Entry> list = get();
        if (list == null) {
            return 0;
        }
        HolderGetter<BannerPattern> patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        int placed = 0;
        for (Entry e : list) {
            BlockPos pos = anchor.offset(e.local());
            level.setBlock(pos, e.state(), Block.UPDATE_CLIENTS);
            placed++;
            if (e.house() != null && level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
                // The block already fixes the base colour; the patterns ride in on an item stack,
                // the same way a banner placed by hand gets them.
                ItemStack carrier = new ItemStack(Items.WHITE_BANNER);
                carrier.set(DataComponents.BANNER_PATTERNS, houseLayers(patterns, e.house()));
                banner.applyComponentsFromItemStack(carrier);
                banner.setChanged();
                level.sendBlockUpdated(pos, e.state(), e.state(), Block.UPDATE_CLIENTS);
            }
        }
        return placed;
    }

    /** A lozenge crest with a ringed centre, a pointed hem and a border, in the house's colours. */
    private static BannerPatternLayers houseLayers(HolderGetter<BannerPattern> patterns, String house) {
        DyeColor base;
        DyeColor accent;
        switch (house) {
            case "gryffindor" -> { base = DyeColor.RED; accent = DyeColor.YELLOW; }
            case "hufflepuff" -> { base = DyeColor.YELLOW; accent = DyeColor.BLACK; }
            case "ravenclaw" -> { base = DyeColor.BLUE; accent = DyeColor.ORANGE; } // bronze
            case "slytherin" -> { base = DyeColor.GREEN; accent = DyeColor.LIGHT_GRAY; } // silver
            default -> {
                return BannerPatternLayers.EMPTY;
            }
        }
        return new BannerPatternLayers.Builder()
                .add(patterns.getOrThrow(BannerPatterns.RHOMBUS_MIDDLE), accent)
                .add(patterns.getOrThrow(BannerPatterns.CIRCLE_MIDDLE), base)
                .add(patterns.getOrThrow(BannerPatterns.TRIANGLES_BOTTOM), accent)
                .add(patterns.getOrThrow(BannerPatterns.BORDER), accent)
                .build();
    }

    /** Loads (once) and caches the decoration list. Returns null if the file is missing or broken. */
    private static synchronized List<Entry> get() {
        if (entries != null || loadFailed) {
            return entries;
        }
        try (InputStream in = MiniHogwartsDecor.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Decor resource not found on classpath: " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray blocks = root.getAsJsonArray("blocks");
            List<Entry> out = new ArrayList<>(blocks.size());
            for (JsonElement el : blocks) {
                JsonObject o = el.getAsJsonObject();
                BlockState state = parseState(o.get("state").getAsString());
                if (state == null) {
                    continue;
                }
                BlockPos local = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
                String house = o.has("banner") ? o.get("banner").getAsString() : null;
                out.add(new Entry(local, state, house));
            }
            entries = List.copyOf(out);
            LOGGER.info("Loaded {} Mini Hogwarts decorations", entries.size());
        } catch (Exception ex) {
            loadFailed = true;
            LOGGER.error("Failed to load the Mini Hogwarts decorations", ex);
        }
        return entries;
    }

    private static BlockState parseState(String stateString) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), stateString, false).blockState();
        } catch (CommandSyntaxException ex) {
            LOGGER.warn("Unknown block in the Mini Hogwarts decorations, skipped: {}", stateString);
            return null;
        }
    }
}
