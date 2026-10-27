package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.mixin.SynchedEntityDataAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One entity's recent past: a pose per game tick in a ring, plus the changes of its synched data and equipment and
 * the things it did. Server thread only. All ticks are the level's game time.
 */
public final class EntityHistory {
    public static final int X = 0, Y = 1, Z = 2, YAW = 3, HEAD = 4, BODY = 5, PITCH = 6;
    private static final int LANES = 7;
    /** A hole this long or shorter (an entity leaving the watched area briefly) is bridged by holding the last pose. */
    private static final int MAX_BRIDGE = 40;
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    public static final class DataRec {
        public long tick;
        public final int key;
        public final byte[] value;

        DataRec(long tick, int key, byte[] value) {
            this.tick = tick;
            this.key = key;
            this.value = value;
        }
    }

    public static final class EquipRec {
        public long tick;
        public final int slot;
        public final byte[] stack;

        EquipRec(long tick, int slot, byte[] stack) {
            this.tick = tick;
            this.slot = slot;
            this.stack = stack;
        }
    }

    public record ActRec(long tick, int kind) {
    }

    /** A copy of a window of a history, for the background thread. Ticks are absolute. */
    public static final class Slice {
        public UUID uuid;
        public String type;
        public String name;
        public boolean player;
        public String skinValue;
        public String skinSignature;
        public double ox, oy, oz;
        public long first;
        public long last;
        public boolean killed;
        public boolean gone;
        public float[][] lanes;
        public List<DataRec> data = new ArrayList<>();
        public List<EquipRec> equipment = new ArrayList<>();
        public List<ActRec> acts = new ArrayList<>();
    }

    public final UUID uuid;
    public final String type;
    public final boolean player;
    public String name;
    public String skinValue;
    public String skinSignature;
    public boolean killed;
    /** The live entity while it is being sampled; dropped when it stops being sampled so a gone entity is not kept alive. */
    Entity ref;

    /** Positions are stored as floats relative to this block, so a far-from-spawn entity keeps its precision. */
    private double ox, oy, oz;
    private final int cap;
    private final float[][] lanes = new float[LANES][];
    private int start;
    private int count;
    private long lastTick = Long.MIN_VALUE;

    private final List<DataRec> data = new ArrayList<>();
    private final Map<Integer, byte[]> lastData = new HashMap<>();
    private final List<EquipRec> equipment = new ArrayList<>();
    private final ItemStack[] lastEquip = new ItemStack[SLOTS.length];
    private final List<ActRec> acts = new ArrayList<>();
    private boolean baselined;

    public EntityHistory(UUID uuid, String type, boolean player, int capTicks) {
        this.uuid = uuid;
        this.type = type;
        this.player = player;
        this.cap = Math.max(8, capTicks);
        Arrays.fill(lastEquip, ItemStack.EMPTY);
    }

    public long lastTick() {
        return lastTick;
    }

    public int count() {
        return count;
    }

    public long firstTick() {
        return count == 0 ? Long.MAX_VALUE : lastTick - count + 1;
    }

    public float at(int lane, long tick) {
        return lanes[lane][(start + (int) (tick - firstTick())) % cap];
    }

    /** The world position at a recorded tick. */
    public double worldX(long tick) {
        return ox + at(X, tick);
    }

    public double worldY(long tick) {
        return oy + at(Y, tick);
    }

    public double worldZ(long tick) {
        return oz + at(Z, tick);
    }

    // ---- sampling ----

    /** Records this tick's pose. A repeated call for the same tick is ignored. */
    public void samplePose(Entity e, long tick) {
        if (count > 0 && tick <= lastTick) {
            return;
        }
        float head = e instanceof LivingEntity le ? le.yHeadRot : e.getYRot();
        float body = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        if (count > 0 && tick - lastTick > 1) {
            if (tick - lastTick - 1 > MAX_BRIDGE) {
                reset();
            } else {
                int idx = (start + count - 1) % cap;
                float[] hold = new float[LANES];
                for (int l = 0; l < LANES; l++) {
                    hold[l] = lanes[l][idx];
                }
                for (long t = lastTick + 1; t < tick; t++) {
                    push(hold[X], hold[Y], hold[Z], hold[YAW], hold[HEAD], hold[BODY], hold[PITCH]);
                }
            }
        }
        if (count == 0) {
            ox = Math.floor(e.getX());
            oy = Math.floor(e.getY());
            oz = Math.floor(e.getZ());
        }
        push((float) (e.getX() - ox), (float) (e.getY() - oy), (float) (e.getZ() - oz), e.getYRot(), head, body, e.getXRot());
        lastTick = tick;
    }

    private void push(float x, float y, float z, float yaw, float head, float body, float pitch) {
        if (lanes[0] == null) {
            for (int l = 0; l < LANES; l++) {
                lanes[l] = new float[cap];
            }
        }
        if (count == cap) {
            start = (start + 1) % cap;
            count--;
        }
        int idx = (start + count) % cap;
        lanes[X][idx] = x;
        lanes[Y][idx] = y;
        lanes[Z][idx] = z;
        lanes[YAW][idx] = yaw;
        lanes[HEAD][idx] = head;
        lanes[BODY][idx] = body;
        lanes[PITCH][idx] = pitch;
        count++;
    }

    private void reset() {
        start = 0;
        count = 0;
        data.clear();
        lastData.clear();
        equipment.clear();
        Arrays.fill(lastEquip, ItemStack.EMPTY);
        acts.clear();
        baselined = false;
    }

    /**
     * Stores synched-data values that changed since the last stored one. The first call stores every non-default
     * value; later calls only look when the entity data is dirty, and then take the items flagged dirty (the flags
     * survive until the next tick's send), which, unlike {@code getNonDefaultValues}, also sees a value that went
     * back to its default.
     */
    public void sampleData(Entity e, long tick, RegistryAccess access) {
        SynchedEntityData sd = e.getEntityData();
        if (!baselined) {
            baselined = true;
            List<SynchedEntityData.DataValue<?>> all = sd.getNonDefaultValues();
            if (all != null) {
                for (SynchedEntityData.DataValue<?> v : all) {
                    storeData(tick, v, access);
                }
            }
            return;
        }
        if (!sd.isDirty()) {
            return;
        }
        SynchedEntityData.DataItem<?>[] items = ((SynchedEntityDataAccessor) sd).hogwartsmagic$items();
        for (SynchedEntityData.DataItem<?> item : items) {
            if (item != null && item.isDirty()) {
                storeData(tick, item.value(), access);
            }
        }
    }

    private void storeData(long tick, SynchedEntityData.DataValue<?> v, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(32), access);
        v.write(buf);
        byte[] all = new byte[buf.readableBytes()];
        buf.readBytes(all);
        byte[] value = Arrays.copyOfRange(all, 1, all.length); // drop the id byte
        byte[] before = lastData.get(v.id());
        if (before != null && Arrays.equals(before, value)) {
            return;
        }
        lastData.put(v.id(), value);
        data.add(new DataRec(tick, v.id(), value));
    }

    public void sampleEquipment(LivingEntity e, long tick, RegistryAccess access) {
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack now = e.getItemBySlot(SLOTS[i]);
            if (!ItemStack.matches(lastEquip[i], now)) {
                lastEquip[i] = now.copy();
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(32), access);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, now);
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                equipment.add(new EquipRec(tick, i, bytes));
            }
        }
    }

    public void act(long tick, int kind) {
        if (!acts.isEmpty()) {
            ActRec last = acts.get(acts.size() - 1);
            if (last.tick() == tick && last.kind() == kind) {
                return;
            }
        }
        acts.add(new ActRec(tick, kind));
    }

    /** Whole-entity sample for one tick: pose, data, equipment. */
    public void sample(Entity e, long tick, ServerLevel level) {
        ref = e;
        if (e instanceof ServerPlayer sp && skinValue == null) {
            sp.getGameProfile().getProperties().get("textures").stream().findFirst().ifPresent(p -> {
                skinValue = p.value();
                skinSignature = p.signature();
            });
        }
        if (name == null) {
            name = e.hasCustomName() || player ? e.getName().getString() : "";
        }
        samplePose(e, tick);
        RegistryAccess access = level.registryAccess();
        sampleData(e, tick, access);
        if (e instanceof LivingEntity le) {
            sampleEquipment(le, tick, access);
        }
    }

    // ---- upkeep ----

    /** Forgets everything before {@code cutoff}, keeping the last data and equipment values as of {@code cutoff}. */
    public void trim(long cutoff) {
        if (count > 0) {
            long first = firstTick();
            if (first < cutoff) {
                int drop = (int) Math.min(count, cutoff - first);
                start = (start + drop) % cap;
                count -= drop;
            }
        }
        collapseData(cutoff);
        collapseEquipment(cutoff);
        acts.removeIf(a -> a.tick() < cutoff);
    }

    private void collapseData(long cutoff) {
        if (data.isEmpty() || data.get(0).tick >= cutoff) {
            return;
        }
        Map<Integer, DataRec> carry = new HashMap<>();
        List<DataRec> keep = new ArrayList<>();
        for (DataRec d : data) {
            if (d.tick < cutoff) {
                carry.put(d.key, d);
            } else {
                keep.add(d);
            }
        }
        data.clear();
        carry.values().stream().sorted((a, b) -> Integer.compare(a.key, b.key)).forEach(d -> {
            d.tick = cutoff;
            data.add(d);
        });
        data.addAll(keep);
    }

    private void collapseEquipment(long cutoff) {
        if (equipment.isEmpty() || equipment.get(0).tick >= cutoff) {
            return;
        }
        Map<Integer, EquipRec> carry = new HashMap<>();
        List<EquipRec> keep = new ArrayList<>();
        for (EquipRec q : equipment) {
            if (q.tick < cutoff) {
                carry.put(q.slot, q);
            } else {
                keep.add(q);
            }
        }
        equipment.clear();
        carry.values().stream().sorted((a, b) -> Integer.compare(a.slot, b.slot)).forEach(q -> {
            q.tick = cutoff;
            equipment.add(q);
        });
        equipment.addAll(keep);
    }

    // ---- copying out ----

    /**
     * A copy of the ticks {@code from..to} (clamped to what exists), or null when nothing of the history falls in it.
     * Data and equipment values older than {@code from} are carried in as of {@code from}.
     */
    public Slice copy(long from, long to, long now) {
        if (count == 0) {
            return null;
        }
        long a = Math.max(from, firstTick());
        long b = Math.min(to, lastTick);
        if (a > b) {
            return null;
        }
        Slice s = new Slice();
        s.uuid = uuid;
        s.type = type;
        s.name = name == null ? "" : name;
        s.player = player;
        s.skinValue = skinValue;
        s.skinSignature = skinSignature;
        s.ox = ox;
        s.oy = oy;
        s.oz = oz;
        s.first = a;
        s.last = b;
        s.killed = killed;
        s.gone = !killed && lastTick < now;
        int n = (int) (b - a + 1);
        s.lanes = new float[LANES][n];
        int from0 = (start + (int) (a - firstTick())) % cap;
        for (int l = 0; l < LANES; l++) {
            int firstRun = Math.min(n, cap - from0);
            System.arraycopy(lanes[l], from0, s.lanes[l], 0, firstRun);
            if (firstRun < n) {
                System.arraycopy(lanes[l], 0, s.lanes[l], firstRun, n - firstRun);
            }
        }
        Map<Integer, DataRec> dataCarry = new HashMap<>();
        for (DataRec d : data) {
            if (d.tick < a) {
                dataCarry.put(d.key, d);
            } else if (d.tick <= b) {
                s.data.add(d);
            }
        }
        List<DataRec> carried = new ArrayList<>();
        dataCarry.values().stream().sorted((x, y) -> Integer.compare(x.key, y.key))
                .forEach(d -> carried.add(new DataRec(a, d.key, d.value)));
        s.data.addAll(0, carried);
        Map<Integer, EquipRec> equipCarry = new HashMap<>();
        for (EquipRec q : equipment) {
            if (q.tick < a) {
                equipCarry.put(q.slot, q);
            } else if (q.tick <= b) {
                s.equipment.add(q);
            }
        }
        List<EquipRec> carriedEquip = new ArrayList<>();
        equipCarry.values().stream().sorted((x, y) -> Integer.compare(x.slot, y.slot))
                .forEach(q -> carriedEquip.add(new EquipRec(a, q.slot, q.stack)));
        s.equipment.addAll(0, carriedEquip);
        for (ActRec act : acts) {
            if (act.tick() >= a && act.tick() <= b) {
                s.acts.add(act);
            }
        }
        return s;
    }
}
