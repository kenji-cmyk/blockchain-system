package com.kna.backend.dto;

import java.util.Map;

public record ApiEnvelope<T>(
        boolean success,
        T data,
        ApiError error,
        Map<String, Object> metadata
) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(true, data, null, Map.of());
    }

    public static <T> ApiEnvelope<T> ok(T data, Map<String, Object> metadata) {
        return new ApiEnvelope<>(true, data, null, Map.copyOf(metadata));
    }

    public static ApiEnvelope<Object> failure(ApiError error) {
        return new ApiEnvelope<>(false, null, error, Map.of());
    }
}
