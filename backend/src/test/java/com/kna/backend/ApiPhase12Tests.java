package com.kna.backend;

import com.kna.backend.pkg.money.MoneyUnits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class ApiPhase12Tests {

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
    void v1EndpointsReturnConsistentSuccessEnvelopeWithMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.metadata.count").value(1))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].index").value(0))
                .andExpect(jsonPath("$.data[0].transactions[0].amountUnits").value(1_000_000_000L));

        mockMvc.perform(get("/api/v1/chain/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.metadata").isMap());
    }

    @Test
    void v1ErrorsReturnConsistentFailureEnvelopeWithoutChangingLegacyErrors() throws Exception {
        mockMvc.perform(get("/api/v1/blocks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.status").value(404))
                .andExpect(jsonPath("$.error.message").value("Block index does not exist"))
                .andExpect(jsonPath("$.error.path").value("/api/v1/blocks/99"));

        mockMvc.perform(get("/api/blocks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Block index does not exist"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void openApiDocumentIsBuiltFromSharedRouteMetadataForLegacyAndV1Routes() throws Exception {
        mockMvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/blocks'].get.summary").value("View the entire chain"))
                .andExpect(jsonPath("$.paths['/api/blocks'].post.summary").value("Mine a legacy demo block"))
                .andExpect(jsonPath("$.paths['/api/v1/blocks'].get.summary").value("View the entire chain"))
                .andExpect(jsonPath("$.paths['/api/v1/chain/status'].get.summary").value("View chain status"))
                .andExpect(jsonPath("$.paths['/api/v1/docs/openapi']").doesNotExist());
    }

    @Test
    void moneyUnitsUseFixedSmallestUnitRepresentation() {
        assertThat(MoneyUnits.toUnits(1.25)).isEqualTo(125_000_000L);
        assertThat(MoneyUnits.fromUnits(125_000_000L)).isEqualTo(1.25);
        assertThat(MoneyUnits.canonical(1.23000000)).isEqualTo("1.23");
        assertThatThrownBy(() -> MoneyUnits.toUnits(0.123456789))
                .isInstanceOf(ArithmeticException.class);
    }
}
