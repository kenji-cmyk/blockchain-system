package com.kna.backend.dto;

import java.time.Instant;

public record ConsensusBranch(
        String branchId,
        String status,
        String reason,
        String policy,
        int finalityDelayBlocks,
        int blockCount,
        long cumulativeDifficulty,
        int localBlockCount,
        long localCumulativeDifficulty,
        Integer commonAncestorIndex,
        String commonAncestorHash,
        BlockReference tip,
        Instant reviewedAt
) {
}
