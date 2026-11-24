package com.hogwartsmagic.owlpost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The owl post office: everything that has to outlive the chunks it happens in. Kept once, in the overworld's
 * data storage, for every dimension.
 *
 * <ul>
 *   <li>{@link #perches}: every owl perch that exists, with its name. A perch registers itself when it ticks and
 *   leaves when it is broken, so a destination list never offers a perch that is gone.</li>
 *   <li>{@link #owls}: each owl that carries a route tag: its owner, its home perch and the perches it knows. An owl
 *   out on a delivery is not in the world, so the destination list cannot ask the entity.</li>
 *   <li>{@link #deliveries}: stacks in the air. Each lands on its perch once the time is up and the perch's chunk is
 *   loaded; until then it waits here, so nothing is lost to an unloaded chunk.</li>
 *   <li>{@link #returns}: owls flying home, saved whole. Each is put back into the world above its home perch once
 *   that chunk is ticking entities.</li>
 * </ul>
 */
public final class OwlPostData extends SavedData {
    public static final String DATA_NAME = "hogwartsmagic_owl_post";

    public record OwlRecord(@Nullable UUID owner, @Nullable GlobalPos home, List<GlobalPos> known) {
    }

    public static final class Delivery {
        public GlobalPos dest;
        public ItemStack payload;
        public long arriveAt;
        @Nullable
        public UUID sender;
        /** Where the stack goes back to if its perch is gone; null once it is already going back. */
        @Nullable
        public GlobalPos returnTo;

        Delivery(GlobalPos dest, ItemStack payload, long arriveAt, @Nullable UUID sender, @Nullable GlobalPos returnTo) {
            this.dest = dest;
            this.payload = payload;
            this.arriveAt = arriveAt;
            this.sender = sender;
            this.returnTo = returnTo;
        }
    }

    public static final class Return {
        public final UUID owlId;
        public final CompoundTag owl;
        public final GlobalPos home;
        public final long at;

        Return(UUID owlId, CompoundTag owl, GlobalPos home, long at) {
            this.owlId = owlId;
            this.owl = owl;
            this.home = home;
            this.at = at;
        }
    }

    final Map<GlobalPos, String> perches = new HashMap<>();
    final Map<UUID, OwlRecord> owls = new HashMap<>();
    final List<Delivery> deliveries = new ArrayList<>();
    final List<Return> returns = new ArrayList<>();

    public static OwlPostData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(OwlPostData::new, OwlPostData::load, null), DATA_NAME);
    }

    // ------------------------------------------------------------------ perches

    public void putPerch(GlobalPos pos, String name) {
        String previous = perches.put(pos, name == null ? "" : name);
        if (previous == null || !previous.equals(name)) {
            setDirty();
        }
    }

    public void removePerch(GlobalPos pos) {
        if (perches.remove(pos) != null) {
            setDirty();
        }
    }

    public boolean hasPerch(GlobalPos pos) {
        return perches.containsKey(pos);
    }

    public String perchName(GlobalPos pos) {
        return perches.getOrDefault(pos, "");
    }

    // ------------------------------------------------------------------ owls

    public void putOwl(UUID owl, OwlRecord record) {
        OwlRecord previous = owls.put(owl, record);
        if (!record.equals(previous)) {
            setDirty();
        }
    }

    public void removeOwl(UUID owl) {
        if (owls.remove(owl) != null) {
            setDirty();
        }
    }

    @Nullable
    public OwlRecord owl(UUID owl) {
        return owls.get(owl);
    }

    /** The owls whose home is this perch, in the world or out on a delivery. */
    public List<Map.Entry<UUID, OwlRecord>> owlsHomedAt(GlobalPos perch) {
        List<Map.Entry<UUID, OwlRecord>> out = new ArrayList<>();
        for (Map.Entry<UUID, OwlRecord> entry : owls.entrySet()) {
            if (perch.equals(entry.getValue().home())) {
                out.add(entry);
            }
        }
        return out;
    }

    public boolean isOwlAway(UUID owl) {
        for (Return r : returns) {
            if (r.owlId.equals(owl)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ flights

    public void addDelivery(GlobalPos dest, ItemStack payload, long arriveAt, @Nullable UUID sender, @Nullable GlobalPos returnTo) {
        if (payload.isEmpty()) {
            return;
        }
        deliveries.add(new Delivery(dest, payload.copy(), arriveAt, sender, returnTo));
        setDirty();
    }

    public void addReturn(UUID owlId, CompoundTag owl, GlobalPos home, long at) {
        returns.add(new Return(owlId, owl, home, at));
        setDirty();
    }

    /** Parcels that have arrived at this perch but are still waiting for room in its inbox. */
    public int deliveriesWaitingFor(GlobalPos dest, long now) {
        int n = 0;
        for (Delivery d : deliveries) {
            if (d.dest.equals(dest) && now >= d.arriveAt) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ NBT

    public static CompoundTag writePos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("D", pos.dimension().location().toString());
        tag.putLong("P", pos.pos().asLong());
        return tag;
    }

    @Nullable
    public static GlobalPos readPos(CompoundTag tag) {
        if (!tag.contains("D", Tag.TAG_STRING) || !tag.contains("P", Tag.TAG_LONG)) {
            return null;
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("D"));
        if (dim == null) {
            return null;
        }
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("P")));
    }

    public static ListTag writePosList(List<GlobalPos> list) {
        ListTag out = new ListTag();
        for (GlobalPos pos : list) {
            out.add(writePos(pos));
        }
        return out;
    }

    public static List<GlobalPos> readPosList(ListTag list) {
        List<GlobalPos> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            GlobalPos pos = readPos(list.getCompound(i));
            if (pos != null && !out.contains(pos)) {
                out.add(pos);
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag perchList = new ListTag();
        for (Map.Entry<GlobalPos, String> e : perches.entrySet()) {
            CompoundTag p = writePos(e.getKey());
            p.putString("Name", e.getValue());
            perchList.add(p);
        }
        tag.put("Perches", perchList);

        ListTag owlList = new ListTag();
        for (Map.Entry<UUID, OwlRecord> e : owls.entrySet()) {
            CompoundTag o = new CompoundTag();
            o.putUUID("Id", e.getKey());
            if (e.getValue().owner() != null) {
                o.putUUID("Owner", e.getValue().owner());
            }
            if (e.getValue().home() != null) {
                o.put("Home", writePos(e.getValue().home()));
            }
            o.put("Known", writePosList(e.getValue().known()));
            owlList.add(o);
        }
        tag.put("Owls", owlList);

        ListTag deliveryList = new ListTag();
        for (Delivery d : deliveries) {
            if (d.payload.isEmpty()) {
                continue;
            }
            CompoundTag o = new CompoundTag();
            o.put("Dest", writePos(d.dest));
            o.put("Item", d.payload.save(provider));
            o.putLong("At", d.arriveAt);
            if (d.sender != null) {
                o.putUUID("Sender", d.sender);
            }
            if (d.returnTo != null) {
                o.put("ReturnTo", writePos(d.returnTo));
            }
            deliveryList.add(o);
        }
        tag.put("Deliveries", deliveryList);

        ListTag returnList = new ListTag();
        for (Return r : returns) {
            CompoundTag o = new CompoundTag();
            o.putUUID("Id", r.owlId);
            o.put("Owl", r.owl);
            o.put("Home", writePos(r.home));
            o.putLong("At", r.at);
            returnList.add(o);
        }
        tag.put("Returns", returnList);
        return tag;
    }

    public static OwlPostData load(CompoundTag tag, HolderLookup.Provider provider) {
        OwlPostData data = new OwlPostData();
        ListTag perchList = tag.getList("Perches", Tag.TAG_COMPOUND);
        for (int i = 0; i < perchList.size(); i++) {
            CompoundTag p = perchList.getCompound(i);
            GlobalPos pos = readPos(p);
            if (pos != null) {
                data.perches.put(pos, p.getString("Name"));
            }
        }
        ListTag owlList = tag.getList("Owls", Tag.TAG_COMPOUND);
        for (int i = 0; i < owlList.size(); i++) {
            CompoundTag o = owlList.getCompound(i);
            if (!o.hasUUID("Id")) {
                continue;
            }
            data.owls.put(o.getUUID("Id"), new OwlRecord(
                    o.hasUUID("Owner") ? o.getUUID("Owner") : null,
                    o.contains("Home") ? readPos(o.getCompound("Home")) : null,
                    readPosList(o.getList("Known", Tag.TAG_COMPOUND))));
        }
        ListTag deliveryList = tag.getList("Deliveries", Tag.TAG_COMPOUND);
        for (int i = 0; i < deliveryList.size(); i++) {
            CompoundTag o = deliveryList.getCompound(i);
            GlobalPos dest = readPos(o.getCompound("Dest"));
            ItemStack payload = ItemStack.parseOptional(provider, o.getCompound("Item"));
            if (dest == null || payload.isEmpty()) {
                continue;
            }
            data.deliveries.add(new Delivery(dest, payload, o.getLong("At"),
                    o.hasUUID("Sender") ? o.getUUID("Sender") : null,
                    o.contains("ReturnTo") ? readPos(o.getCompound("ReturnTo")) : null));
        }
        ListTag returnList = tag.getList("Returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < returnList.size(); i++) {
            CompoundTag o = returnList.getCompound(i);
            GlobalPos home = readPos(o.getCompound("Home"));
            if (!o.hasUUID("Id") || home == null) {
                continue;
            }
            data.returns.add(new Return(o.getUUID("Id"), o.getCompound("Owl"), home, o.getLong("At")));
        }
        return data;
    }
}
