package com.amatrix.sic.material.service.document;

import com.amatrix.sic.material.storage.MinioObjectStorage;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

import com.amatrix.sic.material.dao.MaterialMapper;
import com.amatrix.sic.material.dao.GeneratedDocumentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.DocumentGenerationCommand;

import tools.jackson.databind.ObjectMapper;

/**
 * 按 commandId 去重生成 PDF、哈希与 MinIO 对象；创建/复用输出材料类型及材料，生成新 ACTIVE 版本并关联 generated_document。
 */
@Service
public class GeneratedDocumentService {
    private final MaterialMapper materials;
    private final GeneratedDocumentMapper documents;
    private final MinioObjectStorage storage;
    private final GeneratedDocumentRenderer renderer;
    private final ObjectMapper objectMapper;

    public GeneratedDocumentService(MaterialMapper materials,GeneratedDocumentMapper documents, MinioObjectStorage storage, GeneratedDocumentRenderer renderer,
            ObjectMapper objectMapper) {
        this.materials = materials;this.documents = documents;
        this.storage = storage;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    /**
     * 先渲染 PDF，再用 commandId 在数据库抢占生成命令；成功抢占后写对象、材料版本及生成关系。
     * 对象存储调用发生在本方法事务内，但 MinIO 不参加数据库提交，仍存在失败窗口。
     */
    @Transactional
    public void generate(DocumentGenerationCommand command) {
        validate(command);
        byte[] content = renderer.render(command);
        String hash = sha256(content);
        String filename = safeCode(command.documentTypeCode()) + "-" + command.stateSeq() + ".pdf";
        String objectKey = "generated/" + command.projectId() + "/" + command.moduleInstanceId() + "/"
                + safeCode(command.commandId()) + "/" + filename;
        String snapshot = write(command.snapshot());
        java.util.Map<String,Object> document=new java.util.LinkedHashMap<>();
        document.put("command",command);document.put("key",objectKey);document.put("filename",filename);
        document.put("size",content.length);document.put("hash",hash);document.put("snapshot",snapshot);
        int inserted=documents.insert(document);
        if (inserted == 0) return;

        storage.put(objectKey, new ByteArrayInputStream(content), content.length, "application/pdf");
        String typeCode = command.outputMaterialTypeCode();
        String typeName = blank(command.outputMaterialTypeName()) ? typeCode : command.outputMaterialTypeName();
        materials.generatedType(typeCode,typeName,command.moduleType());
        Long materialId=material(command,typeCode,typeName);
        Integer maximum=materials.maximumVersion(materialId);int version=(maximum==null?0:maximum)+1;
        materials.supersede(materialId);
        java.util.Map<String,Object> insert=new java.util.LinkedHashMap<>();
        insert.put("material",materialId);insert.put("number",version);insert.put("filename",filename);
        insert.put("contentType","application/pdf");insert.put("size",content.length);insert.put("sha256",hash);
        insert.put("key",objectKey);insert.put("actor",command.actorUserId());materials.insertVersion(insert);
        documents.materialVersion(command.commandId(),((Number)Objects.requireNonNull(insert.get("id"))).longValue());
    }

    private Long material(DocumentGenerationCommand command,String typeCode,String typeName) {
        Long existing=materials.material(command.projectId(),typeCode,true);
        if(existing!=null)return existing;
        java.util.Map<String,Object> insert=new java.util.LinkedHashMap<>();
        insert.put("project",command.projectId());insert.put("code",typeCode);insert.put("title",typeName);
        insert.put("actor",command.actorUserId());materials.insertMaterial(insert);
        return ((Number)Objects.requireNonNull(insert.get("id"))).longValue();
    }

    private static void validate(DocumentGenerationCommand command) {
        if (command == null || blank(command.commandId()) || command.projectId() == null
                || command.moduleInstanceId() == null || command.stateSeq() == null
                || blank(command.documentTypeCode()) || blank(command.outputMaterialTypeCode())
                || command.actorUserId() == null) {
            throw new IllegalArgumentException("Document generation command is incomplete");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize generated document snapshot", ex);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash generated document", ex);
        }
    }

    private static String safeCode(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
