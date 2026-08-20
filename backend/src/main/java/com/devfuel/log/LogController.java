package com.devfuel.log;

import com.devfuel.common.EventType;
import com.devfuel.common.exception.ApiException;
import com.devfuel.common.exception.ErrorCode;
import com.devfuel.log.dto.CreateLogRequest;
import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.log.dto.LogItemResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @PostMapping
    public ResponseEntity<CreateLogResponse> createLog(@Valid @RequestBody CreateLogRequest request) {
        CreateLogResponse response = logService.createLog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateLogResponse> createImageLog(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "note", required = false) String note
    ) {
        CreateLogResponse response = logService.createImageLog(file, source, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<LogItemResponse>> getTimeline() {
        return ResponseEntity.ok(logService.getTimeline());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getImage(@PathVariable UUID id) {
        EventLog event = logService.getById(id);
        if (event == null || event.getImageRef() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "image not found");
        }
        Path path = logService.resolveImage(event);
        String filename = path.getFileName().toString().toLowerCase();
        MediaType mediaType = MediaType.IMAGE_JPEG;
        if (filename.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (filename.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/search")
    public ResponseEntity<List<LogItemResponse>> searchLogs(
            @RequestParam("q") String q,
            @RequestParam(value = "eventType", required = false) EventType eventType
    ) {
        return ResponseEntity.ok(Collections.emptyList());
    }
}
