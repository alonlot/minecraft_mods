package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;

/**
 * Dark Magic costs (Phase 2), the server side. Every cost reads the tier through DarkMagicData.getTier (so
 * enableDarkMagic off reads Clean and turns them all off); the numbers live in DarkCostRules. NPC goals check
 * darkestOnlineTier first, a cache refreshed at most once a second and dropped on every DarkMagicData.sync, so no NPC
 * searches anything while nobody online is dark enough to matter.
 */
public final class DarkCosts {
    /** Lost: -2 max health. Its own id, so it stacks with the Horcrux cost and the Hufflepuff bonus. */
    public static final ResourceLocation LOST_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dark_magic_lost_health");

    private static WeakReference<MinecraftServer> presenceServer = new WeakReference<>(null);
    private static int presenceAt;
    private static int darkestOnline = DarkRules.CLEAN;
    private static volatile boolean presenceDirty = true;

    private DarkCosts() {
    }

    /** A player's effective tier (Clean with enableDarkMagic off); Clean for anything that is not a server player. */
    public static int tierOf(Entity entity) {
        return entity instanceof ServerPlayer player ? DarkMagicData.getTier(player) : DarkRules.CLEAN;
    }

    /** The darkest effective tier among online players, cached for up to a second. Clean on the client. */
    public static int darkestOnlineTier(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return DarkRules.CLEAN;
        }
        int now = server.getTickCount();
        if (presenceDirty || presenceServer.get() != server || DarkCostRules.presenceStale(now, presenceAt)) {
            presenceDirty = false;
            int darkest = DarkRules.CLEAN;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                darkest = Math.max(darkest, DarkMagicData.getTier(player));
            }
            darkestOnline = darkest;
            presenceAt = now;
            presenceServer = new WeakReference<>(server);
        }
        return darkestOnline;
    }

    /** True when someone online is at or past the lowest tier this cost applies at. The NPC goals' first check. */
    public static boolean anyoneOnlineAt(Level level, DarkCostRules.Cost cost) {
        return darkestOnlineTier(level) >= cost.fromTier();
    }

    /** From DarkMagicData.sync: a tier may have changed, so the next NPC check recomputes. */
    public static void invalidatePresence() {
        presenceDirty = true;
    }

    /** The corporeal Patronus chance for this caster (the body that casts it). */
    public static float corporealPatronusChance(ServerPlayer caster, float base) {
        return DarkCostRules.corporealPatronusChance(base, DarkMagicData.getTier(caster));
    }

    /** The Patronus shield upkeep multiplier for this caster: 1.5 from Corrupted. */
    public static float shieldUpkeepMultiplier(Entity caster) {
        return DarkCostRules.shieldUpkeepMultiplier(tierOf(caster));
    }

    /**
     * Adds or removes the Lost -2 max health so it matches the tier. Does nothing when it already matches. Health
     * above the new max is lowered to it (never below 1, never on a dead player); removing the cost never changes
     * health, so it can never kill.
     */
    public static void refreshMaxHealth(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double wanted = DarkCostRules.maxHealthDelta(DarkMagicData.getTier(player));
        AttributeModifier present = attribute.getModifier(LOST_HEALTH_MODIFIER_ID);
        double have = present == null ? 0.0D : present.amount();
        if (have == wanted) {
            return;
        }
        attribute.removeModifier(LOST_HEALTH_MODIFIER_ID);
        if (wanted != 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(LOST_HEALTH_MODIFIER_ID, wanted,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        float health = player.getHealth();
        float clamped = DarkCostRules.healthAfterMaxChange(health, player.getMaxHealth());
        if (clamped != health) {
            player.setHealth(clamped);
        }
    }

    /**
     * From ModEvents.onPlayerTick (server, END phase). Returns on 9 ticks out of 10 before any read. Faint smoke around
     * a Corrupted or Lost player, sent to everyone within 32 blocks; none while invisible, spectating or dead.
     */
    public static void tickSmoke(ServerPlayer player) {
        if (!DarkCostRules.dueToSmoke(player.tickCount, player.getId())) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isInvisible()) {
            return;
        }
        int count = DarkCostRules.smokeCount(DarkMagicData.getTier(player));
        if (count <= 0) {
            return;
        }
        player.serverLevel().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(),
                count, 0.3D, 0.5D, 0.3D, 0.005D);
    }
}
