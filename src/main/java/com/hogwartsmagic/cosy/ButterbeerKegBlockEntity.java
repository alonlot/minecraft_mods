package com.hogwartsmagic.cosy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a butterbeer keg holds: which of the three ingredients are in, the game time the batch started, and the
 * servings left. Aging is a stored start time, not a countdown, so it carries on in an unloaded chunk.
 */
public class ButterbeerKegBlockEntity extends BlockEntity {
    private boolean sugar;
    private boolean honey;
    private boolean wheat;
    private long brewStart = KegRules.NOT_STARTED;
    private int servings;

    public ButterbeerKegBlockEntity(BlockPos pos, BlockState state) {
        super(CosyFood.BUTTERBEER_KEG_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean has(int ingredient) {
        return ingredient == KegRules.SUGAR ? sugar : ingredient == KegRules.HONEY ? honey : wheat;
    }

    /** Puts one ingredient in. False when the keg already has that one. Starts the batch when it completes. */
    public boolean addIngredient(int ingredient, long now) {
        if (has(ingredient) || servings > 0) {
            return false;
        }
        if (ingredient == KegRules.SUGAR) {
            sugar = true;
        } else if (ingredient == KegRules.HONEY) {
            honey = true;
        } else {
            wheat = true;
        }
        if (KegRules.complete(sugar, honey, wheat)) {
            brewStart = now;
            servings = KegRules.SERVINGS;
        }
        setChanged();
        return true;
    }

    public boolean isBrewing(long now) {
        return servings > 0 && !KegRules.ready(brewStart, now);
    }

    public boolean isReady(long now) {
        return servings > 0 && KegRules.ready(brewStart, now);
    }

    public int servings() {
        return servings;
    }

    /** Pours one serving. False unless the batch is ready. The keg empties itself after the last serving. */
    public boolean pour(long now) {
        if (!isReady(now)) {
            return false;
        }
        servings--;
        if (servings <= 0) {
            reset();
        }
        setChanged();
        return true;
    }

    private void reset() {
        sugar = false;
        honey = false;
        wheat = false;
        brewStart = KegRules.NOT_STARTED;
        servings = 0;
    }

    /** The block-state fill for this keg now. */
    public int fill(long now) {
        return KegRules.fill(sugar, honey, wheat, brewStart, servings, now);
    }

    /** Ticks left before the batch is ready, or 0. */
    public long ticksLeft(long now) {
        return brewStart == KegRules.NOT_STARTED ? 0 : Math.max(0, KegRules.AGE_TICKS - (now - brewStart));
    }

    /** For tests: moves the batch start, so a batch can be aged without waiting. Public because tests live elsewhere. */
    public void setBrewStartForTest(long start) {
        this.brewStart = start;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Sugar", sugar);
        tag.putBoolean("Honey", honey);
        tag.putBoolean("Wheat", wheat);
        tag.putLong("BrewStart", brewStart);
        tag.putInt("Servings", servings);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sugar = tag.getBoolean("Sugar");
        honey = tag.getBoolean("Honey");
        wheat = tag.getBoolean("Wheat");
        brewStart = tag.contains("BrewStart") ? tag.getLong("BrewStart") : KegRules.NOT_STARTED;
        servings = tag.getInt("Servings");
    }
}
