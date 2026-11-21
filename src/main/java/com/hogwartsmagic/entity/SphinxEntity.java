package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.tomb.TombCurses;
import com.hogwartsmagic.util.ModAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Sphinx who keeps a desert tomb: a lion's body, a woman's head in a striped headdress.
 *
 * <p>She lies on the paving in front of the tomb and does nothing until spoken to. Right-click her
 * and she asks a riddle, with three answers to click in chat.
 *
 * <ul>
 *   <li><b>Right:</b> she shows every curse in the tomb at once (40 blocks, three minutes, against
 *       Revelio's eight blocks and one minute) and gives a gift. Once per player per Sphinx.</li>
 *   <li><b>Wrong:</b> she stands and attacks that player until they die or get 24 blocks away. Then
 *       she goes back to her place and lies down. She does not ask an angry question twice.</li>
 *   <li><b>No answer:</b> nothing. A riddle lapses after a minute.</li>
 * </ul>
 *
 * <p>She never wanders off: a guardian that strolls away from her door is not guarding it. The
 * riddles are old folk riddles, the first of them the Sphinx's own from the Greek story.
 */
public class SphinxEntity extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> ANGRY =
            SynchedEntityData.defineId(SphinxEntity.class, EntityDataSerializers.BOOLEAN);

    /** Riddles in the lang file as sphinx.hogwartsmagic.riddle.N, answers .N.0 (right), .N.1, .N.2. */
    public static final int RIDDLES = 9;
    public static final int ANSWER_RANGE = 8;
    private static final int PENDING_TICKS = 1200;
    private static final int GIFT_RADIUS = 40;
    private static final int GIFT_TICKS = 3600;
    private static final double CALM_DISTANCE = 24.0D;
    private static final ResourceKey<LootTable> GIFT = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gameplay/sphinx_gift"));

    /** A riddle asked and not answered yet: which one, the order its answers were shown in, and until when. */
    private record Pending(int riddle, int[] order, long until) {}

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<UUID> solved = new HashSet<>();
    private BlockPos home;

    /** Client only: 1 lying, 0 standing, eased between. */
    private float lie = 1.0F;
    private float lieO = 1.0F;

    public SphinxEntity(EntityType<? extends SphinxEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.STEP_HEIGHT, 1.1D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ANGRY, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        goalSelector.addGoal(2, new ReturnHomeGoal());
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public boolean isAngry() {
        return entityData.get(ANGRY);
    }

    /** For the model: how far she is lying down, 0..1. */
    public float lieAmount(float partialTick) {
        return Mth.lerp(partialTick, lieO, lie);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            lieO = lie;
            boolean resting = !isAngry() && getDeltaMovement().horizontalDistanceSqr() < 1.0E-4D;
            lie = Mth.approach(lie, resting ? 1.0F : 0.0F, 0.08F);
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        LivingEntity target = getTarget();
        boolean angry = target != null && target.isAlive() && distanceTo(target) < CALM_DISTANCE
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
        if (!angry && target != null) {
            setTarget(null);
        }
        if (angry != isAngry()) {
            entityData.set(ANGRY, angry);
        }
        if (tickCount % 200 == 0) {
            long now = level().getGameTime();
            pending.values().removeIf(p -> p.until() < now);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (isAngry()) {
            return InteractionResult.PASS;
        }
        if (solved.contains(sp.getUUID())) {
            sp.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.solved").withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        ask(sp);
        return InteractionResult.SUCCESS;
    }

    /** Asks {@code player} a riddle, with its answers shuffled into clickable lines. */
    public void ask(ServerPlayer player) {
        Pending old = pending.get(player.getUUID());
        int riddle = old != null ? old.riddle() : getRandom().nextInt(RIDDLES);
        int[] order = old != null ? old.order() : shuffled();
        pending.put(player.getUUID(), new Pending(riddle, order, level().getGameTime() + PENDING_TICKS));
        lookAt(player, 30.0F, 30.0F);
        playSound(SoundEvents.CAT_PURR, 1.2F, 0.5F);
        player.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.speaks").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.riddle." + riddle)
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        for (int shown = 0; shown < 3; shown++) {
            String command = "/sphinx answer " + getId() + " " + shown;
            MutableComponent line = Component.literal("  [" + (char) ('A' + shown) + "] ")
                    .append(Component.translatable("sphinx.hogwartsmagic.riddle." + riddle + "." + order[shown]))
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("sphinx.hogwartsmagic.click"))));
            player.sendSystemMessage(line);
        }
    }

    private int[] shuffled() {
        int[] order = {0, 1, 2};
        for (int i = 2; i > 0; i--) {
            int j = getRandom().nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        return order;
    }

    /**
     * The answer the player clicked, as its position in the list they were shown.
     *
     * @return false when there is nothing to answer (no riddle asked, too far, lapsed, angry)
     */
    public boolean answer(ServerPlayer player, int shown) {
        Pending p = pending.get(player.getUUID());
        if (p == null || isAngry() || shown < 0 || shown > 2 || p.until() < level().getGameTime()
                || distanceTo(player) > ANSWER_RANGE) {
            return false;
        }
        pending.remove(player.getUUID());
        if (p.order()[shown] == 0) {
            reward(player);
        } else {
            punish(player);
        }
        return true;
    }

    /** Test hook: the index on screen of the right answer for this player's pending riddle, or -1. */
    public int rightAnswerShownAt(ServerPlayer player) {
        Pending p = pending.get(player.getUUID());
        if (p == null) {
            return -1;
        }
        for (int i = 0; i < 3; i++) {
            if (p.order()[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private void reward(ServerPlayer player) {
        solved.add(player.getUUID());
        ServerLevel level = (ServerLevel) level();
        player.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.right").withStyle(ChatFormatting.GOLD));
        int shown = TombCurses.reveal(level, position(), GIFT_RADIUS, GIFT_TICKS);
        if (shown > 0) {
            player.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.curses_shown", shown)
                    .withStyle(ChatFormatting.GRAY));
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(GIFT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 2.0, getZ(), 60, 0.8, 0.6, 0.8, 0.8);
        level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5F, 0.8F);
        ModAdvancements.award(player, "sphinx_riddle");
    }

    private void punish(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.wrong").withStyle(ChatFormatting.RED));
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 1.2F);
        setTarget(player);
        entityData.set(ANGRY, true);
    }

    // --- sound ------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return isAngry() ? SoundEvents.RAVAGER_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.25F + (getRandom().nextFloat() - 0.5F) * 0.1F;
    }

    // --- save ---------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
        ListTag list = new ListTag();
        for (UUID id : solved) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Solved", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Home").ifPresent(p -> home = p);
        solved.clear();
        for (Tag t : tag.getList("Solved", Tag.TAG_INT_ARRAY)) {
            solved.add(NbtUtils.loadUUID(t));
        }
    }

    /** Back to her place when she is not fighting, and lie down there. */
    private final class ReturnHomeGoal extends Goal {
        ReturnHomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return home != null && getTarget() == null && home.distToCenterSqr(position()) > 4.0D;
        }

        @Override
        public void start() {
            getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
        }

        @Override
        public boolean canContinueToUse() {
            return getTarget() == null && !getNavigation().isDone();
        }
    }
}
