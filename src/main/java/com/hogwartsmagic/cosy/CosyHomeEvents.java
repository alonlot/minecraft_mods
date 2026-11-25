package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.decor.ModDecorations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side bookkeeping for the cosy home pack. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class CosyHomeEvents {
    private CosyHomeEvents() {
    }

    /** Warmed only mends the unhurt, so remember when each player last took damage. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof net.minecraft.server.level.ServerPlayer && event.getAmount() > 0.0F) {
            entity.getPersistentData().putLong(WarmedEffect.HURT_KEY, entity.level().getGameTime());
        }
    }

    /**
     * Floating candles placed before they had a block entity have none, and are drawn by it, so they would be
     * invisible. The chunk packet snapshots block entities when it is built, so the entity has to exist before any
     * send: give them one as the chunk finishes loading on the server.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            adoptCandles(chunk);
        }
    }

    /** While Warmed the cold cannot take hold. Done here, not inside the effect tick, which would break its own iteration. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.hasEffect(CosyHome.WARMED.getHolder().get())) {
            return;
        }
        if (entity.hasEffect(ExampleMod.GLACIUS_CHILL.getHolder().get())) {
            entity.removeEffect(ExampleMod.GLACIUS_CHILL.getHolder().get());
        }
        if (entity.getTicksFrozen() > 0) {
            entity.setTicksFrozen(0);
        }
    }

    /** Creates the missing block entity of every floating candle in the chunk. Returns how many it made. */
    public static int adoptCandles(LevelChunk chunk) {
        int made = 0;
        net.minecraft.world.level.block.Block candle = ModDecorations.FLOATING_CANDLE.get();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(candle))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(candle)) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                        if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null
                                && chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) != null) {
                            made++;
                        }
                    }
                }
            }
        }
        if (made > 0) {
            chunk.setUnsaved(true); // so the entities persist and this is not redone every session
        }
        return made;
    }
}
