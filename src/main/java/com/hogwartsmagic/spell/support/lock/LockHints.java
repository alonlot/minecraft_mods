package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Revelio's answer to a piston door: glowing outlines and floating labels on what to flip, press, turn or
 * fill, for 15 seconds. They are vanilla display entities, so no client code; any that survive a restart
 * are refused when they load. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockHints {
    public static final String TAG = "hogwartsmagic_lock_hint";
    /** Put on a real item frame while it glows, so a frame saved glowing is cleaned up when it loads. */
    public static final String GLOW_TAG = "hogwartsmagic_lock_glow";
    private static final int GLOW = 0x55FFFF;

    private record Shown(ResourceKey<Level> dimension, UUID entity, long until, boolean frame) {
    }

    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final Set<UUID> LIVE = new HashSet<>();

    private LockHints() {
    }

    public static void show(ServerLevel level, CircuitTracer.TracedLock lock, LockSolver.Solution s) {
        long until = level.getGameTime() + LockRules.HINT_TICKS;
        List<CircuitTracer.InputSite> sites = lock.inputs();
        for (int i = 0; i < s.set().length; i++) {
            CircuitTracer.InputSite site = sites.get(s.set()[i]);
            mark(level, site, settingLabel(level, site, s.setTo()[i]), until);
        }
        Map<Integer, List<Integer>> orders = new LinkedHashMap<>();
        for (int i = 0; i < s.presses().length; i++) {
            orders.computeIfAbsent(s.presses()[i], k -> new ArrayList<>()).add(i + 1);
        }
        for (Map.Entry<Integer, List<Integer>> e : orders.entrySet()) {
            CircuitTracer.InputSite site = sites.get(e.getKey());
            String list = e.getValue().stream().map(String::valueOf).collect(Collectors.joining(", "));
            String key = site.block() instanceof BasePressurePlateBlock ? "step"
                    : site.block() instanceof SculkSensorBlock ? "sound" : "press";
            mark(level, site, Component.translatable("message.hogwartsmagic.lock.hint." + key, list), until);
        }
    }

    private static Component settingLabel(ServerLevel level, CircuitTracer.InputSite site, int to) {
        String p = "message.hogwartsmagic.lock.hint.";
        if (site.kind() == LockCircuit.LEVER) {
            return Component.translatable(p + (to > 0 ? "lever_on" : "lever_off"));
        }
        if (site.source() == CircuitTracer.InputSite.FRAME) {
            if (to == 0) {
                return Component.translatable(p + "frame_empty");
            }
            // Turns are counted from where the frame stands now; an empty frame starts at 0 once an item is in.
            int turns = to - 1;
            if (site.frame() != null && level.getEntity(site.frame()) instanceof ItemFrame frame
                    && !frame.getItem().isEmpty()) {
                turns = Math.floorMod((to - 1) - frame.getRotation(), 8);
            }
            return Component.translatable(p + "frame", turns);
        }
        if (site.source() == CircuitTracer.InputSite.CONTAINER) {
            if (to == 0) {
                return Component.translatable(p + "container_empty");
            }
            int[] items = LockRules.itemsForSignal(to, site.slots());
            return Component.translatable(p + "container", items[0], items[1]);
        }
        return Component.translatable(p + "signal", to);
    }

    private static void mark(ServerLevel level, CircuitTracer.InputSite site, Component label, long until) {
        Vec3 labelAt;
        if (site.frame() != null && level.getEntity(site.frame()) instanceof ItemFrame frame) {
            frame.setGlowingTag(true);
            frame.addTag(GLOW_TAG);
            SHOWN.add(new Shown(level.dimension(), frame.getUUID(), until, true));
            labelAt = frame.position().add(0, 0.5, 0);
        } else {
            BlockPos pos = site.pos();
            if (!level.isLoaded(pos)) {
                return;
            }
            CompoundTag outline = new CompoundTag();
            outline.putString("id", "minecraft:block_display");
            outline.put("block_state", NbtUtils.writeBlockState(level.getBlockState(pos)));
            outline.putBoolean("Glowing", true);
            outline.putInt("glow_color_override", GLOW);
            spawn(level, outline, Vec3.atLowerCornerOf(pos), until);
            labelAt = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        }
        CompoundTag text = new CompoundTag();
        text.putString("id", "minecraft:text_display");
        text.putString("text", Component.Serializer.toJson(label, level.registryAccess()));
        text.putString("billboard", "center");
        text.putBoolean("see_through", true);
        text.putInt("background", 0x90000000);
        spawn(level, text, labelAt, until);
        level.sendParticles(ParticleTypes.GLOW, labelAt.x, labelAt.y - 0.3, labelAt.z, 10, 0.25, 0.25, 0.25, 0.01);
    }

    private static void spawn(ServerLevel level, CompoundTag tag, Vec3 at, long until) {
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
            return e;
        });
        if (entity == null) {
            return;
        }
        LIVE.add(entity.getUUID());
        if (level.addFreshEntity(entity)) {
            SHOWN.add(new Shown(level.dimension(), entity.getUUID(), until, false));
        } else {
            LIVE.remove(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SHOWN.isEmpty() || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Shown> it = SHOWN.iterator();
        while (it.hasNext()) {
            Shown shown = it.next();
            if (shown.dimension() != level.dimension() || now < shown.until()) {
                continue;
            }
            it.remove();
            if (shown.frame() && holdsFrame(shown.entity())) {
                continue; // a newer hint still glows this frame; its own expiry lets go
            }
            clear(level, shown);
        }
    }

    private static boolean holdsFrame(UUID frame) {
        for (Shown other : SHOWN) {
            if (other.frame() && other.entity().equals(frame)) {
                return true;
            }
        }
        return false;
    }

    private static void clear(ServerLevel level, Shown shown) {
        Entity entity = level.getEntity(shown.entity());
        if (shown.frame()) {
            if (entity != null) {
                entity.setGlowingTag(false);
                entity.removeTag(GLOW_TAG);
            }
        } else {
            LIVE.remove(shown.entity());
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /** A hint saved with the world before a restart is never let back in; a frame left glowing is put right. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (entity.getTags().contains(TAG) && !LIVE.contains(entity.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (entity.getTags().contains(GLOW_TAG)) {
            UUID id = entity.getUUID();
            boolean live = false;
            for (Shown shown : SHOWN) {
                if (shown.frame() && shown.entity().equals(id)) {
                    live = true;
                    break;
                }
            }
            if (!live) {
                entity.setGlowingTag(false);
                entity.removeTag(GLOW_TAG);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Shown shown : SHOWN) {
            ServerLevel level = event.getServer().getLevel(shown.dimension());
            if (level != null) {
                clear(level, shown);
            }
        }
        SHOWN.clear();
        LIVE.clear();
    }
}
