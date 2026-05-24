package com.kna.backend.dto;

public record OperationMetrics(
        int chainSize,
        int pendingTransactions,
        long cumulativeDifficulty,
        int forkBlocks,
        int orphanBlocks,
        int peers
) {
}
