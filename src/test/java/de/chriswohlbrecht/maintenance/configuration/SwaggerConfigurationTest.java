package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger UI and the generated API docs exist only in local development, never in the published image.
 *
 * <p>One of the few tests with the whole application context: what is checked is the interplay of the profile,
 * springdoc, Spring MVC and Spring Security, which no slice contains completely.
 */
class SwaggerConfigurationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @WithMockUser
    class WithoutProfile {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void apiDocsAreNotAvailable() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        }

        @Test
        void swaggerUiIsNotAvailable() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "local"})
    @WithMockUser
    class WithLocalProfile {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void apiDocsAreAvailable() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }

        @Test
        void swaggerUiIsAvailable() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}
