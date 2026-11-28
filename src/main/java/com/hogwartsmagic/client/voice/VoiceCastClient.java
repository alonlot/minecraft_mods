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
 * Voice casting, in one of two modes ({@link VoiceCastConfig#MODE}):
 * <ul>
 *   <li>open mic (the default): the microphone is open while a wand is in the main hand, and a spell said on its
 *   own casts. A spell name inside a sentence does not ({@code IncantationMatcher.match(text, true)}).</li>
 *   <li>push-to-talk: hold the voice key, say a spell, let go.</li>
 * </ul>
 * The speech is transcribed on this machine and the spell is cast exactly as if it were picked on the radial menu
 * ({@code ModEventsClientHooks.selectSpellAndCast}), so the server sees nothing new: the same select and cast
 * packets, checked the same way (learned, cooldown, mana, stun, silence). Hold spells (Fiendfyre, the Patronus
 * shield, Wingardium Leviosa, Crucio, Imperio at Master, Protego Maxima, a lock pick) then wait
 * {@code ClientModEvents.VOICE_HOLD_GRACE_TICKS} (1.5 s) for right-click to go down and keep them going.
 *
 * <p>Passwords: while the server says a Mini Hogwarts gargoyle is listening (the player stands by a closed one), a
 * short phrase heard in either mode is also sent to the server, which checks it against the password. Nothing heard is
 * sent at any other time.</p>
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

    /** Ticks the open mic stays open after the wand leaves the main hand, so scrolling past it does not churn. */
    private static final int OPEN_MIC_GRACE_TICKS = 20;
    /** After a microphone failed to open, the open mic tries again this many ticks later (not every tick). */
    private static final int OPEN_MIC_RETRY_TICKS = 200;

    private static final MicRecorder RECORDER = new MicRecorder();
    private static final WhisperEngine ENGINE = new WhisperEngine();
    private static final OpenMicListener OPEN_MIC = new OpenMicListener(ENGINE);
    private static Phase phase = Phase.IDLE;
    private static int tail;
    private static boolean failureReported;
    private static int openMicGrace;
    private static int openMicRetry;
    private static boolean noMicReported;
    /** A gargoyle is listening to this player (server's word): pass short heard phrases on. */
    private static boolean gargoyleListening;
    /** Longest phrase passed to a gargoyle, in words: a password, not a conversation. */
    private static final int PASSWORD_MAX_WORDS = 6;

    private VoiceCastClient() {
    }

    public static void setGargoyleListening(boolean on) {
        gargoyleListening = on;
    }

    /**
     * Hand a short heard phrase to a listening gargoyle; true when it was sent (the caller then shows no "heard nothing
     * known" line, the gargoyle answers instead).
     */
    private static boolean tellGargoyle(String text) {
        if (!gargoyleListening || text == null || text.isBlank()) {
            return false;
        }
        String t = text.strip();
        if (t.split("\\s+").length > PASSWORD_MAX_WORDS || t.length() > com.hogwartsmagic.network.SpellPackets.VoiceSpokenPacket.MAX_LENGTH) {
            return false;
        }
        com.hogwartsmagic.network.SpellPackets.CHANNEL.send(new com.hogwartsmagic.network.SpellPackets.VoiceSpokenPacket(t),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg());
        return true;
    }

    /** The open voice practice screen: while set, everything heard goes to it and nothing casts. */
    private static VoicePracticeScreen practice;
    /** {@code /voicepractice} asks for the screen; it opens on the next tick, after the chat screen has closed. */
    private static boolean practiceRequested;
    /** Ticks until the known spells are read again for the recogniser's prompt. */
    private static int promptRefresh;
    private static final int PROMPT_REFRESH_TICKS = 40;
    /**
     * Prime the recogniser with only the spells the player knows (true) or every spell (false). Measured with
     * tools/spell_voice/run_bench.sh on a weak microphone (2026-11-28): open mic 35/42 against 32/42 on tiny,
     * 38/42 against 33/42 on base. Matching still runs against every spell, so an unknown one still says so.
     */
    static final boolean PROMPT_KNOWN_ONLY = true;

    static void requestPractice() {
        practiceRequested = true;
    }

    /** The practice screen is open: listen with the open mic whatever the mode, and send it what is heard. */
    static void beginPractice(VoicePracticeScreen screen) {
        practice = screen;
        if (phase == Phase.RECORDING || phase == Phase.TAIL) {
            RECORDER.stopQuietly();
            phase = Phase.IDLE;
        }
        ENGINE.warmUp();
    }

    static void endPractice(VoicePracticeScreen screen) {
        if (practice == screen) {
            practice = null;
            OPEN_MIC.stop(); // the open mic mode starts it again next tick if a wand is held
        }
    }

    /** The speech engine's state and model, for the practice screen. */
    static WhisperEngine.State engineState() {
        return ENGINE.state();
    }

    static VoiceCastConfig.Model engineModel() {
        return ENGINE.model();
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft mc) {
        boolean down = KEY.isDown();
        Player player = mc.player;
        if (player == null) {
            abort();
            return;
        }
        if (--promptRefresh <= 0) {
            promptRefresh = PROMPT_REFRESH_TICKS;
            refreshPrompt(player);
        }
        if (practiceRequested) {
            practiceRequested = false;
            mc.setScreen(new VoicePracticeScreen());
        }
        if (practice != null) {
            if (mc.screen != practice) {
                endPractice(practice);
            } else {
                tickPractice(mc, player);
                return;
            }
        }
        if (VoiceCastConfig.MODE.get() == VoiceCastConfig.Mode.WAND_OPEN_MIC) {
            if (phase == Phase.RECORDING || phase == Phase.TAIL) {
                RECORDER.stopQuietly(); // the mode was switched mid-take
                phase = Phase.IDLE;
            }
            tickOpenMic(mc, player);
            return;
        }
        if (OPEN_MIC.isRunning()) {
            OPEN_MIC.stop();
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

    /** Open mic: listen while a wand is in the main hand (with a short grace), stop otherwise. */
    private static void tickOpenMic(Minecraft mc, Player player) {
        boolean wanted = VoiceCastConfig.ENABLED.get()
                && MagicWandItem.isAnyWand(player.getMainHandItem())
                && !com.hogwartsmagic.client.PossessionClient.isCaster()
                && ENGINE.state() != WhisperEngine.State.FAILED;
        if (wanted) {
            openMicGrace = OPEN_MIC_GRACE_TICKS;
        } else if (openMicGrace > 0) {
            openMicGrace--;
        }
        boolean keepOpen = wanted || (openMicGrace > 0 && VoiceCastConfig.ENABLED.get()
                && ENGINE.state() != WhisperEngine.State.FAILED);
        if (openMicRetry > 0) {
            openMicRetry--;
        }
        if (keepOpen && !OPEN_MIC.isRunning() && wanted && openMicRetry == 0) {
            ENGINE.warmUp();
            int threads = VoiceCastConfig.threads(VoiceCastConfig.THREADS.get(), Runtime.getRuntime().availableProcessors());
            if (!OPEN_MIC.start(threads, (heard, error) -> mc.execute(() -> onOpenMicHeard(mc, heard, error)))) {
                openMicRetry = OPEN_MIC_RETRY_TICKS;
                if (!noMicReported) {
                    noMicReported = true;
                    player.displayClientMessage(Component.translatable("voice.hogwartsmagic.no_microphone")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        } else if (!keepOpen && OPEN_MIC.isRunning()) {
            OPEN_MIC.stop();
        }
        if (ENGINE.state() == WhisperEngine.State.FAILED) {
            reportFailure(player);
        }
    }

    /** Practice: keep the open mic going (any mode, wand or not) for as long as the screen is up. */
    private static void tickPractice(Minecraft mc, Player player) {
        if (openMicRetry > 0) {
            openMicRetry--;
        }
        if (!OPEN_MIC.isRunning() && openMicRetry == 0 && ENGINE.state() != WhisperEngine.State.FAILED) {
            int threads = VoiceCastConfig.threads(VoiceCastConfig.THREADS.get(), Runtime.getRuntime().availableProcessors());
            if (!OPEN_MIC.start(threads, (heard, error) -> mc.execute(() -> onOpenMicHeard(mc, heard, error)))) {
                openMicRetry = OPEN_MIC_RETRY_TICKS;
                practice.microphoneMissing();
            }
        }
    }

    /** Prime the recogniser with the spells this player knows (or all, see {@link #PROMPT_KNOWN_ONLY}). */
    private static void refreshPrompt(Player player) {
        if (!PROMPT_KNOWN_ONLY) {
            return;
        }
        java.util.List<String> known = new java.util.ArrayList<>();
        for (SpellType s : SpellType.values()) {
            if (MagicBookItem.isSpellLearned(player, s)) {
                known.add(s.getId());
            }
        }
        ENGINE.setPrompt(IncantationMatcher.recogniserPrompt(known));
    }

    /** The matcher with this computer's practice aliases for the model that heard the take. */
    private static IncantationMatcher.Result match(WhisperEngine.Heard heard, boolean strict) {
        return IncantationMatcher.match(heard.text(), strict, VoiceAliasStore.view(heard.model()));
    }

    private static void saveTake(WhisperEngine.Heard heard, String mode, IncantationMatcher.Result result) {
        TakeLog.save(heard, mode, "", result.matched() ? result.spellId() : "", result.distance(), result.margin());
    }

    private static void onOpenMicHeard(Minecraft mc, WhisperEngine.Heard heard, Throwable error) {
        Player player = mc.player;
        if (player == null || error != null || heard == null) {
            return; // an engine failure is reported by the tick
        }
        if (practice != null) {
            if (mc.screen == practice) {
                practice.onHeard(heard);
            }
            return; // practice never casts
        }
        if (VoiceCastConfig.MODE.get() != VoiceCastConfig.Mode.WAND_OPEN_MIC) {
            return;
        }
        String text = heard.text();
        tellGargoyle(text);
        // Strict: the open mic hears all talk, so only a spell said on its own casts. Nothing is shown for talk.
        IncantationMatcher.Result result = match(heard, true);
        if (!text.isBlank()) {
            saveTake(heard, "open", result);
        }
        SpellType spell = result.matched() ? byId(result.spellId()) : null;
        if (spell != null) {
            castHeard(mc, player, spell);
        }
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
        ENGINE.transcribe(audio, threads).whenComplete((heard, error) -> mc.execute(() -> onHeard(mc, heard, error)));
    }

    private static void onHeard(Minecraft mc, WhisperEngine.Heard heard, Throwable error) {
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
        String text = heard.text();
        boolean toldGargoyle = tellGargoyle(text);
        IncantationMatcher.Result result = match(heard, false);
        if (!text.isBlank()) {
            saveTake(heard, "ptt", result);
        }
        SpellType spell = result.matched() ? byId(result.spellId()) : null;
        if (spell == null) {
            if (toldGargoyle) {
                return;                                   // the gargoyle answers on the action bar
            }
            if (VoiceCastConfig.SHOW_HEARD.get() && !text.isBlank()) {
                player.displayClientMessage(Component.translatable("voice.hogwartsmagic.heard_nothing_known", text)
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                clearActionBar(mc);
            }
            return;
        }
        clearActionBar(mc);
        castHeard(mc, player, spell);
    }

    /** The shared end of both modes: still holding a wand, no screen open, the spell is known, then cast. */
    private static void castHeard(Minecraft mc, Player player, SpellType spell) {
        if (!MagicWandItem.isAnyWand(player.getMainHandItem()) || mc.screen != null) {
            return; // put away, or a screen opened while the words were being worked out
        }
        if (!MagicBookItem.isSpellLearned(player, spell)) {
            player.displayClientMessage(Component.translatable("voice.hogwartsmagic.not_known",
                    Component.translatable("spell.hogwartsmagic." + spell.getId())).withStyle(ChatFormatting.RED), true);
            return;
        }
        // Sneaking while saying a self-cast spell (Protego, Episkey, Lumos, Arresto Momentum, Reparo) casts it on
        // yourself, as a left-click would. The stamp tells the server this cast was spoken (voiceOnlySpells).
        boolean selfCast = player.isShiftKeyDown() && com.hogwartsmagic.event.ModEvents.isLeftClickSelfCast(spell);
        com.hogwartsmagic.network.SpellPackets.CHANNEL.send(
                new com.hogwartsmagic.network.SpellPackets.VoiceCastStampPacket(spell.getId(), selfCast),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg());
        com.hogwartsmagic.client.ModEventsClientHooks.selectSpellAndCastByVoice(player, spell, selfCast);
        if (!selfCast) {
            // A hold spell cannot already have right-click down: give it a short window to go down and keep it.
            com.hogwartsmagic.event.ClientModEvents.startVoiceHoldGrace();
        }
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

    /** Logging out closes the microphone in both modes. */
    private static void abort() {
        com.hogwartsmagic.event.ClientModEvents.setVoiceOnly(false); // the next server sends its own value on login
        gargoyleListening = false;
        practice = null;
        practiceRequested = false;
        if (OPEN_MIC.isRunning()) {
            OPEN_MIC.stop();
        }
        openMicGrace = 0;
        if (phase == Phase.RECORDING || phase == Phase.TAIL) {
            RECORDER.stopQuietly();
        }
        if (phase != Phase.THINKING) {
            phase = Phase.IDLE;
        }
    }
}
