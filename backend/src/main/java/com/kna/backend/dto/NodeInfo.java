package com.kna.backend.dto;

import java.util.List;

public record NodeInfo(
        String nodeId,
        String version,
        List<String> capabilities,
        int chainSize,
        long cumulativeDifficulty
) {
}
