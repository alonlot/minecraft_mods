package com.hogwartsmagic.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Where things go in the Mini Hogwarts castle, as the castle generator wrote them down next to the schematic
 * ({@code mini_hogwarts.anchors.json}). The furnishing, the ceiling and the doors read these instead of guessing
 * from the finished blocks, so a room only has to say "a chest here, a statue there" and the world gets it.
 *
 * <p>Every position is local to the schematic's min corner ({@code (0, 0, 0)}); a caller adds the placement
 * anchor. Immutable, so worldgen threads can read it at the same time.
 *
 * <p>How each key is read:
 * <ul>
 *   <li>{@code chests}: {@code at} is the empty floor cell the chest stands in; {@code theme} names a loot theme.</li>
 *   <li>{@code paintings}, {@code fat_lady} and {@code sir_cadogan}: {@code at} (or {@code wall}) is the wall block
 *       the canvas hangs on, {@code facing} is the way the canvas faces, so the canvas hangs in
 *       {@code at.relative(facing)}. For the two living portraits that cell is the entity's tile cell (see
 *       {@code PortraitPlacement.canvasCell}).</li>
 *   <li>{@code banners}: {@code at} is the cell the wall banner itself occupies (air, against a wall) and
 *       {@code facing} is the way the banner faces, away from that wall; {@code house} picks the colour.</li>
 *   <li>{@code stool}: the middle of the Sorting stool's slab (a point, so {@code .5} on all three axes);
 *       {@link #stool()} is the slab's block.</li>
 *   <li>{@code hall_box}: inclusive cell corners; {@link #hallBox()} is the box those cells fill.</li>
 *   <li>everything else is a plain point, a cell, or a list of them.</li>
 * </ul>
 */
public final class MiniHogwartsAnchors {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");
    private static final String RESOURCE = "/data/hogwartsmagic/schematic/mini_hogwarts.anchors.json";

    private static MiniHogwartsAnchors instance;
    private static boolean loadFailed;
    /** GameTests swap in anchors for their small stand-in castle here; null in normal play. */
    private static volatile MiniHogwartsAnchors testOverride;

    /** A themed chest: the cell it stands in, the way it faces, and the loot theme (an enum name, as written). */
    public record Chest(BlockPos at, Direction facing, String theme) {
    }

    /** A statue post: the feet cell and the yaw it stands at (Minecraft degrees). */
    public record Statue(BlockPos at, float yaw) {
    }

    /** A wall block and the way what hangs on it faces. */
    public record Wall(BlockPos at, Direction facing) {
    }

    /** A wall banner: its own cell, the way it faces, and a house name in lower case. */
    public record Banner(BlockPos at, Direction facing, String house) {
    }

    /**
     * The enchanted ceiling plane: its XZ corners and its height, and {@code clearance}, the blocks from the plane up to
     * the roof's underside (0 when the file does not say, which the renderers treat as no room for storm cloud).
     */
    public record Ceiling(double minX, double minZ, double maxX, double maxZ, double y, double clearance) {
        public Ceiling(double minX, double minZ, double maxX, double maxZ, double y) {
            this(minX, minZ, maxX, maxZ, y, 0.0);
        }
    }

    /** A box of floating candles: where, how far each axis may scatter, and how many. */
    public record Box(Vec3 at, Vec3 spread, int count) {
    }

    /** A light ray through a hall window: where, its half-extents, and the yaw it points. */
    public record Ray(Vec3 at, Vec3 spread, float yaw) {
    }

    /** A door leaf: the hinge point, whether the leaf is mirrored, and its closed yaw. */
    public record Door(Vec3 hinge, boolean mirrored, float yawClosed) {
    }

    /** One cell of a moving part and the block-state string it holds in one state ({@code minecraft:cave_air} when empty). */
    public record Placed(BlockPos at, String state) {
    }

    /** A part of the gargoyle stair in its two states; both lists cover the same cells. */
    public record Part(List<Placed> open, List<Placed> closed) {
    }

    /**
     * The Headmaster's stair: {@code listen} is the gargoyle's cell (the password is heard near it), {@code officeCall}
     * the office cell where a player leaving makes the stair rise, {@code slide} how far the gargoyle moves from closed
     * to open, {@code treads} each tread's column in climbing order, {@code cover} the planks over the office's opening,
     * {@code riders} the feet cells on the stair (it never sinks under someone).
     */
    public record HeadmasterStair(BlockPos listen, BlockPos officeCall, Part gargoyle, BlockPos slide, List<Part> treads,
                                  Part cover, List<BlockPos> riders) {
    }

    /**
     * A moving flight: it swings about the vertical axis through {@code (pivotX, pivotZ)} (block corners) between its
     * configs, config {@code k} being {@code turns[k]} clockwise quarter turns from config 0 (the docked one the
     * schematic has). {@code configs[k]} gives the state of every cell either config uses; {@code moving} the blocks
     * that swing, in config 0's places; {@code gates[k]} the cells of config k that stay put (fences closing an edge);
     * {@code riders} the feet cells of both configs (it never swings with anyone on it); {@code near} its middle.
     */
    public record MovingStair(String name, double pivotX, double pivotZ, List<Integer> turns, List<List<Placed>> configs,
                              List<Placed> moving, List<List<BlockPos>> gates, List<BlockPos> riders, BlockPos near) {
    }

    private final List<Chest> chests;
    private final List<BlockPos> studentSpots;
    private final List<BlockPos> elfSpots;
    private final List<Statue> statues;
    @Nullable
    private final BlockPos pensieve;
    @Nullable
    private final Wall fatLady;
    @Nullable
    private final Wall sirCadogan;
    private final List<Wall> paintings;
    private final List<Banner> banners;
    private final Ceiling hallCeiling;
    private final List<Box> candles;
    private final List<Ray> rays;
    private final List<Door> doors;
    private final AABB hallBox;
    private final BlockPos mainEntrance;
    @Nullable
    private final BlockPos titlePoint;
    @Nullable
    private final BlockPos stool;
    @Nullable
    private final BlockPos officePensieve;
    @Nullable
    private final BlockPos officeHat;
    @Nullable
    private final BlockPos officeSword;
    @Nullable
    private final HeadmasterStair headmasterStair;
    private final List<MovingStair> movingStairs;

    private MiniHogwartsAnchors(JsonObject json) {
        this.chests = list(json, "chests", MiniHogwartsAnchors::chest);
        this.studentSpots = list(json, "student_spots", MiniHogwartsAnchors::cell);
        this.elfSpots = list(json, "elf_spots", MiniHogwartsAnchors::cell);
        this.statues = list(json, "statues", MiniHogwartsAnchors::statue);
        this.pensieve = json.has("pensieve") ? cell(json.get("pensieve")) : null;
        this.fatLady = json.has("fat_lady") ? wall(json.get("fat_lady")) : null;
        this.sirCadogan = json.has("sir_cadogan") ? wall(json.get("sir_cadogan")) : null;
        this.paintings = list(json, "paintings", MiniHogwartsAnchors::wall);
        this.banners = list(json, "banners", MiniHogwartsAnchors::banner);
        this.hallCeiling = ceiling(require(json, "hall_ceiling"));
        this.candles = list(json, "candles", MiniHogwartsAnchors::box);
        this.rays = list(json, "rays", MiniHogwartsAnchors::ray);
        this.doors = list(json, "doors", MiniHogwartsAnchors::door);
        this.hallBox = hallBox(require(json, "hall_box"));
        this.mainEntrance = cell(require(json, "main_entrance"));
        this.titlePoint = json.has("title_point") ? cell(json.get("title_point")) : null;
        this.stool = json.has("stool") ? cell(json.get("stool")) : null;
        this.officePensieve = json.has("office_pensieve") ? cell(json.get("office_pensieve")) : null;
        this.officeHat = json.has("office_hat") ? cell(json.get("office_hat")) : null;
        this.officeSword = json.has("office_sword") ? cell(json.get("office_sword")) : null;
        this.headmasterStair = json.has("headmaster_stair") ? headmasterStair(json.get("headmaster_stair")) : null;
        this.movingStairs = list(json, "moving_stairs", MiniHogwartsAnchors::movingStair);
    }

    /**
     * The shipped anchors, loaded once. Null (and an error logged) when the file is missing or unreadable, which
     * sends the furnishing back to its schematic-only heuristics. A GameTest's override wins while it is set.
     */
    @Nullable
    public static synchronized MiniHogwartsAnchors get() {
        MiniHogwartsAnchors override = testOverride;
        if (override != null) {
            return override;
        }
        if (instance != null) {
            return instance;
        }
        if (loadFailed) {
            return null;
        }
        try (InputStream in = MiniHogwartsAnchors.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("anchors resource not found on classpath: " + RESOURCE);
            }
            instance = parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            loadFailed = true;
            LOGGER.error("Failed to load mini_hogwarts anchors; furnishing falls back to heuristics", e);
        }
        return instance;
    }

    /** Reads an anchors document; throws {@link IllegalArgumentException} when a present key is malformed. */
    public static MiniHogwartsAnchors parse(JsonObject json) {
        try {
            return new MiniHogwartsAnchors(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed mini_hogwarts anchors: " + e.getMessage(), e);
        }
    }

    /**
     * The anchors that belong to {@code schematic}: the shipped ones for the castle in the jar, and for a
     * GameTest's stand-in castle only what that test set with {@link #overrideForTests} (none by default, so the
     * real castle's positions are never read against a different building).
     */
    @Nullable
    public static MiniHogwartsAnchors forSchematic(@Nullable MiniHogwartsSchematic schematic) {
        if (MiniHogwartsSchematic.isTestSchematic(schematic)) {
            return testOverride;
        }
        return get();
    }

    /** Makes {@link #get()} return {@code anchors} until called again with null. GameTests only. */
    public static void overrideForTests(@Nullable MiniHogwartsAnchors anchors) {
        testOverride = anchors;
    }

    // ── accessors ────────────────────────────────────────────────────────────────────────

    public List<Chest> chests() {
        return chests;
    }

    public List<BlockPos> studentSpots() {
        return studentSpots;
    }

    /** House-elf cells; empty until the kitchens exist, when the furnishing falls back to its own guess. */
    public List<BlockPos> elfSpots() {
        return elfSpots;
    }

    public List<Statue> statues() {
        return statues;
    }

    @Nullable
    public BlockPos pensieve() {
        return pensieve;
    }

    @Nullable
    public Wall fatLady() {
        return fatLady;
    }

    /** Sir Cadogan's wall: on the imperial landing, beside the door into the Grand Staircase. */
    @Nullable
    public Wall sirCadogan() {
        return sirCadogan;
    }

    public List<Wall> paintings() {
        return paintings;
    }

    public List<Banner> banners() {
        return banners;
    }

    public Ceiling hallCeiling() {
        return hallCeiling;
    }

    public List<Box> candles() {
        return candles;
    }

    public List<Ray> rays() {
        return rays;
    }

    public List<Door> doors() {
        return doors;
    }

    /** The Great Hall's volume: the {@code hall_box} cells, so its far edges are one past the max cell. */
    public AABB hallBox() {
        return hallBox;
    }

    public BlockPos mainEntrance() {
        return mainEntrance;
    }

    /** Where the "Hogwarts" title card looks; null until a room names one, when the castle's centre stands in. */
    @Nullable
    public BlockPos titlePoint() {
        return titlePoint;
    }

    /** The block of the Sorting stool's slab, or null when the castle has no stool. */
    @Nullable
    public BlockPos stool() {
        return stool;
    }

    /** The Pensieve in the Headmaster's office (public, like the library's), or null. */
    @Nullable
    public BlockPos officePensieve() {
        return officePensieve;
    }

    /** The slab on the shelf behind the Headmaster's chair where the Sorting Hat rests between sortings, or null. */
    @Nullable
    public BlockPos officeHat() {
        return officeHat;
    }

    /** The lower of the two glass blocks of the Sword of Gryffindor's case in the Headmaster's office, or null. */
    @Nullable
    public BlockPos officeSword() {
        return officeSword;
    }

    /** The gargoyle stair up to the Headmaster's office, or null when the castle has none. */
    @Nullable
    public HeadmasterStair headmasterStair() {
        return headmasterStair;
    }

    /** The Grand Staircase's moving flights (empty when the castle has none). */
    public List<MovingStair> movingStairs() {
        return movingStairs;
    }

    // ── reading ──────────────────────────────────────────────────────────────────────────

    private static JsonElement require(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (e == null) {
            throw new IllegalArgumentException("missing key '" + key + "'");
        }
        return e;
    }

    private static <T> List<T> list(JsonObject json, String key, java.util.function.Function<JsonElement, T> reader) {
        List<T> out = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement e : json.getAsJsonArray(key)) {
                out.add(reader.apply(e));
            }
        }
        return List.copyOf(out);
    }

    private static double[] numbers(JsonElement e, int count) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != count) {
            throw new IllegalArgumentException("expected " + count + " numbers, got " + a);
        }
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = a.get(i).getAsDouble();
        }
        return out;
    }

    /** A block cell; a point such as the stool's is floored to the block holding it. */
    private static BlockPos cell(JsonElement e) {
        double[] n = numbers(e, 3);
        return BlockPos.containing(n[0], n[1], n[2]);
    }

    private static Vec3 point(JsonElement e) {
        double[] n = numbers(e, 3);
        return new Vec3(n[0], n[1], n[2]);
    }

    private static Direction facing(JsonObject o) {
        String name = o.get("facing").getAsString();
        Direction d = Direction.byName(name);
        if (d == null || d.getAxis().isVertical()) {
            throw new IllegalArgumentException("not a horizontal direction: " + name);
        }
        return d;
    }

    private static Chest chest(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Chest(cell(o.get("at")), facing(o), o.get("theme").getAsString());
    }

    private static Statue statue(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Statue(cell(o.get("at")), o.get("yaw").getAsFloat());
    }

    /** A wall anchor: {@code at} for the paintings, {@code wall} for the Fat Lady. */
    private static Wall wall(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Wall(cell(o.has("at") ? o.get("at") : o.get("wall")), facing(o));
    }

    private static Banner banner(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Banner(cell(o.get("at")), facing(o), o.get("house").getAsString());
    }

    private static Ceiling ceiling(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        double[] min = numbers(o.get("min"), 2);
        double[] max = numbers(o.get("max"), 2);
        return new Ceiling(min[0], min[1], max[0], max[1], o.get("y").getAsDouble(),
                o.has("clearance") ? Math.max(0.0, o.get("clearance").getAsDouble()) : 0.0);
    }

    private static Box box(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Box(point(o.get("at")), point(o.get("spread")), o.get("count").getAsInt());
    }

    private static Ray ray(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Ray(point(o.get("at")), point(o.get("spread")), o.get("yaw").getAsFloat());
    }

    private static Door door(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Door(point(o.get("hinge")), o.get("mirrored").getAsBoolean(), o.get("yaw_closed").getAsFloat());
    }

    private static AABB hallBox(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        double[] min = numbers(o.get("min"), 3);
        double[] max = numbers(o.get("max"), 3);
        return new AABB(min[0], min[1], min[2], max[0] + 1, max[1] + 1, max[2] + 1);
    }

    private static List<Placed> placedList(JsonElement e) {
        List<Placed> out = new ArrayList<>();
        for (JsonElement entry : e.getAsJsonArray()) {
            JsonArray a = entry.getAsJsonArray();
            if (a.size() != 4) {
                throw new IllegalArgumentException("expected [x, y, z, state], got " + a);
            }
            out.add(new Placed(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()),
                    a.get(3).getAsString()));
        }
        return List.copyOf(out);
    }

    private static List<BlockPos> cells(JsonElement e) {
        List<BlockPos> out = new ArrayList<>();
        for (JsonElement entry : e.getAsJsonArray()) {
            out.add(cell(entry));
        }
        return List.copyOf(out);
    }

    private static Part part(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        Part p = new Part(placedList(require(o, "open")), placedList(require(o, "closed")));
        if (p.open().size() != p.closed().size()) {
            throw new IllegalArgumentException("a stair part's open and closed states cover different cells");
        }
        return p;
    }

    private static HeadmasterStair headmasterStair(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        JsonObject gargoyle = require(o, "gargoyle").getAsJsonObject();
        List<Part> treads = new ArrayList<>();
        for (JsonElement t : require(o, "treads").getAsJsonArray()) {
            treads.add(part(t));
        }
        return new HeadmasterStair(cell(require(o, "listen")), cell(require(o, "office_call")), part(gargoyle),
                cell(require(gargoyle, "slide")), List.copyOf(treads), part(require(o, "cover")),
                cells(require(o, "riders")));
    }

    private static MovingStair movingStair(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        double[] pivot = numbers(require(o, "pivot"), 2);
        List<Integer> turns = new ArrayList<>();
        for (JsonElement t : require(o, "turns").getAsJsonArray()) {
            turns.add(t.getAsInt());
        }
        List<List<Placed>> configs = new ArrayList<>();
        for (JsonElement c : require(o, "configs").getAsJsonArray()) {
            configs.add(placedList(c));
        }
        List<List<BlockPos>> gates = new ArrayList<>();
        for (JsonElement g : require(o, "gates").getAsJsonArray()) {
            gates.add(cells(g));
        }
        if (configs.size() < 2 || configs.size() != turns.size() || configs.size() != gates.size()) {
            throw new IllegalArgumentException("a moving stair needs matching configs, turns and gates");
        }
        return new MovingStair(require(o, "name").getAsString(), pivot[0], pivot[1], List.copyOf(turns),
                List.copyOf(configs), placedList(require(o, "moving")), List.copyOf(gates), cells(require(o, "riders")),
                cell(require(o, "near")));
    }
}
