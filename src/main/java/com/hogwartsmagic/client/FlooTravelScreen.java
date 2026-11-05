package com.hogwartsmagic.client;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class FlooTravelScreen extends Screen {
    private static final int NODE_SIZE = 18;
    private static final int MAP_WIDTH = 340;
    private static final int MAP_HEIGHT = 214;
    private static final int PANEL_WIDTH = 536;
    private static final int PANEL_HEIGHT = 292;
    private static final int OUTLINE_POINTS = 32;
    private static final int[] TERRAIN_COLORS = {0x2F8B6A42, 0x2A6A4D2E, 0x2448A27B};

    private final BlockPos sourcePos;
    private final List<SpellPackets.FlooDestinationData> destinations;
    private final List<MapNode> nodes = new ArrayList<>();
    private Button travelButton;
    private Button closeButton;
    private int selectedIndex = 0;
    private int left;
    private int top;
    private int maxDx = 1;
    private int maxDz = 1;
    private int sampleRadiusX = 192;
    private int sampleRadiusZ = 144;
    private TerrainSample[][] terrainSamples = new TerrainSample[0][0];
    private int terrainColumns;
    private int terrainRows;

    public FlooTravelScreen(BlockPos sourcePos, List<SpellPackets.FlooDestinationData> destinations) {
        super(Component.translatable("gui.hogwartsmagic.floo_network"));
        this.sourcePos = sourcePos;
        this.destinations = destinations;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        left = width / 2 - PANEL_WIDTH / 2;
        top = height / 2 - PANEL_HEIGHT / 2;
        buildMapNodes();
        buildTerrainSamples();

        travelButton = addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.floo.travel"),
                        button -> travelToSelected())
                .bounds(left + 428, top + 244, 92, 20)
                .build());
        closeButton = addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), button -> onClose())
                .bounds(left + 428, top + 268, 92, 20)
                .build());
        updateButtons();
    }

    private void buildMapNodes() {
        nodes.clear();
        if (destinations.isEmpty()) {
            selectedIndex = -1;
            return;
        }

        int mapLeft = left + 16;
        int mapTop = top + 40;
        int mapRight = mapLeft + MAP_WIDTH;
        int mapBottom = mapTop + MAP_HEIGHT;
        int centerX = mapLeft + MAP_WIDTH / 2;
        int centerY = mapTop + MAP_HEIGHT / 2;

        maxDx = 1;
        maxDz = 1;
        for (SpellPackets.FlooDestinationData destination : destinations) {
            maxDx = Math.max(maxDx, Math.abs(destination.pos().getX() - sourcePos.getX()));
            maxDz = Math.max(maxDz, Math.abs(destination.pos().getZ() - sourcePos.getZ()));
        }
        updateProjectionBounds();

        for (int i = 0; i < destinations.size(); i++) {
            SpellPackets.FlooDestinationData destination = destinations.get(i);
            Point screenPoint = projectPoint(destination.pos().getX(), destination.pos().getZ(), mapLeft, mapTop, mapRight, mapBottom);
            int screenX = screenPoint.x();
            int screenY = screenPoint.y();
            nodes.add(new MapNode(destination, screenX, screenY, i));
        }

        selectedIndex = Mth.clamp(selectedIndex, 0, destinations.size() - 1);
    }

    private void updateButtons() {
        if (travelButton != null) {
            travelButton.active = selectedIndex >= 0 && selectedIndex < destinations.size();
        }
    }

    private void travelToSelected() {
        if (selectedIndex < 0 || selectedIndex >= destinations.size()) {
            return;
        }
        SpellPackets.FlooDestinationData destination = destinations.get(selectedIndex);
        SpellPackets.CHANNEL.send(new SpellPackets.ChooseFlooDestinationPacket(sourcePos, destination.pos()),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        float animation = ((System.currentTimeMillis() % 6000L) / 6000.0f) + partialTick * 0.0025f;
        int panelLeft = left;
        int panelTop = top;
        int panelRight = left + PANEL_WIDTH;
        int panelBottom = top + PANEL_HEIGHT;
        int mapLeft = left + 16;
        int mapTop = top + 40;
        int mapRight = mapLeft + MAP_WIDTH;
        int mapBottom = mapTop + MAP_HEIGHT;
        int sideLeft = mapRight + 14;
        int sideRight = panelRight - 16;

        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF1C140D);
        guiGraphics.fill(panelLeft + 3, panelTop + 3, panelRight - 3, panelBottom - 3, 0xFFF0D8C4);
        guiGraphics.fill(mapLeft, mapTop, mapRight, mapBottom, 0xFFB58E58);
        guiGraphics.fill(mapLeft + 3, mapTop + 3, mapRight - 3, mapBottom - 3, 0xFFF1C785);
        guiGraphics.fill(sideLeft, mapTop, sideRight, top + 180, 0xFF342516);
        guiGraphics.fill(sideLeft, top + 186, sideRight, panelBottom - 14, 0xFF2A1E14);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        drawMapFrame(guiGraphics, mapLeft, mapTop, mapRight, mapBottom);
        drawMapDecor(guiGraphics, mapLeft, mapTop, mapRight, mapBottom, animation);
        drawTerrainOutline(guiGraphics, mapLeft, mapTop, mapRight, mapBottom, animation);
        drawRoutes(guiGraphics, animation);
        drawNodes(guiGraphics, mouseX, mouseY, animation);
        drawSavedFireplaces(guiGraphics, mouseX, mouseY);
        drawDetails(guiGraphics);

        guiGraphics.drawCenteredString(font, title, width / 2, top + 12, 0xFF315C39);
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.floo.map_subtitle"), mapLeft, top + 24, 0xFF5B3A20, false);
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.floo.saved_fireplaces"), sideLeft + 10, top + 24, 0xFF5B3A20, false);

        if (travelButton != null) {
            travelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (closeButton != null) {
            closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawMapFrame(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapRight, int mapBottom) {
        guiGraphics.fill(mapLeft + 1, mapTop + 1, mapRight - 1, mapTop + 3, 0x77B58C3F);
        guiGraphics.fill(mapLeft + 1, mapBottom - 3, mapRight - 1, mapBottom - 1, 0x775A3E19);
        guiGraphics.fill(mapLeft + 1, mapTop + 1, mapLeft + 3, mapBottom - 1, 0x77B58C3F);
        guiGraphics.fill(mapRight - 3, mapTop + 1, mapRight - 1, mapBottom - 1, 0x775A3E19);
        for (int x = mapLeft + 26; x < mapRight; x += 48) {
            guiGraphics.fill(x, mapTop + 8, x + 2, mapBottom - 8, 0x22815D34);
        }
        for (int y = mapTop + 24; y < mapBottom; y += 42) {
            guiGraphics.fill(mapLeft + 8, y, mapRight - 8, y + 2, 0x22815D34);
        }
    }

    private void drawMapDecor(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapRight, int mapBottom, float animation) {
        int centerX = mapLeft + MAP_WIDTH / 2;
        int centerY = mapTop + MAP_HEIGHT / 2;
        guiGraphics.fill(centerX - 1, mapTop + 10, centerX + 1, mapBottom - 10, 0x556B4B28);
        guiGraphics.fill(centerX - 3, mapTop + 10, centerX + 3, mapBottom - 10, 0x116B4B28);
        guiGraphics.fill(mapLeft + 10, centerY - 1, mapRight - 10, centerY + 1, 0x556B4B28);
        guiGraphics.fill(mapLeft + 10, centerY - 3, mapRight - 10, centerY + 3, 0x116B4B28);
        int compassColor = pulseColor(0xFF365B31, 0xFF7CF3AE, animation * 1.4f);
        guiGraphics.fill(centerX - 18, mapTop + 7, centerX + 18, mapTop + 8, 0x332F8D63);
        guiGraphics.fill(centerX - 18, mapBottom - 10, centerX + 18, mapBottom - 9, 0x332F8D63);
        guiGraphics.fill(mapLeft + 7, centerY - 18, mapLeft + 8, centerY + 18, 0x332F8D63);
        guiGraphics.fill(mapRight - 8, centerY - 18, mapRight - 7, centerY + 18, 0x332F8D63);
        guiGraphics.drawCenteredString(font, Component.literal("N"), centerX, mapTop + 10, compassColor);
        guiGraphics.drawCenteredString(font, Component.literal("S"), centerX, mapBottom - 18, compassColor);
        guiGraphics.drawString(font, Component.literal("W"), mapLeft + 12, centerY - 4, compassColor, false);
        guiGraphics.drawString(font, Component.literal("E"), mapRight - 18, centerY - 4, compassColor, false);
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.floo.you_are_here"), mapLeft + 12, mapBottom - 20, 0xFF365B31, false);
    }

    private void drawTerrainOutline(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapRight, int mapBottom, float animation) {
        if (terrainColumns > 0 && terrainRows > 0) {
            drawSampledTerrain(guiGraphics, mapLeft, mapTop);
            return;
        }
    }

    private void drawRoutes(GuiGraphics guiGraphics, float animation) {
        int sourceX = left + 16 + MAP_WIDTH / 2;
        int sourceY = top + 40 + MAP_HEIGHT / 2;
        for (MapNode node : nodes) {
            boolean selected = node.index == selectedIndex;
            int outer = selected ? pulseColor(0x6632C86B, 0xAA79FFF2, animation * 2.1f) : 0x444F6D43;
            int inner = selected ? pulseColor(0xFF32C86B, 0xFF9AFBFF, animation * 2.8f) : 0x886E9A5C;
            drawLine(guiGraphics, sourceX, sourceY, node.x, node.y, outer, selected ? 4 : 2);
            drawLine(guiGraphics, sourceX, sourceY, node.x, node.y, inner, selected ? 2 : 1);
            drawRouteSparkles(guiGraphics, sourceX, sourceY, node.x, node.y, animation + node.index * 0.12f, selected);
        }
        drawFireNode(guiGraphics, sourceX, sourceY, true, true, animation);
    }

    private void drawNodes(GuiGraphics guiGraphics, int mouseX, int mouseY, float animation) {
        for (MapNode node : nodes) {
            boolean hovered = Math.abs(mouseX - node.x) <= NODE_SIZE && Math.abs(mouseY - node.y) <= NODE_SIZE;
            boolean selected = node.index == selectedIndex;
            drawFireNode(guiGraphics, node.x, node.y, selected, hovered, animation + node.index * 0.09f);
            guiGraphics.fill(node.x + 10, node.y - 7, node.x + 10 + font.width(trimLabel(node.destination.name(), 16)) + 4, node.y + 5, 0x77422C18);
            guiGraphics.drawString(font, trimLabel(node.destination.name(), 16), node.x + 12, node.y - 4, 0xFF1F140B, false);
        }
    }

    private void drawSavedFireplaces(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int listY = top + 46;
        for (int i = 0; i < destinations.size() && i < 8; i++) {
            SpellPackets.FlooDestinationData destination = destinations.get(i);
            int rowX = left + 418;
            int rowY = listY - 2;
            boolean hovered = mouseX >= rowX && mouseX <= rowX + 96 && mouseY >= rowY && mouseY <= rowY + 14;
            if (i == selectedIndex || hovered) {
                guiGraphics.fill(rowX - 4, rowY - 2, rowX + 104, rowY + 14, i == selectedIndex ? 0x8841C978 : 0x553A6D46);
            }
            int color = i == selectedIndex ? 0xFF7CF3AE : 0xFFE8D7A5;
            guiGraphics.drawString(font, (i == selectedIndex ? "> " : "  ") + trimLabel(destination.name(), 16), left + 422, listY, color, false);
            listY += 16;
        }
    }

    private void drawDetails(GuiGraphics guiGraphics) {
        if (selectedIndex < 0 || selectedIndex >= destinations.size()) {
            return;
        }
        SpellPackets.FlooDestinationData destination = destinations.get(selectedIndex);
        int detailLeft = left + 422;
        int detailTop = top + 196;
        guiGraphics.drawString(font, trimLabel(destination.name(), 18), detailLeft, detailTop, 0xFF7CF3AE, false);
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.floo.coords", destination.pos().getX(), destination.pos().getY(), destination.pos().getZ()),
                detailLeft, detailTop + 14, 0xFFE8D7A5, false);
        int dx = destination.pos().getX() - sourcePos.getX();
        int dz = destination.pos().getZ() - sourcePos.getZ();
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.floo.distance", Math.round(Math.sqrt(dx * dx + dz * dz))),
                detailLeft, detailTop + 28, 0xFFE8D7A5, false);
    }

    private void drawRouteSparkles(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, float animation, boolean selected) {
        int sparkleCount = selected ? 4 : 2;
        for (int i = 0; i < sparkleCount; i++) {
            float progress = (animation * (selected ? 0.7f : 0.35f) + i * (1.0f / sparkleCount)) % 1.0f;
            int x = Mth.floor(Mth.lerp(progress, x1, x2));
            int y = Mth.floor(Mth.lerp(progress, y1, y2));
            int glow = selected ? 0xCCB8FFF5 : 0x88C9E2A7;
            guiGraphics.fill(x - 2, y - 2, x + 3, y + 3, glow);
            guiGraphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFF9FFF1);
        }
    }

    private void drawFireNode(GuiGraphics guiGraphics, int centerX, int centerY, boolean selected, boolean hovered, float animation) {
        int frameColor = selected ? pulseColor(0xFF2D8F45, 0xFF77F5D8, animation * 2.2f) : hovered ? 0xFF8A9F54 : 0xFF6C5436;
        int stoneColor = selected ? pulseColor(0xFFCCB06E, 0xFFF2E4A9, animation * 1.8f) : 0xFFD4BC83;
        int flameOuter = selected ? pulseColor(0xFF30E58D, 0xFFB8FFF8, animation * 2.6f) : 0xFF7DEAB0;
        int flameInner = selected ? pulseColor(0xFFF7F6C8, 0xFFFFFFFF, animation * 2.9f) : 0xFFFDF6D9;
        guiGraphics.fill(centerX - 8, centerY + 3, centerX + 8, centerY + 8, frameColor);
        guiGraphics.fill(centerX - 6, centerY, centerX - 3, centerY + 3, stoneColor);
        guiGraphics.fill(centerX + 3, centerY, centerX + 6, centerY + 3, stoneColor);
        guiGraphics.fill(centerX - 4, centerY + 1, centerX + 4, centerY + 4, 0xFF453221);
        guiGraphics.fill(centerX - 4, centerY - 7, centerX + 4, centerY + 1, flameOuter);
        guiGraphics.fill(centerX - 2, centerY - 10, centerX + 2, centerY - 2, flameInner);
        guiGraphics.fill(centerX - 1, centerY - 12, centerX + 1, centerY - 7, 0xFFF7FFFF);
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float progress = steps == 0 ? 0.0f : i / (float) steps;
            int x = Mth.floor(Mth.lerp(progress, x1, x2));
            int y = Mth.floor(Mth.lerp(progress, y1, y2));
            guiGraphics.fill(x - thickness, y - thickness, x + thickness + 1, y + thickness + 1, color);
        }
    }

    private int outlineJitter(int seed, float animation) {
        return Math.round((float) Math.sin(seed * 1.37f + animation * 6.28318f) * 3.0f);
    }

    private void drawOutlineRings(GuiGraphics guiGraphics, int[][] outline) {
        int points = outline.length;
        for (int ring = 0; ring < TERRAIN_COLORS.length; ring++) {
            int color = TERRAIN_COLORS[ring];
            for (int i = 0; i < points; i++) {
                int[] start = outline[i];
                int[] end = outline[(i + 1) % points];
                int inset = ring * 3;
                drawLine(guiGraphics,
                        start[0] - inset, start[1] - inset / 2,
                        end[0] - inset, end[1] - inset / 2,
                        color, Math.max(1, 3 - ring));
            }
        }
    }

    private void drawParchmentLandFill(GuiGraphics guiGraphics, int[][] outline, int centerX, int centerY) {
        int points = outline.length;
        for (int i = 0; i < points; i++) {
            int[] point = outline[i];
            drawLine(guiGraphics, centerX, centerY, point[0], point[1], 0x1C8A6A39, 1);
        }
        guiGraphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0x2A6E8B52);
    }

    private void drawGeographicFeatures(GuiGraphics guiGraphics, int[][] outline, int centerX, int centerY, float animation) {
        drawMountainRanges(guiGraphics, outline, centerX, centerY, animation);
        drawForestClusters(guiGraphics, centerX, centerY);
        drawLakeAndRiver(guiGraphics, centerX, centerY);
    }

    private void drawSampledTerrain(GuiGraphics guiGraphics, int mapLeft, int mapTop) {
        for (int row = 0; row < terrainRows; row++) {
            for (int col = 0; col < terrainColumns; col++) {
                TerrainSample sample = terrainSamples[row][col];
                if (sample == null) {
                    continue;
                }
                int x1 = mapLeft + col * MAP_WIDTH / terrainColumns;
                int y1 = mapTop + row * MAP_HEIGHT / terrainRows;
                int x2 = mapLeft + (col + 1) * MAP_WIDTH / terrainColumns;
                int y2 = mapTop + (row + 1) * MAP_HEIGHT / terrainRows;
                guiGraphics.fill(x1, y1, x2, y2, sample.fillColor());

                if (sample.coastNorth()) {
                    guiGraphics.fill(x1, y1, x2, y1 + 1, 0xAA5C4529);
                }
                if (sample.coastSouth()) {
                    guiGraphics.fill(x1, y2 - 1, x2, y2, 0xAA5C4529);
                }
                if (sample.coastWest()) {
                    guiGraphics.fill(x1, y1, x1 + 1, y2, 0xAA5C4529);
                }
                if (sample.coastEast()) {
                    guiGraphics.fill(x2 - 1, y1, x2, y2, 0xAA5C4529);
                }

                if (sample.ridge()) {
                    int midX = (x1 + x2) / 2;
                    drawMountain(guiGraphics, midX, y2 - 1, Math.max(3, (x2 - x1) / 2), row * 0.11f + col * 0.07f);
                } else if (sample.forest()) {
                    int midX = (x1 + x2) / 2;
                    int midY = (y1 + y2) / 2 + 1;
                    drawTree(guiGraphics, midX, midY);
                }
            }
        }
    }

    private void buildTerrainSamples() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            terrainSamples = new TerrainSample[0][0];
            terrainColumns = 0;
            terrainRows = 0;
            return;
        }

        terrainColumns = 68;
        terrainRows = 43;
        terrainSamples = new TerrainSample[terrainRows][terrainColumns];

        int seaLevel = level.getSeaLevel();

        int[][] heights = new int[terrainRows][terrainColumns];
        boolean[][] water = new boolean[terrainRows][terrainColumns];

        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        for (int row = 0; row < terrainRows; row++) {
            for (int col = 0; col < terrainColumns; col++) {
                float u = terrainColumns == 1 ? 0.5f : col / (float) (terrainColumns - 1);
                float v = terrainRows == 1 ? 0.5f : row / (float) (terrainRows - 1);
                int worldX = sourcePos.getX() + Math.round((u - 0.5f) * sampleRadiusX * 2.0f);
                int worldZ = sourcePos.getZ() + Math.round((v - 0.5f) * sampleRadiusZ * 2.0f);
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                BlockPos topPos = new BlockPos(worldX, Math.max(level.getMinBuildHeight(), surfaceY - 1), worldZ);
                boolean isWater = level.getFluidState(topPos).isSource() || surfaceY <= seaLevel + 1;
                heights[row][col] = surfaceY;
                water[row][col] = isWater;
                if (!isWater) {
                    minHeight = Math.min(minHeight, surfaceY);
                    maxHeight = Math.max(maxHeight, surfaceY);
                }
            }
        }

        if (minHeight == Integer.MAX_VALUE) {
            minHeight = seaLevel;
            maxHeight = seaLevel + 1;
        }

        int heightSpan = Math.max(1, maxHeight - minHeight);
        for (int row = 0; row < terrainRows; row++) {
            for (int col = 0; col < terrainColumns; col++) {
                boolean isWater = water[row][col];
                boolean coastNorth = row > 0 && water[row - 1][col] != isWater;
                boolean coastSouth = row < terrainRows - 1 && water[row + 1][col] != isWater;
                boolean coastWest = col > 0 && water[row][col - 1] != isWater;
                boolean coastEast = col < terrainColumns - 1 && water[row][col + 1] != isWater;

                float normalizedHeight = Mth.clamp((heights[row][col] - minHeight) / (float) heightSpan, 0.0f, 1.0f);
                boolean ridge = !isWater && normalizedHeight > 0.72f;
                boolean forest = !isWater && !ridge && normalizedHeight > 0.28f && normalizedHeight < 0.62f && ((row + col + sourcePos.getX()) % 5 == 0);

                int fillColor;
                if (isWater) {
                    fillColor = normalizedHeight < 0.15f ? 0x886AA7B8 : 0x887DB9C9;
                } else if (normalizedHeight > 0.72f) {
                    fillColor = 0x887D6A4C;
                } else if (normalizedHeight > 0.45f) {
                    fillColor = 0x886F8A57;
                } else {
                    fillColor = 0x8894A86C;
                }

                terrainSamples[row][col] = new TerrainSample(fillColor, ridge, forest, coastNorth, coastSouth, coastWest, coastEast);
            }
        }
    }

    private void drawMountainRanges(GuiGraphics guiGraphics, int[][] outline, int centerX, int centerY, float animation) {
        int seed = sourcePos.hashCode();
        for (int i = 0; i < outline.length; i += 4) {
            int[] edge = outline[i];
            int inwardX = Math.round(Mth.lerp(0.16f, edge[0], centerX));
            int inwardY = Math.round(Mth.lerp(0.16f, edge[1], centerY));
            int chainLength = 2 + positiveHash(seed, i + 11) % 3;
            int stepX = Math.round((centerX - edge[0]) / 14.0f);
            int stepY = Math.round((centerY - edge[1]) / 14.0f);
            for (int peak = 0; peak < chainLength; peak++) {
                int x = inwardX + stepX * peak + ((positiveHash(seed, i * 31 + peak) % 5) - 2);
                int y = inwardY + stepY * peak + ((positiveHash(seed, i * 17 + peak + 9) % 5) - 2);
                int size = 6 + positiveHash(seed, i * 13 + peak + 21) % 5;
                drawMountain(guiGraphics, x, y, size, animation + i * 0.03f + peak * 0.07f);
            }
        }
    }

    private void drawMountain(GuiGraphics guiGraphics, int centerX, int baseY, int size, float animation) {
        int leftX = centerX - size;
        int rightX = centerX + size;
        int peakY = baseY - size - 4;
        int snowGlow = pulseColor(0xAAFFF8D2, 0xCCFFFFFF, animation * 0.2f);
        drawLine(guiGraphics, leftX, baseY, centerX, peakY, 0xAA5D4730, 1);
        drawLine(guiGraphics, centerX, peakY, rightX, baseY, 0xAA7B6545, 1);
        drawLine(guiGraphics, leftX + 1, baseY, centerX - 1, peakY + 2, 0x66735C3F, 1);
        drawLine(guiGraphics, centerX + 1, peakY + 2, rightX - 1, baseY, 0x886B563A, 1);
        guiGraphics.fill(centerX - 1, peakY - 1, centerX + 2, peakY + 2, snowGlow);
    }

    private void drawForestClusters(GuiGraphics guiGraphics, int centerX, int centerY) {
        int seed = sourcePos.hashCode() ^ 0x6A5F13;
        int clusterCount = Math.max(3, Math.min(6, nodes.size() + 1));
        for (int cluster = 0; cluster < clusterCount; cluster++) {
            float angle = (float) ((Math.PI * 2.0 * cluster) / clusterCount + (positiveHash(seed, cluster + 3) % 18) * 0.06f);
            int radius = 34 + positiveHash(seed, cluster + 19) % 42;
            int x = centerX + Math.round((float) Math.cos(angle) * radius);
            int y = centerY + Math.round((float) Math.sin(angle) * radius * 0.74f);
            if (isNearNode(x, y, 18)) {
                continue;
            }
            int trees = 3 + positiveHash(seed, cluster + 41) % 4;
            for (int tree = 0; tree < trees; tree++) {
                int tx = x + (positiveHash(seed, cluster * 29 + tree + 7) % 13) - 6;
                int ty = y + (positiveHash(seed, cluster * 37 + tree + 15) % 9) - 4;
                drawTree(guiGraphics, tx, ty);
            }
        }
    }

    private void drawTree(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y + 1, x + 1, y + 5, 0xAA5B3A22);
        guiGraphics.fill(x - 3, y - 2, x + 3, y + 2, 0xAA476839);
        guiGraphics.fill(x - 2, y - 4, x + 2, y - 1, 0xCC5D7F47);
        guiGraphics.fill(x - 1, y - 6, x + 1, y - 3, 0xDD88A963);
    }

    private void drawLakeAndRiver(GuiGraphics guiGraphics, int centerX, int centerY) {
        int lakeX = centerX - 58;
        int lakeY = centerY + 26;
        if (isNearNode(lakeX, lakeY, 26)) {
            lakeX = centerX + 62;
            lakeY = centerY + 18;
        }
        guiGraphics.fill(lakeX - 12, lakeY - 5, lakeX + 12, lakeY + 5, 0x557AB5C6);
        guiGraphics.fill(lakeX - 8, lakeY - 8, lakeX + 8, lakeY + 8, 0x667FC7D8);
        guiGraphics.fill(lakeX - 4, lakeY - 10, lakeX + 4, lakeY + 10, 0x5599DCE8);
        for (int segment = 0; segment < 6; segment++) {
            int x1 = lakeX + 10 + segment * 10;
            int y1 = lakeY - 2 - segment;
            int x2 = x1 + 12;
            int y2 = y1 - 2 + (segment % 2);
            drawLine(guiGraphics, x1, y1, x2, y2, 0x446FAFC4, 1);
        }
    }

    private boolean isNearNode(int x, int y, int padding) {
        int sourceX = left + 16 + MAP_WIDTH / 2;
        int sourceY = top + 40 + MAP_HEIGHT / 2;
        if (Math.abs(sourceX - x) <= padding && Math.abs(sourceY - y) <= padding) {
            return true;
        }
        for (MapNode node : nodes) {
            if (Math.abs(node.x - x) <= padding && Math.abs(node.y - y) <= padding) {
                return true;
            }
        }
        return false;
    }

    private void expandOutlineFromPoint(int[] radii, int centerX, int centerY, int pointX, int pointY, int margin) {
        float dx = pointX - centerX;
        float dy = (pointY - centerY) / 0.76f;
        float distance = Mth.sqrt(dx * dx + dy * dy);
        if (distance <= 0.0f) {
            return;
        }

        float angle = (float) Math.atan2(dy, dx);
        if (angle < 0.0f) {
            angle += (float) (Math.PI * 2.0);
        }

        int sector = Math.round((angle / (float) (Math.PI * 2.0)) * OUTLINE_POINTS) % OUTLINE_POINTS;
        for (int offset = -2; offset <= 2; offset++) {
            int index = Math.floorMod(sector + offset, OUTLINE_POINTS);
            int softenedMargin = margin - Math.abs(offset) * 4;
            radii[index] = Math.max(radii[index], Math.round(distance) + softenedMargin);
        }
    }

    private void smoothRadii(int[] radii) {
        int[] copy = radii.clone();
        for (int i = 0; i < radii.length; i++) {
            int previous = copy[Math.floorMod(i - 1, copy.length)];
            int current = copy[i];
            int next = copy[(i + 1) % copy.length];
            radii[i] = Math.round((previous + current * 2.0f + next) / 4.0f);
        }
    }

    private Point projectPoint(int blockX, int blockZ, int mapLeft, int mapTop, int mapRight, int mapBottom) {
        int centerX = mapLeft + MAP_WIDTH / 2;
        int centerY = mapTop + MAP_HEIGHT / 2;
        int dx = blockX - sourcePos.getX();
        int dz = blockZ - sourcePos.getZ();
        int screenX = centerX + Math.round((dx / (float) sampleRadiusX) * (MAP_WIDTH / 2f));
        int screenY = centerY + Math.round((dz / (float) sampleRadiusZ) * (MAP_HEIGHT / 2f));
        screenX = Mth.clamp(screenX, mapLeft + 8, mapRight - 8);
        screenY = Mth.clamp(screenY, mapTop + 8, mapBottom - 8);
        return new Point(screenX, screenY);
    }

    private void updateProjectionBounds() {
        sampleRadiusX = Math.max(192, maxDx + 64);
        sampleRadiusZ = Math.max(144, maxDz + 64);
    }

    private int positiveHash(int seed, int salt) {
        int mixed = seed ^ (salt * 0x45d9f3b);
        mixed ^= (mixed >>> 16);
        return mixed & Integer.MAX_VALUE;
    }

    private int pulseColor(int colorA, int colorB, float animation) {
        float blend = 0.5f + 0.5f * (float) Math.sin(animation * 6.28318f);
        int aA = (colorA >> 24) & 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;
        int aB = (colorB >> 24) & 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;
        int a = Mth.floor(Mth.lerp(blend, aA, aB));
        int r = Mth.floor(Mth.lerp(blend, rA, rB));
        int g = Mth.floor(Mth.lerp(blend, gA, gB));
        int b = Mth.floor(Mth.lerp(blend, bA, bB));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String trimLabel(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (MapNode node : nodes) {
            if (Math.abs(mouseX - node.x) <= NODE_SIZE && Math.abs(mouseY - node.y) <= NODE_SIZE) {
                selectedIndex = node.index;
                updateButtons();
                return true;
            }
        }
        int listY = top + 46;
        for (int i = 0; i < destinations.size() && i < 8; i++) {
            int rowX = left + 418;
            int rowY = listY - 2;
            if (mouseX >= rowX && mouseX <= rowX + 96 && mouseY >= rowY && mouseY <= rowY + 14) {
                selectedIndex = i;
                updateButtons();
                return true;
            }
            listY += 16;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render() calls this again after the panel is drawn, which put the menu blur
        // and backdrop on top of it. render() draws the plain dim itself (no blur, per the screen rule).
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record MapNode(SpellPackets.FlooDestinationData destination, int x, int y, int index) {
    }

    private record Point(int x, int y) {
    }

    private record TerrainSample(int fillColor, boolean ridge, boolean forest,
                                 boolean coastNorth, boolean coastSouth, boolean coastWest, boolean coastEast) {
    }
}
