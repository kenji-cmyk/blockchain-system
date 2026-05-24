package com.kna.backend.dto;

public record SyncResult(
        String peerId,
        int peerChainSize,
        int localChainSizeBefore,
        int localChainSizeAfter,
        boolean peerValid,
        boolean adopted,
        String message
) {
}
