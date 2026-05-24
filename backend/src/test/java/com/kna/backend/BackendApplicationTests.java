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
import static org.hamcrest.Matchers.not;
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
        mockMvc.perform(put("/api/chain/difficulty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk());

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
                .andExpect(jsonPath("$[0].hash").isNotEmpty())
                .andExpect(jsonPath("$[0].transactions", hasSize(1)))
                .andExpect(jsonPath("$[0].transactions[0].sender").value("SYSTEM"))
                .andExpect(jsonPath("$[0].transactions[0].receiver").value("GENESIS"))
                .andExpect(jsonPath("$[0].transactions[0].amount").value(10.0))
                .andExpect(jsonPath("$[0].previousHash").value("0"));
    }

    @Test
    void canViewBlockByIndex() throws Exception {
        mockMvc.perform(get("/api/blocks/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.index").value(0))
                .andExpect(jsonPath("$.previousHash").value("0"))
                .andExpect(jsonPath("$.transactions[0].receiver").value("GENESIS"));
    }

    @Test
    void unknownBlockIndexReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/blocks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Block index does not exist"))
                .andExpect(jsonPath("$.path").value("/api/blocks/99"));
    }

    @Test
    void canAddLegacyDemoBlockAndValidateChain() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"Learn block hash\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(1))
                .andExpect(jsonPath("$.transactions[0].receiver").value("Learn block hash"));

        mockMvc.perform(get("/api/chain/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.difficulty").value(2))
                .andExpect(jsonPath("$.pendingTransactions").value(0))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void addLegacyDemoBlockRejectsBlankData() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/blocks"));
    }

    @Test
    void chainStatusReportsSizeDifficultyPendingAndValidity() throws Exception {
        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.difficulty").value(2))
                .andExpect(jsonPath("$.pendingTransactions").value(0))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void canUpdateDifficulty() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value(2))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void rejectsDifficultyOutsideDemoRange() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":7}"))
                .andExpect(status().isBadRequest());
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
    void tamperRejectsUnknownBlockIndex() throws Exception {
        mockMvc.perform(post("/api/chain/tamper")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"index\":42,\"data\":\"Changed data\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsWalletsWithDistinctKeys() throws Exception {
        JsonObject firstWallet = createWallet();
        JsonObject secondWallet = createWallet();

        mockMvc.perform(get("/api/wallets/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isNotEmpty())
                .andExpect(jsonPath("$.privateKey").isNotEmpty());

        org.hamcrest.MatcherAssert.assertThat(
                firstWallet.get("publicKey").getAsString(),
                not(secondWallet.get("publicKey").getAsString())
        );
        org.hamcrest.MatcherAssert.assertThat(
                firstWallet.get("privateKey").getAsString(),
                not(secondWallet.get("privateKey").getAsString())
        );
    }

    @Test
    void startsWithNoPendingTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void canCreateSignedTransactionAndViewPendingPool() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        fundWallet(senderWallet);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value(senderWallet.get("publicKey").getAsString()))
                .andExpect(jsonPath("$.receiver").value(receiverWallet.get("publicKey").getAsString()))
                .andExpect(jsonPath("$.amount").value(5.0))
                .andExpect(jsonPath("$.fee").value(0.0))
                .andExpect(jsonPath("$.signature").isNotEmpty())
                .andExpect(jsonPath("$.transactionId").isNotEmpty());

        mockMvc.perform(get("/api/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].receiver").value(receiverWallet.get("publicKey").getAsString()))
                .andExpect(jsonPath("$[0].amount").value(5.0));
    }

    @Test
    void rejectsTransactionWithWrongPrivateKey() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        JsonObject unrelatedWallet = createWallet();
        fundWallet(senderWallet);

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
                unrelatedWallet.get("privateKey").getAsString()
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsTransactionWithInvalidAmount() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTransactionWhenSenderBalanceIsInsufficient() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sender balance is insufficient"));
    }

    @Test
    void reportsWalletBalanceIncludingPendingOutgoingTransactions() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        fundWallet(senderWallet);

        mockMvc.perform(get("/api/wallets/{address}/balance", senderWallet.get("publicKey").getAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.0));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 3, 0.5)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wallets/{address}/balance", senderWallet.get("publicKey").getAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(6.5));
    }

    @Test
    void rejectsMiningWhenThereAreNoPendingTransactions() throws Exception {
        JsonObject minerWallet = createWallet();

        mockMvc.perform(post("/api/transactions/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mineJson(minerWallet)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void canCreateSignedTransactionMineRewardAndValidateChain() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        JsonObject minerWallet = createWallet();
        fundWallet(senderWallet);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 5, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(5.0))
                .andExpect(jsonPath("$.fee").value(1.0))
                .andExpect(jsonPath("$.signature").isNotEmpty());

        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingTransactions").value(1));

        mockMvc.perform(post("/api/transactions/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mineJson(minerWallet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(2))
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].amount").value(5.0))
                .andExpect(jsonPath("$.transactions[0].fee").value(1.0))
                .andExpect(jsonPath("$.transactions[1].sender").value("SYSTEM"))
                .andExpect(jsonPath("$.transactions[1].amount").value(11.0));

        mockMvc.perform(get("/api/chain/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.pendingTransactions").value(0))
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(get("/api/wallets/{address}/balance", senderWallet.get("publicKey").getAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(4.0));

        mockMvc.perform(get("/api/wallets/{address}/balance", receiverWallet.get("publicKey").getAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5.0));

        mockMvc.perform(get("/api/wallets/{address}/balance", minerWallet.get("publicKey").getAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(11.0));
    }

    @Test
    void miningPendingTransactionsRespectsTransactionCountLimit() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        JsonObject minerWallet = createWallet();
        fundWallet(senderWallet);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(senderWallet, receiverWallet, 1)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/transactions/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mineJson(minerWallet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactions", hasSize(5)));

        mockMvc.perform(get("/api/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/transactions/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mineJson(minerWallet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactions", hasSize(2)));

        mockMvc.perform(get("/api/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void resetClearsPendingTransactionsAndRestoresGenesisOnlyChain() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();
        fundWallet(senderWallet);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 3)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingTransactions").value(1));

        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Blockchain reset with a new genesis block"));

        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.pendingTransactions").value(0))
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(get("/api/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void startsWithNoRegisteredPeers() throws Exception {
        mockMvc.perform(get("/api/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void canRegisterPeerAndFetchPeerChain() throws Exception {
        mockMvc.perform(post("/api/peers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"node-b\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.peerId").value("node-b"))
                .andExpect(jsonPath("$.chainSize").value(1))
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(get("/api/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].peerId").value("node-b"));

        mockMvc.perform(get("/api/peers/node-b/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].transactions[0].receiver").value("GENESIS"));
    }

    @Test
    void rejectsBlankPeerRegistrationAndUnknownPeerFetch() throws Exception {
        mockMvc.perform(post("/api/peers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/peers/missing-node/chain"))
                .andExpect(status().isNotFound());
    }

    @Test
    void syncDoesNotAdoptPeerChainWhenItIsNotLonger() throws Exception {
        registerPeer("node-b");

        mockMvc.perform(post("/api/peers/node-b/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peerId").value("node-b"))
                .andExpect(jsonPath("$.peerChainSize").value(1))
                .andExpect(jsonPath("$.localChainSizeBefore").value(1))
                .andExpect(jsonPath("$.localChainSizeAfter").value(1))
                .andExpect(jsonPath("$.peerValid").value(true))
                .andExpect(jsonPath("$.adopted").value(false));
    }

    @Test
    void peerCanMineBlocksAndLocalNodeCanAdoptLongerValidChain() throws Exception {
        JsonObject minerWallet = createWallet();
        registerPeer("node-b");

        mockMvc.perform(post("/api/peers/node-b/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(peerMineJson(minerWallet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(1))
                .andExpect(jsonPath("$.transactions[0].receiver").value(minerWallet.get("publicKey").getAsString()));

        mockMvc.perform(post("/api/peers/node-b/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(peerMineJson(minerWallet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(2));

        mockMvc.perform(get("/api/peers/node-b/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].previousHash").isNotEmpty());

        mockMvc.perform(post("/api/peers/node-b/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peerChainSize").value(3))
                .andExpect(jsonPath("$.localChainSizeBefore").value(1))
                .andExpect(jsonPath("$.localChainSizeAfter").value(3))
                .andExpect(jsonPath("$.peerValid").value(true))
                .andExpect(jsonPath("$.adopted").value(true));

        mockMvc.perform(get("/api/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void resetClearsRegisteredPeers() throws Exception {
        registerPeer("node-b");

        mockMvc.perform(get("/api/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.info.title").value("Blockchain Learning Backend API"))
                .andExpect(jsonPath("$.paths['/api/blocks']").exists())
                .andExpect(jsonPath("$.paths['/api/wallets/{address}/balance']").exists())
                .andExpect(jsonPath("$.paths['/api/peers/{peerId}/sync']").exists());
    }

    private JsonObject createWallet() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isNotEmpty())
                .andExpect(jsonPath("$.privateKey").isNotEmpty())
                .andReturn();

        return JsonParser.parseString(result.getResponse().getContentAsString()).getAsJsonObject();
    }

    private void registerPeer(String peerId) throws Exception {
        mockMvc.perform(post("/api/peers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"%s\"}".formatted(peerId)))
                .andExpect(status().isCreated());
    }

    private void fundWallet(JsonObject wallet) throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"%s\"}".formatted(wallet.get("publicKey").getAsString())))
                .andExpect(status().isCreated());
    }

    private String transactionJson(JsonObject senderWallet, JsonObject receiverWallet, double amount) {
        return transactionJson(senderWallet, receiverWallet, amount, 0);
    }

    private String transactionJson(JsonObject senderWallet, JsonObject receiverWallet, double amount, double fee) {
        return """
                {
                  "sender": "%s",
                  "receiver": "%s",
                  "amount": %s,
                  "fee": %s,
                  "privateKey": "%s"
                }
                """.formatted(
                senderWallet.get("publicKey").getAsString(),
                receiverWallet.get("publicKey").getAsString(),
                amount,
                fee,
                senderWallet.get("privateKey").getAsString()
        );
    }

    private String mineJson(JsonObject wallet) {
        return """
                {
                  "rewardAddress": "%s"
                }
                """.formatted(wallet.get("publicKey").getAsString());
    }

    private String peerMineJson(JsonObject wallet) {
        return """
                {
                  "minerAddress": "%s"
                }
                """.formatted(wallet.get("publicKey").getAsString());
    }

}
