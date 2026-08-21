package com.devfuel.log;

import com.devfuel.common.EventTimePrecision;
import com.devfuel.config.ParserProperties;
import com.devfuel.log.dto.CreateLogRequest;
import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.log.dto.LogItemResponse;
import com.devfuel.parser.EventParserService;
import com.devfuel.parser.ImageParseService;
import com.devfuel.parser.ImageParseService.ImageParseResult;
import com.devfuel.parser.ParseResult;
import com.devfuel.temporal.TemporalResolution;
import com.devfuel.temporal.TemporalResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
public class LogService {

    private static final String DEFAULT_SOURCE = "web";
    private static final String VISION_PARSER_VERSION = "vision-v1";
    private static final Duration LIVE_LOG_SLOP = Duration.ofMinutes(15);

    private final EventLogRepository eventLogRepository;
    private final ParserProperties parserProperties;
    private final EventParserService eventParserService;
    private final ImageStorageService imageStorageService;
    private final ImageParseService imageParseService;
    private final TemporalResolver temporalResolver;
    private final Clock clock;

    public LogService(
            EventLogRepository eventLogRepository,
            ParserProperties parserProperties,
            EventParserService eventParserService,
            ImageStorageService imageStorageService,
            ImageParseService imageParseService,
            TemporalResolver temporalResolver,
            Clock clock
    ) {
        this.eventLogRepository = eventLogRepository;
        this.parserProperties = parserProperties;
        this.eventParserService = eventParserService;
        this.imageStorageService = imageStorageService;
        this.imageParseService = imageParseService;
        this.temporalResolver = temporalResolver;
        this.clock = clock;
    }

    /**
     * Phase 4: parse with OpenAI (or UNKNOWN fallback), then persist.
     */
    @Transactional
    public CreateLogResponse createLog(CreateLogRequest request) {
        Instant now = clock.instant();
        String rawText = request.getMessage().trim();
        String source = resolveSource(request.getSource());

        ParseResult parsed = eventParserService.parse(rawText);
        TemporalResolution time = temporalResolver.resolve(rawText, now);

        EventLog event = new EventLog();
        applyOccurrence(event, time, now);
        event.setRawText(rawText);
        event.setEventType(parsed.eventType());
        event.setStructuredJson(new HashMap<>(parsed.structured()));
        event.setSource(source);
        event.setParserVersion(parserProperties.getVersion());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        EventLog saved = eventLogRepository.save(event);
        return toCreateResponse(saved);
    }

    /**
     * Phase 5A: store image, classify with Vision (UNKNOWN fallback), persist.
     * Occurrence time comes from {@code note} only, never from the vision description.
     */
    @Transactional
    public CreateLogResponse createImageLog(MultipartFile file, String source, String note) {
        Instant now = clock.instant();
        ImageStorageService.StoredImage stored = imageStorageService.store(file);
        ImageParseResult parsed = imageParseService.parse(stored.bytes(), stored.contentType());
        String rawText = resolveImageRawText(note, parsed.rawText());
        String temporalText = note != null && !note.isBlank() ? note.trim() : "";
        TemporalResolution time = temporalResolver.resolve(temporalText, now);

        EventLog event = new EventLog();
        applyOccurrence(event, time, now);
        event.setRawText(rawText);
        event.setEventType(parsed.eventType());
        event.setStructuredJson(new HashMap<>(parsed.structured()));
        event.setSource(resolveSource(source));
        event.setParserVersion(VISION_PARSER_VERSION);
        event.setImageRef(stored.imageRef());
        event.setRawModelOutput(parsed.rawModelOutput() == null ? null : new HashMap<>(parsed.rawModelOutput()));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        EventLog saved = eventLogRepository.save(event);
        return toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public EventLog getById(java.util.UUID id) {
        return eventLogRepository.findById(id).orElse(null);
    }

    public java.nio.file.Path resolveImage(EventLog event) {
        return imageStorageService.resolve(event.getImageRef());
    }

    @Transactional(readOnly = true)
    public List<LogItemResponse> getTimeline() {
        return eventLogRepository.findAllByOrderByEventTimestampDescLoggedAtDesc()
                .stream()
                .map(this::toItemResponse)
                .toList();
    }

    private void applyOccurrence(EventLog event, TemporalResolution time, Instant now) {
        event.setEventTimestamp(time.occurrence());
        event.setLoggedAt(now);
        event.setEventTimePrecision(time.precision());
        event.setEventTimezone(time.zone());
    }

    private String resolveImageRawText(String note, String visionDescription) {
        if (note != null && !note.isBlank()) {
            return note.trim();
        }
        if (visionDescription != null && !visionDescription.isBlank()) {
            return visionDescription.trim();
        }
        return "image log";
    }

    private String resolveSource(String source) {
        if (source == null || source.isBlank()) {
            return DEFAULT_SOURCE;
        }
        return source.trim();
    }

    private CreateLogResponse toCreateResponse(EventLog event) {
        return new CreateLogResponse(
                true,
                event.getId(),
                event.getEventType(),
                event.getRawText(),
                event.getStructuredJson() != null ? event.getStructuredJson() : Collections.emptyMap(),
                event.getSource(),
                event.getParserVersion(),
                event.getEventTimestamp(),
                event.getImageRef(),
                event.getLoggedAt(),
                event.getEventTimePrecision(),
                event.getEventTimezone(),
                isBackdated(event),
                isLoggedLater(event)
        );
    }

    private LogItemResponse toItemResponse(EventLog event) {
        return new LogItemResponse(
                event.getId(),
                event.getEventTimestamp(),
                event.getEventType(),
                event.getRawText(),
                event.getStructuredJson() != null ? event.getStructuredJson() : Collections.emptyMap(),
                event.getSource(),
                event.getParserVersion(),
                event.getImageRef(),
                event.getLoggedAt(),
                event.getEventTimePrecision(),
                event.getEventTimezone(),
                isBackdated(event),
                isLoggedLater(event)
        );
    }

    static boolean isBackdated(EventLog event) {
        if (event.getEventTimePrecision() != EventTimePrecision.NOW) {
            return true;
        }
        if (event.getEventTimestamp() == null || event.getLoggedAt() == null) {
            return false;
        }
        return Duration.between(event.getEventTimestamp(), event.getLoggedAt()).abs().compareTo(LIVE_LOG_SLOP) > 0;
    }

    public static boolean isLoggedLater(EventLog event) {
        if (event.getEventTimestamp() == null || event.getLoggedAt() == null) {
            return false;
        }
        ZoneId zone = ZoneId.of(event.getEventTimezone() == null || event.getEventTimezone().isBlank()
                ? TemporalResolver.DEFAULT_ZONE.getId()
                : event.getEventTimezone());
        return !event.getEventTimestamp().atZone(zone).toLocalDate()
                .equals(event.getLoggedAt().atZone(zone).toLocalDate());
    }
}
