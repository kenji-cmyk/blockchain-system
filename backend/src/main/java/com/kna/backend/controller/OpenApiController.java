package com.kna.backend.controller;

import com.kna.backend.dto.ApiRoute;
import com.kna.backend.dto.ApiRouteMetadata;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping("/api/docs/openapi")
    public Map<String, Object> openApi() {
        return openApiSpec();
    }

    @GetMapping("/api/docs/apidog")
    public Map<String, Object> apidog() {
        Map<String, Object> spec = openApiSpec();
        spec.put("x-apidog-import-notes", List.of(
                "Import this JSON as OpenAPI 3.0 in Apidog.",
                "Set the server variable baseUrl to http://localhost:8080 for local runs.",
                "Use operator-token as the default Bearer token for protected demo endpoints."
        ));
        return spec;
    }

    @GetMapping(value = {"/swagger-ui", "/swagger-ui/", "/swagger-ui/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public String swaggerUi() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>Blockchain API Swagger UI</title>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
                  <style>
                    body { margin: 0; background: #0b1020; }
                    .topbar { display: none; }
                    .swagger-ui .info .title, .swagger-ui .info p, .swagger-ui .scheme-container { color: #111827; }
                  </style>
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                  <script>
                    window.ui = SwaggerUIBundle({
                      url: "/api/docs/openapi",
                      dom_id: "#swagger-ui",
                      deepLinking: true,
                      persistAuthorization: true,
                      displayRequestDuration: true
                    });
                  </script>
                </body>
                </html>
                """;
    }

    private Map<String, Object> openApiSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "Blockchain Learning Backend API",
                "version", "0.0.1",
                "description", "Learning API for blocks, wallets, transactions, mining, peers, and sync."
        ));
        spec.put("servers", List.of(
                Map.of("url", "http://localhost:8080", "description", "Local Spring Boot server"),
                Map.of("url", "http://localhost:8081", "description", "Docker Compose node-b"),
                Map.of("url", "http://localhost:8082", "description", "Docker Compose node-c")
        ));
        spec.put("tags", List.of(
                Map.of("name", "blocks", "description", "Chain and block operations"),
                Map.of("name", "wallets", "description", "Wallet and balance operations"),
                Map.of("name", "transactions", "description", "Transaction and mempool operations"),
                Map.of("name", "peers", "description", "Peer registration, health, gossip, and sync"),
                Map.of("name", "operations", "description", "Health, metrics, and docs")
        ));
        spec.put("paths", paths());
        spec.put("components", components());
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
        operations.put(route.method(), operation(route));
    }

    private Map<String, Object> operation(ApiRoute route) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", route.summary());
        operation.put("operationId", operationId(route));
        operation.put("tags", List.of(tagFor(route.path())));
        operation.put("responses", responses(route.path().startsWith("/api/v1")));

        if (route.path().contains("{index}")) {
            operation.put("parameters", List.of(pathParameter("index", "Block index", "integer")));
        } else if (route.path().contains("{address}")) {
            operation.put("parameters", List.of(pathParameter("address", "Wallet public key / address", "string")));
        } else if (route.path().contains("{peerId}")) {
            operation.put("parameters", List.of(pathParameter("peerId", "Registered peer id", "string")));
        }

        Map<String, Object> requestBody = requestBody(route.path());
        if (requestBody != null) {
            operation.put("requestBody", requestBody);
        }
        if (requiresOperator(route)) {
            operation.put("security", List.of(Map.of("operatorBearer", List.of())));
        }
        return operation;
    }

    private Map<String, Object> responses(boolean enveloped) {
        Map<String, Object> successContent = jsonContent(enveloped ? schemaRef("ApiEnvelope") : schemaObject());
        return Map.of(
                "200", Map.of("description", "OK", "content", successContent),
                "201", Map.of("description", "Created", "content", successContent),
                "400", Map.of("description", "Bad Request", "content", jsonContent(schemaRef(enveloped ? "ApiEnvelope" : "ApiError"))),
                "401", Map.of("description", "Unauthorized", "content", jsonContent(schemaRef(enveloped ? "ApiEnvelope" : "ApiError"))),
                "403", Map.of("description", "Forbidden", "content", jsonContent(schemaRef(enveloped ? "ApiEnvelope" : "ApiError"))),
                "404", Map.of("description", "Not Found", "content", jsonContent(schemaRef(enveloped ? "ApiEnvelope" : "ApiError")))
        );
    }

    private Map<String, Object> requestBody(String path) {
        String legacyPath = path.replaceFirst("^/api/v1", "/api");
        return switch (legacyPath) {
            case "/api/blocks" -> jsonRequest("AddBlockRequest", Map.of("data", "demo-miner-address"));
            case "/api/blocks/broadcast" -> jsonRequest("Block", Map.of(
                    "index", 1,
                    "previousHash", "GENESIS_HASH",
                    "hash", "MINED_BLOCK_HASH",
                    "transactions", List.of()
            ));
            case "/api/transactions" -> jsonRequest("CreateTransactionRequest", Map.of(
                    "sender", "{{senderPublicKey}}",
                    "receiver", "{{receiverPublicKey}}",
                    "amount", 1.25,
                    "fee", 0.05,
                    "privateKey", "{{senderPrivateKey}}"
            ));
            case "/api/transactions/broadcast" -> jsonRequest("Transaction", Map.of(
                    "sender", "{{senderPublicKey}}",
                    "receiver", "{{receiverPublicKey}}",
                    "amount", 1,
                    "fee", 0,
                    "transactionId", "{{transactionId}}"
            ));
            case "/api/transactions/mine" -> jsonRequest("MineTransactionsRequest", Map.of("rewardAddress", "{{minerPublicKey}}"));
            case "/api/peers" -> jsonRequest("RegisterPeerRequest", Map.of("peerId", "node-b", "baseUrl", "http://localhost:8081"));
            case "/api/peers/discover" -> jsonRequest("PeerDiscoveryRequest", Map.of("peerUrls", List.of("http://localhost:8081", "http://localhost:8082")));
            case "/api/peers/{peerId}/blocks" -> jsonRequest("MinePeerBlockRequest", Map.of("minerAddress", "{{minerPublicKey}}"));
            case "/api/chain/difficulty" -> jsonRequest("DifficultyRequest", Map.of("difficulty", 2));
            case "/api/chain/tamper" -> jsonRequest("TamperBlockRequest", Map.of("index", 1, "data", "tampered-data"));
            default -> null;
        };
    }

    private Map<String, Object> jsonRequest(String schemaName, Map<String, Object> example) {
        return Map.of(
                "required", true,
                "content", Map.of(
                        "application/json", Map.of(
                                "schema", schemaRef(schemaName),
                                "example", example
                        )
                )
        );
    }

    private Map<String, Object> components() {
        return Map.of(
                "securitySchemes", Map.of(
                        "operatorBearer", Map.of(
                                "type", "http",
                                "scheme", "bearer",
                                "bearerFormat", "operator-token"
                        )
                ),
                "schemas", Map.ofEntries(
                        Map.entry("ApiEnvelope", objectSchema(Map.of(
                                "success", Map.of("type", "boolean", "example", true),
                                "data", Map.of("nullable", true),
                                "error", schemaRef("ApiError"),
                                "metadata", Map.of("type", "object", "additionalProperties", true)
                        ))),
                        Map.entry("ApiError", objectSchema(Map.of(
                                "timestamp", Map.of("type", "string", "format", "date-time"),
                                "status", Map.of("type", "integer", "example", 400),
                                "error", Map.of("type", "string", "example", "Bad Request"),
                                "message", Map.of("type", "string", "example", "Sender balance is insufficient"),
                                "path", Map.of("type", "string", "example", "/api/transactions")
                        ))),
                        Map.entry("AddBlockRequest", objectSchema(Map.of("data", Map.of("type", "string", "example", "demo-miner-address")))),
                        Map.entry("CreateTransactionRequest", objectSchema(Map.of(
                                "sender", Map.of("type", "string", "example", "{{senderPublicKey}}"),
                                "receiver", Map.of("type", "string", "example", "{{receiverPublicKey}}"),
                                "amount", Map.of("type", "number", "format", "double", "example", 1.25),
                                "fee", Map.of("type", "number", "format", "double", "example", 0.05),
                                "privateKey", Map.of("type", "string", "example", "{{senderPrivateKey}}")
                        ))),
                        Map.entry("MineTransactionsRequest", objectSchema(Map.of("rewardAddress", Map.of("type", "string", "example", "{{minerPublicKey}}")))),
                        Map.entry("RegisterPeerRequest", objectSchema(Map.of(
                                "peerId", Map.of("type", "string", "example", "node-b"),
                                "baseUrl", Map.of("type", "string", "example", "http://localhost:8081")
                        ))),
                        Map.entry("PeerDiscoveryRequest", objectSchema(Map.of("peerUrls", Map.of("type", "array", "items", Map.of("type", "string"))))),
                        Map.entry("MinePeerBlockRequest", objectSchema(Map.of("minerAddress", Map.of("type", "string", "example", "{{minerPublicKey}}")))),
                        Map.entry("DifficultyRequest", objectSchema(Map.of("difficulty", Map.of("type", "integer", "minimum", 0, "maximum", 6, "example", 2)))),
                        Map.entry("TamperBlockRequest", objectSchema(Map.of(
                                "index", Map.of("type", "integer", "example", 1),
                                "data", Map.of("type", "string", "example", "tampered-data")
                        ))),
                        Map.entry("Block", objectSchema(Map.of())),
                        Map.entry("Transaction", objectSchema(Map.of()))
                )
        );
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties);
    }

    private Map<String, Object> schemaObject() {
        return Map.of("type", "object");
    }

    private Map<String, Object> schemaRef(String name) {
        return Map.of("$ref", "#/components/schemas/" + name);
    }

    private Map<String, Object> jsonContent(Map<String, Object> schema) {
        return Map.of("application/json", Map.of("schema", schema));
    }

    private Map<String, Object> pathParameter(String name, String description, String type) {
        return Map.of(
                "name", name,
                "in", "path",
                "required", true,
                "description", description,
                "schema", Map.of("type", type)
        );
    }

    private boolean requiresOperator(ApiRoute route) {
        String path = route.path().replaceFirst("^/api/v1", "/api");
        return "post".equals(route.method()) && (
                path.equals("/api/blocks")
                        || path.equals("/api/transactions/mine")
                        || path.startsWith("/api/peers")
                        || path.equals("/api/chain/tamper")
                        || path.equals("/api/chain/reset")
        ) || "put".equals(route.method()) && path.equals("/api/chain/difficulty")
                || "delete".equals(route.method()) && path.startsWith("/api/peers/");
    }

    private String tagFor(String path) {
        if (path.contains("/wallets")) {
            return "wallets";
        }
        if (path.contains("/transactions")) {
            return "transactions";
        }
        if (path.contains("/peers")) {
            return "peers";
        }
        if (path.contains("/ops") || path.contains("/docs") || path.contains("/node")) {
            return "operations";
        }
        return "blocks";
    }

    private String operationId(ApiRoute route) {
        String cleanPath = route.path()
                .replaceFirst("^/", "")
                .replace("{", "by-")
                .replace("}", "")
                .replace("/", "-");
        return route.method() + "-" + cleanPath;
    }
}
