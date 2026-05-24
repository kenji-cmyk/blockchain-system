package com.kna.backend.dto;

public record OperationHealth(
        String status,
        boolean chainValid,
        int chainSize,
        int pendingTransactions,
        boolean persistenceEnabled,
        String persistenceType
) {
}
