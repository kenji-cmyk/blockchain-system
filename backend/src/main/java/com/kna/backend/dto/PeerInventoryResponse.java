package com.kna.backend.dto;

import java.util.List;

public record PeerInventoryResponse(
        List<String> missingBlockHashes,
        List<String> missingTransactionIds
) {
}
