package com.hogwartsmagic.block.decor;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.SchoolTrunkBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Hogwarts decorations: Great Hall, common room, classroom, Quidditch and furniture. Shapes are in
 * pixels for a NORTH-facing block (see {@link DecorShapes}) and match the block models.
 */
public final class ModDecorations {
    private ModDecorations() {
    }

    private static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Decoration items in creative-tab order. */
    public static final List<RegistryObject<Item>> CREATIVE_ORDER = Collections.unmodifiableList(ITEMS);

    public static final String[] HOUSES = {"gryffindor", "ravenclaw", "hufflepuff", "slytherin"};

    private static BlockBehaviour.Properties copy(Block base) {
        return BlockBehaviour.Properties.ofFullCopy(base);
    }

    private static RegistryObject<Block> block(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = ExampleMod.BLOCKS.register(id, factory);
        ITEMS.add(ExampleMod.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }

    private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return DecorShapes.boxes(new double[]{x1, y1, z1, x2, y2, z2});
    }

    // ---- Great Hall -------------------------------------------------------------------------------

    public static final RegistryObject<Block> FLOATING_CANDLE = block("floating_candle",
            () -> new FloatingCandleBlock(copy(Blocks.CANDLE).strength(0.3f).sound(SoundType.CANDLE)));

    public static final List<RegistryObject<Block>> HANGING_BANNERS = new ArrayList<>();
    public static final List<RegistryObject<Block>> POINTS_HOURGLASSES = new ArrayList<>();
    public static final List<RegistryObject<Block>> CREST_PLAQUES = new ArrayList<>();

    static {
        for (String house : HOUSES) {
            HANGING_BANNERS.add(block(house + "_hanging_banner",
                    () -> new TallWallBannerBlock(copy(Blocks.WHITE_WOOL).strength(0.8f).noCollission()
                            .pushReaction(PushReaction.DESTROY),
                            box(1, 0, 13, 15, 16, 16))));
        }
        for (String house : HOUSES) {
            POINTS_HOURGLASSES.add(block(house + "_points_hourglass",
                    () -> new DecorBlock(copy(Blocks.GLASS).strength(1.0f).sound(SoundType.GLASS)
                            .lightLevel(s -> 5),
                            box(2, 0, 2, 14, 16, 14))));
        }
    }

    // ---- Common room ------------------------------------------------------------------------------

    public static final RegistryObject<Block> SCHOOL_TRUNK = block("school_trunk",
            () -> new SchoolTrunkBlock(copy(Blocks.BARREL).strength(2.0f)));

    public static final RegistryObject<Block> OWL_CAGE = block("owl_cage",
            () -> new DecorBlock(copy(Blocks.IRON_BARS).strength(1.5f).sound(SoundType.LANTERN),
                    box(3, 0, 3, 13, 14, 13)));

    public static final RegistryObject<Block> WIZARD_CHESS_BOARD = block("wizard_chess_board",
            () -> new DecorBlock(copy(Blocks.OAK_PLANKS).strength(0.8f),
                    box(0, 0, 0, 16, 3, 16)));

    static {
        for (String house : HOUSES) {
            CREST_PLAQUES.add(block(house + "_crest_plaque",
                    () -> new WallDecorBlock(copy(Blocks.OAK_PLANKS).strength(1.0f).noCollission(),
                            box(1, 0, 14, 15, 16, 16))));
        }
    }

    // ---- Classroom and library --------------------------------------------------------------------

    public static final RegistryObject<Block> POTION_SHELF = block("potion_shelf",
            () -> new WallDecorBlock(copy(Blocks.OAK_PLANKS).strength(1.0f).sound(SoundType.WOOD),
                    box(0, 0, 10, 16, 11, 16)));

    public static final RegistryObject<Block> BRASS_TELESCOPE = block("brass_telescope",
            () -> new DecorBlock(copy(Blocks.COPPER_BLOCK).strength(1.5f).sound(SoundType.COPPER),
                    box(2, 0, 2, 14, 16, 14)));

    public static final RegistryObject<Block> SCROLL_STACK = block("scroll_stack",
            () -> new DecorBlock(copy(Blocks.WHITE_WOOL).strength(0.3f).sound(SoundType.WOOL),
                    box(1, 0, 2, 15, 5, 15)));

    // ---- Quidditch --------------------------------------------------------------------------------

    public static final RegistryObject<Block> QUIDDITCH_HOOP_POLE = block("quidditch_hoop_pole",
            () -> new DecorBlock(copy(Blocks.GOLD_BLOCK).strength(1.5f).sound(SoundType.METAL),
                    box(6.5, 0, 6.5, 9.5, 16, 9.5)));

    public static final RegistryObject<Block> QUIDDITCH_HOOP = block("quidditch_hoop",
            () -> new DecorBlock(copy(Blocks.GOLD_BLOCK).strength(1.5f).sound(SoundType.METAL),
                    box(3, 0, 7, 13, 16, 9)));

    public static final RegistryObject<Block> BROOM_RACK = block("broom_rack",
            () -> new WallDecorBlock(copy(Blocks.OAK_PLANKS).strength(1.0f),
                    box(1, 0, 11, 15, 15, 16)));

    public static final RegistryObject<Block> QUIDDITCH_CUP = block("quidditch_cup",
            () -> new DecorBlock(copy(Blocks.GOLD_BLOCK).strength(1.5f).sound(SoundType.METAL),
                    box(2, 0, 4, 14, 14, 12)));

    // ---- Furniture --------------------------------------------------------------------------------

    public static final RegistryObject<Block> WIZARD_TABLE = block("wizard_table",
            () -> new TableBlock(copy(Blocks.DARK_OAK_PLANKS).strength(2.0f)));

    public static final RegistryObject<Block> WIZARD_CHAIR = block("wizard_chair",
            () -> new SeatBlock(copy(Blocks.DARK_OAK_PLANKS).strength(1.5f),
                    DecorShapes.boxes(new double[]{2, 0, 2, 14, 9, 14}, new double[]{2, 9, 12, 14, 22, 14}),
                    9.5 / 16.0));

    public static final RegistryObject<Block> GREAT_HALL_BENCH = block("great_hall_bench",
            () -> new SeatBlock(copy(Blocks.DARK_OAK_PLANKS).strength(1.5f),
                    box(0, 0, 3, 16, 8, 13), 8.0 / 16.0));

    public static final RegistryObject<Block> COMMON_ROOM_ARMCHAIR = block("common_room_armchair",
            () -> new SeatBlock(copy(Blocks.RED_WOOL).strength(1.0f),
                    DecorShapes.boxes(new double[]{1, 0, 1, 15, 9, 15}, new double[]{1, 9, 12, 15, 22, 15},
                            new double[]{1, 9, 1, 3, 13, 15}, new double[]{13, 9, 1, 15, 13, 15}),
                    9.0 / 16.0));

    public static final RegistryObject<Block> WRITING_DESK = block("writing_desk",
            () -> new DecorBlock(copy(Blocks.DARK_OAK_PLANKS).strength(2.0f),
                    box(0, 0, 1, 16, 15, 15)));

    // ---- Cosy home ----------------------------------------------------------------------------------

    private static final Block[] HOUSE_WOOL = {Blocks.RED_WOOL, Blocks.BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.GREEN_WOOL};

    /** Same chair as the common room armchair, one in each house velvet. */
    private static final VoxelShape ARMCHAIR_SHAPE = DecorShapes.boxes(
            new double[]{1, 0, 1, 15, 9, 15}, new double[]{1, 9, 12, 15, 22, 15},
            new double[]{1, 9, 1, 3, 13, 15}, new double[]{13, 9, 1, 15, 13, 15});

    public static final List<RegistryObject<Block>> HOUSE_ARMCHAIRS = new ArrayList<>();
    public static final List<RegistryObject<Block>> HOUSE_RUGS = new ArrayList<>();

    static {
        for (int i = 0; i < HOUSES.length; i++) {
            Block wool = HOUSE_WOOL[i];
            HOUSE_ARMCHAIRS.add(block(HOUSES[i] + "_armchair",
                    () -> new SeatBlock(copy(wool).strength(1.0f), ARMCHAIR_SHAPE, 9.0 / 16.0)));
        }
    }

    public static final RegistryObject<Block> PATCHWORK_ARMCHAIR = block("patchwork_armchair",
            () -> new SeatBlock(copy(Blocks.WHITE_WOOL).strength(1.0f), ARMCHAIR_SHAPE, 9.0 / 16.0));

    public static final RegistryObject<Block> SOFA = block("sofa",
            () -> new SofaBlock(copy(Blocks.BROWN_WOOL).strength(1.0f)));

    public static final RegistryObject<Block> FOOTSTOOL = block("footstool",
            () -> new SeatBlock(copy(Blocks.WHITE_WOOL).strength(0.8f), box(3, 0, 3, 13, 6, 13), 6.0 / 16.0));

    public static final RegistryObject<Block> PATCHWORK_QUILT = block("patchwork_quilt",
            () -> new QuiltBlock(copy(Blocks.WHITE_CARPET).strength(0.1f).pushReaction(PushReaction.DESTROY)));

    static {
        for (int i = 0; i < HOUSES.length; i++) {
            Block wool = HOUSE_WOOL[i];
            HOUSE_RUGS.add(block(HOUSES[i] + "_rug",
                    () -> new RugBlock(copy(wool).strength(0.1f).pushReaction(PushReaction.DESTROY))));
        }
    }

    public static final RegistryObject<Block> RAG_RUG = block("rag_rug",
            () -> new RugBlock(copy(Blocks.WHITE_WOOL).strength(0.1f).pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<Block> READING_NOOK_SHELF = block("reading_nook_shelf",
            () -> new DecorBlock(copy(Blocks.BOOKSHELF).strength(1.0f).lightLevel(state -> 12),
                    box(0, 0, 3, 16, 9, 15)));

    // ---- Block entities ---------------------------------------------------------------------------

    public static final RegistryObject<BlockEntityType<SchoolTrunkBlockEntity>> SCHOOL_TRUNK_BLOCK_ENTITY =
            ExampleMod.BLOCK_ENTITY_TYPES.register("school_trunk",
                    () -> BlockEntityType.Builder.of(SchoolTrunkBlockEntity::new, SCHOOL_TRUNK.get()).build(null));

    // ---- Cosy home ----
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.cosy.FloatingCandleBlockEntity>> FLOATING_CANDLE_BLOCK_ENTITY =
            ExampleMod.BLOCK_ENTITY_TYPES.register("floating_candle",
                    () -> BlockEntityType.Builder.of(com.hogwartsmagic.cosy.FloatingCandleBlockEntity::new, FLOATING_CANDLE.get()).build(null));

    /** Loads this class so every entry is queued on the deferred registers before they attach. */
    public static void init() {
    }
}
