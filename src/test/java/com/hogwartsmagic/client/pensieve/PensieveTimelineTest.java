package com.hogwartsmagic.client.pensieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
