package com.devfuel.weight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WeightLoggingIT.FrozenClockConfig.class)
class WeightLoggingIT {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = ZonedDateTime.of(2026, 8, 20, 17, 10, 0, 0, IST).toInstant();
    private static final Instant YESTERDAY_NOON = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, IST).toInstant();

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/devfuel_test");
        registry.add("spring.datasource.username", () -> "devfuel");
        registry.add("spring.datasource.password", () -> "devfuel");
        registry.add("app.openai.api-key", () -> "");
        registry.add("app.upload.dir", () -> "target/it-uploads");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEvents() {
        jdbcTemplate.execute("TRUNCATE TABLE event_logs");
    }

    @Test
    void postWeightWithoutOpenAiKey() throws Exception {
        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":93.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("WEIGHT"))
                .andExpect(jsonPath("$.structuredJson.value").value(93.5))
                .andExpect(jsonPath("$.structuredJson.unit").value("kg"))
                .andExpect(jsonPath("$.loggedLater").value(false));
    }

    @Test
    void naturalLanguageWeightWithoutOpenAiKey() throws Exception {
        mockMvc.perform(post("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Weight 93.5\",\"source\":\"web\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("WEIGHT"))
                .andExpect(jsonPath("$.structuredJson.value").value(93.5));
    }

    @Test
    void latestUsesOccurrenceTime() throws Exception {
        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":94.0,\"occurrenceDate\":\"2026-08-19\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loggedLater").value(true))
                .andExpect(jsonPath("$.timestamp").value(YESTERDAY_NOON.toString()));

        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":93.5}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/weight/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(93.5))
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.localDate").value("2026-08-20"))
                .andExpect(jsonPath("$.loggedLater").value(false));
    }

    @Test
    void historyIsAscendingAndSupportsRangeAndBackdate() throws Exception {
        mockMvc.perform(post("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Kal weight 93.4\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("WEIGHT"))
                .andExpect(jsonPath("$.loggedLater").value(true));

        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":93.5}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/weight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.points", hasSize(2)))
                .andExpect(jsonPath("$.points[0].value").value(93.4))
                .andExpect(jsonPath("$.points[0].localDate").value("2026-08-19"))
                .andExpect(jsonPath("$.points[0].loggedLater").value(true))
                .andExpect(jsonPath("$.points[1].value").value(93.5))
                .andExpect(jsonPath("$.points[1].localDate").value("2026-08-20"));

        mockMvc.perform(get("/api/weight").param("from", "2026-08-19").param("to", "2026-08-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].localDate").value("2026-08-19"));
    }

    @Test
    void latestIsNotFoundWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/weight/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidWeight() throws Exception {
        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":400}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":93.5,\"unit\":\"lb\"}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class FrozenClockConfig {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(NOW, IST);
        }
    }
}
