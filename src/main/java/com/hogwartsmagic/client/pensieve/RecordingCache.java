package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordingCodec;
import com.hogwartsmagic.pensieve.capture.RecordingScene;
import com.hogwartsmagic.pensieve.net.PensieveRecordingPacket;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * The recorded memory the client is about to watch, collected from the parts the server sends ahead of the start
 * packet. One recording is held at a time: a part of another scene id drops the partial collection, and a finished
 * recording is decoded once (on first use) into its {@link Recording} and the story {@link SceneDef}.
 */
public final class RecordingCache {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The collection in progress: its scene id, the parts so far and how many have arrived. */
    private static String partialId;
    private static byte[][] partial;
    private static int received;

    /** The finished collection, still encoded until first asked for. */
    private static String doneId;
    private static byte[] blob;
    private static boolean decoded;
    private static Recording recording;
    private static SceneDef scene;

    private RecordingCache() {
    }

    public static void accept(PensieveRecordingPacket part) {
        if (partial == null || !part.sceneId().equals(partialId) || partial.length != part.parts()) {
            partialId = part.sceneId();
            partial = new byte[part.parts()][];
            received = 0;
        }
        if (partial[part.part()] == null) received++;
        partial[part.part()] = part.data();
        if (received < partial.length) return;
        int total = 0;
        for (byte[] p : partial) total += p.length;
        byte[] all = new byte[total];
        int at = 0;
        for (byte[] p : partial) {
            System.arraycopy(p, 0, all, at, p.length);
            at += p.length;
        }
        doneId = partialId;
        blob = all;
        decoded = false;
        recording = null;
        scene = null;
        partialId = null;
        partial = null;
        received = 0;
    }

    /** The story scene of the recording {@code id}, or null while it is incomplete or could not be decoded. */
    @Nullable
    public static SceneDef scene(String id) {
        decode(id);
        return decoded && id.equals(doneId) ? scene : null;
    }

    @Nullable
    public static Recording recording(String id) {
        decode(id);
        return decoded && id.equals(doneId) ? recording : null;
    }

    private static void decode(String id) {
        if (decoded || blob == null || !id.equals(doneId)) return;
        decoded = true;
        try {
            recording = RecordingCodec.read(blob);
            scene = RecordingScene.toScene(recording);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Pensieve: could not read recorded memory '{}': {}", id, e.toString());
            recording = null;
            scene = null;
        }
        blob = null;
    }

    public static void clear() {
        partialId = null;
        partial = null;
        received = 0;
        doneId = null;
        blob = null;
        decoded = false;
        recording = null;
        scene = null;
    }
}
