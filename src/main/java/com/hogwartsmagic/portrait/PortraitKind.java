package com.hogwartsmagic.portrait;

import java.util.List;

/**
 * Everything that differs between one living portrait and another: who it is, how big the canvas is, the paper
 * puppet's constants (in the painting's own pixel space) and its lines. One generic entity, brain, item and renderer
 * serve every kind; each kind gets its own entity type and item {@code hogwartsmagic:<id>_portrait}.
 *
 * <p>Assets of a kind live at {@code textures/entity/portrait/<id>/...}, {@code portraits/<id>.json} and sounds
 * {@code portrait.<id>.<key>}. Common code: no client classes in here.
 *
 * @param id          "fat_lady", "sir_cadogan": names the assets and the lines
 * @param displayName shown in the action-bar subtitle, the item name and the entity type name
 * @param widthBlocks canvas width in blocks (2 for the tall Fat Lady, 4 for the wide Sir Cadogan)
 * @param heightBlocks canvas height in blocks
 * @param paintW      width of the painting's pixel space (the texture is that scaled to 512 x 1024 / 1024 x 512)
 * @param paintH      height of the painting's pixel space
 * @param head        the part that bends about the neck
 * @param neckX       neck pivot (painting pixels)
 * @param neckY       neck pivot (painting pixels)
 * @param torso       the part that breathes and flinches
 * @param edgePin     width of the ramp at the canvas edges where the mesh is held still, in painting pixels
 * @param lines       every line this portrait can say
 */
public record PortraitKind(String id, String displayName, int widthBlocks, int heightBlocks, float paintW,
                           float paintH, Ellipse head, float neckX, float neckY, Ellipse torso, float edgePin,
                           List<PortraitLines.Line> lines) {

    /** An elliptical influence area: full strength inside radius {@code from}, none beyond {@code to} (in radii). */
    public record Ellipse(float cx, float cy, float rx, float ry, float from, float to) {
        /** Lowest painting y this ellipse can still move. */
        public float maxY() {
            return cy + ry * to;
        }
    }

    public static final PortraitKind FAT_LADY = new PortraitKind("fat_lady", "The Fat Lady", 2, 4, 688f, 1376f,
            new Ellipse(355f, 400f, 200f, 235f, 0.8f, 1.45f), 348f, 600f,
            new Ellipse(350f, 960f, 440f, 580f, 0.55f, 1.1f), 70f, PortraitLines.forPortrait("fat_lady"));

    public static final PortraitKind SIR_CADOGAN = new PortraitKind("sir_cadogan", "Sir Cadogan", 4, 2, 1376f, 688f,
            new Ellipse(692f, 120f, 100f, 115f, 0.8f, 1.45f), 692f, 230f,
            new Ellipse(690f, 420f, 230f, 320f, 0.55f, 1.1f), 70f, PortraitLines.forPortrait("sir_cadogan"));

    public static final List<PortraitKind> ALL = List.of(FAT_LADY, SIR_CADOGAN);

    /** Id of the entity type, the item and the sound prefix owner: {@code <id>_portrait}. */
    public String registryPath() {
        return id + "_portrait";
    }

    /** Texture folder inside the mod's assets, with a trailing slash. */
    public String texturePath() {
        return "textures/entity/portrait/" + id + "/";
    }

    /** Path of the data json (line envelopes and clip frame counts). */
    public String dataPath() {
        return "portraits/" + id + ".json";
    }

    /** The line with this index, or null. */
    public PortraitLines.Line line(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index) : null;
    }
}
