package com.kna.backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "blockchain.node.id=test-node-a",
        "blockchain.peer.eviction-score=1",
        "blockchain.peer.max-message-bytes=2048"
})
@ActiveProfiles("test")
class PeerNetworkPhase7Tests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetChain() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void exposesNodeIdentityAndCapabilities() throws Exception {
        mockMvc.perform(get("/api/node/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("test-node-a"))
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.chainSize").value(1))
                .andExpect(jsonPath("$.capabilities", hasItem("node-info")))
                .andExpect(jsonPath("$.capabilities", hasItem("utxo-ledger")))
                .andExpect(jsonPath("$.capabilities", hasItem("gossip-broadcast")));
    }

    @Test
    void registersHttpPeerWithHandshakeMetadata() throws Exception {
        HttpServer server = startPeerServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();

            mockMvc.perform(post("/api/peers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "peerId": "node-http",
                                      "baseUrl": "%s"
                                    }
                                    """.formatted(baseUrl)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.peerId").value("node-http"))
                    .andExpect(jsonPath("$.nodeId").value("remote-node"))
                    .andExpect(jsonPath("$.score").value(2))
                    .andExpect(jsonPath("$.capabilities", hasItem("node-info")))
                    .andExpect(jsonPath("$.capabilities", hasItem("block-broadcast")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsGossipHeadersWhenBroadcastingToPeers() throws Exception {
        AtomicReference<String> nodeIdHeader = new AtomicReference<>();
        AtomicReference<String> gossipIdHeader = new AtomicReference<>();
        HttpServer server = startBroadcastCaptureServer(nodeIdHeader, gossipIdHeader);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            mockMvc.perform(post("/api/peers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "peerId": "node-http",
                                      "baseUrl": "%s"
                                    }
                                    """.formatted(baseUrl)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"data\":\"header-demo\"}"))
                    .andExpect(status().isCreated());

            org.assertj.core.api.Assertions.assertThat(nodeIdHeader.get()).isEqualTo("test-node-a");
            org.assertj.core.api.Assertions.assertThat(gossipIdHeader.get()).isNotBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void quarantinesHttpPeerAfterHealthScoreDropsTooLow() throws Exception {
        HttpServer server = startPeerServer(true);
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        mockMvc.perform(post("/api/peers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "peerId": "node-http",
                                  "baseUrl": "%s"
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isCreated());
        server.stop(0);

        mockMvc.perform(get("/api/peers/node-http/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false));

        mockMvc.perform(get("/api/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].peerId").value("node-http"))
                .andExpect(jsonPath("$[0].state").value("quarantined"))
                .andExpect(jsonPath("$[0].backoffUntil", notNullValue()));
    }

    @Test
    void rejectsMalformedAndOversizedPeerMessages() throws Exception {
        mockMvc.perform(post("/api/blocks/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Malformed peer message")));

        mockMvc.perform(post("/api/transactions/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"%s\"".formatted("x".repeat(3000))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message", containsString("Peer message exceeds")));
    }

    @Test
    void openApiIncludesNodeInfoEndpoint() throws Exception {
        mockMvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/node/info']", notNullValue()));
    }

    private HttpServer startPeerServer() throws IOException {
        return startPeerServer(false);
    }

    private HttpServer startPeerServer(boolean captureBroadcast) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/node/info", exchange -> writeJson(exchange, """
                {
                  "nodeId": "remote-node",
                  "version": "0.0.1-test",
                  "capabilities": ["node-info", "block-broadcast"],
                  "chainSize": 1,
                  "cumulativeDifficulty": 4
                }
                """));
        server.createContext("/api/chain/status", exchange -> writeJson(exchange, """
                {"size":1,"difficulty":2,"pendingTransactions":0,"valid":true,"cumulativeDifficulty":4}
                """));
        server.createContext("/api/blocks", exchange -> writeJson(exchange, "[]"));
        if (!captureBroadcast) {
            server.createContext("/api/transactions/broadcast", exchange -> writeJson(exchange, "{\"transactionId\":\"ok\"}"));
        }
        server.start();
        return server;
    }

    private HttpServer startBroadcastCaptureServer(
            AtomicReference<String> nodeIdHeader,
            AtomicReference<String> gossipIdHeader
    ) throws IOException {
        HttpServer server = startPeerServer(true);
        server.createContext("/api/blocks/broadcast", exchange -> {
            nodeIdHeader.set(exchange.getRequestHeaders().getFirst("X-Node-Id"));
            gossipIdHeader.set(exchange.getRequestHeaders().getFirst("X-Gossip-Id"));
            writeJson(exchange, "{\"accepted\":true}");
        });
        return server;
    }

    private void writeJson(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
