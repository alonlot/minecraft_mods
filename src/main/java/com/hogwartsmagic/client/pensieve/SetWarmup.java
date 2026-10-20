package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.mixin.client.LevelRendererAccessor;
import com.hogwartsmagic.pensieve.scene.CameraKey;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.hogwartsmagic.client.pensieve.PensieveTimeline.sectionDistSq;

/**
 * Has the set drawn where the camera is about to be. Vanilla meshes (compiles) a render section only once it is
 * in the visible list, which is culled from the camera's own position and view; and its occlusion search cannot
 * see past a section that was never meshed. A directed camera that cuts across the set, or turns to face the
 * other way, therefore lands among sections that were never drawn (or still hold the mesh from before the set was
 * built), and they stream in over the next frames: the set visibly loading on every cut.
 *
 * <p>So, every client tick of a memory, the dirty sections around the camera's shot now and around every shot a
 * cut lands in over the next {@link #LOOKAHEAD_TICKS} (its position and its look-at point) are meshed ahead,
 * nearest first, a budget per tick. Vanilla's own pass then finds them clean. {@link #ready} tells a chain's
 * hand-over when the new set is drawn around the camera, so it can fade in on it. Client only.
 */
public final class SetWarmup {
    /** How far ahead the shots a cut lands in are drawn. */
    static final float LOOKAHEAD_TICKS = 60;
    /** Sections this close to a shot's camera or look-at point are meshed ahead. */
    private static final double WARM_RADIUS = 40;
    /** Sections queued for meshing per client tick (the work itself runs on vanilla's section workers). */
    private static final int BUDGET = 24;
    /** A hand-over fades in once every section this close to the camera is drawn. */
    private static final double READY_RADIUS = 24;

    private SetWarmup() {
    }

    /** One client tick of BUILD or PLAY: queue the dirty sections around the next shots, nearest first. */
    static void tick(Minecraft mc, float sceneTime) {
        ClientLevel level = mc.level;
        if (level == null || !(mc.levelRenderer instanceof LevelRendererAccessor r)) return;
        ViewArea area = r.hogwartsmagic$viewArea();
        SectionRenderDispatcher dispatcher = r.hogwartsmagic$sectionRenderDispatcher();
        if (area == null || dispatcher == null || area.sections == null) return;
        List<Vec3> focus = focusPoints(sceneTime);
        if (focus.isEmpty()) return;
        double r2 = WARM_RADIUS * WARM_RADIUS;
        List<Candidate> dirty = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection s : area.sections) {
            if (!s.isDirty()) continue;
            BlockPos o = s.getOrigin();
            double best = Double.MAX_VALUE;
            for (Vec3 f : focus) best = Math.min(best, sectionDistSq(o.getX(), o.getY(), o.getZ(), f.x, f.y, f.z));
            if (best > r2 || !level.getLightEngine().lightOnInSection(SectionPos.of(o))) continue;
            dirty.add(new Candidate(s, best));
        }
        dirty.sort(Comparator.comparingDouble(Candidate::distSq));
        List<SectionRenderDispatcher.RenderSection> picked = new ArrayList<>();
        for (int i = 0; i < dirty.size() && i < BUDGET; i++) picked.add(dirty.get(i).section());
        if (picked.isEmpty()) return;
        RenderRegionCache cache = new RenderRegionCache();
        for (SectionRenderDispatcher.RenderSection s : picked) {
            // As LevelRenderer#compileSections does for a visible dirty section.
            s.rebuildSectionAsync(dispatcher, cache);
            s.setNotDirty();
        }
    }

    /**
     * True once every render section within {@link #READY_RADIUS} of {@code at} has its chunk and is meshed: the
     * camera there sees the set, not the void through holes. False while the view area has not moved there yet.
     */
    static boolean ready(Minecraft mc, Vec3 at) {
        ClientLevel level = mc.level;
        if (level == null || at == null || !(mc.levelRenderer instanceof LevelRendererAccessor r)) return true;
        ViewArea area = r.hogwartsmagic$viewArea();
        if (area == null || area.sections == null) return true;
        double r2 = READY_RADIUS * READY_RADIUS;
        int near = 0;
        for (SectionRenderDispatcher.RenderSection s : area.sections) {
            BlockPos o = s.getOrigin();
            if (sectionDistSq(o.getX(), o.getY(), o.getZ(), at.x, at.y, at.z) > r2) continue;
            near++;
            if (!level.hasChunk(SectionPos.blockToSectionCoord(o.getX()), SectionPos.blockToSectionCoord(o.getZ()))) {
                return false;
            }
            if (s.isDirty() || s.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) return false;
        }
        return near > 0;
    }

    /** The camera's shot now and the shots (camera and look-at point) the cuts of the next few seconds land in. */
    private static List<Vec3> focusPoints(float sceneTime) {
        List<Vec3> out = new ArrayList<>();
        Vec3 now = PensieveCamera.shotPosition(sceneTime);
        if (now != null) out.add(now);
        SceneDef scene = PensieveClientState.scene();
        BlockPos o = PensieveClientState.origin();
        if (scene == null || o == null || scene.camera() == null) return out;
        for (CameraKey k : scene.camera().cutsAhead(sceneTime, LOOKAHEAD_TICKS)) {
            out.add(new Vec3(o.getX() + k.pos()[0], o.getY() + k.pos()[1], o.getZ() + k.pos()[2]));
            float[] look = k.lookAt();
            if (look != null) out.add(new Vec3(o.getX() + look[0], o.getY() + look[1], o.getZ() + look[2]));
        }
        return out;
    }

    private record Candidate(SectionRenderDispatcher.RenderSection section, double distSq) {
    }
}
