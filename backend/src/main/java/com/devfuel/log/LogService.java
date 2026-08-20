package com.devfuel.log;

import com.devfuel.config.ParserProperties;
import com.devfuel.log.dto.CreateLogRequest;
import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.log.dto.LogItemResponse;
import com.devfuel.parser.EventParserService;
import com.devfuel.parser.ImageParseService;
import com.devfuel.parser.ImageParseService.ImageParseResult;
import com.devfuel.parser.ParseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
public class LogService {

    private static final String DEFAULT_SOURCE = "web";
    private static final String VISION_PARSER_VERSION = "vision-v1";

    private final EventLogRepository eventLogRepository;
    private final ParserProperties parserProperties;
    private final EventParserService eventParserService;
    private final ImageStorageService imageStorageService;
    private final ImageParseService imageParseService;

    public LogService(
            EventLogRepository eventLogRepository,
            ParserProperties parserProperties,
            EventParserService eventParserService,
            ImageStorageService imageStorageService,
            ImageParseService imageParseService
    ) {
        this.eventLogRepository = eventLogRepository;
        this.parserProperties = parserProperties;
        this.eventParserService = eventParserService;
        this.imageStorageService = imageStorageService;
        this.imageParseService = imageParseService;
    }

    /**
     * Phase 4: parse with OpenAI (or UNKNOWN fallback), then persist.
     */
    @Transactional
    public CreateLogResponse createLog(CreateLogRequest request) {
        Instant now = Instant.now();
        String rawText = request.getMessage().trim();
        String source = resolveSource(request.getSource());

        ParseResult parsed = eventParserService.parse(rawText);

        EventLog event = new EventLog();
        event.setEventTimestamp(now);
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
     */
    @Transactional
    public CreateLogResponse createImageLog(MultipartFile file, String source, String note) {
        Instant now = Instant.now();
        ImageStorageService.StoredImage stored = imageStorageService.store(file);
        ImageParseResult parsed = imageParseService.parse(stored.bytes(), stored.contentType());
        String rawText = resolveImageRawText(note, parsed.rawText());

        EventLog event = new EventLog();
        event.setEventTimestamp(now);
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
        return eventLogRepository.findAllByOrderByEventTimestampDescCreatedAtDesc()
                .stream()
                .map(this::toItemResponse)
                .toList();
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
                event.getImageRef()
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
                event.getImageRef()
        );
    }
}
