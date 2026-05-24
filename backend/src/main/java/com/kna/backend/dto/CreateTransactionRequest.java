package com.kna.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateTransactionRequest(
        @NotBlank(message = "Sender must not be blank") String sender,
        @NotBlank(message = "Receiver must not be blank") String receiver,
        @Positive(message = "Transaction amount must be greater than 0") double amount,
        @NotBlank(message = "Private key must not be blank") String privateKey
) {
}
