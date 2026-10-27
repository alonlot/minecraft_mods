package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One playing memory: its scene, its region of the memory dimension and the people watching it. Server side
 * only; owned and mutated by {@link PensieveSessions} on the server thread.
 */
public final class Session {
    /**
     * Server only, never on the wire. {@code HANDOVER}: the clock finished (or {@link PensieveSessions#handOver} was
     * asked for) and the next scene is not built yet, or the asked-for fade is not over, so the viewers wait pinned at
     * this anchor until it is.
     */
    public enum Phase { BUILD, PLAY, HANDOVER, CLOSING }

    private final String sceneId;
    private final String rawJson;
    private final SceneDef scene;
    private final int slot;
    private final BlockPos origin;
    @Nullable private final ResourceKey<Level> basinDim;
    @Nullable private final BlockPos basinPos;
    private final boolean preview;
    private final Map<UUID, Viewer> viewers = new LinkedHashMap<>();

    Phase phase = Phase.BUILD;
    long startGameTime = -1;
    /** Memory-level game time when the first viewer crossed; -1 until then. */
    long firstCrossTime = -1;
    @Nullable SetBuilder builder;
    boolean built;
    /** The level the set is built in (the memory level, or the gametest stand-in). */
    ServerLevel level;
    /** How many of the builder's placed blocks are already in the saved region box. */
    int recorded;
    /** Blocks the scene's own block events placed, cleared with the set. */
    final List<BlockPos> eventPlaced = new ArrayList<>();
    /** Index of the next block event (events are kept sorted by time). */
    int nextEvent;
    final List<SceneDef.EventDef> blockEvents;
    /** The basin's chunk while this session holds an entity-ticking ticket on it (the body must stay live). */
    @Nullable ChunkPos basinTicket;
    @Nullable ServerLevel basinTicketLevel;
    /**
     * Built ahead as the next scene of a chain: no viewers, not found by its basin, and not finished for being
     * empty. Cleared when the viewers are handed over to it.
     */
    boolean standby;
    /** The standby session this one hands its viewers to when its clock finishes; null when unchained. */
    @Nullable Session nextSession;
    /** {@link PensieveSessions#handOver} was asked for: the viewers move no sooner than {@link #handOverNotBefore}. */
    boolean handOverAsked;
    /** Memory-level game time the asked-for hand-over's fade to black is over. */
    long handOverNotBefore = -1;
    /** Ticks a hand-over has waited for the next recorded scene to be ready for the clients. */
    int handoverWaitTicks;
    /** The scene id that began the chain; a fresh session's own id. */
    String chainRoot;
    /** Every scene id this chain has played or is playing, this one included: a next scene already here is a cycle. */
    final java.util.Set<String> chainIds = new java.util.HashSet<>();
    /**
     * The start context the chain began with, already resolved (see {@code ItemText.resolve}): what fills a scene's
     * placeholders, such as {@code {viewer_wand}}, in this scene and every scene the chain hands over to.
     */
    Map<String, String> context = Map.of();
    /** The recording a recorded memory replays; null for a story memory. */
    @Nullable Recording recording;
    /** A recorded memory's block changes, oldest first, replayed from PLAY by {@code PensieveSessions}. */
    final List<Recording.BlockChange> recordedChanges = new ArrayList<>();
    /** The cells a recorded change has already put into {@link #eventPlaced}, so each is cleared once. */
    final java.util.Set<Long> changedCells = new java.util.HashSet<>();
    /** Index of the next recorded block change. */
    int nextChange;
    /** The recording's palette as block states (unknown blocks are air), indexed like the recorded changes. */
    BlockState[] recordedStates = new BlockState[0];
    /** Where viewers float instead of one block above the origin (a recorded memory's start); null for none. */
    @Nullable Vec3 anchorOverride;
    /** The set's radius, as the builder clips it; the island a free ghost may roam. */
    int radius = 1;
    /** The viewers last told who the other ghosts are (recorded memories only). */
    java.util.Set<UUID> ghostSet = new java.util.HashSet<>();

    Session(String sceneId, String rawJson, SceneDef scene, int slot, BlockPos origin,
            @Nullable ResourceKey<Level> basinDim, @Nullable BlockPos basinPos, boolean preview) {
        this.sceneId = sceneId;
        this.rawJson = rawJson;
        this.scene = scene;
        this.slot = slot;
        this.origin = origin;
        this.basinDim = basinDim;
        this.basinPos = basinPos == null ? null : basinPos.immutable();
        this.preview = preview;
        this.chainRoot = sceneId;
        this.chainIds.add(sceneId);
        List<SceneDef.EventDef> blocks = new ArrayList<>();
        if (scene.events() != null) {
            for (SceneDef.EventDef e : scene.events()) {
                if ("block".equals(e.kind())) blocks.add(e);
            }
        }
        blocks.sort((a, b) -> Float.compare(a.t(), b.t()));
        this.blockEvents = blocks;
    }

    /** The recording this session replays, or null for a story memory. */
    @Nullable
    public Recording recording() {
        return recording;
    }

    public String sceneId() {
        return sceneId;
    }

    public String rawJson() {
        return rawJson;
    }

    public SceneDef scene() {
        return scene;
    }

    public int slot() {
        return slot;
    }

    /** How many blocks the builder has put down so far (the block events and recorded changes are extra). */
    public int placedBlocks() {
        return builder == null ? 0 : builder.placed().size();
    }

    /** The set's origin in the memory dimension. */
    public BlockPos origin() {
        return origin;
    }

    /** Null for an op preview, which has no basin. */
    @Nullable
    public ResourceKey<Level> basinDim() {
        return basinDim;
    }

    @Nullable
    public BlockPos basinPos() {
        return basinPos;
    }

    public boolean preview() {
        return preview;
    }

    public Phase phase() {
        return phase;
    }

    /** Memory-dimension game time when {@code PLAY} began; -1 while building. */
    public long startGameTime() {
        return startGameTime;
    }

    public Map<UUID, Viewer> viewers() {
        return viewers;
    }

    /** The scene id that began this chain of scenes (this session's own id when it is not chained). */
    public String chainRoot() {
        return chainRoot;
    }

    /** True while this is a next scene built ahead, before any viewer is handed to it. */
    public boolean standby() {
        return standby;
    }

    /** The standby next scene of the chain, if one is built or building; null otherwise. */
    @Nullable
    public Session nextSession() {
        return nextSession;
    }

    /** Where viewers float: one block above the origin, centred. */
    public Vec3 anchor() {
        if (anchorOverride != null) return anchorOverride;
        return Vec3.atBottomCenterOf(origin).add(0, 1, 0);
    }

    /** True when this session plays from the basin at {@code pos}; never for a standby (its viewers are elsewhere). */
    boolean at(ResourceKey<Level> dim, BlockPos pos) {
        return !standby && basinDim != null && basinDim.equals(dim) && basinPos != null && basinPos.equals(pos);
    }

    /** A viewer's return point, the state to restore, and where they are in the dive. */
    public static final class Viewer {
        enum Stage { SWALLOW, INSIDE, EXITING }

        private final UUID id;
        private final Vec3 returnPos;
        private final float yRot;
        private final float xRot;
        private final ResourceKey<Level> returnDim;
        int swallowTicksLeft;
        @Nullable UUID bodyId;

        Stage stage = Stage.SWALLOW;
        /** True once moved into the memory level (an exit then holds them at the anchor, not the basin). */
        boolean crossed;
        int exitTicksLeft;
        @Nullable ExitReason exitReason;
        final boolean wasMayfly;
        final boolean wasFlying;
        final boolean wasInvisible;
        final boolean wasInvulnerable;
        /** The game mode at the dive; if it changed while inside, abilities come from the new mode instead. */
        @Nullable final GameType wasMode;
        /** True when this viewer was already inside as PLAY began: only they have seen the memory whole. */
        boolean sawStart;
        /** True once this viewer was sent the recording's parts (a recorded memory only; once per viewer). */
        boolean recordingSent;

        public Viewer(UUID id, Vec3 returnPos, float yRot, float xRot, ResourceKey<Level> returnDim,
                      int swallowTicksLeft, @Nullable UUID bodyId) {
            this(id, returnPos, yRot, xRot, returnDim, swallowTicksLeft, bodyId, false, false, false, false, null);
        }

        Viewer(UUID id, Vec3 returnPos, float yRot, float xRot, ResourceKey<Level> returnDim, int swallowTicksLeft,
               @Nullable UUID bodyId, boolean wasMayfly, boolean wasFlying, boolean wasInvisible,
               boolean wasInvulnerable, @Nullable GameType wasMode) {
            this.wasMode = wasMode;
            this.id = id;
            this.returnPos = returnPos;
            this.yRot = yRot;
            this.xRot = xRot;
            this.returnDim = returnDim;
            this.swallowTicksLeft = swallowTicksLeft;
            this.bodyId = bodyId;
            this.wasMayfly = wasMayfly;
            this.wasFlying = wasFlying;
            this.wasInvisible = wasInvisible;
            this.wasInvulnerable = wasInvulnerable;
        }

        public UUID id() {
            return id;
        }

        public Vec3 returnPos() {
            return returnPos;
        }

        public float yRot() {
            return yRot;
        }

        public float xRot() {
            return xRot;
        }

        public ResourceKey<Level> returnDim() {
            return returnDim;
        }

        public int swallowTicksLeft() {
            return swallowTicksLeft;
        }

        /** True once the recording was sent to this viewer. */
        public boolean recordingSent() {
            return recordingSent;
        }

        /** True when this viewer was inside before PLAY began (see {@link #sawStart}). */
        public boolean sawStart() {
            return sawStart;
        }

        @Nullable
        public ExitReason exitReason() {
            return exitReason;
        }

        /** The body left at the basin (Task 7); null until then and for previews. */
        @Nullable
        public UUID bodyId() {
            return bodyId;
        }
    }
}
