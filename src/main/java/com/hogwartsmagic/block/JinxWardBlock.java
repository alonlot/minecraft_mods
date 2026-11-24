package com.hogwartsmagic.block;

/**
 * The jinx laid over a hidden house trapped chest, invisible like any glamour. Opening the chest
 * before Revelio lifts the ward fires the hex once ({@code HiddenHouseJinx}); unmasking the house
 * disarms it. It holds no real block, so on reveal it simply becomes air.
 */
public class JinxWardBlock extends GlamouredBlock {
    public JinxWardBlock(Properties properties) {
        super(properties);
    }
}
