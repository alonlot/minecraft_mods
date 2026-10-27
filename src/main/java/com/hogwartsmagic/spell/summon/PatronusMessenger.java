package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.dark.DarkCosts;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.spell.support.SupportMana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A Master of Expecto Patronum can send a message by Patronus to a player up to 500 blocks away in the
 * same dimension. The caster's Patronus leaves in a burst of light and a messenger Patronus in the
 * caster's form appears beside the recipient for five seconds while the message is spoken in chat. It
 * costs the spell's mana and has its own 30-second cooldown (the combat cooldown is untouched). Server
 * only; the text is sanitised, capped and delivered as a plain literal component, attributed to the sender.
 * A caster whose Dark Magic has taken the corporeal Patronus away has no messenger either.
 */
public final class PatronusMessenger {
    private static final String TAG_LAST_SENT = "hogwartsmagic:patronus_message_at";

    private PatronusMessenger() {
    }

    public static boolean send(ServerPlayer sender, ServerPlayer recipient, String rawText) {
        SpellType spell = SpellType.EXPECTO_PATRONUM;
        ItemStack wand = sender.getMainHandItem();
        if (!MagicWandItem.isAnyWand(wand)) {
            fail(sender, "message.hogwartsmagic.patronus.need_wand");
            return false;
        }
        if (!MagicBookItem.isSpellLearned(sender, spell)
                || !PatronusRules.sendsMessages(SpellMasteryData.getRank(sender, spell))) {
            fail(sender, "message.hogwartsmagic.patronus.need_master");
            return false;
        }
        if (CombatEffects.isCastingBlocked(sender)) {
            CombatEffects.notifyCastBlocked(sender);
            return false;
        }
        // The Tainted rule: no corporeal Patronus, so no messenger.
        if (DarkCosts.corporealPatronusChance(sender, 1.0f) <= 0.0f) {
            fail(sender, "message.hogwartsmagic.patronus.too_dark");
            return false;
        }
        if (recipient == sender) {
            fail(sender, "message.hogwartsmagic.patronus.self");
            return false;
        }
        if (recipient.hasDisconnected() || recipient.level() != sender.level()) {
            fail(sender, "message.hogwartsmagic.patronus.out_of_reach", recipient.getDisplayName());
            return false;
        }
        if (!PatronusRules.inMessageRange(recipient.distanceToSqr(sender))) {
            fail(sender, "message.hogwartsmagic.patronus.too_far", recipient.getDisplayName());
            return false;
        }
        String text = PatronusRules.sanitizeMessage(rawText);
        if (text.isEmpty()) {
            fail(sender, "message.hogwartsmagic.patronus.empty");
            return false;
        }
        // The cooldown applies to everyone, creative included: it is the spam limit on a message that
        // lands in another player's chat.
        CompoundTag data = sender.getPersistentData();
        long now = sender.level().getGameTime();
        long left = PatronusRules.messageCooldownLeft(data.contains(TAG_LAST_SENT), data.getLong(TAG_LAST_SENT), now);
        if (left > 0L) {
            fail(sender, "message.hogwartsmagic.patronus.cooldown", (left + 19L) / 20L);
            return false;
        }
        if (!SupportMana.trySpend(sender, SpellCosts.manaCost(sender, wand, spell))) {
            sender.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return false;
        }
        data.putLong(TAG_LAST_SENT, now);

        departFrom(sender);
        arriveAt(recipient, sender, text);
        sender.sendSystemMessage(Component.translatable("message.hogwartsmagic.patronus.sent", recipient.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    private static void departFrom(ServerPlayer sender) {
        ServerLevel level = sender.serverLevel();
        ModEvents.revealInvisibilityCloak(sender);
        level.sendParticles(ParticleTypes.END_ROD, sender.getX(), sender.getY() + 1.2D, sender.getZ(),
                30, 0.6D, 0.8D, 0.6D, 0.08D);
        level.playSound(null, sender.getX(), sender.getY(), sender.getZ(),
                SpellType.EXPECTO_PATRONUM.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** The messenger appears in front of the recipient, but only in open air; the words arrive either way. */
    private static void arriveAt(ServerPlayer recipient, ServerPlayer sender, String text) {
        ServerLevel level = recipient.serverLevel();
        PatronusEntity messenger = new PatronusEntity(ModEntities.PATRONUS.get(), level);
        messenger.setFormId(PatronusSpell.formFor(sender).id());
        messenger.setLifespan(PatronusRules.MESSENGER_TICKS);
        messenger.setOwner(sender.getUUID());
        messenger.setMasterAura(false);
        Vec3 look = recipient.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        boolean placed = false;
        for (double distance : new double[]{2.0D, 1.5D, 1.0D}) {
            Vec3 at = recipient.position().add(flat.scale(distance)).add(0.0D, 0.5D, 0.0D);
            messenger.moveTo(at.x, at.y, at.z, recipient.getYRot() + 180.0F, 0.0F);
            if (level.noCollision(messenger)) {
                placed = true;
                break;
            }
        }
        if (placed) {
            level.addFreshEntity(messenger);
            level.sendParticles(ParticleTypes.END_ROD, messenger.getX(), messenger.getY() + 0.8D, messenger.getZ(),
                    24, 0.5D, 0.6D, 0.5D, 0.05D);
        }
        level.playSound(null, recipient.getX(), recipient.getY(), recipient.getZ(),
                ModSounds.PATRONUS_AMBIENT.get(), SoundSource.PLAYERS, 0.9f, 1.2f);
        // The text is a literal argument of a translatable component: it is never parsed for formatting.
        recipient.sendSystemMessage(Component.translatable("message.hogwartsmagic.patronus.delivered",
                sender.getDisplayName(), Component.literal(text)).withStyle(ChatFormatting.AQUA));
    }

    private static void fail(ServerPlayer sender, String key, Object... args) {
        sender.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }
}
