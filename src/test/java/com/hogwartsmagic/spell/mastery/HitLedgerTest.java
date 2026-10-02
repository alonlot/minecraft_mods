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
    void oldEntriesArePrunedOnceLarge() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("k" + i, 0L, 200L);
        }
        ledger.tryAward("fresh", HitLedger.PRUNE_AGE_TICKS + 1, 200L);
        assertEquals(1, ledger.size());
    }
}
