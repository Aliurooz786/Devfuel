package com.devfuel.log;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses compose Postgres on :5433 / database {@code devfuel_test} (not the live {@code devfuel} DB).
 * Testcontainers is not used here because the local Docker API is unavailable to the JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BackdatedLoggingIT.FrozenClockConfig.class)
class BackdatedLoggingIT {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = ZonedDateTime.of(2026, 8, 20, 17, 10, 0, 0, IST).toInstant();
    private static final Instant KAL_NIGHT = ZonedDateTime.of(2026, 8, 19, 21, 0, 0, 0, IST).toInstant();

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
    void i1V3ColumnsExist() {
        Integer loggedAt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'event_logs' AND column_name = 'logged_at'",
                Integer.class
        );
        Integer precision = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'event_logs' AND column_name = 'event_time_precision'",
                Integer.class
        );
        Integer timezone = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'event_logs' AND column_name = 'event_timezone'",
                Integer.class
        );
        assertEquals(1, loggedAt);
        assertEquals(1, precision);
        assertEquals(1, timezone);
    }

    @Test
    void i2AndI3CreateAndTimelineUseOccurrenceTime() throws Exception {
        mockMvc.perform(post("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Kal raat tahri khayi thi\",\"source\":\"web\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timestamp").value(KAL_NIGHT.toString()))
                .andExpect(jsonPath("$.loggedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.eventTimePrecision").value("PERIOD"))
                .andExpect(jsonPath("$.eventTimezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.loggedLater").value(true))
                .andExpect(jsonPath("$.backdated").value(true));

        mockMvc.perform(post("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"1 cigarette pee li\",\"source\":\"web\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
                .andExpect(jsonPath("$.loggedLater").value(false));

        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].timestamp").value(NOW.toString()))
                .andExpect(jsonPath("$[0].loggedAt").value(NOW.toString()))
                .andExpect(jsonPath("$[1].timestamp").value(KAL_NIGHT.toString()))
                .andExpect(jsonPath("$[1].eventTimePrecision").value("PERIOD"));

        String precision = jdbcTemplate.queryForObject(
                "SELECT event_time_precision FROM event_logs WHERE raw_text = 'Kal raat tahri khayi thi'",
                String.class
        );
        Instant occurrence = jdbcTemplate.queryForObject(
                "SELECT event_timestamp FROM event_logs WHERE raw_text = 'Kal raat tahri khayi thi'",
                Instant.class
        );
        Instant loggedAt = jdbcTemplate.queryForObject(
                "SELECT logged_at FROM event_logs WHERE raw_text = 'Kal raat tahri khayi thi'",
                Instant.class
        );
        assertEquals("PERIOD", precision);
        assertEquals(KAL_NIGHT, occurrence);
        assertEquals(NOW, loggedAt);
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
