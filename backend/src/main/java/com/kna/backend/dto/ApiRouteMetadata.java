package com.kna.backend.dto;

import java.util.List;

public final class ApiRouteMetadata {

    private static final List<ApiRoute> LEGACY_ROUTES = List.of(
            new ApiRoute("get", "/api/blocks", "View the entire chain"),
            new ApiRoute("post", "/api/blocks", "Mine a legacy demo block"),
            new ApiRoute("get", "/api/blocks/{index}", "View a block by index"),
            new ApiRoute("post", "/api/blocks/broadcast", "Accept a broadcast block"),
            new ApiRoute("get", "/api/node/info", "View local node identity and capabilities"),
            new ApiRoute("get", "/api/wallets/new", "Create a wallet"),
            new ApiRoute("get", "/api/wallets/{address}/balance", "View a wallet balance"),
            new ApiRoute("post", "/api/transactions", "Create a signed transaction"),
            new ApiRoute("post", "/api/transactions/broadcast", "Accept a broadcast transaction"),
            new ApiRoute("get", "/api/transactions/pending", "View pending transactions"),
            new ApiRoute("post", "/api/transactions/mine", "Mine pending transactions"),
            new ApiRoute("get", "/api/chain/validate", "Validate the chain"),
            new ApiRoute("get", "/api/chain/status", "View chain status"),
            new ApiRoute("get", "/api/chain/forks", "View tracked fork blocks"),
            new ApiRoute("get", "/api/chain/orphans", "View tracked orphan blocks"),
            new ApiRoute("put", "/api/chain/difficulty", "Update mining difficulty"),
            new ApiRoute("post", "/api/chain/tamper", "Tamper with a block"),
            new ApiRoute("post", "/api/chain/reset", "Reset chain state"),
            new ApiRoute("get", "/api/ops/health", "View backend health"),
            new ApiRoute("get", "/api/ops/metrics", "View backend metrics"),
            new ApiRoute("get", "/api/peers", "View registered peers"),
            new ApiRoute("post", "/api/peers", "Register a simulated or HTTP peer"),
            new ApiRoute("post", "/api/peers/discover", "Discover HTTP peers"),
            new ApiRoute("post", "/api/peers/broadcast/transactions", "Broadcast pending transactions"),
            new ApiRoute("get", "/api/peers/{peerId}/health", "Check peer health"),
            new ApiRoute("get", "/api/peers/{peerId}/chain", "Fetch a peer chain"),
            new ApiRoute("delete", "/api/peers/{peerId}", "Remove a peer"),
            new ApiRoute("post", "/api/peers/{peerId}/blocks", "Mine a demo block on a peer"),
            new ApiRoute("post", "/api/peers/{peerId}/sync", "Sync from a peer"),
            new ApiRoute("get", "/api/docs/openapi", "View this OpenAPI document")
    );

    private ApiRouteMetadata() {
    }

    public static List<ApiRoute> legacyRoutes() {
        return LEGACY_ROUTES;
    }

    public static List<ApiRoute> versionedRoutes() {
        return LEGACY_ROUTES.stream()
                .filter(route -> !route.path().equals("/api/docs/openapi"))
                .map(route -> new ApiRoute(route.method(), route.path().replaceFirst("^/api", "/api/v1"), route.summary()))
                .toList();
    }
}
