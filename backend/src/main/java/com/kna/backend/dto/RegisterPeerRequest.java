package com.kna.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterPeerRequest(
        @NotBlank(message = "Peer id must not be blank") String peerId,
        String baseUrl
) {
}
