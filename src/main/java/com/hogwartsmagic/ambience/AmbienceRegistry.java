package com.hogwartsmagic.ambience;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ambient effects of one level (enchanted ceilings, floating candles, light rays, door leaves), saved with the
 * world and told to the clients of that level. The castle registers its own when it is furnished and the ceiling spell
 * registers a player's; the renderers read the client copy ({@code WorldAmbience}). Server thread only.
 */
public final class AmbienceRegistry extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = "hogwartsmagic_ambience";
    /** A guard against runaway registration; far above what a castle and a server of spell ceilings need. */
    public static final int MAX_EFFECTS = 4096;

    private final Map<UUID, AmbientEffect> effects = new LinkedHashMap<>();

    public static AmbienceRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(AmbienceRegistry::new, AmbienceRegistry::load, null),
                DATA_NAME);
    }

    /** Every effect in this level, oldest first (read only). */
    public List<AmbientEffect> all() {
        return Collections.unmodifiableList(new ArrayList<>(effects.values()));
    }

    /** The effect with this id, or null when there is none. */
    public @Nullable AmbientEffect get(UUID id) {
        return effects.get(id);
    }

    /** Registers the effect (replacing one with the same id) and tells every player in the level. */
    public void add(ServerLevel level, AmbientEffect effect) {
        if (!effects.containsKey(effect.id()) && effects.size() >= MAX_EFFECTS) {
            LOGGER.warn("Ambience registry is full ({}); dropping {}", MAX_EFFECTS, effect.id());
            return;
        }
        effects.put(effect.id(), effect);
        setDirty();
        AmbienceNet.broadcastDelta(level, true, effect);
    }

    /** Removes the effect and tells every player in the level; false when there was no such effect. */
    public boolean remove(ServerLevel level, UUID id) {
        AmbientEffect gone = effects.remove(id);
        if (gone == null) {
            return false;
        }
        setDirty();
        AmbienceNet.broadcastDelta(level, false, gone);
        return true;
    }

    /** The ceilings this player made, oldest first (for the spell's per-player limit). */
    public List<AmbientEffect> ceilingsOwnedBy(UUID owner) {
        List<AmbientEffect> found = new ArrayList<>();
        for (AmbientEffect e : effects.values()) {
            if (e.kind() == AmbientEffect.KIND_CEILING && owner.equals(e.owner())) {
                found.add(e);
            }
        }
        return found;
    }

    /**
     * The ceiling whose rectangle contains this point in x and z and whose vertical span, from {@code band} below the
     * sky plane to {@code band} above the roof ({@code y - band <= point.y <= y + hy + band}), contains it. A ceiling's
     * roof blocks sit at or above its plane, so the spell that targets a block of the roof finds the ceiling with a
     * small band (2 blocks). The oldest match wins.
     */
    public @Nullable AmbientEffect ceilingAt(Vec3 point, double band) {
        for (AmbientEffect e : effects.values()) {
            if (e.kind() != AmbientEffect.KIND_CEILING) {
                continue;
            }
            if (Math.abs(point.x - e.x()) > e.hx() || Math.abs(point.z - e.z()) > e.hz()) {
                continue;
            }
            if (point.y < e.y() - band || point.y > e.y() + Math.max(0f, e.hy()) + band) {
                continue;
            }
            return e;
        }
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (AmbientEffect e : effects.values()) {
            list.add(e.save());
        }
        tag.put("Effects", list);
        return tag;
    }

    public static AmbienceRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        AmbienceRegistry registry = new AmbienceRegistry();
        ListTag list = tag.getList("Effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && registry.effects.size() < MAX_EFFECTS; i++) {
            AmbientEffect e = AmbientEffect.load(list.getCompound(i));
            if (e != null) {
                registry.effects.put(e.id(), e);
            } else {
                LOGGER.warn("Dropping a damaged ambient effect at index {}", i);
            }
        }
        return registry;
    }
}
