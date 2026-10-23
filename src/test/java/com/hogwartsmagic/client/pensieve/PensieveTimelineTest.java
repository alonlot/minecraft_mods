package com.hogwartsmagic.client.pensieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PensieveTimelineTest {
    @Test
    void surfaceRisesOnlyOverTheLastTwelveTicksOfTheSwallow() {
        assertEquals(0f, PensieveTimeline.surfaceRise(0), 1e-6f);
        assertEquals(0f, PensieveTimeline.surfaceRise(18), 1e-6f);
        assertTrue(PensieveTimeline.surfaceRise(24) > 0.3f && PensieveTimeline.surfaceRise(24) < 0.7f);
        assertEquals(1f, PensieveTimeline.surfaceRise(30), 1e-6f);
    }

    @Test
    void swallowEasesFromEyeToSurface() {
        assertEquals(0f, PensieveTimeline.swallowMove(0), 1e-6f);
        assertEquals(0.5f, PensieveTimeline.swallowMove(15), 1e-4f);
        assertEquals(1f, PensieveTimeline.swallowMove(30), 1e-6f);
        assertEquals(1f, PensieveTimeline.swallowMove(45), 1e-6f);
    }

    @Test
    void revealGrowsFogFromTwoToRadiusAndLiftsTheGrade() {
        float r0 = PensieveTimeline.reveal(0, 60);
        float r1 = PensieveTimeline.reveal(60, 60);
        assertEquals(2f, PensieveTimeline.revealFogRadius(r0, 40), 1e-5f);
        assertEquals(40f, PensieveTimeline.revealFogRadius(r1, 40), 1e-5f);
        assertEquals(0.9f, PensieveTimeline.revealDesaturation(r0), 1e-5f);
        assertEquals(0.35f, PensieveTimeline.revealDesaturation(r1), 1e-5f);
        assertEquals(1f, PensieveTimeline.revealDarkness(r0), 1e-5f);
        assertEquals(0f, PensieveTimeline.revealDarkness(r1), 1e-5f);
        assertEquals(1f, PensieveTimeline.reveal(5, 0), 1e-6f);
    }

    @Test
    void playFogFraysJustInsideAndOutsideTheRadius() {
        assertEquals(32f, PensieveTimeline.fogStart(40), 1e-6f);
        assertEquals(42f, PensieveTimeline.fogEnd(40), 1e-6f);
        assertEquals(0f, PensieveTimeline.fogStart(3), 1e-6f);
    }

    @Test
    void fogFarHazesFromThreeQuartersAndRespectsTheRenderDistance() {
        float[] full = PensieveTimeline.farFog(200, 200, 180, 1000);
        assertEquals(135f, full[0], 1e-4f);
        assertEquals(182f, full[1], 1e-4f);
        float[] capped = PensieveTimeline.farFog(200, 200, 180, 128);
        assertEquals(128f, capped[1], 1e-4f);
        assertTrue(capped[0] < capped[1], "near " + capped[0] + " must stay below far " + capped[1]);
        float[] half = PensieveTimeline.farFog(100, 200, 180, 1000);
        assertEquals(92f, half[1], 1e-4f);
        assertEquals(67.5f, half[0], 1e-4f);
    }

    @Test
    void exitCollapsesTheFogAndRaisesTheDarkInFifteenTicks() {
        assertEquals(40f, PensieveTimeline.exitFogRadius(0, 40), 1e-5f);
        assertEquals(0f, PensieveTimeline.exitFogRadius(15, 40), 1e-5f);
        assertEquals(0f, PensieveTimeline.exitDarkness(0), 1e-6f);
        assertEquals(1f, PensieveTimeline.exitDarkness(15), 1e-6f);
    }

    @Test
    void thudDecaysOverTwelveTicksAndShudderOverSix() {
        assertEquals(0.8f, PensieveTimeline.thud(0.8f, 0), 1e-6f);
        assertEquals(0.4f, PensieveTimeline.thud(0.8f, 6), 1e-6f);
        assertEquals(0f, PensieveTimeline.thud(0.8f, 12), 1e-6f);
        assertEquals(0f, PensieveTimeline.shudder(1f, 6), 1e-6f);
        assertEquals(0f, PensieveTimeline.thud(1f, -1), 1e-6f);
    }

    @Test
    void askedHandOverCarriesBlack() {
        // A chain end from a white scene carries white; a hand-over the server asked of it (fade started) is black.
        assertTrue(PensieveTimeline.carriesWhite(true, true, false));
        assertFalse(PensieveTimeline.carriesWhite(true, true, true));
        assertFalse(PensieveTimeline.carriesWhite(true, false, false));
        assertFalse(PensieveTimeline.carriesWhite(false, true, false));
        assertTrue(PensieveTimeline.sceneFadesWhite(true, true, false));
        assertFalse(PensieveTimeline.sceneFadesWhite(true, true, true));
        assertFalse(PensieveTimeline.sceneFadesWhite(false, true, false));
    }

    @Test
    void whiteHandoverFadesToWhite() {
        float[] mid = PensieveTimeline.handoverColour(false, true, 0f, PensieveTimeline.chainEndDarkness(190, 200));
        assertEquals(0f, mid[0], 1e-6f);
        assertEquals(0.5f, mid[1], 1e-6f);
        float[] end = PensieveTimeline.handoverColour(false, true, 0f, PensieveTimeline.chainEndDarkness(200, 200));
        assertEquals(0f, end[0], 1e-6f);
        assertEquals(1f, end[1], 1e-6f);
        // A black hand-over is the old behaviour: all black, no white.
        float[] black = PensieveTimeline.handoverColour(false, false, 0f, 0.5f);
        assertEquals(0.5f, black[0], 1e-6f);
        assertEquals(0f, black[1], 1e-6f);
        // Nothing covers the view mid-scene.
        float[] none = PensieveTimeline.handoverColour(true, true, 0f, 0f);
        assertEquals(0f, none[0], 1e-6f);
        assertEquals(0f, none[1], 1e-6f);
    }

    @Test
    void whiteHoldsThroughTheBuild() {
        // The hand-over holds the reveal at 0 (darkness 1) while the set is drawn: still fully white.
        float[] held = PensieveTimeline.handoverColour(true, false, PensieveTimeline.revealDarkness(0f), 0f);
        assertEquals(0f, held[0], 1e-6f);
        assertEquals(1f, held[1], 1e-6f);
        // Even if the new scene itself hands over through black later, the held cover is the carried white.
        float[] heldB = PensieveTimeline.handoverColour(true, true, 1f, 0f);
        assertEquals(1f, heldB[1], 1e-6f);
    }

    @Test
    void nextSceneFadesInFromWhite() {
        float half = PensieveTimeline.revealDarkness(PensieveTimeline.reveal(10, PensieveTimeline.LATE_FADE_TICKS));
        float[] in = PensieveTimeline.handoverColour(true, false, half, 0f);
        assertEquals(0f, in[0], 1e-6f);
        assertEquals(half, in[1], 1e-6f);
        float[] done = PensieveTimeline.handoverColour(true, false,
                PensieveTimeline.revealDarkness(PensieveTimeline.reveal(20, PensieveTimeline.LATE_FADE_TICKS)), 0f);
        assertEquals(0f, done[0], 1e-6f);
        assertEquals(0f, done[1], 1e-6f);
        // A carried-black scene fades in from black.
        float[] black = PensieveTimeline.handoverColour(false, false, half, 0f);
        assertEquals(half, black[0], 1e-6f);
        assertEquals(0f, black[1], 1e-6f);
    }

    @Test
    void chainedSceneFadesToBlackOverItsLastTwentyTicksAndHoldsBlack() {
        assertEquals(0f, PensieveTimeline.chainEndDarkness(0, 200), 1e-6f);
        assertEquals(0f, PensieveTimeline.chainEndDarkness(180, 200), 1e-6f);
        assertEquals(0.5f, PensieveTimeline.chainEndDarkness(190, 200), 1e-6f);
        assertEquals(1f, PensieveTimeline.chainEndDarkness(200, 200), 1e-6f);
        assertEquals(1f, PensieveTimeline.chainEndDarkness(260, 200), 1e-6f);
        // A scene shorter than the fade starts it from the first tick.
        assertTrue(PensieveTimeline.chainEndDarkness(0, 10) > 0f);
        assertEquals(1f, PensieveTimeline.chainEndDarkness(10, 10), 1e-6f);
    }

    @Test
    void cameraSettlesOnLandingButNotOnAChainHandOver() {
        assertEquals(PensieveTimeline.SETTLE_HEIGHT, PensieveTimeline.settleHeight(0, false), 1e-6f);
        assertTrue(PensieveTimeline.settleHeight(10, false) > 0f);
        assertEquals(0f, PensieveTimeline.settleHeight(PensieveTimeline.SETTLE_TICKS, false), 1e-6f);
        // A hand-over fades in from black on the same shot: the camera must not drop.
        assertEquals(0f, PensieveTimeline.settleHeight(0, true), 1e-6f);
        assertEquals(0f, PensieveTimeline.settleHeight(10, true), 1e-6f);
    }

    @Test
    void spokenWordsFadeUnderShutLidsAndChainEndBlack() {
        assertEquals(1f, PensieveTimeline.spokenWordsAlpha(0f, 0f), 1e-6f);
        assertEquals(0f, PensieveTimeline.spokenWordsAlpha(1f, 0f), 1e-6f);
        assertEquals(0f, PensieveTimeline.spokenWordsAlpha(0f, 1f), 1e-6f);
        assertEquals(0.25f, PensieveTimeline.spokenWordsAlpha(0.75f, 0.5f), 1e-6f);
        assertEquals(0.25f, PensieveTimeline.spokenWordsAlpha(0.5f, 0.75f), 1e-6f);
    }

    @Test
    void aHandOverHoldsBlackUntilTheSetIsDrawnOrTheWaitGivesUp() {
        assertTrue(PensieveTimeline.handOverHeld(false, 0));
        assertTrue(PensieveTimeline.handOverHeld(false, PensieveTimeline.HANDOVER_HOLD_LIMIT - 1));
        assertFalse(PensieveTimeline.handOverHeld(false, PensieveTimeline.HANDOVER_HOLD_LIMIT));
        assertFalse(PensieveTimeline.handOverHeld(true, 0));
    }

    @Test
    void aHandOverFadesOnlyTheDarknessNotTheFogOrTheGrade() {
        // The memory is already there: no fog closing in to two blocks, no grey wash, as on entering.
        assertEquals(1f, PensieveTimeline.setReveal(0f, true), 1e-6f);
        assertEquals(0.4f, PensieveTimeline.setReveal(0.4f, false), 1e-6f);
    }

    @Test
    void aCameraJumpIsAnythingFartherThanTwoBlocksInOneFrame() {
        assertFalse(PensieveTimeline.cameraJumped(0.5, 0.2, 0.5));
        assertFalse(PensieveTimeline.cameraJumped(2.0, 0, 0));
        assertTrue(PensieveTimeline.cameraJumped(1.5, 0, 1.5));
        assertTrue(PensieveTimeline.cameraJumped(0, 0, -70));
    }

    @Test
    void sectionDistanceIsZeroInsideAndMeasuredToTheNearestFace() {
        // Section at block origin (16, 0, -32): it spans x 16..32, y 0..16, z -32..-16.
        assertEquals(0.0, PensieveTimeline.sectionDistSq(16, 0, -32, 20, 5, -20), 1e-9);
        assertEquals(16.0, PensieveTimeline.sectionDistSq(16, 0, -32, 12, 5, -20), 1e-9);
        assertEquals(9.0 + 16.0, PensieveTimeline.sectionDistSq(16, 0, -32, 35, 20, -20), 1e-9);
        assertEquals(4.0, PensieveTimeline.sectionDistSq(16, 0, -32, 20, 5, -34), 1e-9);
    }
}

