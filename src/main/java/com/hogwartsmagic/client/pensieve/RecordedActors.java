package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.capture.RecordedTrackSampler;
import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordingScene;
import com.hogwartsmagic.pensieve.scene.MemoryBall;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The puppets of a recorded memory: where each one stands at a scene time, its synched data and worn items, its walk
 * cycle and its skin. The scene's timed actions (swing, hurt, die, cast) come from the story scene
 * {@link RecordingScene#toScene} built, through {@link MemoryActors}; this class replaces only the path, look, life
 * and ride logic, which a recorded actor does not have.
 */
public final class RecordedActors {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    /** The recording the id map below was made for. */
    private static Recording mapped;
    private static Map<String, Recording.Track> ids = Map.of();
    /** One profile per recorded player track (the key of {@link ActorSkins#skinForProfile}). */
    private static final Map<Recording.Track, GameProfile> PROFILES = new IdentityHashMap<>();

    private RecordedActors() {
    }

    /** The recorded track behind the scene actor {@code actorId}, or null (story memory, or no such actor). */
    @Nullable
    static Recording.Track trackFor(String actorId) {
        Recording r = PensieveClientState.recording();
        if (r == null) return null;
        if (r != mapped) {
            mapped = r;
            ids = RecordingScene.actorIds(r);
            PROFILES.clear();
        }
        return ids.get(actorId);
    }

    /** The puppet player's profile: the original player's uuid and name, with the recorded textures when there are. */
    static GameProfile profile(Recording.Track t) {
        return PROFILES.computeIfAbsent(t, tr -> {
            UUID id = tr.uuid() != null ? tr.uuid() : UUID.nameUUIDFromBytes(("pensieve:" + tr.actor()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            GameProfile p = new GameProfile(id, tr.name() != null && !tr.name().isEmpty() ? tr.name() : "Player");
            if (tr.skinValue() != null) {
                p.getProperties().put("textures", tr.skinSignature() != null
                        ? new Property("textures", tr.skinValue(), tr.skinSignature())
                        : new Property("textures", tr.skinValue()));
            }
            return p;
        });
    }

    /** The skin of a recorded player puppet, or null when {@code player} is not one. */
    @Nullable
    public static PlayerSkin skin(AbstractClientPlayer player) {
        ActorRuntime a = MemoryActors.runtimeFor(player);
        if (a == null || a.track == null || !a.track.player()) return null;
        GameProfile profile = profile(a.track);
        if (a.track.skinValue() != null) return ActorSkins.skinForProfile(profile);
        // No recorded textures: the player's skin as the connection knows it, else looked up by name.
        net.minecraft.client.multiplayer.ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(profile.getId());
        if (info != null) return info.getSkin();
        return ActorSkins.skinFor(a.def.skin(), profile.getId());
    }

    /** Forget the per-recording lookups (a new memory is starting or the dive is over). */
    static void clear() {
        mapped = null;
        ids = Map.of();
        PROFILES.clear();
    }

    /**
     * Advances a recorded actor one client tick to scene time {@code t}: the old pose is kept for interpolation, the
     * synched data and equipment due by now are applied in order, then the sampled pose is set and the walk cycle is
     * driven from how far it moved.
     */
    static void step(ActorRuntime a, float t) {
        Recording.Track tr = a.track;
        Entity e = a.entity;
        LivingEntity l = a.living;
        BlockPos origin = PensieveClientState.origin();
        boolean first = !a.placed;
        e.setOldPosAndRot();
        if (l != null) {
            l.yBodyRotO = l.yBodyRot;
            l.yHeadRotO = l.yHeadRot;
        }
        applyPatches(a, t);

        float[] p = RecordedTrackSampler.pose(tr, t);
        double x = origin.getX() + p[0];
        double y = origin.getY() + p[1];
        double z = origin.getZ() + p[2];
        double dx = first ? 0 : x - e.getX();
        double dy = first ? 0 : y - e.getY();
        double dz = first ? 0 : z - e.getZ();
        // A jump the path does not walk (a teleport, a respawn) is a cut: the renderer must not slide across it.
        boolean cut = dx * dx + dy * dy + dz * dz > MemoryBall.MAX_STEP * MemoryBall.MAX_STEP;
        e.setPos(x, y, z);
        e.setYRot(p[3]);
        e.setXRot(p[6]);
        if (l != null) {
            l.setYBodyRot(p[5]);
            l.setYHeadRot(p[4]);
        }
        if (first || cut) {
            e.setOldPosAndRot();
            if (l != null) {
                l.yBodyRotO = l.yBodyRot;
                l.yHeadRotO = l.yHeadRot;
            }
        }
        a.placed = true;
        if (l != null) {
            float stride = first || cut ? 0 : Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f);
            l.walkAnimation.update(stride, 0.4f);
        }
    }

    private static void applyPatches(ActorRuntime a, float t) {
        if (a.patchesFailed) return;
        Recording.Track tr = a.track;
        Entity e = a.entity;
        int tick = (int) Math.floor(t);
        try {
            RegistryAccess access = e.level().registryAccess();
            int dataEnd = RecordedTrackSampler.patchesUpTo(tr.data(), a.nextData, tick);
            if (dataEnd > a.nextData) {
                List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(dataEnd - a.nextData);
                for (int i = a.nextData; i < dataEnd; i++) {
                    Recording.DataPatch d = tr.data().get(i);
                    // The patch is the value as DataValue#write wrote it after the id byte: the serializer's
                    // varint, then the value itself.
                    values.add(SynchedEntityData.DataValue.read(
                            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(d.value()), access), d.key()));
                }
                a.nextData = dataEnd;
                e.getEntityData().assignValues(values);
            }
            int equipEnd = RecordedTrackSampler.patchesUpTo(tr.equipment(), a.nextEquip, tick);
            while (a.nextEquip < equipEnd) {
                Recording.EquipPatch q = tr.equipment().get(a.nextEquip++);
                if (a.living == null || q.slot() < 0 || q.slot() >= SLOTS.length) continue;
                ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(
                        new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(q.stack()), access));
                a.living.setItemSlot(SLOTS[q.slot()], stack);
            }
        } catch (RuntimeException ex) {
            a.patchesFailed = true;
            a.nextData = tr.data().size();
            a.nextEquip = tr.equipment().size();
            LOGGER.warn("Pensieve: recorded actor '{}' ({}) has an unreadable data patch; its state stops updating: {}",
                    a.def.id(), tr.type(), ex.toString());
        }
    }
}
