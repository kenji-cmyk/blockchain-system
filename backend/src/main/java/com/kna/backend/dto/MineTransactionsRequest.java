package com.kna.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MineTransactionsRequest(@NotBlank(message = "Reward address must not be blank") String rewardAddress) {
}
