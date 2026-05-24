package com.kna.backend.dto;

public record BlockchainStatus(int size, int difficulty, int pendingTransactions, boolean valid) {
}
