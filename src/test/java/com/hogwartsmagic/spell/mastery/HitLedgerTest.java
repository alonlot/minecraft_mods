package com.hogwartsmagic.spell.mastery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitLedgerTest {
    @Test
    void secondAwardInsideWindowIsRefused() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryAward("hit:stupefy:a", 1000L, 200L));
        assertFalse(ledger.tryAward("hit:stupefy:a", 1199L, 200L));
        assertTrue(ledger.tryAward("hit:stupefy:a", 1200L, 200L));
    }

    @Test
    void refusedAttemptDoesNotRestartTheWindow() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryAward("k", 0L, 200L));
        assertFalse(ledger.tryAward("k", 150L, 200L));
        assertTrue(ledger.tryAward("k", 200L, 200L));
    }

    @Test
    void keysAreIndependent() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryAward("hit:stupefy:a", 0L, 200L));
        assertTrue(ledger.tryAward("hit:glacius:a", 0L, 200L));
        assertTrue(ledger.tryAward("hit:stupefy:b", 0L, 200L));
    }

    @Test
    void expiredEntriesArePrunedOnceLarge() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("k" + i, 0L, 200L);
        }
        ledger.tryAward("fresh", 200L, 200L);
        assertEquals(1, ledger.size());
    }

    @Test
    void liveEntriesSurvivePruning() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("long" + i, 0L, 12000L);
        }
        ledger.tryAward("fresh", 200L, 200L);
        assertEquals(301, ledger.size());
        assertFalse(ledger.tryAward("long7", 11999L, 200L));
        assertTrue(ledger.tryAward("long7", 12000L, 200L));
    }

    @Test
    void isOpenDoesNotRecord() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.isOpen("k", 0L));
        assertTrue(ledger.isOpen("k", 0L));
        assertTrue(ledger.tryAward("k", 0L, 200L));
        assertFalse(ledger.isOpen("k", 199L));
        assertTrue(ledger.isOpen("k", 200L));
    }
}
