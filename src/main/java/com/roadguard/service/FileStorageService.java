package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stores complaint photos on disk (directory from roadguard.upload.dir) under server-generated names. */
@Service
public class FileStorageService {
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");
    private static final Pattern DATA_URL = Pattern.compile("^data:(image/[a-zA-Z.+-]+);base64,([A-Za-z0-9+/=\\s]+)$", Pattern.DOTALL);

    private final RoadGuardProperties.Upload cfg;
    private final Path root;

    public FileStorageService(RoadGuardProperties props) {
        this.cfg = props.getUpload();
        this.root = Paths.get(cfg.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory " + root + " (roadguard.upload.dir)", e);
        }
    }

    public ImageInspector.ImageInfo inspect(byte[] data, String declaredName, String declaredContentType) {
        return ImageInspector.inspect(data, declaredName, declaredContentType, cfg.getMaxBytes(), cfg.getMaxPixels());
    }

    /** Writes the bytes under a fresh random name and returns that name. */
    public String store(byte[] data, String extension) {
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.write(resolve(name), data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the uploaded photo", e);
        }
        return name;
    }

    public Optional<byte[]> read(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) return Optional.empty();
        try {
            Path p = resolve(storedName);
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) return;
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ignored) {
            // an orphaned file is harmless
        }
    }

    /** Result of decoding a base64 data URL: the declared MIME type and the bytes. */
    public record DecodedImage(String declaredType, byte[] bytes) {}

    public DecodedImage decodeDataUrl(String dataUrl) {
        if (dataUrl.length() > cfg.getMaxBytes() * 4 / 3 + 200) {
            throw new ValidationException(Map.of("image", "The photo is too large. The limit is " + (cfg.getMaxBytes() / (1024 * 1024)) + " MB."));
        }
        Matcher m = DATA_URL.matcher(dataUrl);
        if (!m.matches()) throw new ValidationException(Map.of("image", "The photo must be a base64 image (JPEG, PNG or WebP)."));
        try {
            return new DecodedImage(m.group(1), Base64.getMimeDecoder().decode(m.group(2)));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(Map.of("image", "The photo data is corrupted."));
        }
    }

    private Path resolve(String name) {
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Path escapes upload directory");
        return p;
    }
}
