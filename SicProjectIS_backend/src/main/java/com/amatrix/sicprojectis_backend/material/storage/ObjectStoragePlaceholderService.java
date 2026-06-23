package com.amatrix.sicprojectis_backend.material.storage;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "app.file-storage", name = "provider", havingValue = "object")
public class ObjectStoragePlaceholderService implements FileStorageService {
    @Override
    public StoredFile store(MultipartFile file, String relativePath) throws IOException {
        throw new UnsupportedOperationException("Object storage provider is not configured yet");
    }

    @Override
    public Resource load(String fileUrl) {
        throw new UnsupportedOperationException("Object storage provider is not configured yet");
    }

    @Override
    public Path resolve(String fileUrl) {
        throw new UnsupportedOperationException("Object storage provider is not configured yet");
    }

    @Override
    public void delete(String fileUrl) {
        throw new UnsupportedOperationException("Object storage provider is not configured yet");
    }
}
