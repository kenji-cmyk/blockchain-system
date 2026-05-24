package com.kna.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateTransactionRequest(
        @NotBlank(message = "Sender must not be blank") String sender,
        @NotBlank(message = "Receiver must not be blank") String receiver,
        @Positive(message = "Transaction amount must be greater than 0") double amount,
        @PositiveOrZero(message = "Transaction fee must be greater than or equal to 0") Double fee,
        @NotBlank(message = "Private key must not be blank") String privateKey
) {
    public CreateTransactionRequest {
        fee = fee == null ? 0 : fee;
    }
}
