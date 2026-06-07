package com.kna.backend.dto;

public record ConsensusSettings(
        String policy,
        int finalityDelayBlocks
) {
}
