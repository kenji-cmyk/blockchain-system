package com.kna.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DifficultyRequest(
        @Min(value = 0, message = "Difficulty must be between 0 and 6")
        @Max(value = 6, message = "Difficulty must be between 0 and 6")
        int difficulty
) {
}
