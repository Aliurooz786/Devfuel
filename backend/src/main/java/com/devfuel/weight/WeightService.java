package com.devfuel.weight;

import com.devfuel.common.EventType;
import com.devfuel.common.exception.ApiException;
import com.devfuel.common.exception.ErrorCode;
import com.devfuel.log.EventLog;
import com.devfuel.log.EventLogRepository;
import com.devfuel.log.LogService;
import com.devfuel.log.dto.CreateLogRequest;
import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.temporal.TemporalResolver;
import com.devfuel.weight.dto.CreateWeightRequest;
import com.devfuel.weight.dto.WeightHistoryResponse;
import com.devfuel.weight.dto.WeightPointResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class WeightService {

    static final int HISTORY_CAP = 365;

    private final EventLogRepository eventLogRepository;
    private final LogService logService;
    private final Clock clock;

    public WeightService(EventLogRepository eventLogRepository, LogService logService, Clock clock) {
        this.eventLogRepository = eventLogRepository;
        this.logService = logService;
        this.clock = clock;
    }

    @Transactional
    public CreateLogResponse create(CreateWeightRequest request) {
        WeightValue value = WeightValue.ofKilograms(request.getValue())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "value must be between 30.0 and 250.0 kg"
                ));
        validateUnit(request.getUnit());
        LocalDate occurrenceDate = parseOccurrenceDate(request.getOccurrenceDate());

        CreateLogRequest logRequest = new CreateLogRequest();
        logRequest.setMessage(synthesizeMessage(value, occurrenceDate));
        logRequest.setSource(request.getSource());
        return logService.createLog(logRequest);
    }

    @Transactional(readOnly = true)
    public WeightPointResponse latest() {
        return eventLogRepository.findByEventTypeOrderByEventTimestampDescLoggedAtDesc(EventType.WEIGHT)
                .stream()
                .map(this::toPoint)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "no weight logged"
                ));
    }

    @Transactional(readOnly = true)
    public WeightHistoryResponse history(String from, String to) {
        LocalDate fromDate = parseRangeBound(from, "from");
        LocalDate toDate = parseRangeBound(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "from must be on or before to");
        }

        List<WeightPointResponse> points = eventLogRepository
                .findByEventTypeOrderByEventTimestampAscLoggedAtAsc(EventType.WEIGHT)
                .stream()
                .map(this::toPoint)
                .flatMap(Optional::stream)
                .filter(point -> inRange(point.localDate(), fromDate, toDate))
                .toList();

        if (points.size() > HISTORY_CAP) {
            points = points.subList(points.size() - HISTORY_CAP, points.size());
        }
        return new WeightHistoryResponse(WeightValue.UNIT, List.copyOf(points));
    }

    private Optional<WeightPointResponse> toPoint(EventLog event) {
        return WeightValue.fromStructured(event.getStructuredJson()).map(value -> {
            ZoneId zone = zoneOf(event);
            String localDate = event.getEventTimestamp().atZone(zone).toLocalDate().toString();
            return new WeightPointResponse(
                    event.getId(),
                    value.asDouble(),
                    WeightValue.UNIT,
                    event.getEventTimestamp(),
                    event.getLoggedAt(),
                    event.getEventTimePrecision(),
                    LogService.isLoggedLater(event),
                    localDate
            );
        });
    }

    private String synthesizeMessage(WeightValue value, LocalDate occurrenceDate) {
        String body = "Weight " + value.kilograms().toPlainString() + " kg";
        if (occurrenceDate == null) {
            return body;
        }
        return occurrenceDate + " " + body;
    }

    private LocalDate parseOccurrenceDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LocalDate date = parseIsoDate(raw.trim(), "occurrenceDate");
        LocalDate today = clock.instant().atZone(TemporalResolver.DEFAULT_ZONE).toLocalDate();
        if (date.isAfter(today)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "occurrenceDate cannot be in the future");
        }
        return date;
    }

    private static LocalDate parseRangeBound(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseIsoDate(raw.trim(), field);
    }

    private static LocalDate parseIsoDate(String raw, String field) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, field + " must be YYYY-MM-DD");
        }
    }

    private static void validateUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return;
        }
        if (!WeightValue.UNIT.equalsIgnoreCase(unit.trim())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "unit must be kg");
        }
    }

    private static boolean inRange(String localDate, LocalDate from, LocalDate to) {
        LocalDate date = LocalDate.parse(localDate);
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private static ZoneId zoneOf(EventLog event) {
        String zone = event.getEventTimezone();
        if (zone == null || zone.isBlank()) {
            return TemporalResolver.DEFAULT_ZONE;
        }
        return ZoneId.of(zone);
    }
}
