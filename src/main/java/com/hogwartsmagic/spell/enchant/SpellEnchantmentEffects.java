package com.hogwartsmagic.spell.enchant;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the spell enchantments do. All server side (every event here is a server event, or checked for it); the
 * Warding and Ascendio parts live elsewhere (SpellArmour and the enchantment's own attribute effects).
 * <ul>
 * <li><b>Arresto</b> (boots): a long fall turns into a slow one; sneaking lets you drop.</li>
 * <li><b>Douse</b> I-V (boots): the boots take fire's damage as durability, and put the fire out after 3 s at I,
 * down to at once at V. Lava itself still burns.</li>
 * <li><b>Accio</b> (tools, weapons): what you break or kill flies straight into your inventory.</li>
 * <li><b>Reparo</b> I-III (anything durable): mends one point every 30 / 20 / 10 s while worn or held.</li>
 * <li><b>Lumos</b> (helmet): night vision in the dark.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellEnchantmentEffects {
    /** Douse: ticks a fire burns before the boots put it out, at level I; each level takes 15 off. */
    public static final int DOUSE_BASE_DELAY = 60;
    public static final int DOUSE_STEP = 15;
    /** Arresto catches a fall once it has dropped this far (blocks). */
    private static final float ARRESTO_CATCH = 3.0F;
    private static final int[] REPARO_PERIOD = {600, 400, 200};

    /** Game time each player caught fire (Douse), server thread only. */
    private static final Map<UUID, Long> BURNING_SINCE = new HashMap<>();
    /** Accio: where each player just broke a block, and when. */
    private static final Map<UUID, AccioBreak> ACCIO_BREAKS = new HashMap<>();

    private record AccioBreak(ServerPlayer player, BlockPos pos, long time) {
    }

    private SpellEnchantmentEffects() {
    }

    public static int douseDelay(int level) {
        return Math.max(0, DOUSE_BASE_DELAY - (Math.max(1, level) - 1) * DOUSE_STEP);
    }

    // ------------------------------------------------------------------------------------------------ the tick

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        arresto(player);
        douse(player, level);
        if (player.tickCount % 20 == 0) {
            lumos(player, level);
        }
        reparo(player, level);
    }

    private static void arresto(ServerPlayer player) {
        if (player.onGround() || player.isFallFlying() || player.getAbilities().flying || player.isInWater()
                || player.isShiftKeyDown() || player.isPassenger() || player.fallDistance < ARRESTO_CATCH
                || player.getDeltaMovement().y > -0.3) {
            return;
        }
        if (SpellEnchantments.level(player, SpellEnchantments.ARRESTO, EquipmentSlot.FEET) <= 0) {
            return;
        }
        boolean fresh = !player.hasEffect(MobEffects.SLOW_FALLING);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, true));
        if (fresh) {
            player.serverLevel().sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.RING, 0xBFE6FF, 0.6f, 14),
                    player.getX(), player.getY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS,
                    0.4F, 1.6F);
        }
    }

    private static void douse(ServerPlayer player, ServerLevel level) {
        if (!player.isOnFire()) {
            BURNING_SINCE.remove(player.getUUID());
            return;
        }
        int douse = SpellEnchantments.level(player, SpellEnchantments.DOUSE, EquipmentSlot.FEET);
        if (douse <= 0) {
            BURNING_SINCE.remove(player.getUUID());
            return;
        }
        long now = level.getGameTime();
        long since = BURNING_SINCE.computeIfAbsent(player.getUUID(), id -> now);
        if (now - since < douseDelay(douse)) {
            return;
        }
        player.clearFire();
        BURNING_SINCE.remove(player.getUUID());
        player.getItemBySlot(EquipmentSlot.FEET).hurtAndBreak(1, player, EquipmentSlot.FEET);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SMOKE, 0xE8EEF2, 0.35f, 26),
                player.getX(), player.getY() + 0.9, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                player.getX(), player.getY() + 0.3, player.getZ(), 18, 0.35, 0.2, 0.35, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** Douse boots take fire's damage as their own durability (not lava's, nor a fire spell's). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().is(DamageTypes.ON_FIRE) || event.getSource().is(DamageTypes.IN_FIRE)
                || event.getSource().is(DamageTypes.CAMPFIRE))
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (SpellEnchantments.level(player, SpellEnchantments.DOUSE, EquipmentSlot.FEET) <= 0) {
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        boots.hurtAndBreak(Math.max(1, (int) Math.ceil(event.getAmount())), player, EquipmentSlot.FEET);
        event.setCanceled(true);
    }

    private static void lumos(ServerPlayer player, ServerLevel level) {
        if (SpellEnchantments.level(player, SpellEnchantments.LUMOS, EquipmentSlot.HEAD) <= 0) {
            return;
        }
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, eyes),
                level.getBrightness(LightLayer.SKY, eyes) - level.getSkyDarken());
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (light < 7 && (current == null || current.getDuration() < 240)) {
            // Over 200 ticks so the screen never flickers as it runs out.
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
        }
    }

    private static void reparo(ServerPlayer player, ServerLevel level) {
        if (player.tickCount % REPARO_PERIOD[2] != 0) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamaged()) {
                continue;
            }
            int reparo = Math.min(3, SpellEnchantments.level(level, SpellEnchantments.REPARO, stack));
            if (reparo > 0 && player.tickCount % REPARO_PERIOD[reparo - 1] == 0) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ accio

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        if (SpellEnchantments.level(player, SpellEnchantments.ACCIO, EquipmentSlot.MAINHAND) > 0) {
            ACCIO_BREAKS.put(player.getUUID(), new AccioBreak(player, event.getPos().immutable(),
                    player.level().getGameTime()));
        }
    }

    /** Block drops appear in the same tick as the break, inside its block: those go to the breaker instead. */
    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item) || ACCIO_BREAKS.isEmpty()) {
            return;
        }
        long now = event.getLevel().getGameTime();
        for (AccioBreak brk : ACCIO_BREAKS.values()) {
            if (brk.time() == now && brk.player().level() == event.getLevel()
                    && item.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(brk.pos())) < 1.5 * 1.5) {
                if (pull(brk.player(), item.getItem())) {
                    event.setCanceled(true);
                }
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || SpellEnchantments.level(player, SpellEnchantments.ACCIO, EquipmentSlot.MAINHAND) <= 0) {
            return;
        }
        event.getDrops().removeIf(item -> pull(player, item.getItem()));
    }

    /** Puts {@code stack} into the player's inventory; true when all of it fitted (what does not stays put). */
    private static boolean pull(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.2F, 1.4F + player.getRandom().nextFloat() * 0.4F);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ACCIO_BREAKS.clear(); // a break only pulls the drops of its own tick
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BURNING_SINCE.remove(event.getEntity().getUUID());
        ACCIO_BREAKS.remove(event.getEntity().getUUID());
    }
}
