package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.enchant.SpellEnchantments;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Spell Transmuter. It holds three things on its top: a spell book (one with a spell enchantment, see
 * {@link SpellEnchantments#FROM_SPELL}), a plain book and arcane dust. With all three in place a rite of
 * {@link #RITE_TICKS} runs: the three rise, circle and fold into one another, and a level I enchanted book of the
 * spell's enchantment floats up in their place until someone takes it.
 *
 * <p>The server syncs the items and the rite tick through the block entity's update packet; the circling, the
 * glyphs and the light are drawn by each client from that ({@code SpellTransmuterRenderer} and {@link #clientTick}),
 * the finishing burst is sent from here as server particles so everyone near sees it.
 */
public class SpellTransmuterBlockEntity extends BlockEntity {
    public static final int SLOT_SPELL = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_DUST = 2;
    public static final int RITE_TICKS = 100;
    /** Height of the top the items rest over, in blocks from the block's foot. */
    public static final double TOP = 1.0;

    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private ItemStack result = ItemStack.EMPTY;
    /** Ticks into the rite, or -1 when none is running. */
    private int rite = -1;

    public SpellTransmuterBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.SPELL_TRANSMUTER_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------------------------------------ the slots

    /** Which slot {@code stack} goes in, or -1 if the transmuter has no use for it. */
    public static int slotFor(ItemStack stack) {
        if (stack.is(ExampleMod.MAGIC_BOOK.get())) {
            return SpellEnchantments.forSpell(MagicBookItem.getSpell(stack)) != null ? SLOT_SPELL : -1;
        }
        if (stack.is(Items.BOOK)) {
            return SLOT_BOOK;
        }
        if (stack.is(ExampleMod.ARCANE_DUST.get())) {
            return SLOT_DUST;
        }
        return -1;
    }

    /** Puts one of {@code held} on the transmuter. True if it was taken. */
    public boolean insert(ServerLevel level, ItemStack held) {
        if (rite >= 0 || !result.isEmpty()) {
            return false;
        }
        int slot = slotFor(held);
        if (slot < 0 || !slots[slot].isEmpty()) {
            return false;
        }
        slots[slot] = held.split(1);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F,
                0.9F + slot * 0.15F);
        if (!slots[SLOT_SPELL].isEmpty() && !slots[SLOT_BOOK].isEmpty() && !slots[SLOT_DUST].isEmpty()) {
            beginRite(level);
        }
        changed();
        return true;
    }

    /** What an empty hand takes back: the finished book first, else the last thing put on (not during the rite). */
    public ItemStack takeOne() {
        if (!result.isEmpty()) {
            ItemStack out = result;
            result = ItemStack.EMPTY;
            changed();
            return out;
        }
        if (rite >= 0) {
            return ItemStack.EMPTY;
        }
        for (int i = slots.length - 1; i >= 0; i--) {
            if (!slots[i].isEmpty()) {
                ItemStack out = slots[i];
                slots[i] = ItemStack.EMPTY;
                changed();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack slot(int i) {
        return slots[i];
    }

    public ItemStack result() {
        return result;
    }

    public int rite() {
        return rite;
    }

    public boolean isEmpty() {
        return result.isEmpty() && slots[0].isEmpty() && slots[1].isEmpty() && slots[2].isEmpty();
    }

    /** The spell on the transmuter (its book, or the finished book's source), for colours. */
    @Nullable
    public SpellType spell() {
        return slots[SLOT_SPELL].isEmpty() ? null : MagicBookItem.getSpell(slots[SLOT_SPELL]);
    }

    public int colour() {
        SpellType spell = spell();
        return spell == null ? 0xB58CFF : spell.getColor();
    }

    public void dropContents(Level level) {
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, slots[i]);
                slots[i] = ItemStack.EMPTY;
            }
        }
        if (!result.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, result);
            result = ItemStack.EMPTY;
        }
        rite = -1;
    }

    // ------------------------------------------------------------------------------------------------ the rite

    private void beginRite(ServerLevel level) {
        rite = 0;
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9F, 1.4F);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SpellTransmuterBlockEntity be) {
        if (level.isClientSide) {
            be.clientTick(level);
            if (be.rite >= 0 && be.rite < RITE_TICKS) {
                be.rite++; // run ahead smoothly between the server's syncs
            }
            return;
        }
        if (be.rite < 0) {
            return;
        }
        be.rite++;
        ServerLevel server = (ServerLevel) level;
        if (be.rite % 20 == 0 && be.rite < RITE_TICKS) {
            server.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.9F,
                    0.7F + be.rite / (float) RITE_TICKS * 0.8F);
        }
        if (be.rite == RITE_TICKS - 25) {
            server.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.8F, 1.6F);
        }
        if (be.rite >= RITE_TICKS) {
            be.finish(server);
        }
    }

    private void finish(ServerLevel level) {
        ResourceKey<Enchantment> key = SpellEnchantments.forSpell(spell());
        Optional<Holder.Reference<Enchantment>> holder = key == null ? Optional.empty() : SpellEnchantments.holder(level, key);
        int colour = colour();
        rite = -1;
        if (holder.isEmpty()) {
            changed(); // a datapack took the enchantment away: give the things back as they were
            return;
        }
        for (int i = 0; i < slots.length; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        result = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder.get(), 1));
        changed();

        Vec3 c = Vec3.atBottomCenterOf(worldPosition).add(0.0, TOP + 0.45, 0.0);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.RING, colour, 1.6f, 18), c.x, c.y - 0.3, c.z,
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.RING, 0xFFFFFF, 0.9f, 12), c.x, c.y, c.z,
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, colour, 0.5f, 14), c.x, c.y, c.z,
                3, 0.05, 0.05, 0.05, 0.0);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, colour, 0.08f, 22), c.x, c.y, c.z,
                40, 0.1, 0.1, 0.1, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 24, 0.05, 0.05, 0.05, 0.12);
        Fx.shake(level, c, 0.25f, 6.0f, 8);
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.3F);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.2F);
    }

    /** Cosmetic, on each client: idle motes, and the rite's glyph stream, spiral and rising light. */
    private void clientTick(Level level) {
        RandomSource random = level.random;
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + TOP;
        double cz = worldPosition.getZ() + 0.5;
        int colour = colour();
        if (rite < 0) {
            if (!isEmpty() && random.nextInt(6) == 0) {
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, result.isEmpty() ? colour : 0xD8B8FF,
                        0.035f, 30 + random.nextInt(20)), cx + (random.nextDouble() - 0.5) * 0.7, cy + 0.15,
                        cz + (random.nextDouble() - 0.5) * 0.7, 0.0, 0.01, 0.0);
            }
            if (random.nextInt(14) == 0) {
                // The four crystals catch the light now and then.
                int corner = random.nextInt(4);
                double ox = (corner & 1) == 0 ? -0.34 : 0.34;
                double oz = (corner & 2) == 0 ? -0.34 : 0.34;
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, 0xC9A6FF, 0.06f, 10),
                        cx + ox, cy + 0.12, cz + oz, 0.0, 0.004, 0.0);
            }
            return;
        }
        float p = Math.min(1.0f, rite / (float) RITE_TICKS);
        // Glyphs drawn in from a ring round the transmuter, ever more of them.
        int glyphs = 1 + (int) (p * 4);
        for (int i = 0; i < glyphs; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 1.4 + random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.ENCHANT, cx, cy + 0.6, cz,
                    Math.cos(a) * r, random.nextDouble() * 0.6 - 0.9, Math.sin(a) * r);
        }
        // Three threads of the spell's colour spiralling up out of the crystals.
        double spin = rite * (0.12 + p * 0.25);
        for (int k = 0; k < 3; k++) {
            double a = spin + k * (Math.PI * 2.0 / 3.0);
            double r = 0.45 * (1.0 - p * 0.7);
            level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, colour, 0.04f + p * 0.03f, 14),
                    cx + Math.cos(a) * r, cy + 0.2 + p * 0.5, cz + Math.sin(a) * r, 0.0, 0.02, 0.0);
        }
        // A column of light growing in the middle.
        if (rite % 2 == 0) {
            level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, colour, 0.08f + p * 0.2f, 8),
                    cx, cy + 0.3 + random.nextDouble() * (0.4 + p * 0.8), cz, 0.0, 0.03, 0.0);
        }
        if (p > 0.75f && random.nextInt(2) == 0) {
            level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xFFFFFF, 0.04f, 10),
                    cx + (random.nextDouble() - 0.5) * 0.3, cy + 0.9, cz + (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    // ------------------------------------------------------------------------------------------------ state

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0, 1.5, 0.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty()) {
                tag.put("Slot" + i, slots[i].save(registries));
            }
        }
        if (!result.isEmpty()) {
            tag.put("Result", result.save(registries));
        }
        tag.putInt("Rite", rite);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = tag.contains("Slot" + i) ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i)) : ItemStack.EMPTY;
        }
        result = tag.contains("Result") ? ItemStack.parseOptional(registries, tag.getCompound("Result")) : ItemStack.EMPTY;
        rite = tag.contains("Rite") ? tag.getInt("Rite") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
