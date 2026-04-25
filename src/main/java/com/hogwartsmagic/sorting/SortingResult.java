package com.hogwartsmagic.sorting;

import java.util.List;

public record SortingResult(HogwartsHouse house, String reason, List<HogwartsHouse> likelyHouses) {
    public SortingResult(HogwartsHouse house, String reason) {
        this(house, reason, List.of(house));
    }
}
