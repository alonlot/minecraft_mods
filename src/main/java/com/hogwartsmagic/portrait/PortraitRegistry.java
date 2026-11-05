package com.hogwartsmagic.portrait;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers the living portraits (an entity type and an item per {@link PortraitKind}, one sound per line) from its
 * own subscriber, so nothing in {@code ExampleMod} has to know about them. Everything is constructed INSIDE the
 * {@link RegisterEvent}: entity types and items take intrusive holders and must be built while their registry is
 * still open.
 *
 * <p>Common code: no client classes in here.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortraitRegistry {
    /** Filled by the register event; read them only after registration (entity creation, item use, rendering). */
    private static final Map<String, EntityType<PortraitEntity>> TYPES = new HashMap<>();
    private static final Map<String, Item> ITEMS = new HashMap<>();
    private static final Map<String, SoundEvent> LINE_SOUNDS = new HashMap<>();

    private PortraitRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            for (PortraitKind kind : PortraitKind.ALL) {
                EntityType<PortraitEntity> type = EntityType.Builder
                        .<PortraitEntity>of((t, level) -> new PortraitEntity(t, level, kind), MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .eyeHeight(0.0f)
                        .clientTrackingRange(10)
                        .updateInterval(Integer.MAX_VALUE) // never moves: only its synced data is sent
                        .build(kind.registryPath());
                TYPES.put(kind.id(), type);
                helper.register(id(kind.registryPath()), type);
            }
        });
        event.register(Registries.ITEM, helper -> {
            for (PortraitKind kind : PortraitKind.ALL) {
                Item item = new PortraitItem(new Item.Properties().stacksTo(16), kind);
                ITEMS.put(kind.id(), item);
                helper.register(id(kind.registryPath()), item);
            }
        });
        event.register(Registries.SOUND_EVENT, helper -> {
            for (PortraitKind kind : PortraitKind.ALL) {
                for (PortraitLines.Line line : kind.lines()) {
                    ResourceLocation rl = id(line.soundPath());
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(rl);
                    LINE_SOUNDS.put(line.soundPath(), sound);
                    helper.register(rl, sound);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ExampleMod.MAIN_TAB.getKey())) {
            for (PortraitKind kind : PortraitKind.ALL) {
                Item item = ITEMS.get(kind.id());
                if (item != null) {
                    event.accept(item);
                }
            }
        }
    }

    /** The entity type of a kind (null before registration, which cannot happen at play time). */
    public static EntityType<PortraitEntity> typeOf(PortraitKind kind) {
        return TYPES.get(kind.id());
    }

    /** The item of a kind (null before registration). */
    public static Item itemOf(PortraitKind kind) {
        return ITEMS.get(kind.id());
    }

    /** The voice of a line (null before registration, which cannot happen at play time). */
    public static SoundEvent soundFor(PortraitLines.Line line) {
        return LINE_SOUNDS.get(line.soundPath());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path);
    }
}
