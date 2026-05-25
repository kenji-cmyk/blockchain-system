package com.kna.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "blockchain.security.enabled=true",
        "blockchain.security.operator-token=test-operator",
        "blockchain.security.read-only-token=test-reader",
        "blockchain.security.max-request-bytes=8192",
        "blockchain.rate-limit.enabled=false"
})
@ActiveProfiles("test")
class SecurityPhase8Tests {

    private static final String OPERATOR = "Bearer test-operator";
    private static final String READER = "Bearer test-reader";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetChain() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/chain/reset")
                        .header("Authorization", OPERATOR))
                .andExpect(status().isOk());
    }

    @Test
    void protectsAdminAndPeerManagementRoutesWithOperatorRole() throws Exception {
        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/chain/difficulty")
                        .header("Authorization", READER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/chain/difficulty")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/peers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"secured-peer\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/peers")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"secured-peer\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/peers/secured-peer")
                        .header("Authorization", READER))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/peers/secured-peer")
                        .header("Authorization", OPERATOR))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMalformedKeysReplayedTransactionsAndHostilePeerPayloads() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        fundWallet(senderWallet);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sender": "%s",
                                  "receiver": "%s",
                                  "amount": 1,
                                  "fee": 0,
                                  "privateKey": "not-a-private-key"
                                }
                                """.formatted(
                                senderWallet.get("publicKey").getAsString(),
                                receiverWallet.get("publicKey").getAsString()
                        )))
                .andExpect(status().isBadRequest());

        MvcResult transactionResult = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 1)))
                .andExpect(status().isCreated())
                .andReturn();

        String transactionJson = transactionResult.getResponse().getContentAsString();
        mockMvc.perform(post("/api/transactions/broadcast")
                        .header("X-Gossip-Id", "replay-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/broadcast")
                        .header("X-Gossip-Id", "replay-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/blocks/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Malformed peer message")));

        mockMvc.perform(post("/api/transactions/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"%s\"".formatted("x".repeat(10000))))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void fullSecuredSystemSmokeTest() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        JsonObject minerWallet = createWallet();
        fundWallet(senderWallet);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inputs", hasSize(1)))
                .andExpect(jsonPath("$.outputs", hasSize(2)));

        mockMvc.perform(post("/api/transactions/mine")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardAddress\":\"%s\"}".formatted(minerWallet.get("publicKey").getAsString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactions", hasSize(2)));

        mockMvc.perform(post("/api/peers")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"smoke-peer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.healthy").value(true));

        mockMvc.perform(get("/api/node/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities").isArray());

        mockMvc.perform(get("/api/chain/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.size").value(3));

        mockMvc.perform(get("/api/wallets/{address}/balance", receiverWallet.get("publicKey").getAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(2.0));

        mockMvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/node/info']").exists())
                .andExpect(jsonPath("$.paths['/api/peers/{peerId}/sync']").exists());
    }

    private JsonObject createWallet() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/new"))
                .andExpect(status().isOk())
                .andReturn();

        return JsonParser.parseString(result.getResponse().getContentAsString()).getAsJsonObject();
    }

    private void fundWallet(JsonObject wallet) throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"%s\"}".formatted(wallet.get("publicKey").getAsString())))
                .andExpect(status().isCreated());
    }

    private String transactionJson(JsonObject senderWallet, JsonObject receiverWallet, double amount) {
        return """
                {
                  "sender": "%s",
                  "receiver": "%s",
                  "amount": %s,
                  "fee": 0,
                  "privateKey": "%s"
                }
                """.formatted(
                senderWallet.get("publicKey").getAsString(),
                receiverWallet.get("publicKey").getAsString(),
                amount,
                senderWallet.get("privateKey").getAsString()
        );
    }
}
