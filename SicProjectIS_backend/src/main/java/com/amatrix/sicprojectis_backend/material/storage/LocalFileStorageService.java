package com.amatrix.sicprojectis_backend.material.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "app.file-storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
    private final Path localRoot;
    private final String publicUrlPrefix;

    public LocalFileStorageService(
            @Value("${app.file-storage.local-root:${user.dir}/uploads}") String localRoot,
            @Value("${app.file-storage.public-url-prefix:/api/material-versions}") String publicUrlPrefix) {
        this.localRoot = Path.of(localRoot).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix == null ? "" : publicUrlPrefix.stripTrailing();
    }

    @Override
    public StoredFile store(MultipartFile file, String relativePath) throws IOException {
        String cleanName = StringUtils.cleanPath(relativePath);
        if (cleanName.contains("..")) {
            throw new IOException("Invalid storage path");
        }
        Path target = localRoot.resolve(cleanName).normalize();
        if (!target.startsWith(localRoot)) {
            throw new IOException("Invalid storage path");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        String hash = sha256(target);
        String fileUrl = publicUrlPrefix + "/" + cleanName.replace('\\', '/');
        return new StoredFile(fileUrl, hash);
    }

    @Override
    public Resource load(String fileUrl) {
        try {
            return new UrlResource(resolve(fileUrl).toUri());
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load stored file", ex);
        }
    }

    @Override
    public Path resolve(String fileUrl) {
        String path = fileUrl == null ? "" : fileUrl;
        if (!publicUrlPrefix.isBlank() && path.startsWith(publicUrlPrefix + "/")) {
            path = path.substring(publicUrlPrefix.length() + 1);
        }
        return localRoot.resolve(path).normalize();
    }

    @Override
    public void delete(String fileUrl) {
        try {
            Files.deleteIfExists(resolve(fileUrl));
        } catch (IOException ignored) {
        }
    }

    private String sha256(Path target) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
