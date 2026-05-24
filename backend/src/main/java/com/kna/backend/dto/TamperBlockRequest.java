package com.kna.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TamperBlockRequest(
        @Min(value = 0, message = "Block index must be greater than or equal to 0") int index,
        @NotBlank(message = "Block data must not be blank") String data
) {
}
