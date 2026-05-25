package com.kna.backend.dto;

import java.util.List;

public record PeerSummary(
        String peerId,
        int chainSize,
        boolean valid,
        String baseUrl,
        boolean healthy,
        String mode,
        String nodeId,
        List<String> capabilities,
        int score,
        int failureCount,
        String lastSeenAt
) {
}
