package com.kna.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$[0].data").value("Genesis Block"))
                .andExpect(jsonPath("$[0].previousHash").value("0"));
    }

    @Test
    void canAddBlockAndValidateChain() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"Learn block hash\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.index").value(1))
                .andExpect(jsonPath("$.data").value("Learn block hash"));

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

}
