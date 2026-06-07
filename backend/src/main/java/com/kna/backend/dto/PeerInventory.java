package com.kna.backend.dto;

import java.util.List;

public record PeerInventory(
        List<String> blockHashes,
        List<String> transactionIds
) {
}
