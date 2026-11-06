package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.util.MagicParticles;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.menu.WandWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class WandWorkbenchBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {

    public static final int SLOT_STICK = 0;
    public static final int SLOT_CORE = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int SLOT_RESULT = 3;
    public static final int WAND_CRAFT_TICKS_TOTAL = 100;

    private static final int CHIME_INTERVAL_TICKS = 17;
    private static final int ACCENT_INTERVAL_TICKS = 40;
    private static final int WIND_WHIRL_SOUND_INTERVAL = 14;
    private static final int WIND_IDLE_SOUND_INTERVAL = 6;
    private static final float CHIME_VOLUME = 0.35f;
    private static final float ACCENT_VOLUME = 0.12f;
    /** Wispy magical column around the bench. */
    private static final double WIND_BOX_HALF = 1.5;
    private static final double WIND_HEIGHT = 1.65;
    /** Wide enough to shove all nearby mobs in a ring around the forge. */
    private static final double TORNADO_MOB_RADIUS = 5.25;
    private static final double TORNADO_MOB_Y_BELOW = 0.5;
    private static final double TORNADO_MOB_Y_ABOVE = 5.0;
    /** Per-tick impulse must stay well above {@code 0.003} on each axis or vanilla living AI snaps velocity to zero. */
    private static final double TORNADO_PUSH_PER_TICK = 0.095;
    private static final double TORNADO_LIFT_PER_TICK = 0.11;
    private static final double TORNADO_SPIN_PER_TICK = 0.068;
    /** Players feel the vortex but are nudged less than mobs (still readable in PvE / co-op). */
    private static final double TORNADO_PLAYER_STRENGTH_MULT = 0.42;

    private static final com.hogwartsmagic.MagicParticleOptions MAGIC_SPARK_GOLD = MagicParticles.SPARK_GOLD;
    private static final com.hogwartsmagic.MagicParticleOptions MAGIC_SPARK_VIOLET = MagicParticles.SPARK_VIOLET;

    private int craftTicksRemaining;
    private int craftAccentPhase;

    private final ItemStackHandler inventory = new WorkbenchInventoryHandler();

    private final ContainerData dataAccess = new WorkbenchDataAccess();

    public WandWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.WAND_WORKBENCH_BLOCK_ENTITY.get(), pos, state);
    }

    private final class WorkbenchInventoryHandler extends ItemStackHandler {
        private WorkbenchInventoryHandler() {
            super(4);
        }

        @Override
        protected void onContentsChanged(int slot) {
            WandWorkbenchBlockEntity.this.onInventoryChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_RESULT) {
                return false;
            }
            if (slot == SLOT_STICK) {
                return stack.is(ExampleMod.FINE_WOOD_STICK.get());
            }
            if (slot == SLOT_CORE) {
                return stack.isEmpty()
                        || stack.is(ExampleMod.PHOENIX_FEATHER_CORE.get())
                        || stack.is(ExampleMod.UNICORN_HAIR_CORE.get())
                        || stack.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get());
            }
            if (slot == SLOT_CATALYST) {
                return stack.is(ExampleMod.ARCANE_CORE.get());
            }
            return false;
        }
    }

    private final class WorkbenchDataAccess implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> craftTicksRemaining;
                case 1 -> isCrafting() ? WAND_CRAFT_TICKS_TOTAL : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    public ContainerData getDataAccess() {
        return this.dataAccess;
    }

    public int getCraftTicksRemaining() {
        return this.craftTicksRemaining;
    }

    public boolean isCrafting() {
        return this.craftTicksRemaining > 0;
    }

    private void onInventoryChanged() {
        this.setChanged();
        this.syncToClients();
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WandWorkbenchBlockEntity be) {
        be.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack result = this.inventory.getStackInSlot(SLOT_RESULT);
        if (!result.isEmpty()) {
            if (this.craftTicksRemaining > 0) {
                this.craftTicksRemaining = 0;
                this.syncToClients();
            }
            return;
        }

        if (!canCraftWand()) {
            if (this.craftTicksRemaining > 0) {
                this.craftTicksRemaining = 0;
                this.syncToClients();
            }
            return;
        }

        if (this.craftTicksRemaining == 0) {
            this.craftTicksRemaining = WAND_CRAFT_TICKS_TOTAL;
            this.craftAccentPhase = 0;
            Vec3 center = Vec3.atBottomCenterOf(pos).add(0.0, 0.65, 0.0);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.65f, 1.0f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_INHALE, SoundSource.BLOCKS, 0.85f, 0.82f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.6f, 1.05f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 0.55f, 1.15f);
            this.spawnCraftWindOpeningBurst(serverLevel, pos);
            this.syncToClients();
        }

        this.tickCraftingEffects(serverLevel, pos);
        this.craftTicksRemaining--;
        if (this.craftTicksRemaining <= 0) {
            this.finishCrafting();
            this.craftTicksRemaining = 0;
            this.syncToClients();
        } else if (this.craftTicksRemaining % 4 == 0) {
            this.syncToClients();
        }
    }

    private boolean canCraftWand() {
        ItemStack stick = this.inventory.getStackInSlot(SLOT_STICK);
        ItemStack core = this.inventory.getStackInSlot(SLOT_CORE);
        ItemStack catalyst = this.inventory.getStackInSlot(SLOT_CATALYST);
        if (stick.isEmpty() || catalyst.isEmpty()) {
            return false;
        }
        if (!stick.is(ExampleMod.FINE_WOOD_STICK.get()) || !catalyst.is(ExampleMod.ARCANE_CORE.get())) {
            return false;
        }
        if (core.isEmpty()) {
            return true;
        }
        return core.is(ExampleMod.PHOENIX_FEATHER_CORE.get())
                || core.is(ExampleMod.UNICORN_HAIR_CORE.get())
                || core.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get());
    }

    @Nullable
    private ItemStack getWandResultForCurrentInputs() {
        ItemStack stick = this.inventory.getStackInSlot(SLOT_STICK);
        ItemStack core = this.inventory.getStackInSlot(SLOT_CORE);
        ItemStack catalyst = this.inventory.getStackInSlot(SLOT_CATALYST);
        if (stick.isEmpty() || catalyst.isEmpty()
                || !stick.is(ExampleMod.FINE_WOOD_STICK.get())
                || !catalyst.is(ExampleMod.ARCANE_CORE.get())) {
            return null;
        }
        if (core.isEmpty()) {
            return new ItemStack(ExampleMod.HARRY_POTTER_WAND.get());
        }
        if (core.is(ExampleMod.PHOENIX_FEATHER_CORE.get())) {
            return new ItemStack(ExampleMod.PHOENIX_CORE_WAND.get());
        }
        if (core.is(ExampleMod.UNICORN_HAIR_CORE.get())) {
            return new ItemStack(ExampleMod.UNICORN_CORE_WAND.get());
        }
        if (core.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get())) {
            return new ItemStack(ExampleMod.DRAGON_CORE_WAND.get());
        }
        return null;
    }

    private void tickCraftingEffects(ServerLevel level, BlockPos pos) {
        int total = WAND_CRAFT_TICKS_TOTAL;
        int elapsed = total - this.craftTicksRemaining;
        RandomSource rand = level.random;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.55;
        double cz = pos.getZ() + 0.5;
        long time = level.getGameTime();

        this.spawnRotatingWindRing(level, cx, cy, cz, time, elapsed, total, rand);
        this.spawnWindVolumeChaos(level, cx, cy, cz, rand);
        this.spawnWindSpiralColumn(level, cx, pos.getY() + 0.15, cz, time);
        this.spawnSweepGusts(level, cx, cy, cz, rand);

        if (this.craftTicksRemaining % 3 == 0) {
            double x = cx + (rand.nextDouble() - 0.5) * 0.45;
            double y = pos.getY() + 0.55 + rand.nextDouble() * 0.75;
            double z = cz + (rand.nextDouble() - 0.5) * 0.45;
            level.sendParticles(MagicParticles.MOTE_GOLD, x, y, z, 2, 0.28, 0.16, 0.28, 0.03);
            if (rand.nextFloat() < 0.45f) {
                level.sendParticles(MAGIC_SPARK_GOLD, x, y, z, 1, 0.12, 0.08, 0.12, 0.02);
            }
        }
        if (this.craftTicksRemaining % 5 == 0) {
            level.sendParticles(MagicParticles.SMOKE_VIOLET, cx, cy + 0.1, cz, 1, 0.35, 0.2, 0.35, 0.015);
        }
        if (rand.nextFloat() < 0.2f) {
            double x = cx + (rand.nextDouble() - 0.5) * WIND_BOX_HALF;
            double y = pos.getY() + 0.25 + rand.nextDouble() * WIND_HEIGHT;
            double z = cz + (rand.nextDouble() - 0.5) * WIND_BOX_HALF;
            level.sendParticles(MagicParticles.MOTE_VIOLET, x, y, z, 1, 0.28, 0.12, 0.28, 0.01);
        }
        if (rand.nextFloat() < 0.16f) {
            double x = cx + (rand.nextDouble() - 0.5) * WIND_BOX_HALF;
            double y = pos.getY() + 0.25 + rand.nextDouble() * WIND_HEIGHT;
            double z = cz + (rand.nextDouble() - 0.5) * WIND_BOX_HALF;
            level.sendParticles(MagicParticles.SPARK_VIOLET, x, y, z, 1, 0.25, 0.1, 0.25, 0.025);
            if (rand.nextFloat() < 0.35f) {
                level.sendParticles(MAGIC_SPARK_VIOLET, x, y, z, 1, 0.1, 0.06, 0.1, 0.015);
            }
        }
        if (rand.nextFloat() < 0.22f) {
            level.sendParticles(MagicParticles.MOTE_VIOLET, cx, cy, cz, 2, 0.5, 0.28, 0.5, 0.025);
        }
        if (this.craftTicksRemaining % 12 == 0) {
            level.sendParticles(MagicParticles.MOTE_ICE, cx, cy + 0.2, cz, 3, 0.42, 0.3, 0.42, 0.015);
        }
        if (elapsed > total / 2 && this.craftTicksRemaining % 9 == 0) {
            level.sendParticles(MagicParticles.MOTE_TEAL, cx, cy + 0.25, cz, 2, 0.4, 0.22, 0.4, 0.04);
        }

        ItemStack core = this.inventory.getStackInSlot(SLOT_CORE);
        if (core.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get()) && rand.nextFloat() < 0.12f) {
            level.sendParticles(MagicParticles.ember(), cx + (rand.nextDouble() - 0.5) * 0.4, cy, cz + (rand.nextDouble() - 0.5) * 0.4, 1, 0.2, 0.06, 0.2, 0.01);
        } else if (core.is(ExampleMod.PHOENIX_FEATHER_CORE.get()) && rand.nextFloat() < 0.1f) {
            level.sendParticles(MagicParticles.SPARK_GOLD, cx, cy + 0.1, cz, 1, 0.2, 0.08, 0.2, 0.01);
        }

        if (this.craftTicksRemaining % 14 == 0) {
            level.sendParticles(ParticleTypes.GUST, cx, cy, cz, 1, 0.45, 0.18, 0.45, 0.02);
            level.sendParticles(ParticleTypes.SMALL_GUST, cx, cy + 0.08, cz, 2, 0.5, 0.2, 0.5, 0.02);
            level.sendParticles(MagicParticles.MOTE_WHITE, cx, cy + 0.12, cz, 2, 0.25, 0.12, 0.25, 0.03);
        }
        if (this.craftTicksRemaining % 32 == 0) {
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, cx, pos.getY() + 0.65, cz, 1, 0.08, 0.06, 0.08, 0.0);
        }
        if (this.craftTicksRemaining % 22 == 0) {
            level.sendParticles(MagicParticles.GLINT_GOLD, cx, cy + 0.35, cz, 1, 0.15, 0.08, 0.15, 0.0);
        }

        if (this.craftTicksRemaining % CHIME_INTERVAL_TICKS == 0) {
            float pitch = 0.85f + rand.nextFloat() * 0.3f;
            level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME, pitch);
        }
        if (this.craftTicksRemaining % WIND_WHIRL_SOUND_INTERVAL == 0) {
            float p = 0.9f + rand.nextFloat() * 0.25f;
            level.playSound(null, cx, cy, cz, SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 0.42f + rand.nextFloat() * 0.12f, p);
        }
        if (this.craftTicksRemaining % WIND_IDLE_SOUND_INTERVAL == 0) {
            level.playSound(null, cx, cy, cz, SoundEvents.BREEZE_IDLE_AIR, SoundSource.HOSTILE, 0.22f + rand.nextFloat() * 0.1f, 0.75f + rand.nextFloat() * 0.4f);
        }

        this.craftAccentPhase++;
        if (this.craftAccentPhase >= ACCENT_INTERVAL_TICKS) {
            this.craftAccentPhase = 0;
            level.playSound(null, cx, cy, cz, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, ACCENT_VOLUME, 1.2f);
            level.playSound(null, cx, cy, cz, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, ACCENT_VOLUME * 0.8f, 1.5f);
            level.playSound(null, cx, cy, cz, SoundEvents.BREEZE_CHARGE, SoundSource.HOSTILE, 0.28f, 1.3f);
        }

        this.applyCraftTornadoToMobs(level, pos, 1.0);
    }

    private static boolean isCraftTornadoTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        return true;
    }

    /**
     * Pushes living entities outward from the bench, lifts them slightly, and adds a tangential nudge.
     * Players use {@link #TORNADO_PLAYER_STRENGTH_MULT} so the effect is weaker than for mobs.
     */
    private void applyCraftTornadoToMobs(ServerLevel level, BlockPos pos, double strengthMultiplier) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double yMin = pos.getY() - TORNADO_MOB_Y_BELOW;
        double yMax = pos.getY() + TORNADO_MOB_Y_ABOVE;
        AABB box = new AABB(cx - TORNADO_MOB_RADIUS, yMin, cz - TORNADO_MOB_RADIUS, cx + TORNADO_MOB_RADIUS, yMax, cz + TORNADO_MOB_RADIUS);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, WandWorkbenchBlockEntity::isCraftTornadoTarget)) {
            double dx = entity.getX() - cx;
            double dz = entity.getZ() - cz;
            double distH = Math.sqrt(dx * dx + dz * dz);
            double nx;
            double nz;
            if (distH < 1.0e-4) {
                continue;
            }
            if (distH < 0.12) {
                float a = level.random.nextFloat() * Mth.TWO_PI;
                nx = Mth.cos(a);
                nz = Mth.sin(a);
            } else {
                nx = dx / distH;
                nz = dz / distH;
            }
            double tx = -nz;
            double tz = nx;

            double falloff = Mth.clamp(1.0 - Math.max(distH, 0.12) / TORNADO_MOB_RADIUS, 0.22, 1.0);
            double heightFactor = Mth.clamp((entity.getY() - yMin) / Math.max(0.5, yMax - yMin), 0.25, 1.2);
            double kr = entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double k = falloff * heightFactor * Mth.clamp(1.0 - kr, 0.35, 1.0) * strengthMultiplier;
            if (entity instanceof Player) {
                k *= TORNADO_PLAYER_STRENGTH_MULT;
            }

            double push = TORNADO_PUSH_PER_TICK * k;
            double lift = TORNADO_LIFT_PER_TICK * k;
            double spin = TORNADO_SPIN_PER_TICK * k;

            Vec3 add = new Vec3(nx * push + tx * spin, lift, nz * push + tz * spin);
            entity.push(add);
            entity.hurtMarked = true;
            entity.fallDistance = Math.min(entity.fallDistance, 1.5f);
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }

    private void spawnCraftWindOpeningBurst(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.55;
        double cz = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, cx, cy, cz, 1, 0.06, 0.05, 0.06, 0.0);
        level.sendParticles(MagicParticles.MOTE_GOLD, cx, cy + 0.2, cz, 8, 0.35, 0.2, 0.35, 0.04);
        level.sendParticles(MAGIC_SPARK_GOLD, cx, cy + 0.15, cz, 6, 0.3, 0.15, 0.3, 0.02);
        double r = 0.75;
        for (int i = 0; i < 10; i++) {
            double a = (i / 10.0) * Math.PI * 2;
            double px = cx + Math.cos(a) * r;
            double pz = cz + Math.sin(a) * r;
            level.sendParticles(ParticleTypes.SMALL_GUST, px, cy, pz, 1, 0.15, 0.08, 0.15, 0.015);
            if (i % 2 == 0) {
                level.sendParticles(MagicParticles.SMOKE_WHITE, px, cy + 0.04, pz, 1, 0.12, 0.06, 0.12, 0.008);
            }
            if (i % 3 == 0) {
                level.sendParticles(MagicParticles.MOTE_WHITE, px, cy + 0.08, pz, 1, 0.08, 0.04, 0.08, 0.02);
            }
        }
    }

    private void spawnRotatingWindRing(
            ServerLevel level, double cx, double cy, double cz, long time, int elapsed, int total, RandomSource rand) {
        double spin = time * 0.38 + (100 - this.craftTicksRemaining) * 0.06;
        int segments = 11;
        double radius = 0.78 + 0.2 * Math.sin(time * 0.1) + (elapsed / (float) total) * 0.22;
        for (int i = 0; i < segments; i++) {
            double a = spin + (i * Math.PI * 2 / segments);
            double px = cx + Math.cos(a) * radius;
            double pz = cz + Math.sin(a) * radius;
            double py = cy + rand.nextDouble() * 0.6 + Math.sin(a * 2) * 0.06;
            level.sendParticles(ParticleTypes.SMALL_GUST, px, py, pz, 1, 0.12, 0.08, 0.12, 0.012);
            if (i % 2 == 0) {
                level.sendParticles(MagicParticles.SMOKE_WHITE, px, py, pz, 1, 0.1, 0.06, 0.1, 0.005);
            }
            if (i % 3 == 0) {
                level.sendParticles(MagicParticles.MOTE_GOLD, px, py + 0.06, pz, 1, 0.1, 0.06, 0.1, 0.02);
            }
            if (i % 4 == 0) {
                level.sendParticles(MAGIC_SPARK_VIOLET, px, py + 0.04, pz, 1, 0.06, 0.04, 0.06, 0.01);
            }
            if (i % 5 == 0) {
                level.sendParticles(MagicParticles.SMOKE_VIOLET, px, py - 0.02, pz, 1, 0.1, 0.06, 0.1, 0.01);
            }
        }
    }

    private void spawnWindVolumeChaos(ServerLevel level, double cx, double cy, double cz, RandomSource rand) {
        int puffCount = 5;
        for (int n = 0; n < puffCount; n++) {
            double ox = (rand.nextDouble() - 0.5) * 2.0 * WIND_BOX_HALF;
            double oy = rand.nextDouble() * WIND_HEIGHT;
            double oz = (rand.nextDouble() - 0.5) * 2.0 * WIND_BOX_HALF;
            level.sendParticles(MagicParticles.SMOKE_WHITE, cx + ox, cy + oy * 0.45, cz + oz, 1, 0.15, 0.08, 0.15, 0.004);
            if (rand.nextFloat() < 0.55f) {
                level.sendParticles(MagicParticles.MOTE_GOLD, cx + ox * 0.85, cy + oy * 0.5, cz + oz * 0.85, 1, 0.14, 0.08, 0.14, 0.015);
            }
            if (rand.nextFloat() < 0.35f) {
                level.sendParticles(MAGIC_SPARK_GOLD, cx + ox * 0.8, cy + oy * 0.48, cz + oz * 0.8, 1, 0.08, 0.05, 0.08, 0.012);
            }
            if (rand.nextFloat() < 0.2f) {
                level.sendParticles(MagicParticles.MOTE_TEAL, cx + ox * 0.75, cy + oy * 0.42, cz + oz * 0.75, 1, 0.06, 0.04, 0.06, 0.008);
            }
        }
    }

    private void spawnWindSpiralColumn(ServerLevel level, double cx, double baseY, double cz, long time) {
        for (int i = 0; i < 8; i++) {
            double t = time * 0.24 + i * 0.62;
            double sr = 0.12 + i * 0.065;
            double sx = cx + Math.cos(t) * sr;
            double sz = cz + Math.sin(t) * sr;
            double sy = baseY + i * 0.1;
            level.sendParticles(MagicParticles.MOTE_GOLD, sx, sy, sz, 2, 0.12, 0.07, 0.12, 0.025);
            if (i % 2 == 0) {
                level.sendParticles(MagicParticles.MOTE_VIOLET, sx, sy, sz, 1, 0.08, 0.05, 0.08, 0.012);
            }
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SMALL_GUST, sx, sy, sz, 1, 0.1, 0.05, 0.1, 0.01);
            }
            if (i % 4 == 0) {
                level.sendParticles(MagicParticles.MOTE_WHITE, sx, sy + 0.04, sz, 1, 0.06, 0.03, 0.06, 0.015);
            }
            if (i % 5 == 0) {
                level.sendParticles(MAGIC_SPARK_VIOLET, sx, sy, sz, 1, 0.05, 0.03, 0.05, 0.01);
            }
        }
    }

    private void spawnSweepGusts(ServerLevel level, double cx, double cy, double cz, RandomSource rand) {
        if (this.craftTicksRemaining % 8 != 0) {
            return;
        }
        for (int s = 0; s < 3; s++) {
            double ox = (rand.nextDouble() - 0.5) * 1.65;
            double oz = (rand.nextDouble() - 0.5) * 1.65;
            double py = cy + rand.nextDouble() * 0.55;
            level.sendParticles(MagicParticles.SPARK_WHITE, cx + ox, py, cz + oz, 1, 0.08, 0.03, 0.08, 0.0);
            if (s == 0) {
                level.sendParticles(MagicParticles.SPARK_WHITE, cx + ox * 0.9, py, cz + oz * 0.9, 2, 0.12, 0.05, 0.12, 0.0);
            }
        }
    }

    private void finishCrafting() {
        ItemStack wand = getWandResultForCurrentInputs();
        if (wand == null) {
            return;
        }
        this.inventory.extractItem(SLOT_STICK, 1, false);
        if (!this.inventory.getStackInSlot(SLOT_CORE).isEmpty()) {
            this.inventory.extractItem(SLOT_CORE, 1, false);
        }
        this.inventory.extractItem(SLOT_CATALYST, 1, false);
        this.inventory.setStackInSlot(SLOT_RESULT, wand);

        Level level = this.level;
        if (level != null) {
            BlockPos pos = this.getBlockPos();
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.75;
            double z = pos.getZ() + 0.5;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, x, y, z, 1, 0.1, 0.08, 0.1, 0.0);
                for (int ring = 0; ring < 2; ring++) {
                    double r = 0.45 + ring * 0.35;
                    for (int i = 0; i < 12; i++) {
                        double a = (i / 12.0) * Math.PI * 2 + ring * 0.2;
                        double px = x + Math.cos(a) * r;
                        double pz = z + Math.sin(a) * r;
                        double py = y - 0.05 + ring * 0.06;
                        serverLevel.sendParticles(ParticleTypes.SMALL_GUST, px, py, pz, 1, 0.15, 0.08, 0.15, 0.015);
                        if (i % 2 == 0) {
                            serverLevel.sendParticles(MagicParticles.SMOKE_WHITE, px, py + 0.04, pz, 1, 0.12, 0.06, 0.12, 0.005);
                        }
                        if (i % 3 == 0) {
                            serverLevel.sendParticles(MagicParticles.MOTE_GOLD, px, py + 0.08, pz, 1, 0.08, 0.04, 0.08, 0.012);
                        }
                    }
                }
                serverLevel.sendParticles(MagicParticles.MOTE_WHITE, x, y, z, 6, 0.35, 0.18, 0.35, 0.03);
                serverLevel.sendParticles(MagicParticles.GLINT_GOLD, x, y, z, 4, 0.3, 0.12, 0.3, 0.0);
                serverLevel.sendParticles(MagicParticles.MOTE_VIOLET, x, y, z, 5, 0.35, 0.15, 0.35, 0.02);
                serverLevel.sendParticles(MAGIC_SPARK_GOLD, x, y + 0.1, z, 10, 0.45, 0.2, 0.45, 0.03);
                serverLevel.sendParticles(MAGIC_SPARK_VIOLET, x, y, z, 8, 0.4, 0.18, 0.4, 0.025);
                this.applyCraftTornadoToMobs(serverLevel, pos, 2.4);
            }
            level.playSound(null, x, y, z, ModSounds.WAND_ACHIEVEMENT_SOUND.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
            level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.35f, 1.2f);
            level.playSound(null, x, y, z, SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 0.95f, 1.05f);
            level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.75f, 1.0f);
            level.playSound(null, x, y, z, SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 0.45f, 1.2f);
        }
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            try {
                this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
            } catch (RuntimeException exception) {
                for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
                    this.inventory.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
        this.craftTicksRemaining = Math.max(0, tag.getInt("CraftRemaining"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.hogwartsmagic.wand_workbench");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new WandWorkbenchMenu(containerId, inv, this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return AABB.encapsulatingFullBlocks(getBlockPos(), getBlockPos().above(1));
    }

    public void dropAllContents(Level level, BlockPos pos) {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                this.inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
