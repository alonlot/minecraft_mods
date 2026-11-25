package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.cosy.CardTimeline;
import com.hogwartsmagic.cosy.WizardCards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound cues for one view of a living card: the footfall when the figure starts to walk out (or its clip starts),
 * and Merlin's puff late in his clip. Feed it the state each frame; it notices the change. Client only.
 */
final class CardCues {
    /** Merlin vanishes in a puff, near the end of his clip. */
    private static final String PUFF_KEY = "merlin";
    private static final float PUFF_AT = 0.6F;

    private CardTimeline.State prev;
    private boolean puffed;

    void update(CardTimeline.State now, WizardCards.Card card, CardAssets.Assets assets, float volume) {
        if (this.prev != null && this.prev.idle() && !now.idle() && now.present()) {
            play(ModSounds.CARD_WALK_OUT.get(), 1.0F, volume);
            this.puffed = false;
        }
        if (now.clipFrame() >= 0 && assets.clipFrames() > 0 && !this.puffed && PUFF_KEY.equals(card.key())
                && now.clipFrame() >= assets.clipFrames() * PUFF_AT) {
            play(ModSounds.CARD_PUFF.get(), 1.0F, volume);
            this.puffed = true;
        }
        this.prev = now;
    }

    /** A non-positional sound: the card is in the player's hands or on their screen. */
    static void play(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
