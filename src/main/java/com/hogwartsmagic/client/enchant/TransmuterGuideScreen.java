package com.hogwartsmagic.client.enchant;

import com.hogwartsmagic.block.entity.SpellTransmuterBlockEntity;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.enchant.SpellEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * The Spell Transmuter's guide (sneak + right-click it with an empty hand): every spell book it takes, the enchantment
 * that book becomes, its highest level and what it goes on. Hovering a row shows what the enchantment does; the row of
 * a spell book already laid on the transmuter is lit. Client only, nothing is sent to the server.
 */
public class TransmuterGuideScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int ROW_H = 24;
    private static final int VISIBLE_ROWS = 8;
    private static final int HEADER_H = 56;

    private record Row(SpellType spell, ResourceKey<Enchantment> key, Item on, String onKey) {
    }

    /** The order rows are shown in: armour, then boots, then tools, weapons and the rest. */
    private static final List<Row> ROWS = List.of(
            new Row(SpellType.PROTEGO, SpellEnchantments.WARDING, Items.DIAMOND_CHESTPLATE, "armour"),
            new Row(SpellType.CAVE_INIMICUM, SpellEnchantments.CAVE_INIMICUM, Items.DIAMOND_LEGGINGS, "armour"),
            new Row(SpellType.EXPECTO_PATRONUM, SpellEnchantments.PATRONUS, Items.IRON_CHESTPLATE, "chestplate"),
            new Row(SpellType.LUMOS, SpellEnchantments.LUMOS, Items.DIAMOND_HELMET, "helmet"),
            new Row(SpellType.REVELIO, SpellEnchantments.REVELIO, Items.IRON_HELMET, "helmet"),
            new Row(SpellType.ARRESTO_MOMENTUM, SpellEnchantments.ARRESTO, Items.DIAMOND_BOOTS, "boots"),
            new Row(SpellType.AQUA_ERUCTO, SpellEnchantments.DOUSE, Items.IRON_BOOTS, "boots"),
            new Row(SpellType.ASCENDIO, SpellEnchantments.ASCENDIO, Items.LEATHER_BOOTS, "boots"),
            new Row(SpellType.WINGARDIUM_LEVIOSA, SpellEnchantments.LEVIOSA, Items.GOLDEN_BOOTS, "boots"),
            new Row(SpellType.ALATUS, SpellEnchantments.ALATUS, Items.ELYTRA, "elytra"),
            new Row(SpellType.ACCIO, SpellEnchantments.ACCIO, Items.DIAMOND_PICKAXE, "tools_weapons"),
            new Row(SpellType.INCENDIO, SpellEnchantments.INCENDIO, Items.IRON_PICKAXE, "mining_tools"),
            new Row(SpellType.REPARO, SpellEnchantments.REPARO, Items.ANVIL, "durable"),
            new Row(SpellType.GLACIUS, SpellEnchantments.GLACIUS, Items.BOW, "weapons_bows"),
            new Row(SpellType.INCARCEROUS, SpellEnchantments.INCARCEROUS, Items.CROSSBOW, "crossbow"),
            new Row(SpellType.DEPULSO, SpellEnchantments.DEPULSO, Items.SHIELD, "shield"),
            new Row(SpellType.PROTEGO_MAXIMA, SpellEnchantments.PROTEGO_MAXIMA, Items.SHIELD, "shield"),
            new Row(SpellType.BOMBARDA, SpellEnchantments.BOMBARDA_MAXIMA, Items.TRIDENT, "trident"),
            new Row(SpellType.IMPERIO, SpellEnchantments.IMPERIO, Items.LEAD, "lead"));

    private final BlockPos pos;
    private final List<ItemStack> books = new ArrayList<>();
    private float scroll;

    public TransmuterGuideScreen(BlockPos pos) {
        super(Component.translatable("gui.hogwartsmagic.transmuter.title"));
        this.pos = pos;
        for (Row row : ROWS) {
            books.add(MagicBookItem.createBook(row.spell()));
        }
    }

    private int maxScroll() {
        return Math.max(0, ROWS.size() - VISIBLE_ROWS) * ROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (float) scrollY * ROW_H, 0.0f, maxScroll());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dimmed backdrop only: no blur shader (project convention).
        this.renderTransparentBackground(g);
        int left = (width - PANEL_W) / 2;
        int panelH = HEADER_H + VISIBLE_ROWS * ROW_H + 10;
        int top = (height - panelH) / 2;

        g.fill(left - 2, top - 2, left + PANEL_W + 2, top + panelH + 2, 0xFF6E4FA8);
        g.fill(left, top, left + PANEL_W, top + panelH, 0xF0140E1E);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                width / 2, top + 7, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.hogwartsmagic.transmuter.how"), width / 2, top + 20, 0xB8B0C8);
        g.drawCenteredString(font, Component.translatable("gui.hogwartsmagic.transmuter.anvil"), width / 2, top + 31, 0x8A8298);
        g.drawCenteredString(font, Component.translatable("gui.hogwartsmagic.transmuter.mana"), width / 2, top + 42, 0x6FA8FF);

        SpellType onTransmuter = null;
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof SpellTransmuterBlockEntity be) {
            onTransmuter = be.spell();
        }

        int listTop = top + HEADER_H;
        int listBottom = listTop + VISIBLE_ROWS * ROW_H;
        g.enableScissor(left, listTop, left + PANEL_W, listBottom);
        Row hovered = null;
        var enchantments = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            int y = listTop + i * ROW_H - Math.round(scroll);
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean over = mouseX >= left && mouseX < left + PANEL_W && mouseY >= Math.max(y, listTop)
                    && mouseY < Math.min(y + ROW_H, listBottom);
            if (over) {
                hovered = row;
            }
            int bg = row.spell() == onTransmuter ? 0x60A070FF : over ? 0x40FFFFFF : (i % 2 == 0 ? 0x18FFFFFF : 0x00000000);
            if (bg != 0) {
                g.fill(left + 4, y + 1, left + PANEL_W - 4, y + ROW_H - 1, bg);
            }
            g.renderItem(books.get(i), left + 8, y + 4);
            g.drawString(font, Component.translatable("spell.hogwartsmagic." + row.spell().getId()), left + 28, y + 8, 0xE8DFF5);
            g.drawString(font, "→", left + 128, y + 8, 0x9F8CC8);

            int max = enchantments == null ? 1 : enchantments.getHolder(row.key()).map(h -> h.value().getMaxLevel()).orElse(1);
            Component name = Component.translatable("enchantment.hogwartsmagic." + row.key().location().getPath());
            Component levels = Component.literal(max > 1 ? " I–" + roman(max) : " I").withStyle(ChatFormatting.GRAY);
            g.drawString(font, Component.empty().append(name.copy().withStyle(ChatFormatting.AQUA)).append(levels),
                    left + 142, y + 8, 0xFFFFFF);
            g.renderItem(new ItemStack(row.on()), left + PANEL_W - 26, y + 4);
            Component on = Component.translatable("gui.hogwartsmagic.transmuter.on." + row.onKey());
            g.drawString(font, on, left + PANEL_W - 30 - font.width(on), y + 8, 0x8A8298);
        }
        g.disableScissor();

        if (maxScroll() > 0) {
            int trackH = VISIBLE_ROWS * ROW_H;
            int thumbH = Math.max(12, trackH * VISIBLE_ROWS / ROWS.size());
            int thumbY = listTop + Math.round((trackH - thumbH) * (scroll / maxScroll()));
            g.fill(left + PANEL_W - 3, listTop, left + PANEL_W - 1, listBottom, 0x40FFFFFF);
            g.fill(left + PANEL_W - 3, thumbY, left + PANEL_W - 1, thumbY + thumbH, 0xFFB58CFF);
        }

        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("enchantment.hogwartsmagic." + hovered.key().location().getPath())
                    .withStyle(ChatFormatting.AQUA));
            for (var line : font.split(Component.translatable("enchantment.hogwartsmagic."
                    + hovered.key().location().getPath() + ".desc"), 220)) {
                tip.add(Component.literal(toPlain(line)).withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private static String toPlain(net.minecraft.util.FormattedCharSequence line) {
        StringBuilder out = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    private static String roman(int n) {
        return switch (n) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
