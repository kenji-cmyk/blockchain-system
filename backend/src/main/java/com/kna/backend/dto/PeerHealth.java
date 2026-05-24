package com.kna.backend.dto;

public record PeerHealth(
        String peerId,
        String baseUrl,
        boolean healthy,
        String message
) {
}
