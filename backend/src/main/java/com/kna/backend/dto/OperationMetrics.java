package com.kna.backend.dto;

public record OperationMetrics(
        int chainSize,
        int pendingTransactions,
        long cumulativeDifficulty,
        int forkBlocks,
        int orphanBlocks,
        int peers,
        long validationRuns,
        long minedBlocks,
        long minedTransactions,
        long miningNonceTotal,
        long miningElapsedMsTotal,
        long rejectedTransactions,
        long acceptedBroadcastBlocks,
        long rejectedBroadcastBlocks,
        long peerSyncAttempts,
        long peerSyncSuccesses,
        long peerSyncAdoptions,
        long transactionBroadcastAttempts,
        long transactionBroadcastSuccesses,
        long transactionBroadcastFailures,
        long blockBroadcastAttempts,
        long blockBroadcastSuccesses,
        long blockBroadcastFailures
) {
}
