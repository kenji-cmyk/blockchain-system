package com.kna.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class ApiDocsPhase12Tests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesSwaggerUiForOpenApiDocument() throws Exception {
        mockMvc.perform(get("/swagger-ui"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("SwaggerUIBundle")))
                .andExpect(content().string(containsString("/api/docs/openapi")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Blockchain API Swagger UI")));
    }

    @Test
    void openApiIncludesExamplesSecurityAndSchemasForSwaggerAndApidogImport() throws Exception {
        mockMvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.components.securitySchemes.operatorBearer.type").value("http"))
                .andExpect(jsonPath("$.components.schemas.CreateTransactionRequest.properties.amount.type").value("number"))
                .andExpect(jsonPath("$.paths['/api/transactions'].post.requestBody.content['application/json'].example.amount").value(1.25))
                .andExpect(jsonPath("$.paths['/api/transactions'].post.requestBody.content['application/json'].example.privateKey").value("{{senderPrivateKey}}"))
                .andExpect(jsonPath("$.paths['/api/transactions/mine'].post.security[0].operatorBearer").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/transactions'].post.requestBody.content['application/json'].example.sender").value("{{senderPublicKey}}"));
    }

    @Test
    void apidogEndpointReturnsImportableOpenApiJsonWithUsageNotes() throws Exception {
        mockMvc.perform(get("/api/docs/apidog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.info.title").value("Blockchain Learning Backend API"))
                .andExpect(jsonPath("$.x-apidog-import-notes", hasItem("Import this JSON as OpenAPI 3.0 in Apidog.")))
                .andExpect(jsonPath("$.paths['/api/v1/blocks'].get.responses['200'].content['application/json'].schema.$ref").value("#/components/schemas/ApiEnvelope"));
    }
}
