package com.kna.backend.controller;

import com.kna.backend.dto.ApiRoute;
import com.kna.backend.dto.ApiRouteMetadata;
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
        for (ApiRoute route : ApiRouteMetadata.legacyRoutes()) {
            addRoute(paths, route);
        }
        for (ApiRoute route : ApiRouteMetadata.versionedRoutes()) {
            addRoute(paths, route);
        }
        return paths;
    }

    @SuppressWarnings("unchecked")
    private void addRoute(Map<String, Object> paths, ApiRoute route) {
        Map<String, Object> operations = (Map<String, Object>) paths.computeIfAbsent(route.path(), ignored -> new LinkedHashMap<>());
        operations.put(route.method(), operation(route.summary()));
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
