package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
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
 * The Apparition destination picker: a small top-down minimap of the loaded terrain around the caster.
 * While this screen is open the player is "channeling" — the server runs a 4-second timer, shrinks the
 * caster (the imploding effect), and will cancel if they are hit. Clicking a known tile sends the chosen
 * column to the server, which re-validates and performs the authoritative teleport.
 *
 * <p>Pure client rendering only. No {@code switch} on enums; backdrop drawn with
 * {@code renderTransparentBackground} (never {@code renderBackground}) so the world stays crisp.
 */
public class ApparitionMapScreen extends Screen {

    private static final int TILE = 2;              // pixels per block
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

    // Precomputed minimap (one sample per block column), built in init().
    private int[] tileColors = new int[0];
    private boolean[] tileKnown = new boolean[0];
    private int span;       // 2*radius + 1

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
        int mapSize = span * TILE;
        panelWidth = mapSize + 28;
        panelHeight = mapSize + 56;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        mapLeft = panelLeft + 14;
        mapTop = panelTop + 40;
        buildMap();
    }

    /** Sample the surface block color of every column we can see, once, so render() stays cheap. */
    private void buildMap() {
        tileColors = new int[span * span];
        tileKnown = new boolean[span * span];
        Level level = this.minecraft != null ? this.minecraft.level : null;
        if (level == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int idx = (dz + radius) * span + (dx + radius);
                int wx = origin.getX() + dx;
                int wz = origin.getZ() + dz;
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

        int mapSize = span * TILE;

        // Panel + border.
        g.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COLOR_PANEL);
        drawBorder(g, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COLOR_GOLD);

        // Title + hint.
        g.drawCenteredString(this.font, this.title, panelLeft + panelWidth / 2, panelTop + 8, COLOR_GOLD);
        g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.hint"),
                panelLeft + panelWidth / 2, panelTop + 22, COLOR_BODY);

        // Minimap tiles (clipped to the map area).
        g.enableScissor(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize);
        for (int dz = 0; dz < span; dz++) {
            for (int dx = 0; dx < span; dx++) {
                int idx = dz * span + dx;
                int px = mapLeft + dx * TILE;
                int py = mapTop + dz * TILE;
                g.fill(px, py, px + TILE, py + TILE, tileColors[idx]);
            }
        }

        // Caster marker at the center.
        int centerPx = mapLeft + radius * TILE;
        int centerPy = mapTop + radius * TILE;
        g.fill(centerPx - 1, centerPy - 1, centerPx + TILE + 1, centerPy + TILE + 1, 0xFFFFFFFF);
        g.fill(centerPx, centerPy, centerPx + TILE, centerPy + TILE, 0xFF20C0FF);

        // Hover highlight over a selectable tile.
        int hoverDx = Math.floorDiv(mouseX - mapLeft, TILE);
        int hoverDz = Math.floorDiv(mouseY - mapTop, TILE);
        boolean hoverValid = hoverDx >= 0 && hoverDx < span && hoverDz >= 0 && hoverDz < span
                && tileKnown[hoverDz * span + hoverDx];
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
            if (dx >= 0 && dx < span && dz >= 0 && dz < span && tileKnown[dz * span + dx]) {
                int wx = origin.getX() + (dx - radius);
                int wz = origin.getZ() + (dz - radius);
                SpellPackets.CHANNEL.send(
                        new SpellPackets.ChooseApparitionDestinationPacket(wx, wz),
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
