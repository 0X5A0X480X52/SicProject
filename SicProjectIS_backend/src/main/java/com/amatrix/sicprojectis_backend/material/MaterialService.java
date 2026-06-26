package com.amatrix.sicprojectis_backend.material;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.document.dao.ProcessDocumentFileDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialContextViewDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialTypeDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialVersionDao;
import com.amatrix.sicprojectis_backend.material.dto.MaterialUploadResponse;
import com.amatrix.sicprojectis_backend.material.entity.Material;
import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.material.entity.MaterialType;
import com.amatrix.sicprojectis_backend.material.entity.MaterialVersion;
import com.amatrix.sicprojectis_backend.material.storage.FileStorageService;
import com.amatrix.sicprojectis_backend.material.storage.StoredFile;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordMaterialDao;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;

@Service
public class MaterialService {
    private final ProjectDao projectDao;
    private final PermissionService permissionService;
    private final MaterialTypeDao materialTypeDao;
    private final MaterialDao materialDao;
    private final MaterialVersionDao materialVersionDao;
    private final MaterialContextViewDao materialContextViewDao;
    private final StateRecordMaterialDao stateRecordMaterialDao;
    private final ProcessDocumentFileDao processDocumentFileDao;
    private final FileStorageService storageService;

    public MaterialService(ProjectDao projectDao, PermissionService permissionService, MaterialTypeDao materialTypeDao,
            MaterialDao materialDao, MaterialVersionDao materialVersionDao, MaterialContextViewDao materialContextViewDao,
            StateRecordMaterialDao stateRecordMaterialDao, ProcessDocumentFileDao processDocumentFileDao,
            FileStorageService storageService) {
        this.projectDao = projectDao;
        this.permissionService = permissionService;
        this.materialTypeDao = materialTypeDao;
        this.materialDao = materialDao;
        this.materialVersionDao = materialVersionDao;
        this.materialContextViewDao = materialContextViewDao;
        this.stateRecordMaterialDao = stateRecordMaterialDao;
        this.processDocumentFileDao = processDocumentFileDao;
        this.storageService = storageService;
    }

    @Transactional
    public MaterialUploadResponse upload(AuthenticatedUser user, Long projectId, String materialTypeCode, MultipartFile file) {
        requireAccess(user, projectId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        MaterialType type = materialTypeDao.selectByCode(materialTypeCode);
        if (type == null || Boolean.FALSE.equals(type.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material type not found: " + materialTypeCode);
        }
        validateFile(type, file);
        Material material = materialDao.selectByProjectIdAndTypeId(projectId, type.getMaterialTypeId());
        if (material == null) {
            material = new Material();
            material.setProjectId(projectId);
            material.setMaterialTypeId(type.getMaterialTypeId());
            material.setCreatedBy(user.userId());
            materialDao.insert(material);
        }
        MaterialVersion latest = materialVersionDao.selectLatestByMaterialId(material.getMaterialId());
        int nextVersion = latest == null ? 1 : latest.getVersionNo() + 1;
        String fileName = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin"));
        String relativePath = projectId + "/" + materialTypeCode + "/" + nextVersion + "-"
                + UUID.randomUUID() + "-" + fileName;
        StoredFile stored;
        try {
            stored = storageService.store(file, relativePath);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File storage failed", ex);
        }
        materialVersionDao.clearCurrentByMaterialId(material.getMaterialId());
        MaterialVersion version = new MaterialVersion();
        version.setMaterialId(material.getMaterialId());
        version.setVersionNo(nextVersion);
        version.setFileName(fileName);
        version.setFileUrl(stored.fileUrl());
        version.setFileHash(stored.hash());
        version.setUploadedBy(user.userId());
        version.setUploadedAt(LocalDateTime.now());
        version.setIsCurrent(true);
        materialVersionDao.insert(version);
        return new MaterialUploadResponse(version, materialContextViewDao.selectByMaterialVersionId(version.getMaterialVersionId()));
    }

    public List<MaterialContextView> listProjectMaterials(AuthenticatedUser user, Long projectId) {
        requireAccess(user, projectId);
        return materialContextViewDao.selectByProjectId(projectId);
    }

    public MaterialVersion requireVersion(Long materialVersionId) {
        MaterialVersion version = materialVersionDao.selectById(materialVersionId);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material version not found");
        }
        return version;
    }

    public Resource download(AuthenticatedUser user, Long materialVersionId) {
        MaterialContextView context = materialContextViewDao.selectByMaterialVersionId(materialVersionId);
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material version not found");
        }
        requireAccess(user, context.getProjectId());
        Resource resource = storageService.load(context.getFileUrl());
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file not found");
        }
        return resource;
    }

    public String downloadFileName(Long materialVersionId) {
        MaterialVersion version = requireVersion(materialVersionId);
        return URLEncoder.encode(version.getFileName(), StandardCharsets.UTF_8);
    }

    @Transactional
    public void deleteVersion(AuthenticatedUser user, Long materialVersionId) {
        MaterialContextView context = materialContextViewDao.selectByMaterialVersionId(materialVersionId);
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material version not found");
        }
        requireAccess(user, context.getProjectId());
        boolean linkedToState = stateRecordMaterialDao.selectAll().stream()
                .anyMatch(row -> Objects.equals(row.getMaterialVersionId(), materialVersionId));
        boolean linkedToDocument = processDocumentFileDao.selectAll().stream()
                .anyMatch(row -> Objects.equals(row.getMaterialVersionId(), materialVersionId));
        if (linkedToState || linkedToDocument) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material version is already referenced");
        }
        MaterialVersion version = requireVersion(materialVersionId);
        materialVersionDao.deleteById(materialVersionId);
        storageService.delete(version.getFileUrl());
        if (Boolean.TRUE.equals(version.getIsCurrent())) {
            MaterialVersion latest = materialVersionDao.selectLatestByMaterialId(version.getMaterialId());
            if (latest != null) {
                latest.setIsCurrent(true);
                materialVersionDao.updateById(latest);
            }
        }
    }

    private void validateFile(MaterialType type, MultipartFile file) {
        if (type.getMaxFileSizeMb() != null && file.getSize() > type.getMaxFileSizeMb() * 1024L * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds max size");
        }
        String allowed = type.getAllowedFileTypes();
        if (allowed == null || allowed.isBlank()) {
            return;
        }
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        boolean matched = List.of(allowed.split(",")).stream()
                .map(String::trim)
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .anyMatch(value -> value.equalsIgnoreCase(ext));
        if (!matched) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File type is not allowed");
        }
    }

    private void requireAccess(AuthenticatedUser user, Long projectId) {
        if (projectId == null || projectDao.selectById(projectId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (user == null || !permissionService.canAccessProject(user.userId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
    }
}
