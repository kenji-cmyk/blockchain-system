package com.kna.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetChain() throws Exception {
        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void contextLoads() {
    }

    @Test
    void startsWithGenesisBlock() throws Exception {
        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].transactions", hasSize(1)))
                .andExpect(jsonPath("$[0].transactions[0].receiver").value("GENESIS"))
                .andExpect(jsonPath("$[0].previousHash").value("0"));
    }

    @Test
    void canAddBlockAndValidateChain() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"Learn block hash\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(1))
                .andExpect(jsonPath("$.transactions[0].receiver").value("Learn block hash"));

        mockMvc.perform(get("/api/chain/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void canUpdateDifficulty() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value(2));
    }

    @Test
    void tamperMakesChainInvalidAndResetRestoresIt() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"Original data\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/chain/tamper")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"index\":1,\"data\":\"Changed data\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void canCreateSignedTransactionAndMineReward() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();

        String requestJson = """
                {
                  "sender": "%s",
                  "receiver": "%s",
                  "amount": 5,
                  "privateKey": "%s"
                }
                """.formatted(
                senderWallet.get("publicKey").getAsString(),
                receiverWallet.get("publicKey").getAsString(),
                senderWallet.get("privateKey").getAsString()
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(5.0))
                .andExpect(jsonPath("$.signature").isNotEmpty());

        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingTransactions").value(1));

        String mineRequestJson = """
                {
                  "rewardAddress": "%s"
                }
                """.formatted(senderWallet.get("publicKey").getAsString());

        mockMvc.perform(post("/api/transactions/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mineRequestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(1))
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].amount").value(5.0))
                .andExpect(jsonPath("$.transactions[1].sender").value("SYSTEM"))
                .andExpect(jsonPath("$.transactions[1].amount").value(10.0));

        mockMvc.perform(get("/api/chain/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.pendingTransactions").value(0))
                .andExpect(jsonPath("$.valid").value(true));
    }

    private JsonObject createWallet() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isNotEmpty())
                .andExpect(jsonPath("$.privateKey").isNotEmpty())
                .andReturn();

        return JsonParser.parseString(result.getResponse().getContentAsString()).getAsJsonObject();
    }

}
