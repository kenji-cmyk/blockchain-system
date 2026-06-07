package com.kna.backend.consensus;

import java.util.Arrays;

public enum ConsensusPolicy {
    LONGEST_CHAIN("longest-chain"),
    CUMULATIVE_DIFFICULTY("cumulative-difficulty");

    private final String key;

    ConsensusPolicy(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ConsensusPolicy fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Consensus policy must not be blank");
        }
        String normalized = key.strip().toLowerCase();
        return Arrays.stream(values())
                .filter(policy -> policy.key.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported consensus policy: " + key));
    }
}
