package com.kna.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MinePeerBlockRequest(@NotBlank(message = "Miner address must not be blank") String minerAddress) {
}
