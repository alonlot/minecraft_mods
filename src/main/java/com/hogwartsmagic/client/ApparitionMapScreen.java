package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.support.ApparitionRules;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import static com.hogwartsmagic.client.ThemedGui.drawPanel;
import static com.hogwartsmagic.client.ThemedGui.drawWell;
import static com.hogwartsmagic.client.ThemedGui.fillAnnulus;
import static com.hogwartsmagic.client.ThemedGui.withAlpha;

/**
 * The Apparition destination picker: a round, inked parchment chart of the loaded land within reach.
 * While it is open the player is "channeling": the server runs the rank's timer, shrinks the caster
 * and cancels if they are hit. The countdown is the gold ring draining round the chart; a dark vortex
 * closes in from the edge and the runes spin faster as focus tightens. Clicking a known tile sends the
 * chosen column to the server, which re-validates and performs the authoritative teleport. The chart
 * always fits the screen: up close one tile is one block, for a Master's reach one tile stands for several.
 *
 * <p>Pure client rendering only. No {@code switch} on enums; backdrop drawn with
 * {@code renderTransparentBackground} (never {@code renderBackground}) so the world stays crisp.
 */
public class ApparitionMapScreen extends Screen {
    private static final Theme TH = ThemedGui.VIOLET;
    private static final int TILE = 2;              // pixels per map tile
    private static final int MAX_TILES = 150;
    private static final int RING = 18;             // space round the chart for the countdown + runes
    private static final int ROW = 12;
    private static final float FULL = (float) (Math.PI * 2.0);
    private static final float START = (float) (-Math.PI / 2.0);
    private static final String RUNES = "apparatedisapparatedestinationdeterminationdeliberation";
    private static final int FLOO = 0x3CFF9A;

    private final BlockPos origin;
    private final int radius;
    private final int durationTicks;
    private final java.util.List<SpellPackets.FlooDestinationData> fireplaces;
    private final RandomSource shakeRand = RandomSource.create();

    private int ticksLeft;
    private boolean selectionSent;

    private int span;       // 2*radius + 1 blocks
    private int step = 1;   // blocks per tile
    private int tiles;      // tiles per side
    private ParchmentMap map;

    private int mapLeft;
    private int mapTop;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int infoY;
    private int listTop;

    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks,
                               java.util.List<SpellPackets.FlooDestinationData> fireplaces) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
        this.fireplaces = fireplaces == null ? java.util.List.of() : java.util.List.copyOf(fireplaces);
    }

    /** Header plus one row per known fireplace; 0 when there are none (below Master). */
    private int fireplaceListHeight() {
        return fireplaces.isEmpty() ? 0 : 16 + ROW * fireplaces.size() + 6;
    }

    /** Index of the fireplace row under the mouse, or -1. */
    private int fireplaceRowAt(double mouseX, double mouseY) {
        if (fireplaces.isEmpty()) {
            return -1;
        }
        if (mouseX < panelLeft + 10 || mouseX >= panelLeft + panelWidth - 10) {
            return -1;
        }
        int row = Math.floorDiv((int) mouseY - (listTop + 14), ROW);
        return row >= 0 && row < fireplaces.size() ? row : -1;
    }

    @Override
    protected void init() {
        super.init();
        span = radius * 2 + 1;
        int maxMapPx = Math.max(64, Math.min(this.width - 60 - RING * 2, this.height - 96 - RING * 2 - fireplaceListHeight()));
        step = Math.max(ApparitionRules.mapStep(span, TILE, maxMapPx), ApparitionRules.mapStep(span, 1, MAX_TILES));
        tiles = (span + step - 1) / step;
        int mapSize = tiles * TILE;
        panelWidth = Math.max(mapSize + RING * 2 + 20, 236);
        panelHeight = 34 + mapSize + RING * 2 + 16 + fireplaceListHeight() + 8;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        mapLeft = panelLeft + (panelWidth - mapSize) / 2;
        mapTop = panelTop + 34 + RING;
        infoY = mapTop + mapSize + RING + 2;
        listTop = infoY + 14;
        buildMap();
    }

    private int worldX(int tileX) {
        return origin.getX() - radius + ApparitionRules.tileCenterOffset(tileX, step, span);
    }

    private int worldZ(int tileZ) {
        return origin.getZ() - radius + ApparitionRules.tileCenterOffset(tileZ, step, span);
    }

    private boolean inReach(int tx, int tz) {
        long ddx = worldX(tx) - origin.getX();
        long ddz = worldZ(tz) - origin.getZ();
        return ddx * ddx + ddz * ddz <= (long) radius * radius;
    }

    /** Bake the chart once (tiles outside the reach circle stay transparent), so render() stays cheap. */
    private void buildMap() {
        if (map != null) {
            map.close();
            map = null;
        }
        Level level = this.minecraft != null ? this.minecraft.level : null;
        if (level == null) {
            return;
        }
        map = ParchmentMap.bake(level, tiles, tiles, step, (tx, tz) ->
                inReach(tx, tz) ? new int[]{worldX(tx), worldZ(tz)} : null);
    }

    private boolean selectable(int tx, int tz) {
        return map != null && tx >= 0 && tz >= 0 && tx < tiles && tz < tiles && inReach(tx, tz) && map.isKnown(tx, tz);
    }

    @Override
    public void removed() {
        super.removed();
        if (map != null) {
            map.close();
            map = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ticksLeft > 0) {
            ticksLeft--;
        }
        if (ticksLeft <= 0 && !selectionSent) {
            onClose(); // ran out of time — cancel
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dimmed backdrop only — no blur shader (see WandRadialMenuScreen / project convention).
        this.renderTransparentBackground(g);

        float remaining = Mth.clamp((ticksLeft - partialTick) / (float) durationTicks, 0f, 1f);
        float progress = 1.0f - remaining;
        float time = Util.getMillis() / 1000f;

        // Screen shake: grows as the focus tightens and time runs out.
        float intensity = progress * progress * 6.0f;
        float shakeX = (shakeRand.nextFloat() * 2.0f - 1.0f) * intensity;
        float shakeY = (shakeRand.nextFloat() * 2.0f - 1.0f) * intensity;
        g.pose().pushPose();
        g.pose().translate(shakeX, shakeY, 0.0f);

        int mapSize = tiles * TILE;
        float r = mapSize / 2f;
        float cx = mapLeft + r;
        float cy = mapTop + r;

        drawPanel(g, panelLeft, panelTop, panelWidth, panelHeight, TH);
        ThemedGui.drawFittedCentered(g, this.font, this.title, panelLeft + panelWidth / 2f, panelTop + 8, TH.title(), panelWidth - 20);
        ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.hint"),
                panelLeft + panelWidth / 2f, panelTop + 20, TH.textDim(), panelWidth - 20);

        // Dark well the chart sits in, with an aura.
        fillAnnulus(g, cx, cy, r + RING - 3, r + RING + 1, 0, FULL, 0x30A066FF);
        fillAnnulus(g, cx, cy, 0, r + RING - 3, 0, FULL, 0xFF120C22);
        if (map != null) {
            map.draw(g, mapLeft, mapTop, mapSize, mapSize);
        }

        // The vortex: darkness closing in from the edge, with swirling arcs that speed up.
        float inner = r * (1f - 0.18f - 0.32f * progress);
        int bands = 7;
        for (int b = 0; b < bands; b++) {
            float r0 = Mth.lerp(b / (float) bands, inner, r);
            float r1 = Mth.lerp((b + 1) / (float) bands, inner, r) + 0.5f;
            int alpha = Math.round((0x10 + 0xB0 * progress) * ((b + 1) / (float) bands) * ((b + 1) / (float) bands));
            fillAnnulus(g, cx, cy, r0, r1, 0, FULL, withAlpha(0x140A2A, alpha));
        }
        float swirl = time * (0.6f + 3.2f * progress);
        for (int a = 0; a < 3; a++) {
            float base = swirl + a * FULL / 3f;
            float rr = Mth.lerp(0.5f, inner, r);
            fillAnnulus(g, cx, cy, rr - 0.6f, rr + 0.6f, base, base + 0.9f, withAlpha(0xA066FF, Math.round(0x30 + 0x50 * progress)));
        }

        // Edge, countdown ring and runes.
        fillAnnulus(g, cx, cy, r, r + 1.5f, 0, FULL, TH.trim());
        float ringR = r + 6f;
        fillAnnulus(g, cx, cy, ringR - 1.5f, ringR + 1.5f, 0, FULL, TH.wellShadow());
        int ringColor = remaining <= 1f / 3f ? ThemedGui.pulse(0xFFFF5A4B, 0xFFFFB0A0) : TH.trimBright();
        float end = START + remaining * FULL;
        if (remaining > 0.001f) {
            fillAnnulus(g, cx, cy, ringR - 1.5f, ringR + 1.5f, START, end, ringColor);
            float hx = cx + Mth.cos(end) * ringR;
            float hy = cy + Mth.sin(end) * ringR;
            fillAnnulus(g, hx, hy, 0, 3.5f, 0, FULL, withAlpha(ringColor, 0x60));
            fillAnnulus(g, hx, hy, 0, 2f, 0, FULL, 0xFFFFF6D8);
        }
        ThemedGui.drawRuneRing(g, this.font, cx, cy, r + 13.5f, time * (0.15f + 1.2f * progress), RUNES,
                withAlpha(0xD9B44A, Math.round(0x60 + 0x60 * progress)), 0.75f);

        // Known fireplaces inside the reach, as little green flames.
        for (SpellPackets.FlooDestinationData fireplace : fireplaces) {
            float fx = cx + (fireplace.pos().getX() - origin.getX()) / (float) step * TILE;
            float fz = cy + (fireplace.pos().getZ() - origin.getZ()) / (float) step * TILE;
            float dx = fx - cx;
            float dz = fz - cy;
            if (dx * dx + dz * dz <= (r - 3) * (r - 3)) {
                fillAnnulus(g, fx, fz, 0, 3.5f, 0, FULL, withAlpha(FLOO, 0x50));
                fillAnnulus(g, fx, fz, 0, 1.8f, 0, FULL, withAlpha(FLOO, 0xFF));
            }
        }

        // Caster at the centre: a pulsing violet spark.
        float pulse = 0.5f + 0.5f * Mth.sin(time * 5f);
        fillAnnulus(g, cx, cy, 0, 5f + pulse * 2f, 0, FULL, 0x50A066FF);
        fillAnnulus(g, cx, cy, 0, 2.5f, 0, FULL, 0xFFFFFFFF);
        fillAnnulus(g, cx, cy, 0, 1.4f, 0, FULL, 0xFFB98CFF);

        // Hover: reticle on the tile and a dotted path to it.
        int hoverDx = Math.floorDiv(mouseX - mapLeft, TILE);
        int hoverDz = Math.floorDiv(mouseY - mapTop, TILE);
        boolean overMap = hoverDx >= 0 && hoverDx < tiles && hoverDz >= 0 && hoverDz < tiles;
        boolean hoverValid = overMap && selectable(hoverDx, hoverDz);
        Component info;
        int infoColor = TH.textDim();
        if (hoverValid) {
            float tx = mapLeft + hoverDx * TILE + TILE / 2f;
            float tz = mapTop + hoverDz * TILE + TILE / 2f;
            float dx = tx - cx;
            float dz = tz - cy;
            float len = Mth.sqrt(dx * dx + dz * dz);
            for (float s = 6f; s < len - 6f; s += 5f) {
                float px = cx + dx * s / len;
                float pz = cy + dz * s / len;
                g.fill(Math.round(px), Math.round(pz), Math.round(px) + 1, Math.round(pz) + 1, 0xC0FFF3B0);
            }
            float spin = time * 2.5f;
            for (int q = 0; q < 4; q++) {
                float a = spin + q * FULL / 4f;
                fillAnnulus(g, tx, tz, 6f, 7.2f, a, a + 0.9f, 0xE0FFE08A);
            }
            fillAnnulus(g, tx, tz, 2.2f, 3.2f, 0, FULL, 0xFFFFF6D8);
            int wx = worldX(hoverDx);
            int wz = worldZ(hoverDz);
            int dist = (int) Math.round(Math.sqrt(Math.pow(wx - origin.getX(), 2) + Math.pow(wz - origin.getZ(), 2)));
            info = Component.translatable("gui." + ExampleMod.MODID + ".apparition.target", wx, wz, dist);
            infoColor = TH.trimBright();
        } else if (overMap && inReach(hoverDx, hoverDz)) {
            info = Component.translatable("gui." + ExampleMod.MODID + ".apparition.unknown");
        } else if (overMap) {
            info = Component.translatable("gui." + ExampleMod.MODID + ".apparition.too_far");
        } else {
            info = Component.translatable("gui." + ExampleMod.MODID + ".apparition.reach", radius);
        }
        ThemedGui.drawFittedCentered(g, this.font, info, panelLeft + panelWidth / 2f, infoY, infoColor, panelWidth - 20);

        // Known Floo fireplaces (Master): click one to Apparate there at any distance.
        if (!fireplaces.isEmpty()) {
            int x0 = panelLeft + 10;
            int x1 = panelLeft + panelWidth - 10;
            g.drawString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.fireplaces"), x0, listTop, TH.title(), false);
            drawWell(g, x0, listTop + 12, x1, listTop + 14 + ROW * fireplaces.size() + 2, TH);
            int hovered = fireplaceRowAt(mouseX, mouseY);
            for (int i = 0; i < fireplaces.size(); i++) {
                SpellPackets.FlooDestinationData fireplace = fireplaces.get(i);
                int rowY = listTop + 14 + i * ROW;
                if (i == hovered) {
                    g.fill(x0 + 1, rowY, x1 - 1, rowY + ROW, TH.wellLight());
                }
                fillAnnulus(g, x0 + 7, rowY + 6, 0, i == hovered ? 3f : 2.4f, 0, FULL, withAlpha(FLOO, 0xFF));
                int distance = (int) Math.sqrt(origin.distSqr(fireplace.pos()));
                String dist = distance + "m";
                int distW = this.font.width(dist);
                g.drawString(this.font, dist, x1 - 4 - distW, rowY + 2, TH.textDim(), false);
                String label = this.font.plainSubstrByWidth(fireplace.name(), x1 - 4 - distW - (x0 + 14) - 4);
                g.drawString(this.font, label, x0 + 14, rowY + 2, i == hovered ? TH.trimBright() : TH.text(), false);
            }
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !selectionSent) {
            int row = fireplaceRowAt(mouseX, mouseY);
            if (row >= 0) {
                SpellPackets.CHANNEL.send(
                        new SpellPackets.ChooseApparitionFireplacePacket(fireplaces.get(row).pos()),
                        PacketDistributor.SERVER.noArg());
                selectionSent = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return true;
            }
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
            int dz = Math.floorDiv((int) mouseY - mapTop, TILE);
            if (selectable(dx, dz)) {
                SpellPackets.CHANNEL.send(
                        new SpellPackets.ChooseApparitionDestinationPacket(worldX(dx), worldZ(dz)),
                        PacketDistributor.SERVER.noArg());
                selectionSent = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Called from the network bridge when the server ends the channel; suppresses the cancel packet. */
    public void closeFromServer() {
        selectionSent = true;
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (!selectionSent) {
            // Closed without choosing (Esc or timeout): tell the server to cancel + start the cooldown.
            SpellPackets.CHANNEL.send(new SpellPackets.CancelApparitionPacket(), PacketDistributor.SERVER.noArg());
            selectionSent = true;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
