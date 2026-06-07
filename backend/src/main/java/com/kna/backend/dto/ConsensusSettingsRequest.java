package com.kna.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ConsensusSettingsRequest(
        @NotBlank String policy,
        @Min(0) int finalityDelayBlocks
) {
}
