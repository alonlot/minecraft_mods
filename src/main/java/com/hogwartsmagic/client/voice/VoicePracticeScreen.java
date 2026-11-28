package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.voice.IncantationMatcher;
import com.hogwartsmagic.spell.voice.VoiceAliases;
import com.hogwartsmagic.spell.voice.VoiceCastConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Voice practice: the player says each spell they know three times, and the mod learns how the speech model hears
 * this voice. A take the model already gets right is just counted; a consistent mishearing ("Refillio" for Revelio)
 * is kept as a personal alias for that spell ({@link VoiceAliases}), unless it is ordinary talk or sounds like
 * another spell. Nothing casts while the screen is open. The microphone is the open mic (any mode), so the player
 * just speaks; {@link VoiceCastClient} routes every take here.
 *
 * <p>No {@code switch} on enums in here (the project keeps them out of client code).</p>
 */
public final class VoicePracticeScreen extends Screen {
    static final int TAKES_PER_SPELL = 3;
    /** Ticks the "done" line stays up before the next spell. */
    private static final int ADVANCE_TICKS = 30;
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 216;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_BODY = 0xFFD8C9F0;
    private static final int COLOR_DIM = 0xFF8A80A0;
    private static final int COLOR_GOOD = 0xFF9BE38B;
    private static final int COLOR_LEARN = 0xFF6FC2FF;
    private static final int COLOR_BAD = 0xFFFF8A7A;

    private record Line(Component text, int color) {
    }

    private final List<SpellType> spells = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int index;
    private int takes;
    private int advanceIn = -1;
    private int heardRight;
    private int learned;
    private boolean finished;
    private boolean noMicrophone;
    private int age;
    private int panelLeft;
    private int panelTop;
    private Button recordingsButton;

    public VoicePracticeScreen() {
        super(Component.translatable("voice.hogwartsmagic.practice.title"));
    }

    @Override
    protected void init() {
        super.init();
        if (spells.isEmpty()) {
            collectSpells();
        }
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        int y = panelTop + PANEL_HEIGHT - 26;
        int x = panelLeft + 10;
        addRenderableWidget(Button.builder(Component.translatable("voice.hogwartsmagic.practice.back"), b -> step(-1))
                .bounds(x, y, 46, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("voice.hogwartsmagic.practice.skip"), b -> step(1))
                .bounds(x + 50, y, 46, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("voice.hogwartsmagic.practice.forget"), b -> forget())
                .bounds(x + 100, y, 70, 18).build());
        recordingsButton = addRenderableWidget(Button.builder(recordingsLabel(), b -> toggleRecordings())
                .bounds(x + 174, y, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), b -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 66, y, 56, 18).build());
        VoiceCastClient.beginPractice(this);
    }

    /** The spells the player knows, in the matcher's order; every spell when none is known yet. */
    private void collectSpells() {
        Player player = Minecraft.getInstance().player;
        List<SpellType> all = new ArrayList<>();
        for (String id : IncantationMatcher.incantations().keySet()) {
            SpellType s = byId(id);
            if (s == null) {
                continue;
            }
            all.add(s);
            if (player != null && MagicBookItem.isSpellLearned(player, s)) {
                spells.add(s);
            }
        }
        if (spells.isEmpty()) {
            spells.addAll(all);
        }
    }

    private static SpellType byId(String id) {
        for (SpellType s : SpellType.values()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    private SpellType current() {
        return spells.get(Math.min(index, spells.size() - 1));
    }

    private void step(int by) {
        finished = false;
        index = Math.floorMod(index + by, spells.size());
        takes = 0;
        advanceIn = -1;
        lines.clear();
    }

    private void forget() {
        VoiceCastConfig.Model model = VoiceCastClient.engineModel();
        VoiceAliasStore.forModel(model).clear(current().getId());
        VoiceAliasStore.save();
        lines.add(new Line(Component.translatable("voice.hogwartsmagic.practice.forgot"), COLOR_DIM));
    }

    private Component recordingsLabel() {
        return Component.translatable(VoiceCastConfig.SAVE_TAKES.get()
                ? "voice.hogwartsmagic.practice.recordings_on" : "voice.hogwartsmagic.practice.recordings_off");
    }

    private void toggleRecordings() {
        VoiceCastConfig.SAVE_TAKES.set(!VoiceCastConfig.SAVE_TAKES.get());
        VoiceCastConfig.SAVE_TAKES.save();
        recordingsButton.setMessage(recordingsLabel());
    }

    void microphoneMissing() {
        noMicrophone = true;
    }

    /** One take, on the render thread. Silence and takes during the pause between spells are ignored. */
    void onHeard(WhisperEngine.Heard heard) {
        noMicrophone = false;
        if (finished || advanceIn >= 0 || heard.text().isBlank()) {
            return;
        }
        SpellType spell = current();
        VoiceAliases aliases = VoiceAliasStore.forModel(heard.model());
        IncantationMatcher.Result before = IncantationMatcher.match(heard.text(), true, aliases.view());
        VoiceAliases.Judgement j = aliases.practise(spell.getId(), heard.text());
        TakeLog.save(heard, "practice", spell.getId(), before.matched() ? before.spellId() : "", before.distance(),
                before.margin());
        String q = "\"" + heard.text() + "\"";
        VoiceAliases.Verdict v = j.verdict();
        if (v == VoiceAliases.Verdict.RIGHT) {
            heardRight++;
            lines.add(new Line(Component.translatable("voice.hogwartsmagic.practice.right", q), COLOR_GOOD));
        } else if (v == VoiceAliases.Verdict.LEARNED) {
            learned++;
            VoiceAliasStore.save();
            lines.add(new Line(Component.translatable("voice.hogwartsmagic.practice.learned", q), COLOR_LEARN));
        } else if (v == VoiceAliases.Verdict.ALREADY_KNOWN) {
            heardRight++;
            lines.add(new Line(Component.translatable("voice.hogwartsmagic.practice.already", q), COLOR_GOOD));
        } else if (v == VoiceAliases.Verdict.TALK) {
            lines.add(new Line(Component.translatable("voice.hogwartsmagic.practice.talk", q), COLOR_BAD));
        } else if (v == VoiceAliases.Verdict.OTHER_SPELL) {
            SpellType other = byId(j.otherSpellId());
            Component name = other == null ? Component.literal(String.valueOf(j.otherSpellId()))
                    : Component.translatable("spell.hogwartsmagic." + other.getId());
            lines.add(new Line(Component.translatable("voice.hogwartsmagic.practice.other", q, name), COLOR_BAD));
        } else {
            return; // NOTHING: a noise, not a take
        }
        while (lines.size() > 5) {
            lines.remove(0);
        }
        takes++;
        if (takes >= TAKES_PER_SPELL) {
            advanceIn = ADVANCE_TICKS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (advanceIn > 0 && --advanceIn == 0) {
            advanceIn = -1;
            if (index + 1 >= spells.size()) {
                finished = true;
            } else {
                step(1);
            }
        }
    }

    @Override
    public void removed() {
        VoiceCastClient.endPractice(this);
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render() calls this after the panel is drawn (blur on top); render() dims itself.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        int right = panelLeft + PANEL_WIDTH;
        int bottom = panelTop + PANEL_HEIGHT;
        int cx = panelLeft + PANEL_WIDTH / 2;
        g.fill(panelLeft - 4, panelTop - 4, right + 4, bottom + 4, 0xF0140A1E);
        g.fill(panelLeft - 4, panelTop - 4, right + 4, panelTop - 3, COLOR_GOLD);
        g.fill(panelLeft - 4, bottom + 3, right + 4, bottom + 4, COLOR_GOLD);
        g.fill(panelLeft - 4, panelTop - 4, panelLeft - 3, bottom + 4, COLOR_GOLD);
        g.fill(right + 3, panelTop - 4, right + 4, bottom + 4, COLOR_GOLD);

        g.drawCenteredString(font, title, cx, panelTop + 8, COLOR_GOLD);
        g.drawCenteredString(font, Component.translatable("voice.hogwartsmagic.practice.howto"), cx, panelTop + 22,
                COLOR_DIM);

        if (finished) {
            g.drawCenteredString(font, Component.translatable("voice.hogwartsmagic.practice.finished"), cx,
                    panelTop + 70, COLOR_GOLD);
            g.drawCenteredString(font, Component.translatable("voice.hogwartsmagic.practice.summary", heardRight,
                    learned), cx, panelTop + 86, COLOR_BODY);
        } else {
            SpellType spell = current();
            g.drawCenteredString(font, Component.translatable("voice.hogwartsmagic.practice.progress", index + 1,
                    spells.size()), cx, panelTop + 38, COLOR_DIM);
            String say = IncantationMatcher.incantations().getOrDefault(spell.getId(), spell.getId());
            g.pose().pushPose();
            g.pose().translate(cx, panelTop + 52, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.drawCenteredString(font, say, 0, 0, COLOR_GOLD);
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("voice.hogwartsmagic.practice.take",
                    Math.min(takes + 1, TAKES_PER_SPELL), TAKES_PER_SPELL), cx, panelTop + 76, COLOR_BODY);
            g.drawCenteredString(font, status(), cx, panelTop + 90, statusColor());
            int y = panelTop + 106;
            for (Line l : lines) {
                g.drawCenteredString(font, l.text(), cx, y, l.color());
                y += 11;
            }
        }
        VoiceCastConfig.Model model = VoiceCastClient.engineModel();
        Component foot = Component.translatable("voice.hogwartsmagic.practice.footer",
                model.name().toLowerCase(Locale.ROOT), VoiceAliasStore.forModel(model).size());
        g.drawCenteredString(font, foot, cx, bottom - 40, COLOR_DIM);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component status() {
        WhisperEngine.State s = VoiceCastClient.engineState();
        if (s == WhisperEngine.State.FAILED) {
            return Component.translatable("voice.hogwartsmagic.practice.engine_failed");
        }
        if (noMicrophone) {
            return Component.translatable("voice.hogwartsmagic.practice.no_microphone");
        }
        if (s != WhisperEngine.State.READY) {
            return Component.translatable("voice.hogwartsmagic.practice.loading");
        }
        if (advanceIn >= 0) {
            return Component.translatable("voice.hogwartsmagic.practice.next");
        }
        String dots = ".".repeat(1 + (age / 8) % 3);
        return Component.translatable("voice.hogwartsmagic.practice.listening", dots);
    }

    private int statusColor() {
        WhisperEngine.State s = VoiceCastClient.engineState();
        return s == WhisperEngine.State.FAILED || noMicrophone ? COLOR_BAD : COLOR_BODY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
