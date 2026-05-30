package com.kna.backend.dto;

public record ApiRoute(String method, String path, String summary) {
    public ApiRoute {
        method = method.toLowerCase();
    }

    public ApiRoute withPrefix(String prefix) {
        return new ApiRoute(method, prefix + path, summary);
    }
}
