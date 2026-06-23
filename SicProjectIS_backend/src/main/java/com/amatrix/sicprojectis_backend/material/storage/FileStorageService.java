package com.amatrix.sicprojectis_backend.material.storage;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile store(MultipartFile file, String relativePath) throws IOException;

    Resource load(String fileUrl);

    Path resolve(String fileUrl);

    void delete(String fileUrl);
}
