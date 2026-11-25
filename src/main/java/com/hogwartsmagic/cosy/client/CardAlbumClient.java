package com.hogwartsmagic.cosy.client;

import net.minecraft.client.Minecraft;

import java.util.List;

/** Client entry point for the card album: the packet handler calls this to open the screen. */
public final class CardAlbumClient {
    private CardAlbumClient() {
    }

    public static void open(List<Integer> collected) {
        Minecraft.getInstance().setScreen(new CardAlbumScreen(collected));
    }
}
