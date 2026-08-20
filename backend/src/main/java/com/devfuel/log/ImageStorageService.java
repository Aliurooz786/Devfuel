package com.devfuel.log;

import com.devfuel.common.exception.ApiException;
import com.devfuel.common.exception.ErrorCode;
import com.devfuel.config.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final UploadProperties uploadProperties;

    public ImageStorageService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > uploadProperties.getMaxBytes()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "file exceeds 4MB limit");
        }

        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "file must be image/jpeg, image/png, or image/webp"
            );
        }

        Path dir = Path.of(uploadProperties.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + EXTENSIONS.get(contentType);
            Path destination = dir.resolve(filename);
            byte[] bytes = file.getBytes();
            Files.write(destination, bytes);
            String imageRef = "uploads/" + filename;
            log.info("Stored image at {} (ref={})", destination, imageRef);
            return new StoredImage(imageRef, destination, contentType, bytes);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.PERSISTENCE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Could not store image");
        }
    }

    public Path resolve(String imageRef) {
        if (imageRef == null || imageRef.isBlank() || imageRef.contains("..")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "invalid imageRef");
        }
        String filename = Path.of(imageRef).getFileName().toString();
        Path dir = Path.of(uploadProperties.getDir()).toAbsolutePath().normalize();
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "image not found");
        }
        return path;
    }

    private String normalizeContentType(String contentType, String originalFilename) {
        if (contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }

    public record StoredImage(String imageRef, Path path, String contentType, byte[] bytes) {
    }
}
