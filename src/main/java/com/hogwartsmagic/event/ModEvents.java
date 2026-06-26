package com.hogwartsmagic.event;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.hogwartsmagic.item.InvisibilityCloakItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.item.HogwartsInviteItem;
import com.hogwartsmagic.item.OwlRouteTagItem;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.spell.IncarcerousChainAnimation;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.util.PlayerMagicData;
import com.hogwartsmagic.util.HorcruxData;
import com.hogwartsmagic.util.ReparoManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {
    private static final double POLYJUICE_SPLASH_RANGE_SQ = 16.0D;

    // Track last placed LIGHT block position for Lumos per entity to avoid leaving trails
    private static final java.util.Map<java.util.UUID, net.minecraft.core.BlockPos> LUMOS_POS = new java.util.WeakHashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.Map<net.minecraft.core.BlockPos, Integer>> REVELIO_LIGHTS = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.Map<net.minecraft.core.BlockPos, Integer>> AQUA_ERUCTO_WATER = new java.util.HashMap<>();
    private static boolean serpensortiaUseLatched = false;
    private static boolean fiendfyreUseLatched = false;
    private static boolean wingardiumUseLatched = false;
    private static boolean imperioUseLatched = false;
    private static final String TAG_WINGARDIUM_CHANNELING = "hogwartsmagic:wingardium_channeling";
    private static final String TAG_WINGARDIUM_TARGET = "hogwartsmagic:wingardium_target";
    private static final String TAG_WINGARDIUM_RECENT_TARGET = "hogwartsmagic:wingardium_recent_target";
    private static final String TAG_WINGARDIUM_RECENT_EXPIRY = "hogwartsmagic:wingardium_recent_expiry";
    private static final String TAG_WINGARDIUM_LAST_SEEN = "hogwartsmagic:wingardium_last_seen";
    private static final String TAG_WINGARDIUM_LAST_DRAIN = "hogwartsmagic:wingardium_last_drain";
    private static final int WINGARDIUM_UPKEEP_INTERVAL = 6;
    private static final int WINGARDIUM_HEARTBEAT_TIMEOUT = 4;
    private static final double WINGARDIUM_CAST_RANGE = 6.0D;
    private static final double WINGARDIUM_HOLD_RANGE = 7.5D;
    private static final java.util.Map<java.util.UUID, ActiveWizardRaid> ACTIVE_WIZARD_RAIDS = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.UUID> WIZARD_TO_RAID = new java.util.HashMap<>();
    private static final int WIZARD_RAID_RADIUS = 96;
    private static final int HERO_OF_VILLAGE_DURATION = 20 * 60 * 5;
    private static final int STRUCTURE_SPAWN_CHECK_INTERVAL = 200; // 10s
    private static final int STUDENT_SPAWN_RADIUS = 72;
    private static final int DEATH_EATER_SPAWN_RADIUS = 80;
    private static final int STUDENT_NEARBY_CAP = 8;
    private static final int DEATH_EATER_NEARBY_CAP = 6;
    private static final int DEATH_EATER_BASE_CHECK_INTERVAL = 20 * 30;
    private static final int DEATH_EATER_BASE_SEARCH_RADIUS = 40;
    private static final int DEATH_EATER_BASE_VERTICAL_SEARCH = 8;
    private static final int DEATH_EATER_BASE_SPAWN_MIN_DIST = 18;
    private static final int DEATH_EATER_BASE_SPAWN_MAX_DIST = 34;
    private static final int DEATH_EATER_BASE_RAID_RADIUS = 96;
    private static final String TAG_LAST_DEATH_EATER_BASE_RAID = "hogwartsmagic:last_death_eater_base_raid";
    /** Same stat as phantoms: ticks since last bed / respawn reset (1 day = 24000 ticks). */
    private static final int DEMENTOR_INSOMNIA_TICKS = 5 * 24000;
    private static final int DEMENTOR_SPAWN_CHECK_INTERVAL = 160;
    private static final float DEMENTOR_SPAWN_CHANCE = 0.07f;
    private static final int DEMENTOR_NEARBY_CAP = 4;
    private static final int DEMENTOR_MIN_DIST = 18;
    private static final int DEMENTOR_MAX_DIST = 52;
    private static final int STUDENT_PER_VILLAGER_RATIO = 3;
    /** Roughly 1 in N eligible structure-spawned villagers becomes a lycanthrope villager. */
    private static final int LYCANTHROPE_VILLAGER_ONE_IN = 30;
    private static final int LYCANTHROPE_VILLAGE_STRUCTURE_RADIUS = 96;
    private static final String TAG_PROTEGO_MAXIMA_CHANNELING = "hogwartsmagic:protego_maxima_channeling";
    private static final String TAG_PROTEGO_MAXIMA_LAST_DRAIN = "hogwartsmagic:protego_maxima_last_drain";
    private static final String TAG_PROTEGO_MAXIMA_LAST_SEEN = "hogwartsmagic:protego_maxima_last_seen";
    private static final String TAG_PROTEGO_MAXIMA_HEALTH = "hogwartsmagic:protego_maxima_health";
    private static final String TAG_PROTEGO_MAXIMA_LAST_HIT = "hogwartsmagic:protego_maxima_last_hit";
    private static final int PROTEGO_MAXIMA_UPKEEP_INTERVAL = 10;
    private static final int PROTEGO_MAXIMA_HEARTBEAT_TIMEOUT = 4;
    private static final double PROTEGO_MAXIMA_BARRIER_RADIUS = 4.5D;
    private static final float PROTEGO_MAXIMA_MAX_HEALTH = 100.0f;
    private static final int PROTEGO_MAXIMA_REGEN_DELAY = 20;
    private static final float PROTEGO_MAXIMA_REGEN_PER_TICK = 0.7f;
    private static final String TAG_FIENDFYRE_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
    private static final String TAG_FIENDFYRE_LAST_DRAIN = "hogwartsmagic:fiendfyre_last_drain";
    private static final String TAG_FIENDFYRE_LAST_SEEN = "hogwartsmagic:fiendfyre_last_seen";
    private static final String TAG_FIENDFYRE_ENTITY = "hogwartsmagic:fiendfyre_entity";
    private static final int FIENDFYRE_UPKEEP_INTERVAL = 4;
    private static final int FIENDFYRE_HEARTBEAT_TIMEOUT = 4;
    private static final String TAG_IMPERIO_CHANNELING = "hogwartsmagic:imperio_channeling";
    private static final String TAG_IMPERIO_TARGET = "hogwartsmagic:imperio_target";
    private static final String TAG_IMPERIO_RECENT_HIT_TARGET = "hogwartsmagic:imperio_recent_hit_target";
    private static final String TAG_IMPERIO_RECENT_HIT_EXPIRY = "hogwartsmagic:imperio_recent_hit_expiry";
    private static final String TAG_IMPERIO_LAST_SEEN = "hogwartsmagic:imperio_last_seen";
    private static final String TAG_IMPERIO_LAST_DRAIN = "hogwartsmagic:imperio_last_drain";
    private static final String TAG_IMPERIO_LAST_ATTACK = "hogwartsmagic:imperio_last_attack";
    private static final String TAG_IMPERIO_FORWARD = "hogwartsmagic:imperio_forward";
    private static final String TAG_IMPERIO_STRAFE = "hogwartsmagic:imperio_strafe";
    private static final String TAG_IMPERIO_JUMP = "hogwartsmagic:imperio_jump";
    private static final String TAG_IMPERIO_ATTACK = "hogwartsmagic:imperio_attack";
    private static final String TAG_IMPERIO_YAW = "hogwartsmagic:imperio_yaw";
    private static final String TAG_IMPERIO_PITCH = "hogwartsmagic:imperio_pitch";
    private static final int IMPERIO_UPKEEP_INTERVAL = 10;
    private static final int IMPERIO_HEARTBEAT_TIMEOUT = 4;
    private static final int IMPERIO_ATTACK_COOLDOWN = 10;
    // ── Apparition (channeled self-teleport with a minimap destination picker) ──
    private static final String TAG_APPARITION_CHANNELING = "hogwartsmagic:apparition_channeling";
    private static final String TAG_APPARITION_START = "hogwartsmagic:apparition_start";
    private static final int APPARITION_CHANNEL_TICKS = 80;          // 4 seconds of "Determination/Deliberation"
    private static final int APPARITION_MANA_COST = 100;
    private static final double APPARITION_MAX_RANGE = 48.0D;        // matches the minimap radius (blocks)
    private static final double APPARITION_SIDEALONG_RADIUS = 1.0D;  // side-along: who comes with you
    private static final ResourceLocation APPARITION_IMPLODE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "apparition_implode");
    private static final String TAG_HOGWARTS_INVITE_DELIVERED = "HogwartsInviteDelivered";
    private static final ResourceLocation WAND_ADVANCEMENT_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hp/root");
    private static void syncPolyjuice(ServerPlayer player) {
        SpellPackets.CHANNEL.send(
                new SpellPackets.SyncPolyjuiceDisguisePacket(player.getUUID(), PolyjuiceData.getActive(player)),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player)
        );
    }

    public static void addTemporaryRevelioLight(ServerLevel level, BlockPos pos, int ttlTicks) {
        if (ttlTicks <= 0) {
            return;
        }
        if (!(level.isEmptyBlock(pos) || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LIGHT))) {
            return;
        }

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15), 3);
        REVELIO_LIGHTS
                .computeIfAbsent(level.dimension(), ignored -> new java.util.HashMap<>())
                .merge(pos.immutable(), ttlTicks, Math::max);
    }

    private static void tickRevelioLights(ServerLevel level) {
        java.util.Map<BlockPos, Integer> active = REVELIO_LIGHTS.get(level.dimension());
        if (active == null || active.isEmpty()) {
            return;
        }

        java.util.Set<BlockPos> lumosPositions = new java.util.HashSet<>(LUMOS_POS.values());
        java.util.Iterator<java.util.Map.Entry<BlockPos, Integer>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<BlockPos, Integer> entry = it.next();
            BlockPos pos = entry.getKey();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                it.remove();
                continue;
            }

            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                if (!lumosPositions.contains(pos)) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }

        if (active.isEmpty()) {
            REVELIO_LIGHTS.remove(level.dimension());
        }
    }

    public static void addTemporaryAquaWater(ServerLevel level, BlockPos pos, int ttlTicks, net.minecraft.world.level.block.state.BlockState state) {
        if (ttlTicks <= 0) {
            return;
        }
        if (!(level.isEmptyBlock(pos)
                || level.getBlockState(pos).canBeReplaced()
                || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE)
                || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WATER))) {
            return;
        }

        level.setBlock(pos, state, 3);
        AQUA_ERUCTO_WATER
                .computeIfAbsent(level.dimension(), ignored -> new java.util.HashMap<>())
                .merge(pos.immutable(), ttlTicks, Math::max);
    }

    private static void tickTemporaryAquaWater(ServerLevel level) {
        java.util.Map<BlockPos, Integer> active = AQUA_ERUCTO_WATER.get(level.dimension());
        if (active == null || active.isEmpty()) {
            return;
        }

        java.util.Iterator<java.util.Map.Entry<BlockPos, Integer>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<BlockPos, Integer> entry = it.next();
            BlockPos pos = entry.getKey();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                it.remove();
                continue;
            }

            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }

        if (active.isEmpty()) {
            AQUA_ERUCTO_WATER.remove(level.dimension());
        }
    }

    public static class ActiveWizardRaid {
        final java.util.UUID raidId;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final net.minecraft.core.BlockPos center;
        final net.minecraft.server.level.ServerBossEvent bossBar;
        final java.util.Set<java.util.UUID> members = new java.util.HashSet<>();
        final java.util.Set<java.util.UUID> participants = new java.util.HashSet<>();
        final int initialCount;

        ActiveWizardRaid(java.util.UUID raidId,
                         net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                         net.minecraft.core.BlockPos center,
                         net.minecraft.server.level.ServerBossEvent bossBar,
                         int initialCount) {
            this.raidId = raidId;
            this.dimension = dimension;
            this.center = center;
            this.bossBar = bossBar;
            this.initialCount = initialCount;
        }
    }

    private record BaseAnchor(BlockPos pos, int score) {
    }

    private static boolean shouldReplaceVillageVillagerWithLycan() {
        return ThreadLocalRandom.current().nextInt(LYCANTHROPE_VILLAGER_ONE_IN) == 0;
    }

    public static void startWizardRaid(ServerLevel level, net.minecraft.core.BlockPos center, java.util.List<com.hogwartsmagic.entity.WizardEntity> members) {
        if (members == null || members.isEmpty()) {
            return;
        }

        java.util.UUID raidId = java.util.UUID.randomUUID();
        net.minecraft.server.level.ServerBossEvent bossBar = new net.minecraft.server.level.ServerBossEvent(
                Component.literal("Death Eater Raid"),
                net.minecraft.world.BossEvent.BossBarColor.RED,
                net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setVisible(true);

        ActiveWizardRaid raid = new ActiveWizardRaid(raidId, level.dimension(), center.immutable(), bossBar, members.size());
        for (com.hogwartsmagic.entity.WizardEntity wizard : members) {
            if (wizard != null && wizard.isAlive()) {
                raid.members.add(wizard.getUUID());
                WIZARD_TO_RAID.put(wizard.getUUID(), raidId);
            }
        }
        ACTIVE_WIZARD_RAIDS.put(raidId, raid);
        updateRaidBarPlayers(level, raid);
        updateRaidProgress(level, raid);
    }

    private static void updateRaidBarPlayers(ServerLevel level, ActiveWizardRaid raid) {
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(raid.center, WIZARD_RAID_RADIUS)) {
                raid.bossBar.addPlayer(player);
            } else {
                raid.bossBar.removePlayer(player);
            }
        }
    }

    private static void updateRaidProgress(ServerLevel level, ActiveWizardRaid raid) {
        int alive = 0;
        java.util.Iterator<java.util.UUID> memberIterator = raid.members.iterator();
        while (memberIterator.hasNext()) {
            java.util.UUID id = memberIterator.next();
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            if (entity instanceof com.hogwartsmagic.entity.WizardEntity wizard && wizard.isAlive()) {
                alive++;
            } else {
                memberIterator.remove();
                WIZARD_TO_RAID.remove(id);
            }
        }

        float progress = raid.initialCount <= 0 ? 0.0f : (float) alive / (float) raid.initialCount;
        raid.bossBar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));
    }

    private static void finishWizardRaid(ServerLevel level, ActiveWizardRaid raid, boolean cleared) {
        raid.bossBar.removeAllPlayers();
        raid.bossBar.setVisible(false);
        ACTIVE_WIZARD_RAIDS.remove(raid.raidId);
        for (java.util.UUID memberId : raid.members) {
            WIZARD_TO_RAID.remove(memberId);
        }

        if (!cleared) {
            return;
        }

        java.util.Set<java.util.UUID> rewarded = new java.util.HashSet<>(raid.participants);
        for (net.minecraft.server.level.ServerPlayer nearby : level.players()) {
            if (nearby.blockPosition().closerThan(raid.center, WIZARD_RAID_RADIUS)) {
                rewarded.add(nearby.getUUID());
            }
        }

        for (java.util.UUID playerId : rewarded) {
            net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,
                        HERO_OF_VILLAGE_DURATION,
                        0
                ));
                player.displayClientMessage(Component.literal("Raid defeated! Hero of the Village granted."), false);
            }
        }
    }

    private static void trySpawnInsomniaDementor(ServerPlayer player) {
        if (player.tickCount % DEMENTOR_SPAWN_CHECK_INTERVAL != 0) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (!level.dimensionType().hasSkyLight()) {
            return;
        }
        if (level.isDay() && !level.isThundering()) {
            return;
        }
        int sinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        if (sinceRest < DEMENTOR_INSOMNIA_TICKS) {
            return;
        }
        java.util.List<com.hogwartsmagic.entity.DementorEntity> existing = level.getEntitiesOfClass(
                com.hogwartsmagic.entity.DementorEntity.class,
                player.getBoundingBox().inflate(96.0D));
        if (existing.size() >= DEMENTOR_NEARBY_CAP) {
            return;
        }
        if (player.getRandom().nextFloat() > DEMENTOR_SPAWN_CHANCE) {
            return;
        }
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double dist = DEMENTOR_MIN_DIST + player.getRandom().nextDouble() * (DEMENTOR_MAX_DIST - DEMENTOR_MIN_DIST);
        double tx = player.getX() + Math.cos(angle) * dist;
        double tz = player.getZ() + Math.sin(angle) * dist;
        BlockPos column = BlockPos.containing(tx, player.getBlockY(), tz);
        if (!level.hasChunkAt(column)) {
            return;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        double spawnY = surfaceY + 6.0D + player.getRandom().nextDouble() * 8.0D;
        com.hogwartsmagic.entity.DementorEntity dementor = com.hogwartsmagic.entity.ModEntities.DEMENTOR.get().create(level);
        if (dementor == null) {
            return;
        }
        dementor.moveTo(tx, spawnY, tz, player.getYRot() + 180.0F, 0.0F);
        dementor.finalizeSpawn(level, level.getCurrentDifficultyAt(dementor.blockPosition()), MobSpawnType.EVENT, null);
        if (level.noCollision(dementor)) {
            level.addFreshEntity(dementor);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (handleWandUse(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(ExampleMod.OWL_ROUTE_TAG.get())) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        LivingEntity livingEntity = (LivingEntity) event.getTarget();
        net.minecraft.world.InteractionResult result = OwlRouteTagItem.assignToOwl(
                event.getItemStack(),
                event.getEntity(),
                livingEntity
        );
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getItemStack().is(ExampleMod.OWL_ROUTE_TAG.get())) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        LivingEntity livingEntity = (LivingEntity) event.getTarget();
        net.minecraft.world.InteractionResult result = OwlRouteTagItem.assignToOwl(
                event.getItemStack(),
                event.getEntity(),
                livingEntity
        );
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.getPlayer().level().isClientSide) {
            return;
        }
        if (event.getEntity().getItem().is(ExampleMod.INVISIBILITY_CLOAK.get())
                && event.getPlayer() instanceof ServerPlayer player) {
            InvisibilityCloakItem.stopCloak(player, true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (handleBlockNaming(event)) {
            event.setCanceled(true);
            return;
        }
        if (handlePerchMail(event)) {
            event.setCanceled(true);
            return;
        }
        if (handleWardInteraction(event)) {
            event.setCanceled(true);
            return;
        }
        if (handleWandUse(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
            net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
            String learned = modData.getString("LearnedSpells");
            if (!learned.isEmpty()) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncLearnedSpellsPacket(learned),
                        PacketDistributor.PLAYER.with(player));
            }
            HousePerks.applyHouseAttributes(player);
            HorcruxData.applyMaxHealthPenalty(player);
            // Sync initial mana
            float maxMana = PlayerMagicData.getMaxMana(player);
            float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
            if (modData.getFloat("MaxMana") != maxMana) {
                modData.putFloat("MaxMana", maxMana);
            }
            if (mana > maxMana) {
                mana = maxMana;
                modData.putFloat("Mana", mana);
            }
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
            SpellPackets.syncRelics(player);
            syncPolyjuice(player);
            com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(player.serverLevel()).syncRelevantWardsToPlayer(player);
            if (!modData.getBoolean(TAG_HOGWARTS_INVITE_DELIVERED)) {
                spawnFirstYearInvite(player);
                modData.putBoolean(TAG_HOGWARTS_INVITE_DELIVERED, true);
                persisted.put(ExampleMod.MODID, modData);
                player.getPersistentData().put("PlayerPersisted", persisted);
            }
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarned(net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (WAND_ADVANCEMENT_ID.equals(event.getAdvancement().id())) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ModSounds.WAND_ACHIEVEMENT_SOUND.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.minecraft.nbt.CompoundTag modData = serverPlayer.getPersistentData()
                    .getCompound("PlayerPersisted")
                    .getCompound(ExampleMod.MODID);
            HousePerks.applyHouseAttributes(serverPlayer);
            HorcruxData.applyMaxHealthPenalty(serverPlayer);

            String learned = modData.getString("LearnedSpells");
            if (!learned.isEmpty()) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncLearnedSpellsPacket(learned),
                        PacketDistributor.PLAYER.with(serverPlayer));
            }
            
            // Restore full mana on respawn
            float maxMana = PlayerMagicData.getMaxMana(serverPlayer);
            modData.putFloat("Mana", maxMana);
            modData.putFloat("MaxMana", maxMana);
            PolyjuiceData.clearAll(serverPlayer);
            serverPlayer.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(maxMana, maxMana), PacketDistributor.PLAYER.with(serverPlayer));
            SpellPackets.syncRelics(serverPlayer);
            syncPolyjuice(serverPlayer);
            com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(serverPlayer.serverLevel()).syncRelevantWardsToPlayer(serverPlayer);
            resolveHorcruxRespawn(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        HorcruxData.copyPersistent(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            InvisibilityCloakItem.stopCloak(player, false);
        }
    }

    private static void resolveHorcruxRespawn(ServerPlayer player) {
        if (!HorcruxData.hasStoredInventory(player) && !HorcruxData.isPendingRebirth(player)) {
            return;
        }

        HorcruxBlockEntity horcrux = HorcruxData.getValidHorcrux(player);
        if (HorcruxData.hasStoredInventory(player)) {
            HorcruxData.restoreInventory(player);
        }

        if (horcrux == null) {
            HorcruxData.clearPendingRebirth(player);
            return;
        }

        ServerLevel targetLevel = (ServerLevel) horcrux.getLevel();
        BlockPos targetPos = horcrux.getBlockPos().above();
        player.teleportTo(targetLevel, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        horcrux.shatter(targetLevel);
        HorcruxData.restoreSacrifice(player);
        HorcruxData.clearBinding(player);
        HorcruxData.clearPendingRebirth(player);

        player.setHealth(Math.max(4.0F, player.getMaxHealth() * 0.3F));
        int debuffTicks = Config.getHorcruxRebirthDebuffTicks();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, debuffTicks, 2));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, debuffTicks, 1));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 20 * 10, 0));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 20 * 12, 0));
        targetLevel.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.9D, player.getZ(), 26, 0.3, 0.45, 0.3, 0.02);
        targetLevel.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.35, 0.55, 0.35, 0.025);
        targetLevel.sendParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 0.75D, player.getZ(), 14, 0.2, 0.25, 0.2, 0.04);
        targetLevel.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.75f, 1.7f);
        targetLevel.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 0.6f);
        player.displayClientMessage(Component.literal("Your soul claws its way back through the Horcrux.")
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
    }

    private static void tickHorcruxRitual(ServerPlayer player) {
        if (!HorcruxData.isRitualActive(player) || player.tickCount % 20 != 0) {
            return;
        }
        HorcruxBlockEntity horcrux = HorcruxData.getOwnedHorcrux(player);
        if (horcrux == null || !horcrux.isPrimed()) {
            HorcruxData.restoreSacrifice(player);
            HorcruxData.clearBinding(player);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = horcrux.getBlockPos();
        double missingCost = Math.max(0.0D, 2.0D - HorcruxData.getHealthCost(player));
        if (missingCost > 0.0D) {
            HorcruxData.addHealthCost(player, missingCost);
            HorcruxData.applyMaxHealthPenalty(player);
        }
        player.setHealth(Math.min(player.getHealth(), Math.max(1.0F, player.getMaxHealth())));
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0D, player.getZ(),
                18, 0.22D, 0.35D, 0.22D, 0.03D);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                14, 0.25D, 0.35D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                8, 0.2D, 0.15D, 0.2D, 0.02D);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9F, 0.45F);
        level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7F, 0.55F);

        horcrux.completeBinding(level, player);
        HorcruxData.completeRitual(player);
        HorcruxData.bindToHorcrux(player, level, pos);
        player.setHealth(Math.max(1.0F, Math.min(player.getHealth(), player.getMaxHealth())));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 20 * 6, 0));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 20 * 8, 0));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 20 * 12, 1));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                22, 0.3D, 0.4D, 0.3D, 0.015D);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.5F);
        player.displayClientMessage(Component.literal("Your Horcrux is forged. One heart is forever chained to the altar.")
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(player.serverLevel()).onPlayerLeft(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPersistentMagicState(player);
            com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(player.serverLevel()).syncRelevantWardsToPlayer(player);
        }
    }

    private static void syncPersistentMagicState(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        String learned = modData.getString("LearnedSpells");
        SpellPackets.CHANNEL.send(new SpellPackets.SyncLearnedSpellsPacket(learned),
                PacketDistributor.PLAYER.with(player));

        float maxMana = PlayerMagicData.getMaxMana(player);
        float mana = modData.contains("Mana") ? Math.min(modData.getFloat("Mana"), maxMana) : maxMana;
        if (modData.getFloat("MaxMana") != maxMana || modData.getFloat("Mana") != mana) {
            modData.putFloat("MaxMana", maxMana);
            modData.putFloat("Mana", mana);
            PlayerMagicData.saveModData(player, modData);
        }
        SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
        SpellPackets.syncRelics(player);
        syncPolyjuice(player);
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            com.hogwartsmagic.caveinimicum.CaveInimicumManager.unload(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (!event.player.level().isClientSide && event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) event.player;
            net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
            net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
            HousePerks.applyHouseAttributes(player);
            HorcruxData.applyMaxHealthPenalty(player);
            HorcruxData.reconcileMissingHorcrux(player);
            
            float maxMana = PlayerMagicData.getMaxMana(player);
            float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
            if (modData.getFloat("MaxMana") != maxMana) {
                modData.putFloat("MaxMana", maxMana);
                persisted.put(ExampleMod.MODID, modData);
                player.getPersistentData().put("PlayerPersisted", persisted);
            }
            float regenMult = PlayerMagicData.getManaRegenMultiplier(player);
            if (player.hasEffect(ExampleMod.WIZARD_ELIXIR_EFFECT.getHolder().get())) {
                net.minecraft.world.effect.MobEffectInstance wx = player.getEffect(ExampleMod.WIZARD_ELIXIR_EFFECT.getHolder().get());
                int amp = wx != null ? wx.getAmplifier() : 0;
                // +50% regen at amp 0; +25% per extra level (commands / future brews)
                regenMult *= 1.5f + 0.25f * Math.max(0, amp);
            }
            float regenPerTick = Config.getManaRegenPerTick() * regenMult;

            if (currentMana < maxMana) {
                // Regen mana (e.g., 0.2 per tick = 4 per second, 25 seconds to fully regen from 0)
                float newMana = Math.min(currentMana + regenPerTick, maxMana);
                modData.putFloat("Mana", newMana);
                persisted.put(ExampleMod.MODID, modData);
                player.getPersistentData().put("PlayerPersisted", persisted);

                // Sync to client if we cross an integer boundary to save bandwidth
                if ((int) newMana > (int) currentMana) {
                    SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(newMana, maxMana), PacketDistributor.PLAYER.with(player));
                }
            }

            tickInvisibilityCloak(player, modData);

            if (HousePerks.shouldHufflepuffHeal(player, player.level().getGameTime())
                    && player.tickCount % 160 == 0
                    && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0f);
            }

            tickPolyjuice(player);
            tickPolyjuiceMobReactions(player);
            tickPendingFlooTravel(player, modData);
            tickProtegoMaximaChannel(player, modData);
            tickFiendfyreChannel(player, modData);
            tickWingardiumChannel(player, modData);
            tickImperioChannel(player, modData);
            tickApparitionChannel(player, modData);
            tickAccioPull(player);
            tickHorcruxRitual(player);

            trySpawnInsomniaDementor(player);

            // Once every 5s, check if the player is standing in a Forbidden Forest with no castle
            // nearby — if so, raise a Mini Hogwarts. Placement runs outside worldgen (no far-chunk crash).
            if (player.tickCount % 100 == 0) {
                com.hogwartsmagic.worldgen.MiniHogwartsManager.maybeAutoSpawn(player);
            }
        } else if (event.side.isClient() && event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            Player player = event.player;
            com.hogwartsmagic.util.ClientSideBridge.onClientPlayerTick(
                    player,
                    serpensortiaUseLatched,
                    fiendfyreUseLatched,
                    wingardiumUseLatched,
                    imperioUseLatched
            );
        }
    }

    private static void tickInvisibilityCloak(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        long gameTime = player.level().getGameTime();
        boolean cloakActive = InvisibilityCloakItem.isCloakActive(modData, gameTime);
        boolean cloakRevealed = InvisibilityCloakItem.isCloakRevealed(modData, gameTime);
        int transitionMode = modData.getInt(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_MODE);
        long transitionUntil = modData.getLong(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_UNTIL);
        boolean hiddenApplied = modData.getBoolean(InvisibilityCloakItem.TAG_CLOAK_EFFECT_APPLIED);
        if (!cloakActive) {
            if (modData.contains(InvisibilityCloakItem.TAG_CLOAK_ACTIVE_UNTIL)) {
                InvisibilityCloakItem.startCooldown(player, modData);
                modData.remove(InvisibilityCloakItem.TAG_CLOAK_ACTIVE_UNTIL);
                modData.remove(InvisibilityCloakItem.TAG_CLOAK_REVEAL_UNTIL);
            }
            if (hiddenApplied) {
                InvisibilityCloakItem.beginShowTransition(player);
                return;
            }
            if (transitionMode == InvisibilityCloakItem.TRANSITION_MODE_SHOW && gameTime >= transitionUntil) {
                modData.putInt(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_MODE, InvisibilityCloakItem.TRANSITION_MODE_NONE);
                modData.putLong(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_UNTIL, 0L);
                net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
                persisted.put(ExampleMod.MODID, modData);
                player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
            } else if (transitionMode == InvisibilityCloakItem.TRANSITION_MODE_NONE) {
                net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
                persisted.put(ExampleMod.MODID, modData);
                player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
            }
            return;
        }

        if (cloakRevealed) {
            if (hiddenApplied) {
                InvisibilityCloakItem.beginShowTransition(player);
            } else if (transitionMode == InvisibilityCloakItem.TRANSITION_MODE_SHOW && gameTime >= transitionUntil) {
                modData.putInt(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_MODE, InvisibilityCloakItem.TRANSITION_MODE_NONE);
                modData.putLong(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_UNTIL, 0L);
            }
            return;
        }

        if (hiddenApplied) {
            if (!player.isInvisible()) {
                player.setInvisible(true);
            }
            if (transitionMode != InvisibilityCloakItem.TRANSITION_MODE_NONE) {
                modData.putInt(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_MODE, InvisibilityCloakItem.TRANSITION_MODE_NONE);
                modData.putLong(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_UNTIL, 0L);
            }
            return;
        }

        if (transitionMode == InvisibilityCloakItem.TRANSITION_MODE_HIDE) {
            if (gameTime >= transitionUntil) {
                InvisibilityCloakItem.applyStealthEffect(player);
            }
            return;
        }

        if (transitionMode == InvisibilityCloakItem.TRANSITION_MODE_SHOW) {
            if (gameTime >= transitionUntil) {
                modData.putInt(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_MODE, InvisibilityCloakItem.TRANSITION_MODE_NONE);
                modData.putLong(InvisibilityCloakItem.TAG_CLOAK_TRANSITION_UNTIL, 0L);
            }
            return;
        }

        if (!player.isInvisible()) {
            InvisibilityCloakItem.beginHideTransition(player);
        } else {
            InvisibilityCloakItem.applyStealthEffect(player);
        }
    }

    public static void revealInvisibilityCloak(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long gameTime = player.level().getGameTime();
        if (!InvisibilityCloakItem.isCloakActive(modData, gameTime)) {
            return;
        }

        long revealUntil = gameTime + Config.getInvisibilityCloakRevealTicks();
        if (revealUntil > modData.getLong(InvisibilityCloakItem.TAG_CLOAK_REVEAL_UNTIL)) {
            modData.putLong(InvisibilityCloakItem.TAG_CLOAK_REVEAL_UNTIL, revealUntil);
            persisted.put(ExampleMod.MODID, modData);
            player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
        }
        if (modData.getBoolean(InvisibilityCloakItem.TAG_CLOAK_EFFECT_APPLIED)) {
            InvisibilityCloakItem.beginShowTransition(player);
        } else {
            InvisibilityCloakItem.clearStealthEffect(player);
        }
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.4f, 1.5f);
    }

    public static boolean breakPolyjuiceDisguise(ServerPlayer disguisedPlayer) {
        if (!PolyjuiceData.hasActive(disguisedPlayer) && PolyjuiceData.getPendingTicks(disguisedPlayer) <= 0) {
            return false;
        }
        PolyjuiceData.clearAll(disguisedPlayer);
        disguisedPlayer.removeEffect(ExampleMod.POLYJUICE_EFFECT.getHolder().get());
        SpellPackets.CHANNEL.send(
                new SpellPackets.SyncPolyjuiceDisguisePacket(disguisedPlayer.getUUID(), new CompoundTag()),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(disguisedPlayer)
        );
        return true;
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion thrownPotion) || thrownPotion.level().isClientSide) {
            return;
        }

        ItemStack stack = thrownPotion.getItem();
        if (!stack.is(Items.SPLASH_POTION) || !PolyjuiceData.hasPolyjuiceTarget(stack)) {
            return;
        }

        PotionContents contents = stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean hasPolyjuiceEffect = false;
        for (net.minecraft.world.effect.MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().is(ExampleMod.POLYJUICE_EFFECT.getKey())) {
                hasPolyjuiceEffect = true;
                break;
            }
        }
        if (!hasPolyjuiceEffect) {
            return;
        }

        applyPolyjuiceSplash(thrownPotion, stack, event.getRayTraceResult());

        ItemStack inertStack = stack.copyWithCount(1);
        inertStack.remove(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        thrownPotion.setItem(inertStack);
    }

    private static void applyPolyjuiceSplash(ThrownPotion thrownPotion, ItemStack stack, HitResult hitResult) {
        AABB area = thrownPotion.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        Entity directHit = hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) hitResult).getEntity() : null;
        CompoundTag target = PolyjuiceData.getPolyjuiceTarget(stack);

        for (ServerPlayer player : thrownPotion.level().getEntitiesOfClass(ServerPlayer.class, area)) {
            if (!player.isAffectedByPotions()) {
                continue;
            }

            double distanceSq = thrownPotion.distanceToSqr(player);
            if (distanceSq >= POLYJUICE_SPLASH_RANGE_SQ) {
                continue;
            }

            double strength = player == directHit ? 1.0D : 1.0D - Math.sqrt(distanceSq) / 4.0D;
            int disguiseTicks = (int) (strength * PolyjuicePotionItem.getPolyjuiceEffectDurationTicks(stack) + 0.5D);
            if (disguiseTicks <= 20) {
                continue;
            }

            PolyjuiceData.setPending(player, target, PolyjuicePotionItem.TRANSFORMATION_DELAY_TICKS, disguiseTicks);
            thrownPotion.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 0.8f);
            SpellPackets.CHANNEL.send(
                    new SpellPackets.SyncPolyjuiceDisguisePacket(player.getUUID(), new CompoundTag()),
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player)
            );
        }
    }

    public static boolean dispelIllusionsAndInvisibility(LivingEntity target) {
        boolean changed = false;
        if (target instanceof ServerPlayer serverPlayer) {
            net.minecraft.nbt.CompoundTag persisted = serverPlayer.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
            net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
            if (InvisibilityCloakItem.isCloakActive(modData, serverPlayer.level().getGameTime())) {
                revealInvisibilityCloak(serverPlayer);
                changed = true;
            }
            changed |= breakPolyjuiceDisguise(serverPlayer);
        }
        if (target.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            target.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            changed = true;
        }
        if (target.isInvisible()) {
            target.setInvisible(false);
            changed = true;
        }
        if (changed) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    20 * 4,
                    0,
                    false,
                    true
            ));
        }
        return changed;
    }

    private static void tickProtegoMaximaChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        if (!player.getPersistentData().getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
            return;
        }

        ItemStack wand = player.getMainHandItem();
        SpellType selected = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (!MagicWandItem.isAnyWand(wand) || selected != SpellType.PROTEGO_MAXIMA || !player.isAlive()) {
            stopProtegoMaximaChannel(player, wand);
            return;
        }

        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float upkeepCost = getProtegoMaximaUpkeepCost(wand);
        long gameTime = player.level().getGameTime();
        long lastDrain = player.getPersistentData().getLong(TAG_PROTEGO_MAXIMA_LAST_DRAIN);
        long lastSeen = player.getPersistentData().getLong(TAG_PROTEGO_MAXIMA_LAST_SEEN);
        float bubbleHealth = player.getPersistentData().contains(TAG_PROTEGO_MAXIMA_HEALTH)
                ? player.getPersistentData().getFloat(TAG_PROTEGO_MAXIMA_HEALTH)
                : PROTEGO_MAXIMA_MAX_HEALTH;
        long lastHit = player.getPersistentData().getLong(TAG_PROTEGO_MAXIMA_LAST_HIT);

        if (gameTime - lastSeen > PROTEGO_MAXIMA_HEARTBEAT_TIMEOUT || player.getXRot() > -50.0F) {
            stopProtegoMaximaChannel(player, wand);
            return;
        }

        if (gameTime - lastHit >= PROTEGO_MAXIMA_REGEN_DELAY && bubbleHealth < PROTEGO_MAXIMA_MAX_HEALTH) {
            bubbleHealth = Math.min(PROTEGO_MAXIMA_MAX_HEALTH, bubbleHealth + PROTEGO_MAXIMA_REGEN_PER_TICK);
            player.getPersistentData().putFloat(TAG_PROTEGO_MAXIMA_HEALTH, bubbleHealth);
        }

        if (!player.isCreative() && gameTime - lastDrain >= PROTEGO_MAXIMA_UPKEEP_INTERVAL) {
            if (currentMana < upkeepCost) {
                stopProtegoMaximaChannel(player, wand);
                player.displayClientMessage(Component.literal("Protego Maxima faded").withStyle(net.minecraft.ChatFormatting.AQUA), true);
                return;
            }

            float previousMana = currentMana;
            currentMana -= upkeepCost;
            modData.putFloat("Mana", currentMana);
            player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            player.getPersistentData().putLong(TAG_PROTEGO_MAXIMA_LAST_DRAIN, gameTime);
            if ((int) previousMana != (int) currentMana) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
            }
        }

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                ExampleMod.PROTEGO_EFFECT.getHolder().get(), 12, 3, false, false, true));

        if (player.level() instanceof ServerLevel serverLevel) {
            spawnProtegoMaximaSkyBeam(serverLevel, player);
            if (player.tickCount % 2 == 0) {
                spawnProtegoMaximaBarrier(serverLevel, player);
            }
            if (player.tickCount % 5 == 0) {
                deflectProtegoMaximaProjectiles(serverLevel, player);
            }
        }

        if (player.tickCount % 2 == 0) {
            int healthPercent = net.minecraft.util.Mth.clamp(Math.round((bubbleHealth / PROTEGO_MAXIMA_MAX_HEALTH) * 100.0f), 0, 100);
            player.displayClientMessage(Component.literal("Protego Maxima Bubble: " + healthPercent + "%")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
    }

    private static void stopProtegoMaximaChannel(ServerPlayer player, ItemStack wand) {
        player.getPersistentData().remove(TAG_PROTEGO_MAXIMA_CHANNELING);
        player.getPersistentData().remove(TAG_PROTEGO_MAXIMA_LAST_DRAIN);
        player.getPersistentData().remove(TAG_PROTEGO_MAXIMA_LAST_SEEN);
        player.getPersistentData().remove(TAG_PROTEGO_MAXIMA_HEALTH);
        player.getPersistentData().remove(TAG_PROTEGO_MAXIMA_LAST_HIT);
        player.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());

        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellType spell = SpellType.PROTEGO_MAXIMA;
            long now = player.level().getGameTime();
            long cooldownTicks = Math.max(1L, Math.round(
                    spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
            ));
            MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
        }
    }

    private static float getProtegoMaximaUpkeepCost(ItemStack wand) {
        return Math.max(0.75f, (SpellType.PROTEGO_MAXIMA.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)) / 24.0f);
    }

    private static void tickFiendfyreChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        if (!player.getPersistentData().getBoolean(TAG_FIENDFYRE_CHANNELING)) {
            return;
        }

        ItemStack wand = player.getMainHandItem();
        SpellType selected = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (!MagicWandItem.isAnyWand(wand) || selected != SpellType.FIENDFYRE || !player.isAlive()) {
            stopFiendfyreChannel(player, wand);
            return;
        }

        long gameTime = player.level().getGameTime();
        long lastSeen = player.getPersistentData().getLong(TAG_FIENDFYRE_LAST_SEEN);
        if (gameTime - lastSeen > FIENDFYRE_HEARTBEAT_TIMEOUT) {
            stopFiendfyreChannel(player, wand);
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        com.hogwartsmagic.entity.FiendfyreSerpentEntity fiendfyre = getOrCreateFiendfyreEntity(player, serverLevel);
        if (fiendfyre == null) {
            stopFiendfyreChannel(player, wand);
            return;
        }
        fiendfyre.setCaster(player);

        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float upkeepCost = getFiendfyreUpkeepCost(wand);
        long lastDrain = player.getPersistentData().getLong(TAG_FIENDFYRE_LAST_DRAIN);

        if (!player.isCreative() && gameTime - lastDrain >= FIENDFYRE_UPKEEP_INTERVAL) {
            if (currentMana < upkeepCost) {
                stopFiendfyreChannel(player, wand);
                player.displayClientMessage(Component.literal("Fiendfyre collapses").withStyle(net.minecraft.ChatFormatting.GOLD), true);
                return;
            }

            float previousMana = currentMana;
            currentMana -= upkeepCost;
            modData.putFloat("Mana", currentMana);
            player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            player.getPersistentData().putLong(TAG_FIENDFYRE_LAST_DRAIN, gameTime);
            if ((int) previousMana != (int) currentMana) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
            }
        }
    }

    private static void tickImperioChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        if (!player.getPersistentData().getBoolean(TAG_IMPERIO_CHANNELING)) {
            return;
        }

        ItemStack wand = player.getMainHandItem();
        SpellType selected = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (!MagicWandItem.isAnyWand(wand) || selected != SpellType.IMPERIO || !player.isAlive()) {
            stopImperioChannel(player, wand);
            return;
        }

        long gameTime = player.level().getGameTime();
        long lastSeen = player.getPersistentData().getLong(TAG_IMPERIO_LAST_SEEN);
        if (gameTime - lastSeen > IMPERIO_HEARTBEAT_TIMEOUT) {
            stopImperioChannel(player, wand);
            return;
        }

        if (!player.getPersistentData().hasUUID(TAG_IMPERIO_TARGET)) {
            stopImperioChannel(player, wand);
            return;
        }

        net.minecraft.world.entity.Entity rawTarget = player.serverLevel().getEntity(player.getPersistentData().getUUID(TAG_IMPERIO_TARGET));
        if (!(rawTarget instanceof LivingEntity target) || !target.isAlive() || target == player || target.distanceToSqr(player) > 32.0D * 32.0D) {
            stopImperioChannel(player, wand);
            return;
        }

        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float upkeepCost = getImperioUpkeepCost(wand);
        long lastDrain = player.getPersistentData().getLong(TAG_IMPERIO_LAST_DRAIN);
        if (!player.isCreative() && gameTime - lastDrain >= IMPERIO_UPKEEP_INTERVAL) {
            if (currentMana < upkeepCost) {
                stopImperioChannel(player, wand);
                player.displayClientMessage(Component.literal("Imperio control faded").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                return;
            }

            float previousMana = currentMana;
            currentMana -= upkeepCost;
            modData.putFloat("Mana", currentMana);
            player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            player.getPersistentData().putLong(TAG_IMPERIO_LAST_DRAIN, gameTime);
            if ((int) previousMana != (int) currentMana) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
            }
        }

        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 12, 0, false, true, true));
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 12, 0, false, false, true));

        applyImperioControl(player, target, gameTime);

        if (player.tickCount % 4 == 0) {
            player.displayClientMessage(Component.literal("Controlling " + target.getName().getString())
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
    }

    private static void tickWingardiumChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        if (!player.getPersistentData().getBoolean(TAG_WINGARDIUM_CHANNELING)) {
            return;
        }

        ItemStack wand = player.getMainHandItem();
        SpellType selected = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (!MagicWandItem.isAnyWand(wand) || selected != SpellType.WINGARDIUM_LEVIOSA || !player.isAlive()) {
            stopWingardiumChannel(player, wand);
            return;
        }

        long gameTime = player.level().getGameTime();
        long lastSeen = player.getPersistentData().getLong(TAG_WINGARDIUM_LAST_SEEN);
        if (gameTime - lastSeen > WINGARDIUM_HEARTBEAT_TIMEOUT) {
            stopWingardiumChannel(player, wand);
            return;
        }

        if (!player.getPersistentData().hasUUID(TAG_WINGARDIUM_TARGET)) {
            stopWingardiumChannel(player, wand);
            return;
        }

        net.minecraft.world.entity.Entity rawTarget = player.serverLevel().getEntity(player.getPersistentData().getUUID(TAG_WINGARDIUM_TARGET));
        if (!(rawTarget instanceof Entity target) || !target.isAlive() || target == player || !target.isPickable()) {
            stopWingardiumChannel(player, wand);
            return;
        }

        if (target.distanceToSqr(player) > WINGARDIUM_HOLD_RANGE * WINGARDIUM_HOLD_RANGE || !player.hasLineOfSight(target)) {
            stopWingardiumChannel(player, wand);
            player.displayClientMessage(Component.literal("Wingardium Leviosa slipped out of range").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            return;
        }

        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float upkeepCost = getWingardiumUpkeepCost(wand);
        long lastDrain = player.getPersistentData().getLong(TAG_WINGARDIUM_LAST_DRAIN);
        if (!player.isCreative() && gameTime - lastDrain >= WINGARDIUM_UPKEEP_INTERVAL) {
            if (currentMana < upkeepCost) {
                stopWingardiumChannel(player, wand);
                player.displayClientMessage(Component.literal("Wingardium Leviosa faded").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                return;
            }

            float previousMana = currentMana;
            currentMana -= upkeepCost;
            modData.putFloat("Mana", currentMana);
            player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            player.getPersistentData().putLong(TAG_WINGARDIUM_LAST_DRAIN, gameTime);
            if ((int) previousMana != (int) currentMana) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
            }
        }

        controlWingardiumTarget(player, target);
    }

    public static void stopWingardiumChannel(ServerPlayer player, ItemStack wand) {
        Entity controlled = null;
        if (player.getPersistentData().hasUUID(TAG_WINGARDIUM_TARGET) && player.level() instanceof ServerLevel serverLevel) {
            controlled = serverLevel.getEntity(player.getPersistentData().getUUID(TAG_WINGARDIUM_TARGET));
        }
        player.getPersistentData().remove(TAG_WINGARDIUM_CHANNELING);
        player.getPersistentData().remove(TAG_WINGARDIUM_TARGET);
        player.getPersistentData().remove(TAG_WINGARDIUM_LAST_SEEN);
        player.getPersistentData().remove(TAG_WINGARDIUM_LAST_DRAIN);

        if (controlled != null && controlled.isAlive()) {
            Vec3 motion = controlled.getDeltaMovement();
            controlled.setDeltaMovement(motion.x * 0.35D, Math.min(motion.y, -0.18D), motion.z * 0.35D);
            controlled.hurtMarked = true;
            controlled.hasImpulse = true;
            if (controlled instanceof ServerPlayer controlledPlayer) {
                controlledPlayer.connection.send(new ClientboundSetEntityMotionPacket(controlledPlayer));
            }
        }

        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellType spell = SpellType.WINGARDIUM_LEVIOSA;
            long now = player.level().getGameTime();
            long cooldownTicks = Math.max(1L, Math.round(
                    spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
            ));
            MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
        }
    }

    public static float getWingardiumStartCost(ItemStack wand) {
        return Math.max(1.0f, SpellType.WINGARDIUM_LEVIOSA.getManaCost() * MagicWandItem.getManaCostMultiplier(wand));
    }

    public static float getWingardiumUpkeepCost(ItemStack wand) {
        return Math.max(0.5f, getWingardiumStartCost(wand) / 12.0f);
    }

    public static void markRecentWingardiumTarget(ServerPlayer player, Entity target) {
        if (player == null || target == null || target == player || !target.isAlive()) {
            return;
        }
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        data.putUUID(TAG_WINGARDIUM_RECENT_TARGET, target.getUUID());
        data.putLong(TAG_WINGARDIUM_RECENT_EXPIRY, player.level().getGameTime() + 40L);
    }

    public static Entity consumeRecentWingardiumTarget(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        long expiry = data.getLong(TAG_WINGARDIUM_RECENT_EXPIRY);
        if (expiry < player.level().getGameTime() || !data.hasUUID(TAG_WINGARDIUM_RECENT_TARGET)) {
            data.remove(TAG_WINGARDIUM_RECENT_TARGET);
            data.remove(TAG_WINGARDIUM_RECENT_EXPIRY);
            return null;
        }

        Entity target = player.serverLevel().getEntity(data.getUUID(TAG_WINGARDIUM_RECENT_TARGET));
        data.remove(TAG_WINGARDIUM_RECENT_TARGET);
        data.remove(TAG_WINGARDIUM_RECENT_EXPIRY);
        if (target == null || !target.isAlive() || target == player || !target.isPickable()) {
            return null;
        }
        if (target.distanceToSqr(player) > WINGARDIUM_CAST_RANGE * WINGARDIUM_CAST_RANGE || !player.hasLineOfSight(target)) {
            return null;
        }
        return target;
    }

    private static void controlWingardiumTarget(ServerPlayer caster, Entity target) {
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        Vec3 look = caster.getLookAngle();
        Vec3 holdPos = caster.getEyePosition().add(look.scale(3.0D));
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 towardHold = holdPos.subtract(targetCenter);
        Vec3 pull = towardHold.scale(0.18D);
        if (pull.lengthSqr() > 0.10D) {
            pull = pull.normalize().scale(0.32D);
        }

        Vec3 motion = target.getDeltaMovement();
        Vec3 next = new Vec3(
                motion.x * 0.72D + pull.x,
                net.minecraft.util.Mth.clamp((motion.y * 0.68D) + (pull.y * 1.15D), -0.30D, 0.30D),
                motion.z * 0.72D + pull.z
        );

        target.setDeltaMovement(next);
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
        target.hasImpulse = true;

        if (target instanceof ServerPlayer controlledPlayer) {
            controlledPlayer.connection.send(new ClientboundSetEntityMotionPacket(controlledPlayer));
        }

        if (caster.level() instanceof ServerLevel serverLevel && caster.tickCount % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    8, 0.25D, 0.35D, 0.25D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    4, 0.2D, 0.25D, 0.2D, 0.01D);
        }
    }

    public static void stopImperioChannel(ServerPlayer player, ItemStack wand) {
        player.getPersistentData().remove(TAG_IMPERIO_CHANNELING);
        player.getPersistentData().remove(TAG_IMPERIO_TARGET);
        player.getPersistentData().remove(TAG_IMPERIO_LAST_SEEN);
        player.getPersistentData().remove(TAG_IMPERIO_LAST_DRAIN);
        player.getPersistentData().remove(TAG_IMPERIO_LAST_ATTACK);
        player.getPersistentData().remove(TAG_IMPERIO_FORWARD);
        player.getPersistentData().remove(TAG_IMPERIO_STRAFE);
        player.getPersistentData().remove(TAG_IMPERIO_JUMP);
        player.getPersistentData().remove(TAG_IMPERIO_ATTACK);
        player.getPersistentData().remove(TAG_IMPERIO_YAW);
        player.getPersistentData().remove(TAG_IMPERIO_PITCH);

        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellType spell = SpellType.IMPERIO;
            long now = player.level().getGameTime();
            long cooldownTicks = Math.max(1L, Math.round(
                    spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
            ));
            MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
        }
    }

    public static void interruptActiveCasting(ServerPlayer player) {
        ItemStack wand = player.getMainHandItem();
        if (player.getPersistentData().getBoolean(TAG_IMPERIO_CHANNELING)) {
            stopImperioChannel(player, wand);
        }
        if (player.getPersistentData().getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
            stopProtegoMaximaChannel(player, wand);
        }
        if (player.getPersistentData().getBoolean(TAG_FIENDFYRE_CHANNELING)) {
            stopFiendfyreChannel(player, wand);
        }
        if (player.getPersistentData().getBoolean(TAG_WINGARDIUM_CHANNELING)) {
            stopWingardiumChannel(player, wand);
        }
        if (player.getPersistentData().getBoolean(TAG_APPARITION_CHANNELING)) {
            // Getting hit mid-focus splinches your concentration: cancel and pay the full cooldown.
            cancelApparitionChannel(player, wand);
        }
    }

    public static LivingEntity consumeRecentImperioHitTarget(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        long expiry = playerData.getLong(TAG_IMPERIO_RECENT_HIT_EXPIRY);
        if (expiry < player.level().getGameTime() || !playerData.hasUUID(TAG_IMPERIO_RECENT_HIT_TARGET)) {
            playerData.remove(TAG_IMPERIO_RECENT_HIT_TARGET);
            playerData.remove(TAG_IMPERIO_RECENT_HIT_EXPIRY);
            return null;
        }
        net.minecraft.world.entity.Entity raw = player.serverLevel().getEntity(playerData.getUUID(TAG_IMPERIO_RECENT_HIT_TARGET));
        playerData.remove(TAG_IMPERIO_RECENT_HIT_TARGET);
        playerData.remove(TAG_IMPERIO_RECENT_HIT_EXPIRY);
        return raw instanceof LivingEntity living && living.isAlive() && raw != player ? living : null;
    }

    public static void updateImperioInput(ServerPlayer player, float forward, float strafe, boolean jump, boolean attack, float yaw, float pitch) {
        player.getPersistentData().putFloat(TAG_IMPERIO_FORWARD, forward);
        player.getPersistentData().putFloat(TAG_IMPERIO_STRAFE, strafe);
        player.getPersistentData().putBoolean(TAG_IMPERIO_JUMP, jump);
        player.getPersistentData().putBoolean(TAG_IMPERIO_ATTACK, attack);
        player.getPersistentData().putFloat(TAG_IMPERIO_YAW, yaw);
        player.getPersistentData().putFloat(TAG_IMPERIO_PITCH, pitch);
        player.getPersistentData().putLong(TAG_IMPERIO_LAST_SEEN, player.level().getGameTime());
    }

    private static void applyImperioControl(ServerPlayer caster, LivingEntity target, long gameTime) {
        float forward = caster.getPersistentData().getFloat(TAG_IMPERIO_FORWARD);
        float strafe = caster.getPersistentData().getFloat(TAG_IMPERIO_STRAFE);
        boolean jump = caster.getPersistentData().getBoolean(TAG_IMPERIO_JUMP);
        boolean attack = caster.getPersistentData().getBoolean(TAG_IMPERIO_ATTACK);
        float yaw = caster.getPersistentData().getFloat(TAG_IMPERIO_YAW);
        float pitch = caster.getPersistentData().getFloat(TAG_IMPERIO_PITCH);

        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        target.setYBodyRot(yaw);
        target.setXRot(pitch);

        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        Vec3 look = Vec3.directionFromRotation(0.0f, yaw).normalize();
        Vec3 right = new Vec3(look.z, 0.0D, -look.x).normalize();
        Vec3 desired = look.scale(forward).add(right.scale(strafe));
        if (desired.lengthSqr() > 1.0D) {
            desired = desired.normalize();
        }

        double moveSpeed = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (moveSpeed <= 0.0D) {
            moveSpeed = target instanceof Player ? 0.1D : 0.1D;
        }
        double controlSpeed = moveSpeed * 1.05D;
        Vec3 motion = target.getDeltaMovement();
        Vec3 next = new Vec3(
                desired.x * controlSpeed + motion.x * 0.15D,
                motion.y,
                desired.z * controlSpeed + motion.z * 0.15D
        );
        if (Math.abs(forward) < 0.01f && Math.abs(strafe) < 0.01f) {
            next = new Vec3(motion.x * 0.35D, motion.y, motion.z * 0.35D);
        }
        if (jump && target.onGround()) {
            next = new Vec3(next.x, 0.42D, next.z);
        }

        target.setDeltaMovement(next);
        target.hurtMarked = true;
        target.hasImpulse = true;
        if (target instanceof ServerPlayer controlledPlayer) {
            controlledPlayer.connection.send(new ClientboundSetEntityMotionPacket(controlledPlayer));
        }

        if (attack && gameTime - caster.getPersistentData().getLong(TAG_IMPERIO_LAST_ATTACK) >= IMPERIO_ATTACK_COOLDOWN) {
            LivingEntity victim = findImperioVictim(caster, target);
            if (victim != null) {
                target.swing(InteractionHand.MAIN_HAND);
                if (target instanceof Mob mob) {
                    performImperioMobAttack(mob, victim);
                } else {
                    float damage = getImperioAttackDamage(target);
                    if (target instanceof Player playerTarget) {
                        victim.hurt(target.damageSources().playerAttack(playerTarget), damage);
                    } else {
                        victim.hurt(target.damageSources().mobAttack(target), damage);
                    }
                }
                caster.getPersistentData().putLong(TAG_IMPERIO_LAST_ATTACK, gameTime);
            }
        }
    }

    private static void performImperioMobAttack(Mob mob, LivingEntity victim) {
        try {
            mob.doHurtTarget(victim);
        } catch (IllegalArgumentException ex) {
            float damage = getImperioAttackDamage(mob);
            victim.hurt(mob.damageSources().mobAttack(mob), damage);
        }
    }

    private static float getImperioAttackDamage(LivingEntity target) {
        try {
            AttributeInstance damageAttr = target.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                return Math.max(1.0f, (float) damageAttr.getValue());
            }
        } catch (IllegalArgumentException ignored) {
            // Some vanilla-controlled attack paths still expect the legacy generic.attack_damage id in 1.21.
        }
        if (target instanceof Player) {
            // Players may not expose ATTACK_DAMAGE in every runtime path here; keep possession melee stable.
            return 1.0f;
        }
        return 2.0f;
    }

    private static LivingEntity findImperioVictim(ServerPlayer caster, LivingEntity puppet) {
        Vec3 eye = puppet.getEyePosition();
        Vec3 look = puppet.getLookAngle().normalize();
        AABB box = puppet.getBoundingBox().inflate(2.5D);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : puppet.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && entity != puppet && entity != caster)) {
            Vec3 toCandidate = candidate.getEyePosition().subtract(eye);
            double dist = toCandidate.lengthSqr();
            if (dist > 10.0D) {
                continue;
            }
            Vec3 dir = toCandidate.normalize();
            if (dir.dot(look) < 0.35D) {
                continue;
            }
            if (dist < bestDist) {
                best = candidate;
                bestDist = dist;
            }
        }
        return best;
    }

    public static float getImperioStartCost(ItemStack wand) {
        return getImperioUpkeepCost(wand);
    }

    private static float getImperioUpkeepCost(ItemStack wand) {
        return Math.max(0.75f, (SpellType.IMPERIO.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)) / 18.0f);
    }

    private static com.hogwartsmagic.entity.FiendfyreSerpentEntity getOrCreateFiendfyreEntity(ServerPlayer player, ServerLevel level) {
        if (player.getPersistentData().hasUUID(TAG_FIENDFYRE_ENTITY)) {
            net.minecraft.world.entity.Entity existing = level.getEntity(player.getPersistentData().getUUID(TAG_FIENDFYRE_ENTITY));
            if (existing instanceof com.hogwartsmagic.entity.FiendfyreSerpentEntity fiendfyre) {
                return fiendfyre;
            }
        }

        com.hogwartsmagic.entity.FiendfyreSerpentEntity fiendfyre = new com.hogwartsmagic.entity.FiendfyreSerpentEntity(com.hogwartsmagic.entity.ModEntities.FIENDFYRE_SERPENT.get(), level);
        fiendfyre.setCaster(player);
        fiendfyre.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
        level.addFreshEntity(fiendfyre);
        player.getPersistentData().putUUID(TAG_FIENDFYRE_ENTITY, fiendfyre.getUUID());
        return fiendfyre;
    }

    private static void stopFiendfyreChannel(ServerPlayer player, ItemStack wand) {
        player.getPersistentData().remove(TAG_FIENDFYRE_CHANNELING);
        player.getPersistentData().remove(TAG_FIENDFYRE_LAST_DRAIN);
        player.getPersistentData().remove(TAG_FIENDFYRE_LAST_SEEN);
        if (player.getPersistentData().hasUUID(TAG_FIENDFYRE_ENTITY) && player.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity existing = serverLevel.getEntity(player.getPersistentData().getUUID(TAG_FIENDFYRE_ENTITY));
            if (existing instanceof com.hogwartsmagic.entity.FiendfyreSerpentEntity fiendfyre) {
                fiendfyre.discard();
            }
        }
        player.getPersistentData().remove(TAG_FIENDFYRE_ENTITY);

        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellType spell = SpellType.FIENDFYRE;
            long now = player.level().getGameTime();
            long cooldownTicks = Math.max(1L, Math.round(
                    spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
            ));
            MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
        }
    }

    private static float getFiendfyreUpkeepCost(ItemStack wand) {
        return Math.max(1.25f, (SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)) / 14.0f);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Apparition: a 4-second channel during which the caster picks a known spot
    //  on a minimap; on confirm they Disapparate (with a CRACK) and reappear there.
    //  Everything authoritative — teleport, mana, splinch, side-along, ward checks —
    //  lives here on the server. The client only renders the map and sends the pick.
    // ──────────────────────────────────────────────────────────────────────────

    /** Begins the channel: validates ward/cooldown/mana, spends 100 mana up front, and opens the map client-side. */
    public static void startApparitionChannel(ServerPlayer player, ItemStack wand) {
        if (!MagicWandItem.isAnyWand(wand)) {
            return;
        }
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        if (playerData.getBoolean(TAG_APPARITION_CHANNELING)) {
            return; // already channeling — ignore duplicate cast packets
        }
        boolean creative = player.isCreative();
        long now = player.level().getGameTime();

        if (!creative && MagicWandItem.isOnCooldown(wand, SpellType.APPARITION, now)) {
            return;
        }

        // Anti-Apparition jinx: you cannot Disapparate from inside an active Cave Inimicum ward.
        if (player.level() instanceof ServerLevel serverLevel
                && com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(serverLevel).isWardedAt(player.blockPosition())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.warded").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            return;
        }

        net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        if (!creative && currentMana < APPARITION_MANA_COST) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (!creative) {
            currentMana -= APPARITION_MANA_COST;
            modData.putFloat("Mana", currentMana);
            playerData.getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
        }

        playerData.putBoolean(TAG_APPARITION_CHANNELING, true);
        playerData.putLong(TAG_APPARITION_START, now);
        revealInvisibilityCloak(player);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(), (int) APPARITION_MAX_RANGE, APPARITION_CHANNEL_TICKS),
                PacketDistributor.PLAYER.with(player));
    }

    // --- Accio: continuously reel a struck target in toward the caster until it arrives ---
    private static final String TAG_ACCIO_TARGET = "hogwartsmagic:accio_target";
    private static final String TAG_ACCIO_UNTIL = "hogwartsmagic:accio_until";
    private static final int ACCIO_PULL_TICKS = 70;

    /** Called when Accio strikes a target: registers it to be pulled in over the next few seconds. */
    public static void startAccioPull(ServerPlayer caster, net.minecraft.world.entity.LivingEntity target) {
        if (target == null || target == caster) return;
        caster.getPersistentData().putUUID(TAG_ACCIO_TARGET, target.getUUID());
        caster.getPersistentData().putLong(TAG_ACCIO_UNTIL, caster.level().getGameTime() + ACCIO_PULL_TICKS);
    }

    private static void tickAccioPull(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(TAG_ACCIO_TARGET)) return;
        long now = player.level().getGameTime();
        net.minecraft.world.entity.Entity raw = player.serverLevel().getEntity(data.getUUID(TAG_ACCIO_TARGET));
        if (raw == null || !raw.isAlive() || !player.isAlive() || now > data.getLong(TAG_ACCIO_UNTIL)) {
            data.remove(TAG_ACCIO_TARGET);
            data.remove(TAG_ACCIO_UNTIL);
            return;
        }
        net.minecraft.world.phys.Vec3 toPlayer = player.position()
                .add(0.0D, player.getBbHeight() * 0.5D, 0.0D).subtract(raw.position());
        double dist = toPlayer.length();
        if (dist < 1.8D) { // arrived
            data.remove(TAG_ACCIO_TARGET);
            data.remove(TAG_ACCIO_UNTIL);
            return;
        }
        net.minecraft.world.phys.Vec3 dir = toPlayer.scale(1.0D / dist);
        double speed = Math.min(1.1D, 0.35D + dist * 0.12D);
        raw.setDeltaMovement(dir.x * speed, dir.y * speed + 0.08D, dir.z * speed);
        raw.hurtMarked = true; // force the server to sync the new velocity to clients
        raw.fallDistance = 0.0F;
        if (raw instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
        }
        if (player.level() instanceof ServerLevel sl && now % 2 == 0) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    raw.getX(), raw.getY() + raw.getBbHeight() * 0.5D, raw.getZ(), 2, 0.1D, 0.1D, 0.1D, 0.01D);
        }
    }

    private static void tickApparitionChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        if (!playerData.getBoolean(TAG_APPARITION_CHANNELING)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!MagicWandItem.isAnyWand(wand)) {
            cancelApparitionChannel(player, wand);
            return;
        }

        long now = player.level().getGameTime();
        int elapsed = (int) (now - playerData.getLong(TAG_APPARITION_START));
        float progress = Math.min(1.0f, elapsed / (float) APPARITION_CHANNEL_TICKS);

        // Implode: shrink the caster over the channel so onlookers see them compress into themselves.
        applyImplodeScale(player, progress);

        if (player.level() instanceof ServerLevel serverLevel) {
            int count = 2 + Math.round(progress * 6.0f);
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1.0, player.getZ(), count, 0.6, 0.8, 0.6, -0.05);
            if (elapsed % 10 == 0) {
                serverLevel.playSound(null, player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.7f + progress);
            }
        }

        // Timed out with no destination chosen → cancel and pay the full cooldown.
        if (elapsed >= APPARITION_CHANNEL_TICKS) {
            cancelApparitionChannel(player, wand);
        }
    }

    /** Client picked a destination — validate it server-side and (maybe) Apparate there. */
    public static void completeApparition(ServerPlayer player, int targetX, int targetZ) {
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        if (!playerData.getBoolean(TAG_APPARITION_CHANNELING)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!(player.level() instanceof ServerLevel level)) {
            cancelApparitionChannel(player, wand);
            return;
        }

        Vec3 from = player.position();
        double dist = Math.hypot((targetX + 0.5) - from.x, (targetZ + 0.5) - from.z);
        Vec3 dest = findApparitionLanding(level, targetX, targetZ);
        com.hogwartsmagic.caveinimicum.CaveInimicumManager wards =
                com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level);
        boolean warded = dest != null && wards.isWardedAt(BlockPos.containing(dest.x, dest.y, dest.z));

        if (dest == null || dist > APPARITION_MAX_RANGE + 4.0D || warded) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.blocked").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            cancelApparitionChannel(player, wand);
            return;
        }

        // End the channel cleanly (success path applies its own cooldown below).
        playerData.remove(TAG_APPARITION_CHANNELING);
        playerData.remove(TAG_APPARITION_START);
        clearImplodeScale(player);
        SpellPackets.CHANNEL.send(new SpellPackets.CloseApparitionMapPacket(), PacketDistributor.PLAYER.with(player));

        // Side-along: anyone within a block comes with you. Gather BEFORE moving.
        java.util.List<LivingEntity> passengers = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(APPARITION_SIDEALONG_RADIUS), e -> e != player && e.isAlive());

        // Splinch risk scales with distance; a botched jump lands you short.
        float risk = 0.04f + 0.30f * (float) Math.min(1.0, dist / APPARITION_MAX_RANGE);
        boolean splinched = !player.isCreative() && player.getRandom().nextFloat() < risk;
        Vec3 landing = dest;
        if (splinched) {
            int sx = (int) Math.floor(from.x + ((targetX + 0.5) - from.x) * 0.65);
            int sz = (int) Math.floor(from.z + ((targetZ + 0.5) - from.z) * 0.65);
            Vec3 shortLanding = findApparitionLanding(level, sx, sz);
            if (shortLanding != null) {
                landing = shortLanding;
            }
        }

        crackEffect(level, from.x, from.y, from.z, true);
        player.teleportTo(landing.x, landing.y, landing.z);
        player.fallDistance = 0.0f;
        crackEffect(level, landing.x, landing.y, landing.z, false);

        for (LivingEntity passenger : passengers) {
            passenger.teleportTo(landing.x + (player.getRandom().nextDouble() - 0.5) * 1.5,
                    landing.y, landing.z + (player.getRandom().nextDouble() - 0.5) * 1.5);
            passenger.fallDistance = 0.0f;
        }

        if (splinched) {
            applySplinch(player);
            for (LivingEntity passenger : passengers) {
                passenger.hurt(passenger.damageSources().magic(), 4.0f);
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.splinched").withStyle(net.minecraft.ChatFormatting.RED), true);
        }

        revealInvisibilityCloak(player);
        applyApparitionCooldown(player, wand);
    }

    /** Cancel for any reason (timeout, hit while channeling, lost wand, closed map): clears state and applies the full cooldown. */
    public static void cancelApparitionChannel(ServerPlayer player, ItemStack wand) {
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        if (!playerData.getBoolean(TAG_APPARITION_CHANNELING)) {
            return;
        }
        playerData.remove(TAG_APPARITION_CHANNELING);
        playerData.remove(TAG_APPARITION_START);
        clearImplodeScale(player);
        SpellPackets.CHANNEL.send(new SpellPackets.CloseApparitionMapPacket(), PacketDistributor.PLAYER.with(player));
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.8f);
        applyApparitionCooldown(player, wand);
    }

    private static void applyApparitionCooldown(ServerPlayer player, ItemStack wand) {
        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            long now = player.level().getGameTime();
            long cooldownTicks = Math.max(1L, Math.round(SpellType.APPARITION.getCooldownTicks()
                    * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)));
            MagicWandItem.setSpellCooldown(wand, SpellType.APPARITION, now + cooldownTicks);
        }
    }

    private static void applyImplodeScale(ServerPlayer player, float progress) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        float factor = 1.0f - 0.65f * progress; // shrink down to 0.35x at full focus
        scale.removeModifier(APPARITION_IMPLODE_MODIFIER_ID);
        scale.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                APPARITION_IMPLODE_MODIFIER_ID, factor - 1.0f,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void clearImplodeScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.removeModifier(APPARITION_IMPLODE_MODIFIER_ID);
        }
    }

    private static void applySplinch(ServerPlayer player) {
        player.hurt(player.damageSources().magic(), 8.0f);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 120, 1));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 160, 0));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
        // "Leave part of yourself behind": drop one random carried item stack.
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        java.util.List<Integer> filled = new java.util.ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                filled.add(i);
            }
        }
        if (!filled.isEmpty()) {
            int slot = filled.get(player.getRandom().nextInt(filled.size()));
            ItemStack dropped = inv.removeItem(slot, 1);
            if (!dropped.isEmpty()) {
                player.drop(dropped, true, false);
            }
        }
    }

    private static void crackEffect(ServerLevel level, double x, double y, double z, boolean departure) {
        level.sendParticles(departure ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL,
                x, y + 1.0, z, 60, 0.5, 1.0, 0.5, departure ? -0.2 : 0.8);
        level.sendParticles(ParticleTypes.CLOUD, x, y + 1.0, z, 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, BlockPos.containing(x, y, z),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** Find a safe feet position (2-high air gap on solid, dry ground) in the target column, or null. */
    private static Vec3 findApparitionLanding(ServerLevel level, int targetX, int targetZ) {
        BlockPos probe = new BlockPos(targetX, level.getMinBuildHeight() + 1, targetZ);
        if (!level.isLoaded(probe)) {
            return null;
        }
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
        int top = Math.min(surface + 2, level.getMaxBuildHeight() - 2);
        for (int y = top; y > level.getMinBuildHeight() + 1; y--) {
            BlockPos feet = new BlockPos(targetX, y, targetZ);
            BlockPos below = feet.below();
            if (level.getBlockState(below).isSolidRender(level, below)
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && level.getFluidState(feet).isEmpty()
                    && level.getFluidState(below).isEmpty()) {
                return new Vec3(targetX + 0.5, y, targetZ + 0.5);
            }
        }
        return null;
    }

    private static void spawnProtegoMaximaSkyBeam(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.45D));
        Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        Vec3 direction = player.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, 1.0D, 0.0D);
        }

        Vec3 startToCenter = start.subtract(center);
        double b = startToCenter.dot(direction);
        double c = startToCenter.lengthSqr() - (PROTEGO_MAXIMA_BARRIER_RADIUS * PROTEGO_MAXIMA_BARRIER_RADIUS);
        double discriminant = (b * b) - c;
        if (discriminant <= 0.0D) {
            return;
        }

        double exitDistance = -b + Math.sqrt(discriminant);
        if (exitDistance <= 0.02D) {
            return;
        }

        Vec3 end = start.add(direction.scale(exitDistance));
        int segments = Math.max(10, net.minecraft.util.Mth.ceil(exitDistance * 7.0D));
        for (int i = 0; i <= segments; i++) {
            double progress = i / (double) segments;
            Vec3 point = start.lerp(end, progress);
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            if (i % 2 == 0) {
                level.sendParticles(SpellType.PROTEGO_MAXIMA.getDustParticle(), point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        level.sendParticles(ParticleTypes.GLOW, end.x, end.y, end.z, 4, 0.08D, 0.08D, 0.08D, 0.0D);
        spawnProtegoMaximaShellRadiance(level, center, end.subtract(center).normalize(), player.tickCount);
    }

    private static void spawnProtegoMaximaShellRadiance(ServerLevel level, Vec3 center, Vec3 hitNormal, int tickCount) {
        Vec3 tangentA = hitNormal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (tangentA.lengthSqr() < 1.0E-6D) {
            tangentA = hitNormal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        tangentA = tangentA.normalize();
        Vec3 tangentB = hitNormal.cross(tangentA).normalize();

        for (int ring = 0; ring < 4; ring++) {
            double ringAngle = 0.18D + ring * 0.22D + (tickCount % 20) * 0.01D;
            double ringRadius = Math.sin(ringAngle) * PROTEGO_MAXIMA_BARRIER_RADIUS;
            double forward = Math.cos(ringAngle) * PROTEGO_MAXIMA_BARRIER_RADIUS;
            Vec3 ringCenter = center.add(hitNormal.scale(forward));
            int points = 14 + ring * 4;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0D * i) / points + tickCount * 0.04D * (ring % 2 == 0 ? 1.0D : -1.0D);
                Vec3 offset = tangentA.scale(Math.cos(angle) * ringRadius).add(tangentB.scale(Math.sin(angle) * ringRadius));
                Vec3 point = ringCenter.add(offset);
                level.sendParticles(ParticleTypes.GLOW, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (i % 2 == 0) {
                    level.sendParticles(ParticleTypes.ENCHANT, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
        }
    }

    private static void spawnProtegoMaximaBarrier(ServerLevel level, LivingEntity entity) {
        double centerX = entity.getX();
        double centerY = entity.getY() + entity.getBbHeight() * 0.55D;
        double centerZ = entity.getZ();

        for (int i = 0; i < 28; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos((level.random.nextDouble() * 2.0D) - 1.0D);
            double radius = PROTEGO_MAXIMA_BARRIER_RADIUS + (level.random.nextDouble() - 0.5D) * 0.25D;
            double x = centerX + radius * Math.sin(phi) * Math.cos(theta);
            double y = centerY + radius * Math.cos(phi);
            double z = centerZ + radius * Math.sin(phi) * Math.sin(theta);

            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
            if (i % 5 == 0) {
                level.sendParticles(ParticleTypes.GLOW, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        for (int ring = 0; ring < 2; ring++) {
            double y = centerY + (ring == 0 ? -1.2D : 1.2D);
            for (int i = 0; i < 18; i++) {
                double angle = ((Math.PI * 2.0D) / 18.0D) * i + (entity.tickCount * 0.08D * (ring == 0 ? 1.0D : -1.0D));
                double x = centerX + Math.cos(angle) * PROTEGO_MAXIMA_BARRIER_RADIUS;
                double z = centerZ + Math.sin(angle) * PROTEGO_MAXIMA_BARRIER_RADIUS;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void deflectProtegoMaximaProjectiles(ServerLevel level, ServerPlayer player) {
        net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(PROTEGO_MAXIMA_BARRIER_RADIUS);

        for (SpellBeamEntity beam : level.getEntitiesOfClass(SpellBeamEntity.class, area,
                beam -> beam.isAlive() && beam.getOwner() != player && beam.position().distanceTo(player.position()) <= PROTEGO_MAXIMA_BARRIER_RADIUS)) {
            float impact = getProtegoMaximaImpactForSpell(beam.getSpellType());
            if (applyProtegoMaximaImpact(level, player, impact, impact * 0.38f, beam.position())) {
                return;
            }
            level.sendParticles(ParticleTypes.FLASH, beam.getX(), beam.getY(), beam.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, beam.getX(), beam.getY(), beam.getZ(), 10, 0.18D, 0.18D, 0.18D, 0.01D);
            beam.discard();
        }

        for (net.minecraft.world.entity.projectile.Projectile projectile : level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class,
                area,
                projectile -> projectile.isAlive()
                        && projectile.getOwner() != player
                        && !(projectile instanceof SpellBeamEntity)
                        && projectile.position().distanceTo(player.position()) <= PROTEGO_MAXIMA_BARRIER_RADIUS)) {
            if (applyProtegoMaximaImpact(level, player, 5.0f, 1.5f, projectile.position())) {
                return;
            }
            Vec3 away = projectile.position().subtract(player.position());
            if (away.lengthSqr() < 1.0E-6D) {
                away = new Vec3(0.0D, 0.15D, 0.0D);
            }
            Vec3 push = away.normalize().scale(0.9D);
            projectile.setDeltaMovement(push.x, Math.max(0.15D, push.y + 0.15D), push.z);
            projectile.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.12D, 0.12D, 0.12D, 0.01D);
        }
    }

    private static float getProtegoMaximaImpactForSpell(SpellType spell) {
        return Math.max(4.0f, spell.getManaCost() * 0.42f);
    }

    private static boolean applyProtegoMaximaImpact(ServerLevel level, ServerPlayer player, float healthDamage, float manaDrain, Vec3 impactPos) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        float bubbleHealth = data.contains(TAG_PROTEGO_MAXIMA_HEALTH)
                ? data.getFloat(TAG_PROTEGO_MAXIMA_HEALTH)
                : PROTEGO_MAXIMA_MAX_HEALTH;
        bubbleHealth = Math.max(0.0f, bubbleHealth - healthDamage);
        data.putFloat(TAG_PROTEGO_MAXIMA_HEALTH, bubbleHealth);
        data.putLong(TAG_PROTEGO_MAXIMA_LAST_HIT, player.level().getGameTime());

        net.minecraft.nbt.CompoundTag persisted = data.getCompound("PlayerPersisted");
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float newMana = Math.max(0.0f, currentMana - manaDrain);
        modData.putFloat("Mana", newMana);
        persisted.put(ExampleMod.MODID, modData);
        data.put("PlayerPersisted", persisted);
        SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(newMana, maxMana), PacketDistributor.PLAYER.with(player));

        level.sendParticles(ParticleTypes.FLASH, impactPos.x, impactPos.y, impactPos.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
        level.sendParticles(ParticleTypes.GLOW, impactPos.x, impactPos.y, impactPos.z, 10, 0.16D, 0.16D, 0.16D, 0.01D);

        if (bubbleHealth <= 0.0f || newMana <= 0.0f) {
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + player.getBbHeight() * 0.7D, player.getZ(), 35, 1.8D, 1.8D, 1.8D, 0.08D);
            level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + player.getBbHeight() * 0.7D, player.getZ(), 24, 1.2D, 1.2D, 1.2D, 0.03D);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
            stopProtegoMaximaChannel(player, player.getMainHandItem());
            player.displayClientMessage(Component.literal("Protego Maxima broke").withStyle(net.minecraft.ChatFormatting.RED), true);
            return true;
        }
        return false;
    }

    private static boolean handleBlockNaming(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(net.minecraft.world.item.Items.NAME_TAG) || !held.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return false;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof com.hogwartsmagic.block.entity.OwlPerchBlockEntity perch) {
            perch.setPerchName(held.getHoverName().getString());
            perch.setOwner(player);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.perch.named", perch.getPerchName()), true);
            return true;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity fireplace) {
            fireplace.setFireplaceName(held.getHoverName().getString(), player);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.floo.named", fireplace.getFireplaceName()), true);
            return true;
        }
        return false;
    }

    private static boolean handlePerchMail(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof com.hogwartsmagic.block.entity.OwlPerchBlockEntity perch)) {
            return false;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(Items.WRITTEN_BOOK)) {
            return false;
        }
        if (perch.queueMail(held, player)) {
            held.shrink(1);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.perch.queued"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.perch.busy"), true);
        }
        return true;
    }

    private static void tickPendingFlooTravel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        if (!modData.getBoolean("FlooTravelPending")) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos sourcePos = BlockPos.of(modData.getLong("FlooSourcePos"));
        BlockPos destinationPos = BlockPos.of(modData.getLong("FlooDestinationPos"));
        int ticks = modData.getInt("FlooTravelTicks");

        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                player.getX(), player.getY() + 0.8, player.getZ(),
                12, 0.32, 0.65, 0.32, 0.02);
        serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                player.getX(), player.getY() + 0.9, player.getZ(),
                8, 0.28, 0.45, 0.28, 0.01);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 0.55, player.getZ(),
                6, 0.16, 0.25, 0.16, 0.006);

        if (serverLevel.isLoaded(destinationPos)
                && serverLevel.getBlockEntity(destinationPos) instanceof com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity destination) {
            destination.ignite(serverLevel);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    destinationPos.getX() + 0.5, destinationPos.getY() + 0.9, destinationPos.getZ() + 0.5,
                    10, 0.28, 0.45, 0.28, 0.01);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    destinationPos.getX() + 0.5, destinationPos.getY() + 0.75, destinationPos.getZ() + 0.5,
                    8, 0.18, 0.18, 0.18, 0.005);
            serverLevel.sendParticles(ParticleTypes.WAX_ON,
                    destinationPos.getX() + 0.5, destinationPos.getY() + 0.85, destinationPos.getZ() + 0.5,
                    6, 0.24, 0.18, 0.24, 0.01);
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        if (ticks > 0) {
            modData.putInt("FlooTravelTicks", ticks - 1);
            PlayerMagicData.saveModData(player, modData);
            return;
        }

        if (!serverLevel.isLoaded(sourcePos)
                || !serverLevel.isLoaded(destinationPos)
                || !(serverLevel.getBlockEntity(sourcePos) instanceof com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity source)
                || !(serverLevel.getBlockEntity(destinationPos) instanceof com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity destination)
                || !source.getLinkedPositions().contains(destinationPos)) {
            modData.remove("FlooTravelPending");
            modData.remove("FlooSourcePos");
            modData.remove("FlooDestinationPos");
            modData.remove("FlooTravelTicks");
            PlayerMagicData.saveModData(player, modData);
            return;
        }

        BlockPos exitPos = destinationPos.above();
        player.teleportTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 0.6, player.getZ(),
                60, 0.55, 0.75, 0.55, 0.03);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 0.9, player.getZ(),
                24, 0.35, 0.55, 0.35, 0.01);
        serverLevel.sendParticles(ParticleTypes.ASH,
                player.getX(), player.getY() + 0.7, player.getZ(),
                20, 0.35, 0.5, 0.35, 0.01);
        serverLevel.sendParticles(ParticleTypes.WAX_ON,
                player.getX(), player.getY() + 0.85, player.getZ(),
                26, 0.38, 0.6, 0.38, 0.03);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.9, player.getZ(),
                12, 0.3, 0.5, 0.3, 0.01);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.7f, 1.5f);
        destination.ignite(serverLevel);

        modData.remove("FlooTravelPending");
        modData.remove("FlooSourcePos");
        modData.remove("FlooDestinationPos");
        modData.remove("FlooTravelTicks");
        PlayerMagicData.saveModData(player, modData);
    }

    private static void tickPolyjuice(ServerPlayer player) {
        int pendingTicks = PolyjuiceData.getPendingTicks(player);
        if (pendingTicks > 0) {
            spawnPolyjuiceTransformation(player);
            pendingTicks--;
            if (pendingTicks <= 0) {
                net.minecraft.nbt.CompoundTag pending = PolyjuiceData.getPending(player);
                if (!pending.isEmpty()) {
                    int disguiseTicks = PolyjuiceData.getPendingDisguiseTicks(player);
                    PolyjuiceData.activate(player, pending);
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            ExampleMod.POLYJUICE_EFFECT.getHolder().get(),
                            disguiseTicks,
                            0,
                            false,
                            false,
                            true
                    ));
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.85f, 0.7f);
                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0D, player.getZ(), 35, 0.35D, 0.6D, 0.35D, 0.05D);
                    }
                    syncPolyjuice(player);
                } else {
                    PolyjuiceData.clearAll(player);
                    syncPolyjuice(player);
                }
            } else {
                PolyjuiceData.setPendingTicks(player, pendingTicks);
            }
        }

        if (!player.hasEffect(ExampleMod.POLYJUICE_EFFECT.getHolder().get()) && PolyjuiceData.hasActive(player)) {
            PolyjuiceData.clearAll(player);
            syncPolyjuice(player);
        }
    }

    private static void spawnFirstYearInvite(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.item.ItemStack invite = HogwartsInviteItem.createFor(player);
        net.minecraft.world.item.ItemStack guide = new net.minecraft.world.item.ItemStack(ExampleMod.HOGWARTS_GUIDE.get());
        OwlEntity owl = com.hogwartsmagic.entity.ModEntities.OWL.get().create(level);
        if (owl == null) {
            if (!player.addItem(invite.copy())) {
                player.drop(invite.copy(), false);
            }
            if (!player.addItem(guide.copy())) {
                player.drop(guide.copy(), false);
            }
            return;
        }

        double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 8.0D + player.getRandom().nextDouble() * 3.0D;
        double spawnX = player.getX() + Math.cos(angle) * radius;
        double spawnY = player.getY() + 7.5D;
        double spawnZ = player.getZ() + Math.sin(angle) * radius;

        owl.moveTo(spawnX, spawnY, spawnZ, player.getYRot() + 180.0F, 0.0F);
        owl.beginPlayerDelivery(player, invite.copy());

        if (level.noCollision(owl)) {
            level.addFreshEntity(owl);
        } else {
            if (!player.addItem(invite)) {
                player.drop(invite, false);
            }
            if (!player.addItem(guide.copy())) {
                player.drop(guide.copy(), false);
            }
        }
    }

    private static void spawnPolyjuiceTransformation(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel) || player.tickCount % 3 != 0) {
            return;
        }
        serverLevel.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.25D, 0.5D, 0.25D, 0.03D);
        serverLevel.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0D, player.getZ(), 10, 0.35D, 0.6D, 0.35D, 0.02D);
    }

    private static void tickPolyjuiceMobReactions(ServerPlayer player) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        net.minecraft.nbt.CompoundTag disguise = PolyjuiceData.getActive(player);
        if (!PolyjuiceData.isMobSample(disguise)) {
            return;
        }
        net.minecraft.world.entity.EntityType<?> disguiseType = PolyjuiceData.getEntityType(disguise).orElse(null);
        if (disguiseType == null) {
            return;
        }

        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0D))) {
            if (!mob.isAlive() || mob == player.getVehicle()) {
                continue;
            }
            if (shouldIgnoreDisguise(mob, disguiseType)) {
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                }
                if (mob.getLastHurtByMob() == player) {
                    mob.setLastHurtByMob(null);
                }
            } else if (shouldForceAttackDisguise(mob, disguiseType) && mob.getTarget() == null && mob.hasLineOfSight(player)) {
                mob.setTarget(player);
            }
        }
    }

    private static boolean shouldIgnoreDisguise(Mob mob, net.minecraft.world.entity.EntityType<?> disguiseType) {
        if (mob instanceof Wolf) {
            return false;
        }
        if (mob.getType() == disguiseType) {
            return true;
        }
        MobCategory disguiseCategory = disguiseType.getCategory();
        return mob instanceof net.minecraft.world.entity.monster.Enemy && disguiseCategory == MobCategory.MONSTER;
    }

    private static boolean shouldForceAttackDisguise(Mob mob, net.minecraft.world.entity.EntityType<?> disguiseType) {
        if (mob instanceof Wolf) {
            return isSkeletonDisguise(disguiseType);
        }
        return false;
    }

    private static boolean isSkeletonDisguise(net.minecraft.world.entity.EntityType<?> disguiseType) {
        return disguiseType == net.minecraft.world.entity.EntityType.SKELETON
                || disguiseType == net.minecraft.world.entity.EntityType.STRAY
                || disguiseType == net.minecraft.world.entity.EntityType.WITHER_SKELETON
                || disguiseType == net.minecraft.world.entity.EntityType.BOGGED;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide()
                && event.getEntity() instanceof ServerPlayer serverPlayer
                && event.getAmount() > 0.0f) {
            interruptActiveCasting(serverPlayer);
            revealInvisibilityCloak(serverPlayer);
        }

        if (event.getEntity() instanceof Player player && !event.getEntity().level().isClientSide) {
            HousePerks.markRecentlyHurt(player, event.getEntity().level().getGameTime());
        }

        if (!event.getEntity().level().isClientSide
                && event.getEntity() instanceof com.hogwartsmagic.entity.WizardEntity wizard
                && event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            java.util.UUID raidId = WIZARD_TO_RAID.get(wizard.getUUID());
            if (raidId != null) {
                ActiveWizardRaid raid = ACTIVE_WIZARD_RAIDS.get(raidId);
                if (raid != null) {
                    raid.participants.add(player.getUUID());
                }
            }
        }

        if (event.getSource().getEntity() instanceof Player attacker && event.getSource().getDirectEntity() == attacker) {
            float boosted = event.getAmount() * HousePerks.getMeleeDamageMultiplier(attacker);
            event.setAmount(boosted);
        }

        ServerPlayer cloakAttacker = resolveCloakAttacker(event);
        if (cloakAttacker != null) {
            revealInvisibilityCloak(cloakAttacker);
        }

        // Avada Kedavra bypasses all shields and protections
        if (event.getSource().getDirectEntity() instanceof com.hogwartsmagic.entity.SpellBeamEntity spell) {
            if (spell.getSpellType() == SpellType.AVADA_KEDAVRA) {
                return; // Let the damage absolutely go through!
            }
        }

        // Cancel fall damage if the player was recently riding the broom or is currently on it
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            if (event.getEntity().getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity) {
                event.setCanceled(true);
                return;
            }
            // If they dismounted mid-air, they might still take fall damage. 
            // We can prevent it by checking their Y-velocity or giving them temporary slow-fall/absorption, 
            // but the simplest check is just clearing fall distance if they dismount.
            // So we'll let BroomEntity handle resetting fall distance in its tick/dismount logic instead.
        }

        if (event.getEntity().hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            // Cancel the damage
            event.setCanceled(true);
            
            // Physical attacks and spells now wear down the shield!
            // Play a breaking/deflecting sound
            event.getEntity().level().playSound(null, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

            var instance = event.getEntity().getEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            if (instance != null) {
                int amp = instance.getAmplifier();
                event.getEntity().removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
                if (amp > 0) {
                    event.getEntity().addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), instance.getDuration(), amp - 1));
                } else {
                    // Shield absolutely shatters
                    event.getEntity().level().playSound(null, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                        net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
                }
            }
        }
        
        if (event.getEntity().hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get())) {
            event.setCanceled(true); // Invulnerability
        }

        // Iron golems defend students like villagers when a student actually takes damage
        if (!event.getEntity().level().isClientSide
                && !event.isCanceled()
                && event.getEntity() instanceof com.hogwartsmagic.entity.StudentEntity student) {
            LivingEntity attacker = resolveStudentHurtAttacker(event);
            if (attacker != null && attacker.isAlive() && attacker != student) {
                net.minecraft.world.phys.AABB box = student.getBoundingBox().inflate(16.0D, 8.0D, 16.0D);
                for (net.minecraft.world.entity.animal.IronGolem golem : ((ServerLevel) student.level()).getEntitiesOfClass(
                        net.minecraft.world.entity.animal.IronGolem.class, box)) {
                    if (golem.isAlive() && golem.canAttack(attacker)) {
                        golem.setTarget(attacker);
                    }
                }
            }
        }
    }

    @javax.annotation.Nullable
    private static LivingEntity resolveStudentHurtAttacker(LivingHurtEvent event) {
        net.minecraft.world.entity.Entity src = event.getSource().getEntity();
        if (src instanceof LivingEntity living) {
            return living;
        }
        if (src instanceof com.hogwartsmagic.entity.SpellBeamEntity beam) {
            net.minecraft.world.entity.Entity owner = beam.getOwner();
            if (owner instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static ServerPlayer resolveCloakAttacker(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (event.getSource().getDirectEntity() instanceof SpellBeamEntity beam
                && beam.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer player && HorcruxData.isDeathHandled(player)) {
            event.getDrops().clear();
            return;
        }
        net.minecraft.util.RandomSource random = dead.getRandom();
        ServerLevel level = (ServerLevel) dead.level();
        double x = dead.getX();
        double y = dead.getY();
        double z = dead.getZ();

        if (dead instanceof com.hogwartsmagic.entity.AcromantulaEntity) {
            if (random.nextFloat() < 0.35f) {
                event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(ExampleMod.ACROMANTULA_VENOM.get(), 1)));
            }
        }

        if (dead instanceof com.hogwartsmagic.entity.LycanthropeVillagerEntity) {
            if (random.nextFloat() < 0.50f) {
                event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(ExampleMod.WOLF_FANG.get(), 1)));
            }
        }

        if (dead instanceof com.hogwartsmagic.entity.MinatorEntity) {
            if (random.nextFloat() < 0.35f) {
                event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(ExampleMod.CENTAUR_TALISMAN.get(), 1)));
            }
        }

        if (!(dead instanceof com.hogwartsmagic.entity.WizardEntity)
                && !(dead instanceof com.hogwartsmagic.entity.StudentEntity)) {
            return;
        }

        if (random.nextFloat() < 0.88f) {
            int dust = 1 + random.nextInt(4);
            event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(ExampleMod.ARCANE_DUST.get(), dust)));
        }
        if (random.nextFloat() < 0.18f) {
            event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(ExampleMod.ARCANE_CORE.get(), 1)));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living
                && !(living instanceof Player)
                && living.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())
                && living.level() instanceof ServerLevel petrifiedLevel) {
            com.hogwartsmagic.entity.PetrifiedRemainsEntity remains = com.hogwartsmagic.entity.PetrifiedRemainsEntity.fromLiving(petrifiedLevel, living);
            if (remains != null) {
                petrifiedLevel.addFreshEntity(remains);
            }
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            InvisibilityCloakItem.stopCloak(player, false);
            PolyjuiceData.clearAll(player);
            syncPolyjuice(player);
            HorcruxBlockEntity horcrux = HorcruxData.getValidHorcrux(player);
            if (horcrux != null) {
                HorcruxData.captureInventory(player);
                HorcruxData.markPendingRebirth(player);
                ServerLevel level = player.serverLevel();
                com.hogwartsmagic.entity.HorcruxSoulEntity soul = com.hogwartsmagic.entity.HorcruxSoulEntity.fromPlayer(level, player, horcrux.getBlockPos());
                if (soul != null) {
                    level.addFreshEntity(soul);
                    SpellPackets.CHANNEL.send(new SpellPackets.StartHorcruxDeathSequencePacket(soul.getId(), player.position(), horcrux.getBlockPos()),
                            PacketDistributor.PLAYER.with(player));
                }
                level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.9D, player.getZ(), 36, 0.35, 0.55, 0.35, 0.03);
                level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.05D, player.getZ(), 24, 0.28, 0.4, 0.28, 0.025);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 0.75D, player.getZ(), 18, 0.24, 0.28, 0.24, 0.04);
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9f, 0.45f);
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.55f);
            }
        }
        if (!(event.getEntity() instanceof com.hogwartsmagic.entity.WizardEntity wizard)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        java.util.UUID raidId = WIZARD_TO_RAID.remove(wizard.getUUID());
        if (raidId == null) {
            return;
        }
        ActiveWizardRaid raid = ACTIVE_WIZARD_RAIDS.get(raidId);
        if (raid == null) {
            return;
        }

        raid.members.remove(wizard.getUUID());
        updateRaidProgress(level, raid);
        if (raid.members.isEmpty()) {
            finishWizardRaid(level, raid, true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            // Advance any in-progress Mini Hogwarts build (spread across ticks).
            com.hogwartsmagic.worldgen.MiniHogwartsManager.tick(event.getServer());
        }
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || ACTIVE_WIZARD_RAIDS.isEmpty()) {
            return;
        }

        java.util.List<java.util.UUID> endedRaids = new java.util.ArrayList<>();
        // Iterate over a snapshot because raid completion can remove entries from ACTIVE_WIZARD_RAIDS.
        for (ActiveWizardRaid raid : new java.util.ArrayList<>(ACTIVE_WIZARD_RAIDS.values())) {
            ServerLevel level = event.getServer().getLevel(raid.dimension);
            if (level == null) {
                endedRaids.add(raid.raidId);
                continue;
            }

            updateRaidBarPlayers(level, raid);
            updateRaidProgress(level, raid);
            if (raid.members.isEmpty()) {
                finishWizardRaid(level, raid, true);
            }
        }

        for (java.util.UUID raidId : endedRaids) {
            ActiveWizardRaid raid = ACTIVE_WIZARD_RAIDS.get(raidId);
            if (raid != null) {
                ACTIVE_WIZARD_RAIDS.remove(raidId);
                raid.bossBar.removeAllPlayers();
                raid.bossBar.setVisible(false);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level).tick();
        ReparoManager.tick(level);
        tickRevelioLights(level);
        tickTemporaryAquaWater(level);
        if (level.getGameTime() % STRUCTURE_SPAWN_CHECK_INTERVAL != 0) {
            return;
        }

        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            trySpawnStudentAtVillage(level, player);
            trySpawnDeathEaterAtOutpost(level, player);
            tryStartDeathEaterBaseRaid(level, player);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getState().is(ExampleMod.HORCRUX.get())
                && level.getBlockEntity(event.getPos()) instanceof HorcruxBlockEntity horcrux
                && (horcrux.isActive() || horcrux.isPrimed())) {
            horcrux.handleForcedDestruction(level, event.getPlayer());
        }
        ReparoManager.recordBrokenBlock(level, event.getPos(), event.getState());
        // Mirror into the broader-radius / longer-TTL tracker used by the StudentEntity AI
        // for "rebuild what just got smashed" Reparo casts. Independent because
        // ReparoManager prunes aggressively when no Reparo-learner player is nearby,
        // which would defeat student-driven repairs.
        if (event.getPlayer() != null && !event.getPlayer().isCreative()) {
            com.hogwartsmagic.util.RecentlyBrokenTracker.record(level, event.getPos(), event.getState());
        }
        level.getServer().execute(() -> com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level)
                .handlePotentialBlockChanges(java.util.List.of(event.getPos().immutable())));
    }

    /**
     * Mob-driven block breaks aren't covered by BlockEvent.BreakEvent (player-only) or
     * ExplosionEvent.Detonate. LivingDestroyBlockEvent fires for things like zombies
     * breaking doors, ravagers chewing through crops, withers boring tunnels, and the
     * Ender Dragon crashing through walls. Mirror those into RecentlyBrokenTracker +
     * ReparoManager so students will repair the damage just like player-broken or
     * explosion-broken blocks.
     */
    @SubscribeEvent
    public static void onLivingDestroyBlock(net.minecraftforge.event.entity.living.LivingDestroyBlockEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        net.minecraft.world.level.block.state.BlockState state = event.getState();
        if (state == null || state.isAir()) return;
        net.minecraft.core.BlockPos pos = event.getPos();
        com.hogwartsmagic.util.RecentlyBrokenTracker.record(level, pos, state);
        ReparoManager.recordBrokenBlock(level, pos, state);
    }

    @SubscribeEvent
    public static void onBlockPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        level.getServer().execute(() -> com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level)
                .handlePotentialBlockChanges(java.util.List.of(event.getPos().immutable())));
    }

    @SubscribeEvent
    public static void onMultiBlockPlace(net.minecraftforge.event.level.BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        java.util.List<BlockPos> changed = new java.util.ArrayList<>();
        for (net.minecraftforge.common.util.BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            changed.add(snapshot.getPos().immutable());
        }
        level.getServer().execute(() -> com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level)
                .handlePotentialBlockChanges(changed));
    }

    @SubscribeEvent
    public static void onFluidPlace(net.minecraftforge.event.level.BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        level.getServer().execute(() -> com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level)
                .handlePotentialBlockChanges(java.util.List.of(event.getPos().immutable())));
    }

    @SubscribeEvent
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getAffectedBlocks().isEmpty()) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            ReparoManager.recordBrokenBlock(level, pos, state);
            // Also mirror into the broader RecentlyBrokenTracker so students can Reparo
            // explosion craters (Bombarda / TNT / creepers), not just player mining.
            if (!state.isAir()) {
                com.hogwartsmagic.util.RecentlyBrokenTracker.record(level, pos, state);
            }
        }
        java.util.List<BlockPos> changed = event.getAffectedBlocks().stream().map(BlockPos::immutable).toList();
        level.getServer().execute(() -> com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level)
                .handlePotentialBlockChanges(changed));
    }

    @SubscribeEvent
    public static void onPistonPost(net.minecraftforge.event.level.PistonEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        java.util.List<BlockPos> changed = new java.util.ArrayList<>();
        BlockPos origin = event.getPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    changed.add(origin.offset(dx, dy, dz).immutable());
                }
            }
        }
        level.getServer().execute(() -> com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level)
                .handlePotentialBlockChanges(changed));
    }

    /**
     * During village / pillager outpost worldgen, about 1 in 30 structure-spawned villagers become lycanthrope villagers;
     * 1/4 of remaining structure-spawned villagers become students; 1/4 of structure-spawned
     * pillagers at outposts become death eaters (wizards).
     * Spawns are deferred to the next server tick to avoid worldgen chunk deadlocks.
     */
    @SubscribeEvent
    public static void onMobFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() != MobSpawnType.STRUCTURE) {
            return;
        }

        if (event.getEntity() instanceof Villager villager) {
            ServerLevel serverLevelVillager = resolveServerLevel(event.getLevel());
            if (serverLevelVillager != null
                    && isNearStructure(serverLevelVillager, villager.blockPosition(), StructureTags.VILLAGE, LYCANTHROPE_VILLAGE_STRUCTURE_RADIUS)
                    && shouldReplaceVillageVillagerWithLycan()) {
                MinecraftServer serverLycan = serverLevelVillager.getServer();
                double lx = villager.getX();
                double ly = villager.getY();
                double lz = villager.getZ();
                float lyRot = villager.getYRot();
                float lxRot = villager.getXRot();
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimLycan = serverLevelVillager.dimension();
                event.setSpawnCancelled(true);
                serverLycan.execute(() -> {
                    ServerLevel level = serverLycan.getLevel(dimLycan);
                    if (level == null) {
                        return;
                    }
                    BlockPos posL = BlockPos.containing(lx, ly, lz);
                    LycanthropeVillagerEntity lycan = com.hogwartsmagic.entity.ModEntities.LYCANTHROPE_VILLAGER.get().create(level);
                    if (lycan == null) {
                        return;
                    }
                    lycan.moveTo(lx, ly, lz, lyRot, lxRot);
                    lycan.finalizeSpawn(level, level.getCurrentDifficultyAt(posL), MobSpawnType.STRUCTURE, null);
                    level.addFreshEntity(lycan);
                });
                return;
            }
            // FinalizeSpawn may run on a worldgen worker thread; never use level.random here (LegacyRandomSource is server-thread-only).
            if (ThreadLocalRandom.current().nextInt(4) != 0) {
                return;
            }
            ServerLevel serverLevel = resolveServerLevel(event.getLevel());
            if (serverLevel == null) {
                return;
            }
            MinecraftServer server = serverLevel.getServer();
            double x = villager.getX();
            double y = villager.getY();
            double z = villager.getZ();
            float yRot = villager.getYRot();
            float xRot = villager.getXRot();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = serverLevel.dimension();
            event.setSpawnCancelled(true);
            server.execute(() -> {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    return;
                }
                BlockPos pos = BlockPos.containing(x, y, z);
                com.hogwartsmagic.entity.StudentEntity student = com.hogwartsmagic.entity.ModEntities.STUDENT.get().create(level);
                if (student == null) {
                    return;
                }
                student.moveTo(x, y, z, yRot, xRot);
                student.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
                level.addFreshEntity(student);
            });
            return;
        }

        if (event.getEntity() instanceof Pillager pillager) {
            if (ThreadLocalRandom.current().nextInt(4) != 0) {
                return;
            }
            ServerLevel serverLevel = resolveServerLevel(event.getLevel());
            if (serverLevel == null) {
                return;
            }
            BlockPos pos = pillager.blockPosition();
            if (!isNearStructureKey(serverLevel, pos, BuiltinStructures.PILLAGER_OUTPOST, 96)) {
                return;
            }
            MinecraftServer server = serverLevel.getServer();
            double x = pillager.getX();
            double y = pillager.getY();
            double z = pillager.getZ();
            float yRot = pillager.getYRot();
            float xRot = pillager.getXRot();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = serverLevel.dimension();
            event.setSpawnCancelled(true);
            server.execute(() -> {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    return;
                }
                com.hogwartsmagic.entity.WizardEntity wizard = com.hogwartsmagic.entity.ModEntities.WIZARD.get().create(level);
                if (wizard == null) {
                    return;
                }
                wizard.moveTo(x, y, z, yRot, xRot);
                wizard.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.STRUCTURE, null);
                level.addFreshEntity(wizard);
            });
        }
    }

    @javax.annotation.Nullable
    private static ServerLevel resolveServerLevel(ServerLevelAccessor accessor) {
        if (accessor instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        return accessor.getLevel();
    }

    private static void trySpawnStudentAtVillage(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        if (!isNearStructure(level, player.blockPosition(), StructureTags.VILLAGE, STUDENT_SPAWN_RADIUS)) {
            return;
        }

        int villagerCount = level.getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                player.getBoundingBox().inflate(STUDENT_SPAWN_RADIUS),
                net.minecraft.world.entity.Entity::isAlive
        ).size();
        if (villagerCount <= 0) {
            return;
        }

        int nearbyCount = level.getEntitiesOfClass(
                com.hogwartsmagic.entity.StudentEntity.class,
                player.getBoundingBox().inflate(STUDENT_SPAWN_RADIUS),
                net.minecraft.world.entity.Entity::isAlive
        ).size();
        int desiredCount = Math.max(2, Math.min(STUDENT_NEARBY_CAP, (villagerCount + STUDENT_PER_VILLAGER_RATIO - 1) / STUDENT_PER_VILLAGER_RATIO));
        if (nearbyCount >= desiredCount) {
            return;
        }

        net.minecraft.core.BlockPos spawnPos = randomSpawnPosAround(level, player.blockPosition(), 12, 40);
        if (spawnPos == null) {
            return;
        }

        com.hogwartsmagic.entity.StudentEntity student = com.hogwartsmagic.entity.ModEntities.STUDENT.get().create(level);
        if (student == null) {
            return;
        }
        student.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        student.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.NATURAL, null);
        level.addFreshEntity(student);
    }

    private static void trySpawnDeathEaterAtOutpost(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        if (!isNearStructureKey(level, player.blockPosition(), net.minecraft.world.level.levelgen.structure.BuiltinStructures.PILLAGER_OUTPOST, DEATH_EATER_SPAWN_RADIUS)) {
            return;
        }
        if (level.random.nextFloat() > 0.16f) {
            return;
        }

        int nearbyCount = level.getEntitiesOfClass(
                com.hogwartsmagic.entity.WizardEntity.class,
                player.getBoundingBox().inflate(DEATH_EATER_SPAWN_RADIUS),
                net.minecraft.world.entity.Entity::isAlive
        ).size();
        if (nearbyCount >= DEATH_EATER_NEARBY_CAP) {
            return;
        }

        net.minecraft.core.BlockPos spawnPos = randomSpawnPosAround(level, player.blockPosition(), 16, 48);
        if (spawnPos == null) {
            return;
        }

        com.hogwartsmagic.entity.WizardEntity wizard = com.hogwartsmagic.entity.ModEntities.WIZARD.get().create(level);
        if (wizard == null) {
            return;
        }
        wizard.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        wizard.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.NATURAL, null);
        level.addFreshEntity(wizard);
    }

    private static void tryStartDeathEaterBaseRaid(ServerLevel level, ServerPlayer player) {
        if (!Config.isDeathEaterBaseRaidsEnabled()) {
            return;
        }
        if (player.tickCount % DEATH_EATER_BASE_CHECK_INTERVAL != 0) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD) || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (isPlayerNearActiveWizardRaid(level, player)) {
            return;
        }

        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long now = level.getGameTime();
        long lastRaidTime = modData.getLong(TAG_LAST_DEATH_EATER_BASE_RAID);
        if (lastRaidTime > 0L && now - lastRaidTime < Config.getDeathEaterBaseRaidCooldownTicks()) {
            return;
        }

        BaseAnchor anchor = findNearbyPlayerBaseAnchor(level, player.blockPosition());
        if (anchor == null || anchor.score() < 3) {
            return;
        }
        if (level.random.nextFloat() > Config.getDeathEaterBaseRaidChance()) {
            return;
        }

        if (!spawnDeathEaterBaseRaid(level, player, anchor.pos())) {
            return;
        }

        modData.putLong(TAG_LAST_DEATH_EATER_BASE_RAID, now);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    private static boolean isPlayerNearActiveWizardRaid(ServerLevel level, ServerPlayer player) {
        for (ActiveWizardRaid raid : ACTIVE_WIZARD_RAIDS.values()) {
            if (!raid.dimension.equals(level.dimension())) {
                continue;
            }
            if (raid.center.closerThan(player.blockPosition(), DEATH_EATER_BASE_RAID_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    private static BaseAnchor findNearbyPlayerBaseAnchor(ServerLevel level, BlockPos center) {
        BaseAnchor best = null;
        BlockPos min = center.offset(-DEATH_EATER_BASE_SEARCH_RADIUS, -DEATH_EATER_BASE_VERTICAL_SEARCH, -DEATH_EATER_BASE_SEARCH_RADIUS);
        BlockPos max = center.offset(DEATH_EATER_BASE_SEARCH_RADIUS, DEATH_EATER_BASE_VERTICAL_SEARCH, DEATH_EATER_BASE_SEARCH_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            int score = scoreBaseBlock(level, pos);
            if (score <= 0) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            if (best == null || score > best.score() || (score == best.score() && immutable.distSqr(center) < best.pos().distSqr(center))) {
                best = new BaseAnchor(immutable, score);
            }
        }
        return best;
    }

    private static int scoreBaseBlock(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block == ExampleMod.WARD.get()) {
            if (level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward) {
                int score = ward.isProtegoMaximaActive() ? 6 : 4;
                if (ward.isActive()) {
                    score += 2;
                }
                return score;
            }
            return 4;
        }
        if (block == ExampleMod.HORCRUX.get()) {
            return 6;
        }
        if (block == ExampleMod.FLOO_FIREPLACE.get()) {
            return 5;
        }
        if (block == ExampleMod.WAND_WORKBENCH.get() || block == ExampleMod.BROOM_WORKBENCH.get()) {
            return 4;
        }
        if (block == ExampleMod.OWL_PERCH.get() || block == ExampleMod.WIZARD_CAULDRON.get()) {
            return 3;
        }
        if (block == ExampleMod.ENCHANTED_BOOK_PILE.get() || block == ExampleMod.MOVING_PORTRAIT.get()) {
            return 2;
        }
        return 0;
    }

    private static boolean spawnDeathEaterBaseRaid(ServerLevel level, ServerPlayer player, BlockPos anchor) {
        BlockPos spawnCenter = randomSpawnPosAround(level, anchor, DEATH_EATER_BASE_SPAWN_MIN_DIST, DEATH_EATER_BASE_SPAWN_MAX_DIST);
        if (spawnCenter == null) {
            spawnCenter = randomSpawnPosAround(level, player.blockPosition(), DEATH_EATER_BASE_SPAWN_MIN_DIST, DEATH_EATER_BASE_SPAWN_MAX_DIST);
        }
        if (spawnCenter == null) {
            return false;
        }

        java.util.List<com.hogwartsmagic.entity.WizardEntity> raidMembers = new java.util.ArrayList<>();
        int raidSize = 3 + level.random.nextInt(3);

        for (int i = 0; i < raidSize; i++) {
            BlockPos spawnPos = i == 0
                    ? spawnCenter
                    : randomSpawnPosAround(level, spawnCenter, 2, 10);
            if (spawnPos == null) {
                spawnPos = spawnCenter;
            }

            com.hogwartsmagic.entity.WizardEntity wizard = com.hogwartsmagic.entity.ModEntities.WIZARD.get().create(level);
            if (wizard == null) {
                continue;
            }

            wizard.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            wizard.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
            if (i == 0) {
                wizard.setRaidLeader(true);
                wizard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.WHITE_BANNER));
            }
            if (player.distanceToSqr(wizard) < (double) (DEATH_EATER_BASE_RAID_RADIUS * DEATH_EATER_BASE_RAID_RADIUS)) {
                wizard.setTarget(player);
            }
            if (level.random.nextFloat() < 0.25f) {
                wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
            }
            level.addFreshEntity(wizard);
            raidMembers.add(wizard);
        }

        if (raidMembers.isEmpty()) {
            return false;
        }

        DarkMarkEntity mark = new DarkMarkEntity(level, anchor.getX() + 0.5D, anchor.getY() + 18.0D, anchor.getZ() + 0.5D, 4800);
        level.addFreshEntity(mark);
        level.playSound(null, anchor, ModSounds.SPELL_MORSMORDRE.get(), SoundSource.HOSTILE, 1.5f, 0.9f);
        player.displayClientMessage(Component.literal("A band of Death Eaters is descending on your base!"), false);
        startWizardRaid(level, anchor, raidMembers);
        return true;
    }

    private static net.minecraft.core.BlockPos randomSpawnPosAround(ServerLevel level, net.minecraft.core.BlockPos center, int minRadius, int maxRadius) {
        for (int i = 0; i < 8; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dist = minRadius + level.random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            net.minecraft.core.BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new net.minecraft.core.BlockPos(x, 0, z));
            net.minecraft.core.BlockPos spawn = ground.above();
            if (level.isEmptyBlock(spawn) && level.isEmptyBlock(spawn.above()) && !level.getBlockState(ground).isAir()) {
                return spawn;
            }
        }
        return null;
    }

    private static net.minecraft.core.BlockPos randomSpawnPosNearStructure(ServerLevel level, net.minecraft.core.BlockPos center, int radius) {
        for (int i = 0; i < 20; i++) {
            int x = center.getX() + level.random.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + level.random.nextInt(radius * 2 + 1) - radius;
            int baseY = center.getY();

            for (int y = baseY + 8; y >= Math.max(level.getMinBuildHeight() + 1, baseY - 8); y--) {
                net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(x, y, z);
                net.minecraft.core.BlockPos below = feet.below();
                if (level.isEmptyBlock(feet)
                        && level.isEmptyBlock(feet.above())
                        && !level.getBlockState(below).isAir()) {
                    return feet;
                }
            }
        }
        return randomSpawnPosAround(level, center, 2, Math.max(4, radius));
    }

    private static boolean isNearStructure(ServerLevel level,
                                           net.minecraft.core.BlockPos origin,
                                           net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> structureTag,
                                           int radiusBlocks) {
        int searchRadiusChunks = Math.max(1, (radiusBlocks + 15) / 16);
        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        java.util.Optional<? extends net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure>> holderSet =
                structureRegistry.getTag(structureTag).map(tag -> tag);
        if (holderSet.isEmpty()) {
            return false;
        }
        com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> nearest =
                level.getChunkSource().getGenerator().findNearestMapStructure(level, holderSet.get(), origin, searchRadiusChunks, false);
        if (nearest == null || nearest.getFirst() == null) {
            return false;
        }
        return nearest.getFirst().distSqr(origin) <= (long) radiusBlocks * (long) radiusBlocks;
    }

    private static boolean isNearStructureKey(ServerLevel level,
                                              net.minecraft.core.BlockPos origin,
                                              net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> structureKey,
                                              int radiusBlocks) {
        net.minecraft.core.BlockPos nearestPos = findNearestStructurePos(level, origin, structureKey, radiusBlocks);
        if (nearestPos == null) {
            return false;
        }
        return nearestPos.distSqr(origin) <= (long) radiusBlocks * (long) radiusBlocks;
    }

    private static net.minecraft.core.BlockPos findNearestStructurePos(ServerLevel level,
                                                                       net.minecraft.core.BlockPos origin,
                                                                       net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> structureKey,
                                                                       int radiusBlocks) {
        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.level.levelgen.structure.Structure>> holder =
                structureRegistry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return null;
        }
        int searchRadiusChunks = Math.max(1, (radiusBlocks + 15) / 16);
        net.minecraft.core.HolderSet.Direct<net.minecraft.world.level.levelgen.structure.Structure> holderSet =
                net.minecraft.core.HolderSet.direct(holder.get());
        com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> nearest =
                level.getChunkSource().getGenerator().findNearestMapStructure(level, holderSet, origin, searchRadiusChunks, false);
        if (nearest == null || nearest.getFirst() == null) {
            return null;
        }
        return nearest.getFirst();
    }

    /** Villagers path away from nearby lycanthropes in werewolf form (no mixin into villager goals). */
    private static void tickVillagerFleeNearbyWerewolfLycanthrope(Villager villager) {
        if (villager.tickCount % 20 != 0) {
            return;
        }
        List<LycanthropeVillagerEntity> threats = villager.level().getEntitiesOfClass(
                LycanthropeVillagerEntity.class,
                villager.getBoundingBox().inflate(12.0),
                e -> e.isAlive() && e.isWerewolfForm());
        if (threats.isEmpty()) {
            return;
        }
        Vec3 vPos = villager.position();
        Vec3 sumAway = Vec3.ZERO;
        for (LycanthropeVillagerEntity w : threats) {
            Vec3 d = vPos.subtract(w.position());
            double len = Math.sqrt(d.lengthSqr());
            if (len < 1e-3) {
                d = new Vec3(1.0, 0.0, 0.0);
            } else {
                d = d.scale(1.0 / len);
            }
            sumAway = sumAway.add(d);
        }
        if (sumAway.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 dest = vPos.add(sumAway.normalize().scale(14.0));
        villager.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.25);
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingTickEvent event) {
        net.minecraft.world.entity.LivingEntity entity = event.getEntity();

        if (!entity.level().isClientSide() && entity.isAlive() && entity instanceof Villager villager) {
            tickVillagerFleeNearbyWerewolfLycanthrope(villager);
        }

        if (entity.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            if (entity.level().isClientSide) {
                spawnProtegoParticles(entity);
            }
        }

        // Handle Tenebris Flight effect - either apply flight behavior or cleanup when effect ends
        if (entity.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get())) {
            handleTenebrisFlight(entity);
        } else {
            if (entity instanceof com.hogwartsmagic.entity.WizardEntity wizard) {
                if (wizard.getPersistentData().getBoolean("hogwartsmagic:tenebris_was_flying")) {
                    // Trigger post-flight escape only if the wizard actually landed the smoke-ram hit.
                    if (wizard.getPersistentData().getBoolean("hogwartsmagic:tenebris_attacked")) {
                        wizard.getPersistentData().putBoolean("hogwartsmagic:tenebris_escape_after_hit", true);
                    }
                    wizard.getPersistentData().remove("hogwartsmagic:tenebris_was_flying");
                }
                // turn off flying flag now that the effect is gone
                wizard.setFlying(false);
                // only clear invisibility once flying has been cleared
                if (!wizard.isFlying()) {
                    wizard.getPersistentData().remove("hogwartsmagic:tenebris_attacked"); // allow next attack
                    wizard.getPersistentData().remove("hogwartsmagic:tenebris_hit_and_run_ticks");
                    wizard.setNoGravity(false); // restore normal physics when flight effect ends
                    wizard.setInvisible(false); // make visible again when flight effect ends
                    if (wizard.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
                        broom.setInvisible(false);
                    }
                }
            }
            if (entity instanceof com.hogwartsmagic.entity.StudentEntity student) {
                // turn off flying flag now that the effect is gone
                student.setFlying(false);
                // only clear invisibility once flying has been cleared
                if (!student.isFlying()) {
                    student.setNoGravity(false); // restore normal physics when flight effect ends
                    student.setInvisible(false); // make visible again when flight effect ends
                }
            }
            if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                if (player.getAbilities().mayfly && player.getPersistentData().getBoolean("hogwartsmagic:tenebris_flying")) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.getPersistentData().remove("hogwartsmagic:tenebris_flying");
                    player.onUpdateAbilities();
                }
            }
        }
        
        if (entity.hasEffect(ExampleMod.LUMOS_EFFECT.getHolder().get())) {
            handleLumosLight(entity);
        } else {
            // Clean up any leftover light when effect ends
            cleanupLumosLight(entity);
        }

        if (!entity.level().isClientSide()
                && entity.isAlive()
                && entity.hasEffect(ExampleMod.INCARCEROUS_CHAINS.getHolder().get())
                && entity.level() instanceof ServerLevel serverLevel) {
            if (entity.tickCount % 2 == 0) {
                IncarcerousChainAnimation.renderSustained(serverLevel, entity);
            }
        }

        if (entity.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())) {
            handleBasiliskPetrification(entity);
        }
    }

    private static void handleBasiliskPetrification(net.minecraft.world.entity.LivingEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;

        if (!entity.level().isClientSide && entity.tickCount % 10 == 0) {
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 20, 2, false, false, false));
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 20, 3, false, false, false));
        }

        if (entity.level().isClientSide && entity.tickCount % 2 == 0) {
            entity.level().addParticle(
                    ParticleTypes.ASH,
                    entity.getRandomX(0.45D),
                    entity.getY() + entity.getBbHeight() * 0.5D,
                    entity.getRandomZ(0.45D),
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private static void handleTenebrisFlight(net.minecraft.world.entity.LivingEntity entity) {
        // unified behaviour for players and wizards; wizards now behave like players
        if (entity instanceof Player player) {
            // Transform player into black smoke cloud form with flight
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.getPersistentData().putBoolean("hogwartsmagic:tenebris_flying", true);
                player.onUpdateAbilities();
            }
            if (player.getAbilities().flying) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 10, 5, false, false, false));
            }
            // Make player invisible while in smoke form - match flight effect duration (40 ticks = 2 seconds)
            if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 40, 0, false, false, false));
            }
        } else if (entity instanceof com.hogwartsmagic.entity.WizardEntity wizard) {
            wizard.setFlying(true);
            wizard.getPersistentData().putBoolean("hogwartsmagic:tenebris_was_flying", true);
            wizard.setNoGravity(true);

// Hide broom if riding
            if (wizard.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
                broom.setInvisible(true);
            }

// Speed boost while in flight
            wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 10, 5, false, false, false));

// Invisibility during flight
            if (!wizard.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                wizard.setInvisible(true);
                wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.INVISIBILITY, 40, 0, false, false, false));
            }

// Spawn ink and smoke particles on first tick
            if (wizard.level() instanceof ServerLevel server && wizard.tickCount < 5) {
                server.sendParticles(ParticleTypes.SQUID_INK,
                        wizard.getX(), wizard.getY() + wizard.getBbHeight() / 2, wizard.getZ(),
                        80, 1, 1, 1, 0.2);
                server.sendParticles(ParticleTypes.SMOKE,
                        wizard.getX(), wizard.getY() + wizard.getBbHeight() / 2, wizard.getZ(),
                        60, 1, 1, 1, 0.1);
            }

// Handle attacking target: smoke-ram behavior (chase -> touch hit -> immediate escape)
            LivingEntity target = wizard.getTarget();
            net.minecraft.nbt.CompoundTag data = wizard.getPersistentData();
            int hitAndRunTicks = data.getInt("hogwartsmagic:tenebris_hit_and_run_ticks");

            if (target != null && target.isAlive()) {
                if (!data.getBoolean("hogwartsmagic:tenebris_attacked")) {
                    Vec3 toTarget = target.getEyePosition(1.0f).subtract(wizard.getEyePosition(1.0f));
                    Vec3 toward = toTarget.lengthSqr() > 1.0E-4 ? toTarget.normalize() : Vec3.ZERO;
                    double ramSpeed = 2.8D;
                    wizard.setDeltaMovement(
                            toward.x * ramSpeed,
                            net.minecraft.util.Mth.clamp(toward.y * 1.2D, -0.45D, 0.45D),
                            toward.z * ramSpeed
                    );

                    // Touch detection: close proximity + slight bounding box growth to feel responsive.
                    boolean touched = wizard.getBoundingBox().inflate(0.45D).intersects(target.getBoundingBox());
                    if (touched) {
                        target.hurt(wizard.damageSources().mobAttack(wizard), 10.0f);
                        target.invulnerableTime = 0; // keep the impact feeling snappy in fast aerial combat
                        data.putBoolean("hogwartsmagic:tenebris_attacked", true);
                        data.putInt("hogwartsmagic:tenebris_hit_and_run_ticks", 10);

                        Vec3 away = wizard.position().subtract(target.position());
                        if (away.lengthSqr() < 1.0E-4) {
                            away = new Vec3((wizard.getRandom().nextDouble() - 0.5D), 0.0D, (wizard.getRandom().nextDouble() - 0.5D));
                        }
                        away = away.normalize();
                        wizard.setDeltaMovement(away.x * 1.8D, 0.18D, away.z * 1.8D);
                    }
                } else if (hitAndRunTicks > 0) {
                    // After impact: flee briefly with reduced speed so wizards don't over-disengage.
                    Vec3 away = wizard.position().subtract(target.position());
                    if (away.lengthSqr() < 1.0E-4) {
                        away = new Vec3(1, 0, 0);
                    }
                    away = away.normalize();
                    wizard.setDeltaMovement(away.x * 1.6D, 0.12D, away.z * 1.6D);
                    data.putInt("hogwartsmagic:tenebris_hit_and_run_ticks", hitAndRunTicks - 1);
                }
            }

// Find ground Y dynamically
            net.minecraft.world.level.Level world = wizard.level();
            double y = wizard.getY();
            while (y > 0 && world.isEmptyBlock(new net.minecraft.core.BlockPos((int) wizard.getX(), (int) (y - 1), (int) wizard.getZ()))) {
                y -= 1;
            }
            double groundY = y;

// Cap flight height 5 blocks above ground
            double maxY = groundY + 5;
            double currentY = wizard.getY();

// Smooth descent logic
            if (currentY > maxY) {
                wizard.setDeltaMovement(wizard.getDeltaMovement().x, -0.3, wizard.getDeltaMovement().z);
            } else if (currentY > groundY + 1) {
                wizard.setDeltaMovement(wizard.getDeltaMovement().x, -0.1, wizard.getDeltaMovement().z);
            } else {
                wizard.setDeltaMovement(wizard.getDeltaMovement().x, 0, wizard.getDeltaMovement().z);
                wizard.setPos(wizard.getX(), groundY, wizard.getZ());
            }

            // No generic AoE damage while flying; impact is now intentional touch-only ram.

        } else if (entity instanceof com.hogwartsmagic.entity.StudentEntity student) {
            // Students also get flight form but with less aggressive behavior
            student.setFlying(true);
            student.setNoGravity(true);
            student.getPersistentData().putDouble("hogwartsmagic:tenebris_startY", student.getY());
            // speed boost while in flight
            student.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 10, 4, false, false, false));
            // invisibility during flight
            if (!student.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                student.setInvisible(true);
                student.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 40, 0, false, false, false));
            }

            LivingEntity target = student.getTarget();
            if (target != null) {
                net.minecraft.world.phys.Vec3 targetPos = target.getEyePosition(1.0f);
                net.minecraft.world.phys.Vec3 studentPos = student.position();
                net.minecraft.world.phys.Vec3 dir = studentPos.subtract(targetPos).normalize();
                student.setDeltaMovement(dir.x * 1.8, student.getDeltaMovement().y, dir.z * 1.8);
            }

            // Find ground Y dynamically
            net.minecraft.world.level.Level world = student.level();
            double y = student.getY();
            while (y > 0 && world.isEmptyBlock(
                    new net.minecraft.core.BlockPos((int) student.getX(), (int) (y - 1), (int) student.getZ()))) {
                y -= 1; // Move down until we hit a block
            }
            double groundY = y;

            // Cap flight height 5 blocks above ground
            double maxY = groundY + 5;
            double currentY = student.getY();

            if (currentY > maxY) {
                student.setDeltaMovement(student.getDeltaMovement().x, -0.3, student.getDeltaMovement().z);
            } else if (currentY > groundY + 1) {
                student.setDeltaMovement(student.getDeltaMovement().x, -0.1, student.getDeltaMovement().z);
            } else {
                student.setDeltaMovement(student.getDeltaMovement().x, 0, student.getDeltaMovement().z);
                student.setPos(student.getX(), groundY, student.getZ());
            }
        }

        if (entity.level() instanceof ServerLevel server) {
            Vec3 velocity = entity.getDeltaMovement();

            double baseX = entity.getX();
            double baseY = entity.getY() + entity.getBbHeight() / 2;
            double baseZ = entity.getZ();

            for (int i = 0; i < 30; i++) {
                double offsetX = (entity.getRandom().nextDouble() - 0.5) * 0.8;
                double offsetY = (entity.getRandom().nextDouble() - 0.3) * 1.5;
                double offsetZ = (entity.getRandom().nextDouble() - 0.5) * 0.8;

                server.sendParticles(
                    ParticleTypes.SQUID_INK,
                    baseX + offsetX,
                    baseY + offsetY,
                    baseZ + offsetZ,
                    1,
                    -velocity.x * 0.3,
                    0.05,
                    -velocity.z * 0.3,
                    0
                );

                if (i % 3 == 0) {
                    server.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        baseX + offsetX,
                        baseY + offsetY,
                        baseZ + offsetZ,
                        1,
                        0, 0.05, 0,
                        0
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewTarget() instanceof Player player) {
            net.minecraft.nbt.CompoundTag disguise = PolyjuiceData.getActive(player);
            if (PolyjuiceData.isMobSample(disguise)) {
                net.minecraft.world.entity.EntityType<?> disguiseType = PolyjuiceData.getEntityType(disguise).orElse(null);
                if (disguiseType != null) {
                    if (shouldIgnoreDisguise(mob, disguiseType)) {
                        event.setCanceled(true);
                        mob.setTarget(null);
                        return;
                    }
                    if (!shouldForceAttackDisguise(mob, disguiseType)) {
                        return;
                    }
                }
            }
        }

        if ((event.getEntity() instanceof net.minecraft.world.entity.animal.IronGolem
                && event.getNewTarget() instanceof com.hogwartsmagic.entity.StudentEntity)
                || (event.getEntity() instanceof com.hogwartsmagic.entity.StudentEntity
                && event.getNewTarget() instanceof net.minecraft.world.entity.animal.IronGolem)) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity().getTags().contains("hogwartsmagic:allied")) {
            if (event.getNewTarget() != null && event.getNewTarget().getTags().contains("hogwartsmagic:allied")) {
                event.setCanceled(true);
            }
        }
    }

    private static void handleLumosLight(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide && entity.tickCount % 3 == 0) {
            java.util.UUID id = entity.getUUID();
            net.minecraft.core.BlockPos desired = entity.blockPosition().above();
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) entity.level();

            net.minecraft.core.BlockPos prev = LUMOS_POS.get(id);
            if (prev != null && !prev.equals(desired)) {
                if (level.getBlockState(prev).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                    level.setBlock(prev, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }

            // Place light at desired position if air or already light
            if (level.isEmptyBlock(desired) || level.getBlockState(desired).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                level.setBlock(desired, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15), 3);
                LUMOS_POS.put(id, desired);
            }
        }
    }

    private static void cleanupLumosLight(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide) {
            java.util.UUID id = entity.getUUID();
            net.minecraft.core.BlockPos prev = LUMOS_POS.remove(id);
            if (prev != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
                if (level.getBlockState(prev).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                    level.setBlock(prev, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void spawnProtegoParticles(net.minecraft.world.entity.LivingEntity entity) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() / 2.0;
        double z = entity.getZ();
        // Smaller, tighter bubble radius
        double radius = entity.getBbWidth() * 0.7;
        if (radius < 0.5) radius = 0.5; // ensure it's not invisible on tiny mobs
        
        // Dense spherical bubble (double particles to 60)
        for (int i = 0; i < 60; i++) {
            double u = Math.random();
            double v = Math.random();
            double theta = 2 * Math.PI * u;
            double phi = Math.acos(2 * v - 1);
            double dx = radius * Math.sin(phi) * Math.cos(theta);
            double dy = radius * Math.sin(phi) * Math.sin(theta);
            double dz = radius * Math.cos(phi);
            
            entity.level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, 
                x + dx, y + dy, z + dz, dx * 0.01, dy * 0.01, dz * 0.01);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
        ItemStack stack = event.getItemStack();
        if (MagicWandItem.isAnyWand(stack)) {
            SpellType selected = MagicWandItem.getSelectedSpell(stack);
            if (selected != null && (selected == com.hogwartsmagic.spell.SpellType.PROTEGO
                    || selected == com.hogwartsmagic.spell.SpellType.HEAL
                    || selected == com.hogwartsmagic.spell.SpellType.ARRESTO_MOMENTUM)) {
                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(true, false, false, false), PacketDistributor.SERVER.noArg());
            }
            event.getEntity().swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (event.getLevel().isClientSide && MagicWandItem.isAnyWand(stack)) {
            SpellType selected = MagicWandItem.getSelectedSpell(stack);
            if (selected != null && (selected == com.hogwartsmagic.spell.SpellType.PROTEGO
                    || selected == com.hogwartsmagic.spell.SpellType.HEAL
                    || selected == com.hogwartsmagic.spell.SpellType.ARRESTO_MOMENTUM)) {
                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(true, false, false, false), PacketDistributor.SERVER.noArg());
            }
            event.getEntity().swing(InteractionHand.MAIN_HAND);
            event.setCanceled(true);
        }
    }

    private static boolean handleWandUse(Player player, ItemStack heldItem) {
        if (player.level().isClientSide() && MagicWandItem.isAnyWand(heldItem)) {
            if (player.isShiftKeyDown()) {
                openMenu(player);
                return true;
            } else {
                SpellType selected = MagicWandItem.getSelectedSpell(heldItem);
                if (selected == SpellType.PROTEGO_MAXIMA && player.getXRot() <= -55.0F) {
                    com.hogwartsmagic.event.ClientModEvents.setProtegoMaximaChannelActive(true);
                    SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                    return true;
                }
                if (selected == SpellType.FIENDFYRE) {
                    if (fiendfyreUseLatched) {
                        return true;
                    }
                    fiendfyreUseLatched = true;
                    com.hogwartsmagic.event.ClientModEvents.startFiendfyreHold();
                    return true;
                }
                if (selected == SpellType.WINGARDIUM_LEVIOSA) {
                    if (wingardiumUseLatched) {
                        return true;
                    }
                    wingardiumUseLatched = true;
                    com.hogwartsmagic.event.ClientModEvents.startWingardiumHold();
                    SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
                    player.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
                if (selected == SpellType.IMPERIO) {
                    if (imperioUseLatched) {
                        return true;
                    }
                    imperioUseLatched = true;
                    com.hogwartsmagic.event.ClientModEvents.startImperioHold();
                    SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
                    player.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
                if (selected == SpellType.SERPENSORTIA) {
                    if (serpensortiaUseLatched) {
                        return true;
                    }
                    serpensortiaUseLatched = true;
                }
                
                // Client-side mana check for immediate feedback
                if (selected != null) {
                    net.minecraft.nbt.CompoundTag modData = player.getPersistentData().getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
                    float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : Config.getBaseMaxMana();
                    int requiredMana = Math.max(1, Math.round(selected.getManaCost() * MagicWandItem.getManaCostMultiplier(heldItem)));
                    if (!player.isCreative() && currentMana < requiredMana) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                        return true;
                    }
                }

                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
                player.swing(InteractionHand.MAIN_HAND);
                
                // Break Protego on cast! Server side will handle actual spell logic, but we can do visuals here if we want, or rely on server syncing.
                if (player.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
                    if (selected != null && selected != SpellType.HEAL && selected != SpellType.PROTEGO) {
                         // Note: Real removal must happen on server. The CastSpellPacket handler should remove it.
                    }
                }
                
                return true;
            }
        }
        return false;
    }

    private static void openMenu(Player player) {
        if (player.level().isClientSide) {
            com.hogwartsmagic.util.ClientSideBridge.openSpellMenu(player);
        }
    }

    public static void resetUseLatches(boolean resetSerpensortia, boolean resetFiendfyre, boolean resetWingardium, boolean resetImperio) {
        if (resetSerpensortia) {
            serpensortiaUseLatched = false;
        }
        if (resetFiendfyre) {
            fiendfyreUseLatched = false;
        }
        if (resetWingardium) {
            wingardiumUseLatched = false;
        }
        if (resetImperio) {
            imperioUseLatched = false;
        }
    }

    private static boolean handleWardInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(ExampleMod.WARD.get())) {
            return false;
        }
        if (event.getLevel().isClientSide()) {
            return true;
        }
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof CaveInimicumWardBlockEntity ward)) {
            return false;
        }

        if (serverPlayer.isShiftKeyDown()) {
            if (ward.isActive()) {
                ward.showOutlinePreview();
                serverPlayer.displayClientMessage(Component.literal("Ward is active - showing current outline"), true);
            } else {
                int size = ward.cycleConfiguredSize();
                serverPlayer.displayClientMessage(Component.literal("Ward size set to " + size + "x" + size + "x" + size), true);
            }
            return true;
        }

        if (event.getLevel() instanceof ServerLevel serverLevel && ward.activatePreparedModes(serverLevel, serverPlayer)) {
            ward.showOutlinePreview();
            serverPlayer.displayClientMessage(Component.literal("Ward activated"), true);
            return true;
        }

        ward.showOutlinePreview();
        serverPlayer.displayClientMessage(ward.getStatusMessage(), true);
        return true;
    }

}
