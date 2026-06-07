package com.kna.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class ApiPhase13Tests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetChain() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/chain/consensus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policy\":\"cumulative-difficulty\",\"finalityDelayBlocks\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/chain/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void exposesAndUpdatesConsensusPolicySettings() throws Exception {
        mockMvc.perform(get("/api/chain/consensus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy").value("cumulative-difficulty"))
                .andExpect(jsonPath("$.finalityDelayBlocks").value(0));

        mockMvc.perform(put("/api/chain/consensus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policy\":\"longest-chain\",\"finalityDelayBlocks\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy").value("longest-chain"))
                .andExpect(jsonPath("$.finalityDelayBlocks").value(2));

        mockMvc.perform(get("/api/v1/chain/consensus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policy").value("longest-chain"))
                .andExpect(jsonPath("$.data.finalityDelayBlocks").value(2));
    }

    @Test
    void exposesConsensusBranchDecisions() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"local-miner\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/chain/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/chain/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void openApiIncludesConsensusResearchEndpoints() throws Exception {
        mockMvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/chain/consensus']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/chain/branches']", notNullValue()))
                .andExpect(jsonPath("$.components.schemas.ConsensusSettings.properties.policy.enum", hasItem("longest-chain")))
                .andExpect(jsonPath("$.components.schemas.ConsensusSettings.properties.policy.enum", hasItem("cumulative-difficulty")));
    }
}
