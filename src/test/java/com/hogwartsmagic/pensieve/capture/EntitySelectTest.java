package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.pensieve.capture.EntitySelect.Candidate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntitySelectTest {
    @Test
    void ownerThenPlayersThenNearest() {
        List<Candidate> all = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            all.add(new Candidate(100 + i, false, false, 20 - i)); // 106 is the nearest non-player
        }
        all.add(new Candidate(2, true, false, 50));
        all.add(new Candidate(3, true, false, 40));
        all.add(new Candidate(1, true, true, 90));
        assertEquals(List.of(1, 3, 2, 106), EntitySelect.pick(all, 4));
    }

    @Test
    void fewerThanMax() {
        assertEquals(List.of(5), EntitySelect.pick(List.of(new Candidate(5, false, false, 1)), 4));
    }
}
