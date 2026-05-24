package com.kna.backend.dto;

public record PeerSummary(String peerId, int chainSize, boolean valid) {
}
