package com.kna.backend.dto;

public record BroadcastResult(
        int peerCount,
        int successCount,
        int failureCount
) {
}
