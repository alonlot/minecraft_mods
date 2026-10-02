package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.support.ApparitionRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Apparition destination picker: a top-down minimap of the loaded terrain around the caster.
 * While this screen is open the player is "channeling": the server runs the rank's timer, shrinks the
 * caster (the imploding effect), and will cancel if they are hit. Clicking a known tile sends the chosen
 * column to the server, which re-validates and performs the authoritative teleport. The map always fits
 * the screen: up close one tile is one block, for a Master's reach one tile stands for several.
 *
 * <p>Pure client rendering only. No {@code switch} on enums; backdrop drawn with
 * {@code renderTransparentBackground} (never {@code renderBackground}) so the world stays crisp.
 */
public class ApparitionMapScreen extends Screen {

    private static final int TILE = 2;              // pixels per map tile
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_BODY = 0xFFE8DBFF;
    private static final int COLOR_UNKNOWN = 0xFF0E0A16;
    private static final int COLOR_PANEL = 0xF0140A1E;

    private final BlockPos origin;
    private final int radius;
    private final int durationTicks;
    private final RandomSource shakeRand = RandomSource.create();

    private int ticksLeft;
    private boolean selectionSent;

    // Precomputed minimap (one sample per tile), built in init().
    private int[] tileColors = new int[0];
    private boolean[] tileKnown = new boolean[0];
    private int span;       // 2*radius + 1 blocks
    private int step = 1;   // blocks per tile
    private int tiles;      // tiles per side

    private int mapLeft;
    private int mapTop;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
    }

    @Override
    protected void init() {
        super.init();
        span = radius * 2 + 1;
        int maxMapPx = Math.max(64, Math.min(this.width - 60, this.height - 100));
        step = ApparitionRules.mapStep(span, TILE, maxMapPx);
        tiles = (span + step - 1) / step;
        int mapSize = tiles * TILE;
        panelWidth = mapSize + 28;
        panelHeight = mapSize + 56;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        mapLeft = panelLeft + 14;
        mapTop = panelTop + 40;
        buildMap();
    }

    private int worldX(int tileX) {
        return origin.getX() - radius + ApparitionRules.tileCenterOffset(tileX, step, span);
    }

    private int worldZ(int tileZ) {
        return origin.getZ() - radius + ApparitionRules.tileCenterOffset(tileZ, step, span);
    }

    /** Sample the surface block color of every tile's middle column we can see, once, so render() stays cheap. */
    private void buildMap() {
        tileColors = new int[tiles * tiles];
        tileKnown = new boolean[tiles * tiles];
        Level level = this.minecraft != null ? this.minecraft.level : null;
        if (level == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int tz = 0; tz < tiles; tz++) {
            for (int tx = 0; tx < tiles; tx++) {
                int idx = tz * tiles + tx;
                int wx = worldX(tx);
                int wz = worldZ(tz);
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    tileColors[idx] = COLOR_UNKNOWN;
                    tileKnown[idx] = false;
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                pos.set(wx, surfaceY - 1, wz);
                MapColor mapColor = level.getBlockState(pos).getMapColor(level, pos);
                if (mapColor == MapColor.NONE) {
                    tileColors[idx] = 0xFF182030;
                } else {
                    tileColors[idx] = 0xFF000000 | mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
                }
                tileKnown[idx] = true;
            }
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

        // Screen shake: grows as the focus tightens and time runs out.
        float progress = 1.0f - (ticksLeft / (float) durationTicks);
        float intensity = progress * progress * 7.0f;
        float shakeX = (shakeRand.nextFloat() * 2.0f - 1.0f) * intensity;
        float shakeY = (shakeRand.nextFloat() * 2.0f - 1.0f) * intensity;

        g.pose().pushPose();
        g.pose().translate(shakeX, shakeY, 0.0f);

        int mapSize = tiles * TILE;

        // Panel + border.
        g.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COLOR_PANEL);
        drawBorder(g, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COLOR_GOLD);

        // Title + hint.
        g.drawCenteredString(this.font, this.title, panelLeft + panelWidth / 2, panelTop + 8, COLOR_GOLD);
        g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.hint"),
                panelLeft + panelWidth / 2, panelTop + 22, COLOR_BODY);

        // Minimap tiles (clipped to the map area).
        g.enableScissor(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize);
        for (int tz = 0; tz < tiles; tz++) {
            for (int tx = 0; tx < tiles; tx++) {
                int idx = tz * tiles + tx;
                int px = mapLeft + tx * TILE;
                int py = mapTop + tz * TILE;
                g.fill(px, py, px + TILE, py + TILE, tileColors[idx]);
            }
        }

        // Caster marker at the center.
        int centerTile = radius / step;
        int centerPx = mapLeft + centerTile * TILE;
        int centerPy = mapTop + centerTile * TILE;
        g.fill(centerPx - 1, centerPy - 1, centerPx + TILE + 1, centerPy + TILE + 1, 0xFFFFFFFF);
        g.fill(centerPx, centerPy, centerPx + TILE, centerPy + TILE, 0xFF20C0FF);

        // Hover highlight over a selectable tile.
        int hoverDx = Math.floorDiv(mouseX - mapLeft, TILE);
        int hoverDz = Math.floorDiv(mouseY - mapTop, TILE);
        boolean hoverValid = hoverDx >= 0 && hoverDx < tiles && hoverDz >= 0 && hoverDz < tiles
                && tileKnown[hoverDz * tiles + hoverDx];
        if (hoverValid) {
            int hx = mapLeft + hoverDx * TILE;
            int hy = mapTop + hoverDz * TILE;
            g.fill(hx - 1, hy - 1, hx + TILE + 1, hy + TILE + 1, 0x80FFE08A);
        }
        g.disableScissor();
        drawBorder(g, mapLeft - 1, mapTop - 1, mapLeft + mapSize + 1, mapTop + mapSize + 1, 0x66D4AF37);

        // Countdown bar.
        int barLeft = mapLeft;
        int barTop = mapTop + mapSize + 6;
        int barWidth = mapSize;
        g.fill(barLeft, barTop, barLeft + barWidth, barTop + 4, 0xFF2A2238);
        int filled = Math.round(barWidth * (ticksLeft / (float) durationTicks));
        int barColor = ticksLeft <= durationTicks / 3 ? 0xFFFF5A4B : COLOR_GOLD;
        g.fill(barLeft, barTop, barLeft + filled, barTop + 4, barColor);

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !selectionSent) {
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
            int dz = Math.floorDiv((int) mouseY - mapTop, TILE);
            if (dx >= 0 && dx < tiles && dz >= 0 && dz < tiles && tileKnown[dz * tiles + dx]) {
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

    private void drawBorder(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
