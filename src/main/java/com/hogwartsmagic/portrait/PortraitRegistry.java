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
 * Registers the living portraits (entity type, item, one sound per line) from its own subscriber, so nothing in
 * {@code ExampleMod} has to know about them. Everything is constructed INSIDE the {@link RegisterEvent}: entity
 * types and items take intrusive holders and must be built while their registry is still open.
 *
 * <p>Common code: no client classes in here.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortraitRegistry {
    public static final String FAT_LADY_ID = "fat_lady_portrait";

    /** Filled by the register event; read them only after registration (entity creation, item use, rendering). */
    public static EntityType<FatLadyPortraitEntity> FAT_LADY_TYPE;
    public static Item FAT_LADY_ITEM;

    private static final Map<String, SoundEvent> LINE_SOUNDS = new HashMap<>();

    private PortraitRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            FAT_LADY_TYPE = EntityType.Builder.<FatLadyPortraitEntity>of(FatLadyPortraitEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.0f)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE) // never moves: only its synced data is sent
                    .build(FAT_LADY_ID);
            helper.register(id(FAT_LADY_ID), FAT_LADY_TYPE);
        });
        event.register(Registries.ITEM, helper -> {
            FAT_LADY_ITEM = new FatLadyPortraitItem(new Item.Properties().stacksTo(16));
            helper.register(id(FAT_LADY_ID), FAT_LADY_ITEM);
        });
        event.register(Registries.SOUND_EVENT, helper -> {
            for (FatLadyLines.Line line : FatLadyLines.LINES) {
                ResourceLocation rl = id("portrait.fat_lady." + line.key());
                SoundEvent sound = SoundEvent.createVariableRangeEvent(rl);
                LINE_SOUNDS.put(line.key(), sound);
                helper.register(rl, sound);
            }
        });
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (FAT_LADY_ITEM != null && event.getTabKey().equals(ExampleMod.MAIN_TAB.getKey())) {
            event.accept(FAT_LADY_ITEM);
        }
    }

    /** The voice of a line (null before registration, which cannot happen at play time). */
    public static SoundEvent soundFor(FatLadyLines.Line line) {
        return LINE_SOUNDS.get(line.key());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path);
    }
}
