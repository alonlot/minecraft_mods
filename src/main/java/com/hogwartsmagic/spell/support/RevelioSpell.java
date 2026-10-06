package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.InvisibilityCloakItem;
import com.hogwartsmagic.locket.LocketCaves;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Revelio at Master: everything hidden within 20 blocks of the impact shows itself for 5 seconds.
 * A Polyjuice disguise is not broken (a direct hit still breaks it, as at every rank); instead every
 * client watching that player draws the real player for the window. A worn Invisibility Cloak drops
 * for the window. Invisible mobs are dispelled. Server only.
 */
public final class RevelioSpell {
    private RevelioSpell() {
    }

    public static int revealHidden(ServerLevel level, Vec3 center, @Nullable Entity caster) {
        LocketCaves.reveal(level, center, LocketCaves.REVEAL_RADIUS);
        int rank = DisableSupport.casterRank(caster, SpellType.REVELIO);
        if (!RevelioRules.revealsDisguises(rank)) {
            return 0;
        }
        Entity owner = CombatEffects.responsible(caster);
        double radius = RevelioRules.MASTER_RADIUS;
        long now = level.getGameTime();
        int revealed = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e != owner && e.distanceToSqr(center) <= radius * radius)) {
            boolean shown = false;
            if (living instanceof ServerPlayer player) {
                if (PolyjuiceData.hasActive(player)) {
                    SpellPackets.CHANNEL.send(new SpellPackets.RevealDisguisePacket(player.getUUID(), RevelioRules.REVEAL_TICKS),
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
                    shown = true;
                }
                if (InvisibilityCloakItem.isCloakActive(PlayerMagicData.getModData(player), now)) {
                    ModEvents.revealInvisibilityCloak(player, RevelioRules.REVEAL_TICKS);
                    shown = true;
                }
            } else {
                shown = ModEvents.dispelIllusionsAndInvisibility(living);
            }
            if (shown) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, RevelioRules.REVEAL_TICKS, 0, false, false));
                level.sendParticles(ParticleTypes.GLOW, living.getX(), living.getY() + 1.0, living.getZ(),
                        12, 0.3, 0.5, 0.3, 0.02);
                revealed++;
            }
        }
        if (revealed > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        return revealed;
    }
}
