package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The corporeal half of Expecto Patronum. The cast path rolls {@link #rollsCorporeal}; on a hit it calls
 * {@link #summonCorporeal}. Novice and Adept casters summon the generic stag; Expert and Master casters
 * summon their own form, chosen once and kept in PlayerPersisted (it survives death).
 */
public final class PatronusSpell {
    /** PlayerPersisted/hogwartsmagic key: the caster's PatronusForm id, set the first time it is needed. */
    public static final String FORM_KEY = "PatronusForm";

    private PatronusSpell() {
    }

    public static boolean rollsCorporeal(ServerPlayer player) {
        int rank = SpellMasteryData.getRank(player, SpellType.EXPECTO_PATRONUM);
        // Dark Magic halves or removes the rank's chance (DarkCostRules).
        float chance = com.hogwartsmagic.spell.dark.DarkCosts.corporealPatronusChance(player, PatronusRules.corporealChance(rank));
        return player.getRandom().nextFloat() < chance;
    }

    public static PatronusEntity summonCorporeal(ServerPlayer player) {
        int rank = SpellMasteryData.getRank(player, SpellType.EXPECTO_PATRONUM);
        PatronusForm form = PatronusRules.personalForm(rank) ? formFor(player) : PatronusForm.GENERIC;
        PatronusEntity patronus = new PatronusEntity(ModEntities.PATRONUS.get(), player.level());
        patronus.setPos(player.getX(), player.getY(), player.getZ());
        patronus.setYRot(player.getYRot());
        patronus.setYHeadRot(player.getYRot());
        patronus.setYBodyRot(player.getYRot());
        patronus.setFormId(form.id());
        player.level().addFreshEntity(patronus);
        return patronus;
    }

    /**
     * The caster's personal form: picked from their UUID and house the first time it is needed, then
     * stored and never re-rolled, even if the house changes later. The caster is told once.
     */
    public static PatronusForm formFor(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        if (modData.contains(FORM_KEY)) {
            PatronusForm stored = PatronusForm.byId(modData.getInt(FORM_KEY));
            if (stored != PatronusForm.GENERIC) {
                return stored;
            }
        }
        HogwartsHouse house = HousePerks.getHouse(player);
        UUID id = player.getUUID();
        PatronusForm form = PatronusForms.pick(id.getMostSignificantBits(), id.getLeastSignificantBits(),
                house == null ? -1 : house.ordinal());
        modData.putInt(FORM_KEY, form.id());
        PlayerMagicData.saveModData(player, modData);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.patronus.form",
                Component.translatable(form.langKey())).withStyle(ChatFormatting.AQUA), false);
        return form;
    }
}
