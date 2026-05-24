package com.kna.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AddBlockRequest(@NotBlank(message = "Block data must not be blank") String data) {
}
