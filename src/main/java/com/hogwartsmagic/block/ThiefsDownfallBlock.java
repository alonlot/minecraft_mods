package com.hogwartsmagic.block;

import com.hogwartsmagic.item.InvisibilityCloakItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Thief's Downfall: the fall of water the goblins keep across the line, which washes away every
 * enchantment and concealment anyone passing through is wearing.
 *
 * <p>What it takes is deliberately not everything. It strips the <b>Polyjuice</b>, the
 * <b>invisibility cloak</b>, and <b>every potion effect</b> - including the Fire Resistance a
 * careful thief was relying on, which is the point. It does <b>not</b> touch item enchantments.
 * That is the film-accurate reading and it is still the wrong call: silently deleting somebody's
 * Mending pickaxe because they rode a cart is how you end a save file.
 *
 * <p>It also does not throw you out of the cart. Being killed by a set piece you had no way to
 * avoid is a bad death; you get blinded and soaked for a second and the cavern opens up instead.
 *
 * <p>You hear it and see the mist a good way before you see the water, which is the real warning -
 * the text is only there for anyone who missed both.
 */
public class ThiefsDownfallBlock extends Block {

    /** One pass per this many ticks, so a curtain several blocks thick fires once. */
    private static final int COOLDOWN = 60;
    private static final String WASHED_KEY = "hogwartsmagic:downfall_washed";

    public ThiefsDownfallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)) {
            return;
        }
        wash(server, pos, living);
    }

    /**
     * Called both from walking into it and, explicitly, from the cart passing the waypoint - a
     * cart at 31 m/s steps clean over a one-block curtain in a single tick and never touches it.
     */
    public static void wash(ServerLevel level, BlockPos pos, LivingEntity victim) {
        long now = level.getGameTime();
        if (now - victim.getPersistentData().getLong(WASHED_KEY) < COOLDOWN) {
            return;
        }
        victim.getPersistentData().putLong(WASHED_KEY, now);

        boolean tookSomething = !victim.getActiveEffects().isEmpty();
        victim.removeAllEffects();

        if (victim instanceof ServerPlayer player) {
            if (PolyjuiceData.hasActive(player)) {
                PolyjuiceData.clearAll(player);
                SpellPackets.CHANNEL.send(
                        new SpellPackets.SyncPolyjuiceDisguisePacket(player.getUUID(),
                                new net.minecraft.nbt.CompoundTag()),
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
                tookSomething = true;
            }
            InvisibilityCloakItem.stopCloak(player, false);
            player.displayClientMessage(Component.translatable(
                    tookSomething
                            ? "block.hogwartsmagic.thiefs_downfall.washed"
                            : "block.hogwartsmagic.thiefs_downfall.nothing"), true);
        }

        // Soaked and blind for a moment rather than thrown: you are very often on a cart.
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 24, 0, false, false, true));
        victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 60));

        level.sendParticles(ParticleTypes.SPLASH, victim.getX(), victim.getY() + 1.0D,
                victim.getZ(), 40, 0.5D, 0.8D, 0.5D, 0.2D);
        level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + 1.2D,
                victim.getZ(), 12, 0.4D, 0.6D, 0.4D, 0.02D);
        level.playSound(null, pos, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS,
                1.4F, 0.7F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Mist and noise, so it is heard and seen well before it is reached.
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0D, -0.1D, 0.0D);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.FALLING_WATER,
                    pos.getX() + random.nextDouble(), pos.getY(), pos.getZ() + random.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(),
                    SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.BLOCKS, 0.35F, 0.9F, false);
        }
    }
}
