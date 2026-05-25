package com.kna.backend.entity;

public record UtxoEntry(String transactionId, int outputIndex, String owner, double amount) {
}
