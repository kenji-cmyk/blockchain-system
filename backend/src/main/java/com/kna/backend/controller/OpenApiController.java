package com.kna.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping("/api/docs/openapi")
    public Map<String, Object> openApi() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "Blockchain Learning Backend API",
                "version", "0.0.1",
                "description", "Learning API for blocks, wallets, transactions, mining, peers, and sync."
        ));
        spec.put("paths", paths());
        return spec;
    }

    private Map<String, Object> paths() {
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/api/blocks", Map.of(
                "get", operation("View the entire chain"),
                "post", operation("Mine a legacy demo block")
        ));
        paths.put("/api/blocks/{index}", Map.of("get", operation("View a block by index")));
        paths.put("/api/wallets/new", Map.of("get", operation("Create a wallet")));
        paths.put("/api/wallets/{address}/balance", Map.of("get", operation("View a wallet balance")));
        paths.put("/api/transactions", Map.of("post", operation("Create a signed transaction")));
        paths.put("/api/transactions/broadcast", Map.of("post", operation("Accept a broadcast transaction")));
        paths.put("/api/transactions/pending", Map.of("get", operation("View pending transactions")));
        paths.put("/api/transactions/mine", Map.of("post", operation("Mine pending transactions")));
        paths.put("/api/blocks/broadcast", Map.of("post", operation("Accept a broadcast block")));
        paths.put("/api/chain/validate", Map.of("get", operation("Validate the chain")));
        paths.put("/api/chain/status", Map.of("get", operation("View chain status")));
        paths.put("/api/chain/forks", Map.of("get", operation("View tracked fork blocks")));
        paths.put("/api/chain/orphans", Map.of("get", operation("View tracked orphan blocks")));
        paths.put("/api/chain/difficulty", Map.of("put", operation("Update mining difficulty")));
        paths.put("/api/chain/tamper", Map.of("post", operation("Tamper with a block")));
        paths.put("/api/chain/reset", Map.of("post", operation("Reset chain state")));
        paths.put("/api/peers", Map.of(
                "get", operation("View registered simulated peers"),
                "post", operation("Register a simulated peer")
        ));
        paths.put("/api/peers/discover", Map.of("post", operation("Discover HTTP peers")));
        paths.put("/api/peers/broadcast/transactions", Map.of("post", operation("Broadcast pending transactions")));
        paths.put("/api/peers/{peerId}/health", Map.of("get", operation("Check peer health")));
        paths.put("/api/peers/{peerId}/chain", Map.of("get", operation("Fetch a peer chain")));
        paths.put("/api/peers/{peerId}", Map.of("delete", operation("Remove a peer")));
        paths.put("/api/peers/{peerId}/blocks", Map.of("post", operation("Mine a demo block on a peer")));
        paths.put("/api/peers/{peerId}/sync", Map.of("post", operation("Sync from a peer")));
        paths.put("/api/docs/openapi", Map.of("get", operation("View this OpenAPI document")));
        return paths;
    }

    private Map<String, Object> operation(String summary) {
        return Map.of(
                "summary", summary,
                "responses", Map.of(
                        "200", Map.of("description", "OK"),
                        "400", Map.of("description", "Bad Request"),
                        "404", Map.of("description", "Not Found")
                ),
                "tags", List.of("blockchain")
        );
    }
}
