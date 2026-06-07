package com.kna.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "blockchain.security.enabled=true",
        "blockchain.security.operator-token=test-token",
        "blockchain.rate-limit.enabled=true",
        "blockchain.rate-limit.expensive-limit=1",
        "blockchain.rate-limit.window-ms=60000"
})
@ActiveProfiles("test")
class RateLimitPhase8Tests {

    private static final String OPERATOR = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetChain() throws Exception {
        mockMvc.perform(put("/api/chain/difficulty")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void rateLimitsExpensiveMiningEndpoints() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"first\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"second\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
