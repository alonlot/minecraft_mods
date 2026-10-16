package com.hogwartsmagic.assignment;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.AssignmentItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns game events into assignment progress. Every handler returns at once when the player has no
 * assignment in progress, so a server full of players without one pays almost nothing. Death and damage
 * handlers run at LOWEST priority and skip cancelled events, so a kill another mod cancels never counts.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AssignmentEvents {
    /** Players who landed a long fall: game time of landing and the fall distance. */
    private static final Map<UUID, long[]> PENDING_FALLS = new HashMap<>();

    private AssignmentEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AssignmentRegistry());
    }

    // ---- Hooks called from mod code -------------------------------------------------------------

    /** A player caused a chest's loot to generate (they opened it for the first time). */
    public static void onChestLoot(ServerPlayer player, ResourceLocation tableId) {
        if (!AssignmentProgress.hasAny(player)) {
            return;
        }
        String id = tableId.toString();
        AssignmentProgress.bump(player, ObjectiveType.LOOT_CHEST, 1, objective -> objective.matches(id));
    }

    /** A cast finished (cooldown started), or a beam hit a living target when {@code livingHit}. */
    public static void onCast(ServerPlayer player, SpellType spell, boolean livingHit) {
        if (!AssignmentProgress.hasAny(player)) {
            return;
        }
        AssignmentProgress.bump(player, ObjectiveType.CAST, 1, objective ->
                objective.matches(spell.getId()) && "living".equals(objective.param("on")) == livingHit);
    }

    public static void onDuelWin(ServerPlayer player, Entity opponent) {
        if (!AssignmentProgress.hasAny(player)) {
            return;
        }
        AssignmentProgress.bump(player, ObjectiveType.DUEL_WIN, 1, objective -> entityMatches(objective, opponent));
    }

    public static void onFlooTravel(ServerPlayer player) {
        if (AssignmentProgress.hasAny(player)) {
            AssignmentProgress.bump(player, ObjectiveType.FLOO, 1, objective -> true);
        }
    }

    // ---- Forge events ---------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player deadPlayer) {
            PENDING_FALLS.remove(deadPlayer.getUUID());
            return;
        }
        DamageSource source = event.getSource();
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer direct ? direct
                : victim.getKillCredit() instanceof ServerPlayer credited ? credited : null;
        if (killer != null && AssignmentProgress.hasAny(killer)) {
            AssignmentProgress.bump(killer, ObjectiveType.KILL, 1, objective -> killMatches(objective, victim, source));
        }
        if (source.getEntity() instanceof Mob attacker && attacker != victim && victim instanceof Mob
                && victim.level() instanceof ServerLevel level) {
            for (ServerPlayer witness : level.getEntitiesOfClass(ServerPlayer.class,
                    victim.getBoundingBox().inflate(AssignmentRules.WITNESS_RADIUS))) {
                if (AssignmentProgress.hasAny(witness)) {
                    AssignmentProgress.bump(witness, ObjectiveType.MOB_KILLS_MOB, 1, objective -> entityMatches(objective, victim));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0f || !(event.getEntity() instanceof ServerPlayer player)
                || !AssignmentProgress.hasAny(player)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != player) {
            AssignmentProgress.bump(player, ObjectiveType.HURT_BY, 1, objective -> entityMatches(objective, attacker));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !AssignmentProgress.hasAny(player)) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        BlockPos pos = event.getPos();
        boolean noSky = player.level().getBrightness(LightLayer.SKY, pos) == 0;
        AssignmentProgress.bump(player, ObjectiveType.PLACE, 1, objective ->
                blockMatches(objective, state) && (!objective.flag("no_sky") || noSky));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !AssignmentProgress.hasAny(player)) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        AssignmentProgress.bump(player, ObjectiveType.BREAK, 1, objective ->
                blockMatches(objective, state) && biomeMatches(objective, player, pos));
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            ItemStack stack = event.getCrafting();
            AssignmentProgress.bump(player, ObjectiveType.CRAFT, stack.getCount(),
                    objective -> AssignmentProgress.itemMatches(objective, stack.getItem()));
        }
    }

    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            ItemStack stack = event.getSmelting();
            AssignmentProgress.bump(player, ObjectiveType.SMELT, stack.getCount(),
                    objective -> AssignmentProgress.itemMatches(objective, stack.getItem()));
        }
    }

    @SubscribeEvent
    public static void onBrew(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !AssignmentProgress.hasAny(player)) {
            return;
        }
        PotionContents contents = event.getStack().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        String potion = contents == null ? "" : contents.potion()
                .flatMap(holder -> holder.unwrapKey()).map(key -> key.location().toString()).orElse("");
        if (potion.isEmpty() || potion.equals("minecraft:water")) {
            return;
        }
        AssignmentProgress.bumpDistinct(player, ObjectiveType.BREW, potion, objective -> objective.matches(potion));
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            ItemStack stack = event.getItem();
            AssignmentProgress.bump(player, ObjectiveType.EAT, 1,
                    objective -> AssignmentProgress.itemMatches(objective, stack.getItem()));
        }
    }

    @SubscribeEvent
    public static void onFish(ItemFishedEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            AssignmentProgress.bump(player, ObjectiveType.FISH, 1, objective -> true);
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (!event.isCanceled() && event.getCausedByPlayer() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            Entity parent = event.getParentA();
            AssignmentProgress.bump(player, ObjectiveType.BREED, 1, objective -> entityMatches(objective, parent));
        }
    }

    @SubscribeEvent
    public static void onRepair(AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            AssignmentProgress.bump(player, ObjectiveType.REPAIR, 1, objective -> true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && AssignmentProgress.hasAny(player)) {
            AssignmentProgress.bump(player, ObjectiveType.BLOCK_SHIELD, 1, objective -> true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFall(LivingFallEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && event.getDistance() >= 3f
                && AssignmentProgress.hasAny(player)) {
            PENDING_FALLS.put(player.getUUID(), new long[]{player.level().getGameTime(), (long) event.getDistance()});
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_FALLS.remove(event.getEntity().getUUID());
        AssignmentItem.forget(event.getEntity().getUUID());
    }

    /** Once a second: time-based lines (stand), places (visit), survived falls and live completion. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || !player.isAlive() || !AssignmentProgress.hasAny(player)) {
            return;
        }
        long[] fall = PENDING_FALLS.get(player.getUUID());
        if (fall != null && player.level().getGameTime() - fall[0] >= AssignmentRules.FALL_SURVIVE_TICKS) {
            PENDING_FALLS.remove(player.getUUID());
            long distance = fall[1];
            AssignmentProgress.bump(player, ObjectiveType.FALL, 1, objective -> distance >= objective.intParam("min", 20));
        }
        AssignmentProgress.bump(player, ObjectiveType.STAND, 1, objective -> standMatches(objective, player));
        AssignmentProgress.bump(player, ObjectiveType.VISIT, 1, objective -> insideStructure(objective, player));
        AssignmentProgress.announceCompleted(player);
    }

    // ---- Matching -------------------------------------------------------------------------------

    private static boolean killMatches(ObjectiveDef objective, LivingEntity victim, DamageSource source) {
        if (!entityMatches(objective, victim)) {
            return false;
        }
        String cause = objective.param("cause");
        if ("fall".equals(cause) && !source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if ("explosion".equals(cause) && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        if ("fire".equals(cause) && !(source.is(DamageTypeTags.IS_FIRE) || victim.isOnFire())) {
            return false;
        }
        if ("armed".equals(objective.param("victim")) && !AssignmentRules.isWeaponPath(
                BuiltInRegistries.ITEM.getKey(victim.getMainHandItem().getItem()).getPath())) {
            return false;
        }
        return true;
    }

    /** Targets are entity ids, entity tags, or "monster" for anything hostile. */
    static boolean entityMatches(ObjectiveDef objective, Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityType<?> type = entity.getType();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (objective.targets().contains("monster") && entity instanceof Enemy) {
            return true;
        }
        return objective.matches(id, tag -> type.is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(tag))));
    }

    private static boolean blockMatches(ObjectiveDef objective, BlockState state) {
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return objective.matches(id, tag -> state.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse(tag))));
    }

    private static boolean biomeMatches(ObjectiveDef objective, Player player, BlockPos pos) {
        String biome = objective.param("biome");
        if (biome == null) {
            return true;
        }
        var holder = player.level().getBiome(pos);
        if (biome.startsWith("#")) {
            return holder.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(biome.substring(1))));
        }
        return holder.unwrapKey().map(key -> key.location().toString().equals(biome)).orElse(false);
    }

    private static boolean standMatches(ObjectiveDef objective, ServerPlayer player) {
        int y = player.getBlockY();
        if (objective.param("min_y") != null && y < objective.intParam("min_y", Integer.MIN_VALUE)) {
            return false;
        }
        if (objective.param("max_y") != null && y > objective.intParam("max_y", Integer.MAX_VALUE)) {
            return false;
        }
        if (objective.flag("night") && !player.level().isNight()) {
            return false;
        }
        if (objective.flag("sky") && !player.level().canSeeSky(player.blockPosition().above())) {
            return false;
        }
        if (objective.flag("in_water") && !player.isInWater()) {
            return false;
        }
        return biomeMatches(objective, player, player.blockPosition());
    }

    private static boolean insideStructure(ObjectiveDef objective, ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        BlockPos pos = player.blockPosition();
        List<String> targets = objective.targets();
        for (String target : targets) {
            ResourceLocation id = ResourceLocation.tryParse(target);
            Structure structure = id == null ? null : registry.get(id);
            if (structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid()) {
                return true;
            }
        }
        return false;
    }
}
