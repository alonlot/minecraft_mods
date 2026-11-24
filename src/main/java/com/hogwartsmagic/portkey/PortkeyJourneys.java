package com.hogwartsmagic.portkey;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.capture.CaptureHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every Portkey journey on the server, one per holder: the charge (use held for three seconds), the hook (the
 * group within two blocks is gathered and the Portkey goes on its cooldown), the held transit (the travellers are
 * kept still inside the implosion and the tunnel while the far end loads), the move, and the landing. An Old
 * Portkey is spent at the move and crumbles in the hand two seconds later.
 *
 * <p>The far end is loaded through {@link PortkeyLanding.Request} (a ticket, polled each tick, never a blocking load),
 * started at the beginning of the charge so most journeys find it ready at the hook. If it never comes, after ten
 * seconds the travellers are dropped back where they were. Logging out, dying or changing dimension drops the
 * journey and its ticket; nothing here can strand anyone. While in transit a traveller cannot be hurt.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class PortkeyJourneys {

    /** A traveller who strays further than this from where they were hooked is put back (they are in the tunnel). */
    private static final double HOLD_SLACK = 0.75;
    /** Pets land on a ring this wide round the holder. */
    private static final double PET_RING = 1.2;
    /** Slowness II for the stagger. */
    private static final int LANDING_SLOW_AMPLIFIER = 1;

    private static final Map<UUID, Journey> JOURNEYS = new HashMap<>();
    /** Everyone in a tunnel right now (hooked, not yet landed). */
    private static final Set<UUID> TRAVELLING = new HashSet<>();
    /** Spent Old Portkeys waiting to crumble: holder -> server tick it crumbles at. */
    private static final Map<UUID, Crumble> CRUMBLES = new HashMap<>();

    private PortkeyJourneys() {
    }

    private record Traveller(Entity entity, Vec3 hold) {
    }

    private record Crumble(ServerPlayer holder, int dueTick) {
    }

    private static final class Journey {
        final ServerPlayer holder;
        final ServerLevel origin;
        final InteractionHand hand;
        final boolean requireUse;
        final boolean old;
        final ItemStack stack;
        final PortkeyLanding.Request request;
        final List<Traveller> travellers = new ArrayList<>();
        int age;
        boolean hooked;
        int transitAge;

        Journey(ServerPlayer holder, InteractionHand hand, boolean requireUse, boolean old, ItemStack stack,
                PortkeyLanding.Request request) {
            this.holder = holder;
            this.origin = holder.serverLevel();
            this.hand = hand;
            this.requireUse = requireUse;
            this.old = old;
            this.stack = stack;
            this.request = request;
        }
    }

    // ---- what other code calls --------------------------------------------------------------------------------

    /**
     * Start a charge in {@code holder}'s {@code hand}. With {@code requireUse} the charge needs use held all the way
     * (the item path); without it the charge runs on its own (tests). False, with a message where it helps, when it
     * cannot start.
     */
    public static boolean begin(ServerPlayer holder, InteractionHand hand, boolean requireUse) {
        UUID id = holder.getUUID();
        if (JOURNEYS.containsKey(id) || TRAVELLING.contains(id) || !holder.isAlive() || holder.isSpectator()
                || ModEvents.isHeldInPlace(holder) || PensieveSessions.isViewer(holder)) {
            return false;
        }
        ItemStack stack = holder.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortkeyItem)) {
            return false;
        }
        boolean old = stack.getItem() instanceof OldPortkeyItem;
        ServerLevel from = holder.serverLevel();
        String fromDim = from.dimension().location().toString();
        PortkeyLanding.Request request;
        if (old) {
            if (PortkeyData.spent(stack)) {
                return false;
            }
            if (!PortkeyRules.oldWorksIn(fromDim, from.dimensionType().hasSkyLight(),
                    from.dimensionType().hasCeiling())) {
                tell(holder, "old_refused");
                return false;
            }
            request = PortkeyLanding.Request.old(from, holder.blockPosition(), holder.getRandom());
        } else {
            if (PortkeyRules.cooldownLeft(from.getGameTime(), PortkeyData.readyAt(stack)) > 0L
                    || holder.getCooldowns().isOnCooldown(stack.getItem())) {
                return false;
            }
            PortkeyData.Binding binding = PortkeyData.binding(stack).orElse(null);
            if (binding == null) {
                return false;
            }
            String refusal = refusalMessage(PortkeyRules.refusal(fromDim, binding.dim().location().toString()));
            if (refusal != null) {
                tell(holder, refusal);
                return false;
            }
            ServerLevel target = holder.server.getLevel(binding.dim());
            if (target == null) {
                tell(holder, "gone");
                return false;
            }
            request = PortkeyLanding.Request.bound(target, binding.pos(), binding.yaw());
        }
        JOURNEYS.put(id, new Journey(holder, hand, requireUse, old, stack, request));
        PortkeyFx.charge(holder, old);
        return true;
    }

    /** Whether {@code entity} is inside a Portkey's tunnel right now. */
    public static boolean isTravelling(Entity entity) {
        return TRAVELLING.contains(entity.getUUID());
    }

    /** Whether {@code holder} has a journey charging or under way. */
    public static boolean hasJourney(UUID holder) {
        return JOURNEYS.containsKey(holder);
    }

    /** Drop {@code holder}'s journey (its ticket released, anyone in its tunnel dropped back). */
    public static void forget(UUID holder) {
        Journey journey = JOURNEYS.get(holder);
        if (journey != null) {
            drop(journey, null);
        }
    }

    /** How many journeys are charging or under way (for tests). */
    public static int liveJourneys() {
        return JOURNEYS.size();
    }

    // ---- the tick ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!JOURNEYS.isEmpty()) {
            for (Journey journey : new ArrayList<>(JOURNEYS.values())) {
                if (JOURNEYS.get(journey.holder.getUUID()) != journey) {
                    continue; // dropped by another journey this tick
                }
                if (journey.hooked) {
                    tickTransit(journey);
                } else {
                    tickCharge(journey);
                }
            }
        }
        if (!CRUMBLES.isEmpty()) {
            int now = event.getServer().getTickCount();
            Iterator<Map.Entry<UUID, Crumble>> it = CRUMBLES.entrySet().iterator();
            while (it.hasNext()) {
                Crumble c = it.next().getValue();
                if (now >= c.dueTick || c.holder.hasDisconnected()) {
                    it.remove();
                    crumble(c.holder);
                }
            }
        }
    }

    private static void tickCharge(Journey journey) {
        ServerPlayer holder = journey.holder;
        boolean gone = holder.isRemoved() || !holder.isAlive() || holder.hasDisconnected()
                || holder.level() != journey.origin;
        boolean letGo = journey.requireUse && (!holder.isUsingItem() || holder.getUsedItemHand() != journey.hand
                || holder.getUseItem() != journey.stack);
        if (gone || letGo || holder.getItemInHand(journey.hand) != journey.stack) {
            cancel(journey);
            return;
        }
        journey.request.poll(); // the far end loads while the boot charges
        journey.age++;
        if (journey.age >= PortkeyRules.CHARGE_TICKS) {
            hook(journey);
        }
    }

    /** The charge stops before the hook: no journey, no cooldown, the hum powers down. */
    private static void cancel(Journey journey) {
        JOURNEYS.remove(journey.holder.getUUID());
        journey.request.release();
        if (journey.holder.isAlive() && !journey.holder.isRemoved()) {
            PortkeyFx.cancel(journey.holder);
        }
    }

    private static void hook(Journey journey) {
        ServerPlayer holder = journey.holder;
        holder.stopUsingItem();
        if (!journey.old && holder.server.getLevel(journey.request.level().dimension()) != journey.request.level()) {
            tell(holder, "gone");
            cancel(journey);
            return;
        }
        PortkeyLanding.Request.Status status = journey.request.poll();
        if (status == PortkeyLanding.Request.Status.READY && journey.request.landing() == null) {
            tell(holder, journey.request.warded() ? "warded" : "fizzle");
            cancel(journey);
            return;
        }
        if (status == PortkeyLanding.Request.Status.FAILED) {
            tell(holder, journey.old ? "nowhere" : "sputter");
            cancel(journey);
            return;
        }

        journey.hooked = true;
        journey.travellers.add(new Traveller(holder, holder.position()));
        AABB around = holder.getBoundingBox().inflate(PortkeyRules.GROUP_RADIUS);
        for (LivingEntity e : journey.origin.getEntitiesOfClass(LivingEntity.class, around,
                e -> e != holder && e.isAlive())) {
            boolean player = e instanceof Player;
            boolean tamed = e instanceof OwnableEntity pet && pet.getOwnerUUID() != null;
            boolean sitting = e instanceof TamableAnimal t && t.isOrderedToSit();
            boolean viewer = e.isSpectator() || (e instanceof Player p && PensieveSessions.isViewer(p));
            PortkeyRules.Candidate c = new PortkeyRules.Candidate(e.distanceToSqr(holder), player, tamed, sitting,
                    ModEvents.isHeldInPlace(e), viewer, TRAVELLING.contains(e.getUUID()));
            if (PortkeyRules.travels(c)) {
                Journey own = JOURNEYS.get(e.getUUID());
                if (own != null && own != journey && !own.hooked) {
                    if (e instanceof ServerPlayer sp) {
                        sp.stopUsingItem();
                    }
                    cancel(own); // taken along by someone else's Portkey
                }
                journey.travellers.add(new Traveller(e, e.position()));
            }
        }
        for (Traveller t : journey.travellers) {
            TRAVELLING.add(t.entity.getUUID());
        }
        if (!journey.old) {
            long now = journey.origin.getGameTime();
            PortkeyData.setReadyAt(journey.stack, now + PortkeyRules.COOLDOWN_TICKS);
            holder.getCooldowns().addCooldown(journey.stack.getItem(), PortkeyRules.COOLDOWN_TICKS);
        }
        PortkeyFx.hook(journey.origin, holder.position(), holder);
        boolean crossDim = journey.request.level() != journey.origin;
        for (Traveller t : journey.travellers) {
            if (t.entity instanceof ServerPlayer sp) {
                PortkeyFx.transit(sp, crossDim, sp == holder, journey.old);
            }
        }
    }

    private static void tickTransit(Journey journey) {
        ServerPlayer holder = journey.holder;
        if (holder.isRemoved() || !holder.isAlive() || holder.hasDisconnected()
                || holder.level() != journey.origin) {
            drop(journey, null);
            return;
        }
        journey.transitAge++;
        // Hold everyone still inside the implosion while the far end loads.
        journey.travellers.removeIf(t -> {
            boolean lost = t.entity.isRemoved() || !t.entity.isAlive() || t.entity.level() != journey.origin;
            if (lost) {
                TRAVELLING.remove(t.entity.getUUID());
            }
            return lost;
        });
        for (Traveller t : journey.travellers) {
            Entity e = t.entity;
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
            e.fallDistance = 0.0f;
            if (e.position().distanceToSqr(t.hold) > HOLD_SLACK * HOLD_SLACK) {
                e.teleportTo(t.hold.x, t.hold.y, t.hold.z);
            }
        }
        if (journey.transitAge < PortkeyRules.TELEPORT_DELAY_TICKS) {
            return;
        }
        PortkeyLanding.Request.Status status = journey.request.poll();
        if (status == PortkeyLanding.Request.Status.READY) {
            if (journey.request.landing() == null) {
                drop(journey, journey.request.warded() ? "warded" : "fizzle");
            } else {
                move(journey);
            }
        } else if (status == PortkeyLanding.Request.Status.FAILED) {
            drop(journey, journey.old ? "nowhere" : "sputter");
        } else if (PortkeyRules.waitGivesUp(journey.transitAge - PortkeyRules.TELEPORT_DELAY_TICKS)) {
            drop(journey, "nowhere");
        }
    }

    /** Everyone is moved to the far end and lands. */
    private static void move(Journey journey) {
        ServerLevel target = journey.request.level();
        Vec3 land = journey.request.landing();
        ServerPlayer holder = journey.holder;
        float yaw = journey.old ? holder.getYRot() : journey.request.yaw();
        JOURNEYS.remove(holder.getUUID());
        journey.request.release();
        if (journey.old) {
            PortkeyData.markSpent(journey.stack);
        }
        List<Entity> pets = new ArrayList<>();
        int count = 0;
        for (Traveller t : journey.travellers) {
            Entity e = t.entity;
            TRAVELLING.remove(e.getUUID());
            e.stopRiding();
            e.ejectPassengers();
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer sp) {
                sp.teleportTo(target, land.x, land.y, land.z, yaw, sp.getXRot());
                sp.fallDistance = 0.0f;
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PortkeyRules.LANDING_SLOW_TICKS,
                        LANDING_SLOW_AMPLIFIER, false, false, true));
                CaptureHooks.markDiscontinuity(sp);
                count++;
            } else {
                pets.add(e);
            }
        }
        for (int i = 0; i < pets.size(); i++) {
            Entity pet = pets.get(i);
            Vec3 spot = petSpot(target, land, pet, i, pets.size());
            pet.teleportTo(target, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0f);
            count++;
        }
        PortkeyFx.arrive(target, land, count);
        if (journey.old) {
            CRUMBLES.put(holder.getUUID(), new Crumble(holder,
                    holder.server.getTickCount() + PortkeyRules.OLD_CRUMBLE_DELAY_TICKS));
        }
    }

    /** A pet's place on a ring round the landing, where it fits and has ground, else the landing itself. */
    private static Vec3 petSpot(ServerLevel target, Vec3 land, Entity pet, int index, int of) {
        double angle = Math.PI * 2.0 * index / Math.max(1, of) + 0.4;
        Vec3 ring = land.add(Math.cos(angle) * PET_RING, 0.0, Math.sin(angle) * PET_RING);
        Vec3 ground = PortkeyLanding.landingAt(target, net.minecraft.core.BlockPos.containing(ring));
        if (ground != null) {
            Vec3 spot = new Vec3(ring.x, ground.y, ring.z);
            if (target.noCollision(pet, pet.getType().getDimensions().makeBoundingBox(spot))) {
                return spot;
            }
        }
        return land;
    }

    /**
     * The journey ends without a landing: the ticket goes, everyone in the tunnel is let go where they stand, and a
     * Portkey's cooldown is given back (it never went). {@code message} is told to the holder, if any.
     */
    private static void drop(Journey journey, String message) {
        JOURNEYS.remove(journey.holder.getUUID());
        journey.request.release();
        for (Traveller t : journey.travellers) {
            TRAVELLING.remove(t.entity.getUUID());
            if (t.entity instanceof ServerPlayer sp && !sp.hasDisconnected()) {
                PortkeyFx.abort(sp);
            }
        }
        if (journey.hooked && !journey.old) {
            PortkeyData.setReadyAt(journey.stack, 0L);
            journey.holder.getCooldowns().removeCooldown(journey.stack.getItem());
        }
        if (!journey.hooked && journey.holder.isAlive() && !journey.holder.isRemoved()) {
            PortkeyFx.cancel(journey.holder);
        }
        if (message != null && !journey.holder.hasDisconnected()) {
            tell(journey.holder, message);
        }
    }

    /** A spent Old Portkey falls to dust in the hand. */
    private static void crumble(ServerPlayer holder) {
        boolean found = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = holder.getItemInHand(hand);
            if (s.getItem() instanceof OldPortkeyItem && PortkeyData.spent(s)) {
                s.shrink(1);
                found = true;
                break;
            }
        }
        if (!found) {
            var inventory = holder.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack s = inventory.getItem(i);
                if (s.getItem() instanceof OldPortkeyItem && PortkeyData.spent(s)) {
                    s.shrink(1);
                    found = true;
                    break;
                }
            }
        }
        if (found && !holder.hasDisconnected()) {
            PortkeyFx.crumble(holder);
        }
    }

    // ---- edges ------------------------------------------------------------------------------------------------

    /** Nobody inside a tunnel can be hurt (but /kill and the void still work). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!TRAVELLING.isEmpty() && !event.getEntity().level().isClientSide()
                && TRAVELLING.contains(event.getEntity().getUUID())
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Journey journey = JOURNEYS.get(id);
        if (journey != null) {
            drop(journey, null);
        }
        for (Journey j : new ArrayList<>(JOURNEYS.values())) {
            j.travellers.removeIf(t -> t.entity.getUUID().equals(id));
        }
        TRAVELLING.remove(id);
        Crumble c = CRUMBLES.remove(id);
        if (c != null && event.getEntity() instanceof ServerPlayer sp) {
            crumble(sp); // a spent Old Portkey never survives a logout
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Journey journey : new ArrayList<>(JOURNEYS.values())) {
            journey.request.release();
        }
        JOURNEYS.clear();
        TRAVELLING.clear();
        for (Crumble c : CRUMBLES.values()) {
            crumble(c.holder);
        }
        CRUMBLES.clear();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static String refusalMessage(PortkeyRules.Refusal refusal) {
        if (refusal == PortkeyRules.Refusal.INTO_MEMORY) {
            return "refused_memory";
        }
        if (refusal == PortkeyRules.Refusal.INTO_ALLEY) {
            return "refused_alley";
        }
        if (refusal == PortkeyRules.Refusal.OUT_OF_MEMORY) {
            return "refused_leave_memory";
        }
        return null;
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.portkey." + key)
                .withStyle(ChatFormatting.GRAY), true);
    }
}
