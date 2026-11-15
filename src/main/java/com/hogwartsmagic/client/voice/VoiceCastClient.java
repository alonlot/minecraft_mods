package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.voice.IncantationMatcher;
import com.hogwartsmagic.spell.voice.VoiceCastConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * Voice casting: hold the voice key with a wand in the main hand, say a spell, let go. The take is transcribed on
 * this machine and the spell is cast exactly as if it were picked on the radial menu
 * ({@code ModEventsClientHooks.selectSpellAndCast}), so the server sees nothing new: the same select and cast
 * packets, checked the same way (learned, cooldown, mana, stun, silence). Hold spells work as on the radial menu:
 * keep right-click down for Fiendfyre, the Patronus shield, Wingardium Leviosa and a lock pick.
 */
public final class VoiceCastClient {
    public static final KeyMapping KEY = new KeyMapping(
            "key.hogwartsmagic.voice_cast",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.hogwartsmagic");

    /** Ticks the microphone stays open after the key goes up: players let go as the last syllable ends. */
    private static final int TAIL_TICKS = 4;
    /** Takes shorter than this (key tapped by mistake) are dropped without running the model. */
    private static final int MIN_TAKE_SAMPLES = MicRecorder.TARGET_RATE / 5;

    private enum Phase { IDLE, RECORDING, TAIL, THINKING, WAIT_RELEASE }

    private static final MicRecorder RECORDER = new MicRecorder();
    private static final WhisperEngine ENGINE = new WhisperEngine();
    private static Phase phase = Phase.IDLE;
    private static int tail;
    private static boolean failureReported;

    private VoiceCastClient() {
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft mc) {
        boolean down = KEY.isDown();
        Player player = mc.player;
        if (player == null) {
            abort();
            return;
        }
        // if/else, not a switch: the project keeps enum switches out of client code.
        if (phase == Phase.IDLE) {
            if (down) {
                begin(mc, player);
            }
        } else if (phase == Phase.RECORDING) {
            if (!down || !RECORDER.isRecording()) {
                tail = RECORDER.isRecording() ? TAIL_TICKS : 0;
                phase = Phase.TAIL;
            }
        } else if (phase == Phase.TAIL) {
            if (--tail <= 0) {
                finish(mc);
            }
        } else if (phase == Phase.WAIT_RELEASE) {
            if (!down) {
                phase = Phase.IDLE;
            }
        }
        // THINKING: the result comes back through mc.execute; presses meanwhile are ignored.
    }

    private static void begin(Minecraft mc, Player player) {
        phase = Phase.WAIT_RELEASE; // every early return below waits for the key to go up first
        if (!VoiceCastConfig.ENABLED.get() || mc.screen != null) {
            return;
        }
        if (!MagicWandItem.isAnyWand(player.getMainHandItem())) {
            return; // the server only casts from the main hand
        }
        if (com.hogwartsmagic.client.PossessionClient.isCaster()) {
            return; // a possessing caster casts through the puppet wheel
        }
        if (ENGINE.state() == WhisperEngine.State.FAILED) {
            reportFailure(player);
            return;
        }
        ENGINE.warmUp();
        if (!RECORDER.start(VoiceCastConfig.MAX_SECONDS.get())) {
            player.displayClientMessage(Component.translatable("voice.hogwartsmagic.no_microphone")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        player.displayClientMessage(Component.translatable("voice.hogwartsmagic.listening")
                .withStyle(ChatFormatting.AQUA), true);
        phase = Phase.RECORDING;
    }

    private static void finish(Minecraft mc) {
        float[] audio = RECORDER.stop();
        if (audio.length < MIN_TAKE_SAMPLES) {
            phase = Phase.WAIT_RELEASE;
            clearActionBar(mc);
            return;
        }
        phase = Phase.THINKING;
        int threads = VoiceCastConfig.threads(VoiceCastConfig.THREADS.get(), Runtime.getRuntime().availableProcessors());
        ENGINE.transcribe(audio, threads).whenComplete((text, error) -> mc.execute(() -> onHeard(mc, text, error)));
    }

    private static void onHeard(Minecraft mc, String text, Throwable error) {
        phase = Phase.WAIT_RELEASE;
        Player player = mc.player;
        if (player == null) {
            return;
        }
        if (error != null) {
            if (ENGINE.state() == WhisperEngine.State.FAILED) {
                reportFailure(player);
            }
            clearActionBar(mc);
            return;
        }
        IncantationMatcher.Result result = IncantationMatcher.match(text);
        SpellType spell = result.matched() ? byId(result.spellId()) : null;
        if (spell == null) {
            if (VoiceCastConfig.SHOW_HEARD.get() && !text.isBlank()) {
                player.displayClientMessage(Component.translatable("voice.hogwartsmagic.heard_nothing_known", text)
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                clearActionBar(mc);
            }
            return;
        }
        if (!MagicWandItem.isAnyWand(player.getMainHandItem()) || mc.screen != null) {
            clearActionBar(mc); // put away or a screen opened while the words were being worked out
            return;
        }
        if (!MagicBookItem.isSpellLearned(player, spell)) {
            player.displayClientMessage(Component.translatable("voice.hogwartsmagic.not_known",
                    Component.translatable("spell.hogwartsmagic." + spell.getId())).withStyle(ChatFormatting.RED), true);
            return;
        }
        clearActionBar(mc);
        com.hogwartsmagic.client.ModEventsClientHooks.selectSpellAndCast(player, spell);
    }

    private static SpellType byId(String id) {
        for (SpellType s : SpellType.values()) {
            if (s.getId().equals(id)) {
                return s; // not SpellType.fromId: it falls back to Wingardium for an unknown id
            }
        }
        return null;
    }

    private static void reportFailure(Player player) {
        if (!failureReported) {
            failureReported = true;
            player.displayClientMessage(Component.translatable("voice.hogwartsmagic.engine_failed")
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    private static void clearActionBar(Minecraft mc) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.empty(), true);
        }
    }

    /** Logging out mid-take closes the microphone. */
    private static void abort() {
        if (phase == Phase.RECORDING || phase == Phase.TAIL) {
            RECORDER.stopQuietly();
        }
        if (phase != Phase.THINKING) {
            phase = Phase.IDLE;
        }
    }
}
