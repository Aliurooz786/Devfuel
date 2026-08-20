package com.devfuel.log;

import com.devfuel.common.EventTimePrecision;
import com.devfuel.common.EventType;
import com.devfuel.config.ParserProperties;
import com.devfuel.log.dto.CreateLogRequest;
import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.parser.EventParserService;
import com.devfuel.parser.ImageParseService;
import com.devfuel.parser.ImageParseService.ImageParseResult;
import com.devfuel.parser.ParseResult;
import com.devfuel.temporal.TemporalResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogServiceBackdatedLoggingTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = ZonedDateTime.of(2026, 8, 20, 17, 10, 0, 0, IST).toInstant();

    private EventLogRepository repository;
    private EventParserService eventParserService;
    private ImageStorageService imageStorageService;
    private ImageParseService imageParseService;
    private LogService logService;
    private final AtomicReference<EventLog> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = mock(EventLogRepository.class);
        eventParserService = mock(EventParserService.class);
        imageStorageService = mock(ImageStorageService.class);
        imageParseService = mock(ImageParseService.class);
        ParserProperties parserProperties = new ParserProperties();
        parserProperties.setVersion("v1");
        when(repository.save(any(EventLog.class))).thenAnswer(invocation -> {
            EventLog event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId(UUID.randomUUID());
            }
            saved.set(event);
            return event;
        });
        logService = new LogService(
                repository,
                parserProperties,
                eventParserService,
                imageStorageService,
                imageParseService,
                new TemporalResolver(IST),
                Clock.fixed(NOW, IST)
        );
    }

    @Test
    void s1KalRaatUsesOccurrenceAndCaptureClocks() {
        when(eventParserService.parse(any())).thenReturn(ParseResult.of(EventType.FOOD, Map.of("item", "tahri")));

        CreateLogRequest request = new CreateLogRequest();
        request.setMessage("Kal raat tahri khayi thi");
        CreateLogResponse response = logService.createLog(request);

        EventLog event = saved.get();
        Instant occurrence = ZonedDateTime.of(2026, 8, 19, 21, 0, 0, 0, IST).toInstant();
        assertEquals(occurrence, event.getEventTimestamp());
        assertEquals(NOW, event.getLoggedAt());
        assertEquals(NOW, event.getCreatedAt());
        assertEquals(EventTimePrecision.PERIOD, event.getEventTimePrecision());
        assertEquals(occurrence, response.timestamp());
        assertEquals(NOW, response.loggedAt());
        assertTrue(response.loggedLater());
        assertTrue(response.backdated());
        assertFalse(event.getStructuredJson().containsKey("temporal"));
    }

    @Test
    void s2UnknownParseStillBackdates() {
        when(eventParserService.parse(any())).thenReturn(ParseResult.unknownParseError());

        CreateLogRequest request = new CreateLogRequest();
        request.setMessage("Parso 2 cigarette pi thi");
        logService.createLog(request);

        EventLog event = saved.get();
        assertEquals(EventType.UNKNOWN, event.getEventType());
        assertEquals(ZonedDateTime.of(2026, 8, 18, 12, 0, 0, 0, IST).toInstant(), event.getEventTimestamp());
        assertEquals(EventTimePrecision.DAY, event.getEventTimePrecision());
        assertEquals(NOW, event.getLoggedAt());
    }

    @Test
    void s3ImageNoteDrivesResolver() {
        mockImagePipeline();
        logService.createImageLog(sampleFile(), "web", "Parso 2 cigarette pi thi");

        EventLog event = saved.get();
        assertEquals(ZonedDateTime.of(2026, 8, 18, 12, 0, 0, 0, IST).toInstant(), event.getEventTimestamp());
        assertEquals(EventTimePrecision.DAY, event.getEventTimePrecision());
        assertEquals(NOW, event.getLoggedAt());
        assertFalse(event.getStructuredJson().containsKey("temporal"));
    }

    @Test
    void s4ImageWithoutNoteIsNow() {
        mockImagePipeline();
        CreateLogResponse response = logService.createImageLog(sampleFile(), "web", "  ");

        EventLog event = saved.get();
        assertEquals(NOW, event.getEventTimestamp());
        assertEquals(NOW, event.getLoggedAt());
        assertEquals(EventTimePrecision.NOW, event.getEventTimePrecision());
        assertFalse(response.loggedLater());
        assertFalse(response.backdated());
    }

    private void mockImagePipeline() {
        when(imageStorageService.store(any())).thenReturn(
                new ImageStorageService.StoredImage("uploads/x.jpg", Path.of("x.jpg"), "image/jpeg", new byte[]{1})
        );
        when(imageParseService.parse(any(), any())).thenReturn(
                new ImageParseResult(EventType.FOOD, new HashMap<>(Map.of("item", "tahri")), "vision text", Map.of())
        );
    }

    private static MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "meal.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
