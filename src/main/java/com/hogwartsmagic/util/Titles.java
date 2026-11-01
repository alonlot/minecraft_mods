package com.hogwartsmagic.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** Screen titles, sent the way the {@code /title} command does. Shared by the duels and the castle title card. */
public final class Titles {
    private Titles() {
    }

    /** {@link #sendTitle} with plain text; {@code subtitle} may be null for a title alone. */
    public static void send(ServerPlayer player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        sendTitle(player, Component.literal(title), subtitle == null ? null : Component.literal(subtitle),
                fadeIn, stay, fadeOut);
    }

    /**
     * Shows {@code title} (and {@code subtitle}, when not null) to one player. The three packets go in this order
     * because the title text is what makes the client start the fade; the timings and subtitle must already be set.
     */
    public static void sendTitle(ServerPlayer player, Component title, Component subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
