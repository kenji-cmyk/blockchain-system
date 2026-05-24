package com.kna.backend.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PeerDiscoveryRequest(
        @NotEmpty(message = "Peer URLs must not be empty") List<String> peerUrls
) {
}
