package com.devfuel.weight;

import com.devfuel.common.EventTimePrecision;
import com.devfuel.common.EventType;
import com.devfuel.common.exception.ApiException;
import com.devfuel.log.EventLog;
import com.devfuel.log.EventLogRepository;
import com.devfuel.log.LogService;
import com.devfuel.log.dto.CreateLogRequest;
import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.weight.dto.CreateWeightRequest;
import com.devfuel.weight.dto.WeightHistoryResponse;
import com.devfuel.weight.dto.WeightPointResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeightServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = ZonedDateTime.of(2026, 8, 20, 17, 10, 0, 0, IST).toInstant();
    private static final Instant YESTERDAY_NOON = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, IST).toInstant();

    private EventLogRepository repository;
    private LogService logService;
    private WeightService weightService;

    @BeforeEach
    void setUp() {
        repository = mock(EventLogRepository.class);
        logService = mock(LogService.class);
        weightService = new WeightService(repository, logService, Clock.fixed(NOW, IST));
    }

    @Test
    void latestPicksMaxOccurrenceThenMaxLoggedAt() {
        EventLog older = weightRow(YESTERDAY_NOON, NOW, 94.0);
        EventLog firstToday = weightRow(NOW, NOW.minusSeconds(60), 93.0);
        EventLog laterCapture = weightRow(NOW, NOW, 93.5);
        when(repository.findByEventTypeOrderByEventTimestampDescLoggedAtDesc(EventType.WEIGHT))
                .thenReturn(List.of(laterCapture, firstToday, older));

        WeightPointResponse latest = weightService.latest();

        assertEquals(93.5, latest.value());
        assertEquals(laterCapture.getId(), latest.id());
        assertEquals("2026-08-20", latest.localDate());
    }

    @Test
    void historyIsAscendingAndSkipsUnparseable() {
        EventLog yesterday = weightRow(YESTERDAY_NOON, NOW, 94.0);
        EventLog invalid = weightRow(NOW.minusSeconds(30), NOW, 93.0);
        invalid.setStructuredJson(Map.of());
        EventLog today = weightRow(NOW, NOW, 93.5);
        when(repository.findByEventTypeOrderByEventTimestampAscLoggedAtAsc(EventType.WEIGHT))
                .thenReturn(List.of(yesterday, invalid, today));

        WeightHistoryResponse history = weightService.history(null, null);

        assertEquals("kg", history.unit());
        assertEquals(2, history.points().size());
        assertEquals(94.0, history.points().get(0).value());
        assertEquals("2026-08-19", history.points().get(0).localDate());
        assertTrue(history.points().get(0).loggedLater());
        assertEquals(93.5, history.points().get(1).value());
        assertEquals("2026-08-20", history.points().get(1).localDate());
    }

    @Test
    void historyRespectsInclusiveLocalDateRange() {
        EventLog yesterday = weightRow(YESTERDAY_NOON, NOW, 94.0);
        EventLog today = weightRow(NOW, NOW, 93.5);
        when(repository.findByEventTypeOrderByEventTimestampAscLoggedAtAsc(EventType.WEIGHT))
                .thenReturn(List.of(yesterday, today));

        WeightHistoryResponse history = weightService.history("2026-08-19", "2026-08-19");

        assertEquals(1, history.points().size());
        assertEquals("2026-08-19", history.points().get(0).localDate());
    }

    @Test
    void latestThrowsWhenEmpty() {
        when(repository.findByEventTypeOrderByEventTimestampDescLoggedAtDesc(EventType.WEIGHT))
                .thenReturn(List.of());
        ApiException ex = assertThrows(ApiException.class, () -> weightService.latest());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void createSynthesizesMessageAndReusesLogService() {
        CreateLogResponse created = new CreateLogResponse(
                true, UUID.randomUUID(), EventType.WEIGHT, "Weight 93.5 kg",
                Map.of("value", 93.5, "unit", "kg"), "web", "v1",
                NOW, null, NOW, EventTimePrecision.NOW, "Asia/Kolkata", false, false
        );
        when(logService.createLog(any())).thenReturn(created);

        CreateWeightRequest request = new CreateWeightRequest();
        request.setValue(93.5);
        CreateLogResponse response = weightService.create(request);

        ArgumentCaptor<CreateLogRequest> captor = ArgumentCaptor.forClass(CreateLogRequest.class);
        verify(logService).createLog(captor.capture());
        assertEquals("Weight 93.5 kg", captor.getValue().getMessage());
        assertEquals(created, response);
    }

    @Test
    void createPrefixesOccurrenceDateForBackdating() {
        when(logService.createLog(any())).thenReturn(mock(CreateLogResponse.class));
        CreateWeightRequest request = new CreateWeightRequest();
        request.setValue(93.4);
        request.setOccurrenceDate("2026-08-19");
        weightService.create(request);

        ArgumentCaptor<CreateLogRequest> captor = ArgumentCaptor.forClass(CreateLogRequest.class);
        verify(logService).createLog(captor.capture());
        assertEquals("2026-08-19 Weight 93.4 kg", captor.getValue().getMessage());
    }

    @Test
    void createRejectsOutOfRangeAndLb() {
        CreateWeightRequest tooHeavy = new CreateWeightRequest();
        tooHeavy.setValue(400.0);
        ApiException range = assertThrows(ApiException.class, () -> weightService.create(tooHeavy));
        assertEquals(HttpStatus.BAD_REQUEST, range.getStatus());

        CreateWeightRequest pounds = new CreateWeightRequest();
        pounds.setValue(93.5);
        pounds.setUnit("lb");
        ApiException unit = assertThrows(ApiException.class, () -> weightService.create(pounds));
        assertEquals(HttpStatus.BAD_REQUEST, unit.getStatus());
    }

    private static EventLog weightRow(Instant occurrence, Instant loggedAt, double kg) {
        EventLog event = new EventLog();
        event.setId(UUID.randomUUID());
        event.setEventType(EventType.WEIGHT);
        event.setEventTimestamp(occurrence);
        event.setLoggedAt(loggedAt);
        event.setEventTimePrecision(EventTimePrecision.DAY);
        event.setEventTimezone("Asia/Kolkata");
        event.setStructuredJson(Map.of("value", kg, "unit", "kg"));
        event.setRawText("Weight " + kg + " kg");
        return event;
    }
}
