package com.kna.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = "blockchain.persistence.enabled=true")
@ActiveProfiles("test")
class DatabasePersistenceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void databaseModePersistsNormalizedStateTables() throws Exception {
        JsonObject senderWallet = createWallet();
        JsonObject receiverWallet = createWallet();

        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"%s\"}".formatted(senderWallet.get("publicKey").getAsString())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(senderWallet, receiverWallet, 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/peers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"peerId\":\"node-db\"}"))
                .andExpect(status().isCreated());

        Integer blockCount = jdbcTemplate.queryForObject("select count(*) from blockchain_blocks", Integer.class);
        Integer committedTransactionCount = jdbcTemplate.queryForObject(
                "select count(*) from blockchain_transactions where pending = false",
                Integer.class
        );
        Integer pendingTransactionCount = jdbcTemplate.queryForObject(
                "select count(*) from blockchain_transactions where pending = true",
                Integer.class
        );
        Integer walletCount = jdbcTemplate.queryForObject("select count(*) from blockchain_wallets", Integer.class);
        Integer peerCount = jdbcTemplate.queryForObject("select count(*) from blockchain_peers", Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject("select count(*) from blockchain_schema_migrations", Integer.class);

        assertThat(blockCount).isGreaterThanOrEqualTo(2);
        assertThat(committedTransactionCount).isGreaterThanOrEqualTo(2);
        assertThat(pendingTransactionCount).isEqualTo(1);
        assertThat(walletCount).isGreaterThanOrEqualTo(2);
        assertThat(peerCount).isEqualTo(1);
        assertThat(migrationCount).isEqualTo(1);
    }

    private JsonObject createWallet() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/new"))
                .andExpect(status().isOk())
                .andReturn();

        return JsonParser.parseString(result.getResponse().getContentAsString()).getAsJsonObject();
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
