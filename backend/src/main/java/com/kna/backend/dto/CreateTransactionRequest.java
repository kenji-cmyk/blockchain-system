package com.kna.backend.dto;

public record CreateTransactionRequest(String sender, String receiver, double amount, String privateKey) {
}
