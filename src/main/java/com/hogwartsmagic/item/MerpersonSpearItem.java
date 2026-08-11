package com.hogwartsmagic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A merperson's spear: a barbed shaft of lake-bottom stone lashed to driftwood.
 *
 * <p>It is built for water. The attributes here are the <em>land</em> numbers -
 * unremarkable, a little slow, roughly an iron sword that swings badly. The
 * bonus that makes it dangerous is applied by
 * {@code ModEvents#onMerpersonSpearHit} only while the wielder is submerged,
 * which is where a merperson would ever be using it.
 *
 * <p>Damage is granted as a bonus rather than taken away as a penalty on land so
 * there is no path to a negative-damage hit however the stack is enchanted.
 */
public class MerpersonSpearItem extends SwordItem {

    /** Extra damage the spear does when the attacker is underwater. */
    public static final float SUBMERGED_BONUS = 4.0F;

    public MerpersonSpearItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static Properties properties(Tier tier) {
        return new Item.Properties()
                .attributes(SwordItem.createAttributes(tier, 2, -2.6F))
                .durability(320);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.hogwartsmagic.merperson_spear.desc")
                .withStyle(ChatFormatting.AQUA));
    }
}
