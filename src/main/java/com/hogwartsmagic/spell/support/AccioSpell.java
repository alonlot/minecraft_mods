package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accio, the summoning charm. Creatures are reeled in up to a rank weight limit (never bosses).
 * A block hit summons loose items near the impact; from Expert also the first stack of a container
 * the caster could open; at Master a PvP hit snatches the victim's held item (never the Elder Wand,
 * which only Expelliarmus wins). Summoned items home in on the caster for up to 3 seconds. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccioSpell {
    private record Pull(UUID casterId, ResourceKey<Level> dimension, long until) {
    }

    private static final Map<UUID, Pull> ITEM_PULLS = new HashMap<>();

    private AccioSpell() {
    }

    /** What a block hit achieved: whether anything was summoned, and whether it earns mastery XP. */
    public record BlockResult(boolean pulled, boolean paysMastery) {
        static final BlockResult NOTHING = new BlockResult(false, false);
    }

    /** Item entities already paid for (bounded, oldest forgotten first): an item earns block XP once. */
    private static final Set<UUID> PAID_ITEMS = Collections.newSetFromMap(new LinkedHashMap<UUID, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 512;
        }
    });

    /**
     * Would an event handler (claim or safe-zone mod) cancel this hit? Posts the same LivingAttackEvent
     * that LivingEntity.hurt posts before its i-frame check, so a false from hurt() during the victim's
     * invulnerability window can be told apart from a real veto. Server only; call only after hurt() failed.
     */
    public static boolean attackVetoed(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean allowed = victim instanceof net.minecraft.world.entity.player.Player
                ? net.minecraftforge.common.ForgeHooks.onPlayerAttack(victim, source, amount)
                : net.minecraftforge.common.ForgeHooks.onLivingAttack(victim, source, amount);
        return !allowed;
    }

    /** Fire-and-forget form (self casts, NPC helpers): the result is not needed. */
    public static void onLivingHit(LivingEntity target, @Nullable Entity caster) {
        onLivingHit(target, caster, true);
    }

    /**
     * @param snatchEligible false for a Protego-reflected beam, or when the chip damage was vetoed by a claim
     *                       or safe-zone mod: the target is still reeled but never robbed
     * @return true only if the target was actually reeled (or pushed, for a non-player caster); false when
     *         the creature was too heavy for the rank of the caster, so the hit earns no mastery
     */
    public static boolean onLivingHit(LivingEntity target, @Nullable Entity caster, boolean snatchEligible) {
        if (target.level().isClientSide()) {
            return false;
        }
        if (!(caster instanceof ServerPlayer player)) {
            // NPC casters: the original unconditional push, with no weight limit.
            Vec3 toward = target.position().vectorTo(
                    caster != null ? caster.getEyePosition(1) : target.position().add(0, 0, 5))
                    .normalize().scale(0.8);
            target.push(toward.x, 0.3, toward.z);
            target.hurtMarked = true; // a player target only sees the pull once its motion is sent
            return true;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ACCIO);
        if (!AccioRules.canReel(rank, target.getMaxHealth(), CombatTags.isBoss(target))) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.accio.too_heavy"), true);
            return false;
        }
        boolean snatched = false;
        if (snatchEligible && AccioRules.pullsPlayerItem(rank) && target instanceof ServerPlayer victim
                && victim != player && !victim.isCreative() && !victim.isSpectator() && isPvp(player, victim)) {
            snatched = pullMainHand(victim, player);
        }
        // Continuously reel the target in toward the caster until it reaches them.
        return ModEvents.startAccioPull(player, target) || snatched;
    }

    /** @param reflected a Protego-reflected beam: behaves as Novice (no container pulls) and earns nothing */
    public static BlockResult onBlockHit(ServerLevel level, BlockPos pos, Direction face, Vec3 impact,
                                         @Nullable Entity caster, boolean reflected) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return BlockResult.NOTHING;
        }
        int rank = reflected ? 1 : SpellMasteryData.getRank(player, SpellType.ACCIO);
        int pulled = 0;
        boolean fresh = false;
        if (AccioRules.pullsContainers(rank) && pullFromContainer(level, pos, face, player)) {
            pulled++;
            fresh = true; // the stack came out of the container just now
        }
        double radius = AccioRules.itemRadius(rank);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(impact, impact).inflate(radius),
                e -> e.isAlive() && !e.getItem().isEmpty() && e.distanceToSqr(impact) <= radius * radius)) {
            if (pulled >= AccioRules.MAX_ITEMS) {
                break;
            }
            track(item, player);
            if (PAID_ITEMS.add(item.getUUID())) {
                fresh = true;
            }
            pulled++;
        }
        if (pulled > 0) {
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        return new BlockResult(pulled > 0, fresh && !reflected);
    }

    private static boolean pullFromContainer(ServerLevel level, BlockPos pos, Direction face, ServerPlayer player) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container) || !mayOpen(level, pos, face, player, blockEntity)) {
            return false;
        }
        if (blockEntity instanceof RandomizableContainer loot) {
            loot.unpackLootTable(player); // an unlooted chest rolls its loot for the caster, as opening would
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack taken = container.removeItem(slot, stack.getCount());
            container.setChanged();
            Vec3 at = Vec3.atCenterOf(pos.relative(face));
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, taken);
            item.setPickUpDelay(10);
            item.setTarget(player.getUUID());
            level.addFreshEntity(item);
            PAID_ITEMS.add(item.getUUID());
            track(item, player);
            return true;
        }
        return false;
    }

    /**
     * Could the caster open this container by hand? Spawn protection, the container's own lock, a
     * blocked chest lid, and then a RightClickBlock posted as the caster: claim mods veto there, and
     * the Gringotts alarm, the Living Statues and the merfolk hoards react as if it were opened.
     */
    private static boolean mayOpen(ServerLevel level, BlockPos pos, Direction face, ServerPlayer player, BlockEntity blockEntity) {
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (blockEntity instanceof BaseContainerBlockEntity base && !base.canOpen(player)) {
            return false; // locked: vanilla tells the player
        }
        if (level.getBlockState(pos).getBlock() instanceof ChestBlock && ChestBlock.isChestBlockedAt(level, pos)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        // Same claim pattern as AlohomoraSpell.mayUse: refuse a cancelled event or an explicit useBlock DENY.
        PlayerInteractEvent.RightClickBlock event =
                new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        return !MinecraftForge.EVENT_BUS.post(event)
                && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY;
    }

    private static boolean pullMainHand(ServerPlayer victim, ServerPlayer thief) {
        ItemStack held = victim.getMainHandItem();
        if (held.isEmpty() || held.is(ExampleMod.ELDER_WAND.get())) {
            return false;
        }
        victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ServerLevel level = victim.serverLevel();
        ItemEntity item = new ItemEntity(level, victim.getX(), victim.getEyeY() - 0.3, victim.getZ(), held.copy());
        item.setPickUpDelay(10);
        item.setTarget(thief.getUUID());
        level.addFreshEntity(item);
        PAID_ITEMS.add(item.getUUID());
        track(item, thief);
        return true;
    }

    private static boolean isPvp(ServerPlayer thief, ServerPlayer victim) {
        MinecraftServer server = thief.getServer();
        return server != null && server.isPvpAllowed() && thief.canHarmPlayer(victim);
    }

    private static void track(ItemEntity item, ServerPlayer caster) {
        ITEM_PULLS.put(item.getUUID(), new Pull(caster.getUUID(), item.level().dimension(),
                item.level().getGameTime() + AccioRules.ITEM_PULL_TICKS));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || ITEM_PULLS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Pull>> it = ITEM_PULLS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pull> entry = it.next();
            Pull pull = entry.getValue();
            if (pull.dimension() != level.dimension()) {
                continue;
            }
            Entity raw = level.getEntity(entry.getKey());
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(pull.casterId());
            if (!(raw instanceof ItemEntity item) || !item.isAlive() || caster == null || !caster.isAlive()
                    || caster.level() != level || now > pull.until()) {
                it.remove();
                continue;
            }
            Vec3 toCaster = caster.getEyePosition().subtract(0.0, 0.4, 0.0).subtract(item.position());
            double distance = toCaster.length();
            if (distance < AccioRules.ARRIVE_DISTANCE) {
                it.remove(); // close enough: vanilla pickup takes it from here
                continue;
            }
            item.setDeltaMovement(toCaster.scale(AccioRules.itemPullSpeed(distance) / distance));
            item.hasImpulse = true; // send the new motion now, not on the item's slow update interval
            if (now % 3 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, item.getX(), item.getY() + 0.2, item.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ITEM_PULLS.clear();
        PAID_ITEMS.clear();
    }
}
