package com.amatrix.sic.material;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.DocumentGenerationCommand;

import tools.jackson.databind.ObjectMapper;

@Service
public class GeneratedDocumentService {
    private final JdbcTemplate jdbc;
    private final MinioObjectStorage storage;
    private final GeneratedDocumentRenderer renderer;
    private final ObjectMapper objectMapper;

    public GeneratedDocumentService(JdbcTemplate jdbc, MinioObjectStorage storage, GeneratedDocumentRenderer renderer,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void generate(DocumentGenerationCommand command) {
        validate(command);
        byte[] content = renderer.render(command);
        String hash = sha256(content);
        String filename = safeCode(command.documentTypeCode()) + "-" + command.stateSeq() + ".pdf";
        String objectKey = "generated/" + command.projectId() + "/" + command.moduleInstanceId() + "/"
                + safeCode(command.commandId()) + "/" + filename;
        String snapshot = write(command.snapshot());
        int inserted = jdbc.update("""
                INSERT IGNORE INTO generated_document(command_id,project_id,module_instance_id,state_seq,
                  state_record_id,document_type,document_name,template_code,object_key,original_filename,
                  content_type,size_bytes,sha256,snapshot_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?, ?,?,CAST(? AS JSON))
                """, command.commandId(), command.projectId(), command.moduleInstanceId(), command.stateSeq(),
                command.stateRecordId(), command.documentTypeCode(), command.documentTypeName(),
                command.templateCode(), objectKey, filename, "application/pdf", content.length, hash, snapshot);
        if (inserted == 0) return;

        storage.put(objectKey, new ByteArrayInputStream(content), content.length, "application/pdf");
        String typeCode = command.outputMaterialTypeCode();
        String typeName = blank(command.outputMaterialTypeName()) ? typeCode : command.outputMaterialTypeName();
        jdbc.update("""
                INSERT INTO material_type(code,name,module_type,allowed_file_types,enabled)
                VALUES (?,?,?,'pdf',TRUE)
                ON DUPLICATE KEY UPDATE name=VALUES(name),module_type=COALESCE(module_type,VALUES(module_type)),
                  allowed_file_types='pdf',enabled=TRUE
                """, typeCode, typeName, command.moduleType());
        Long materialId = material(command, typeCode, typeName);
        Integer maximum = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0) FROM material_version WHERE material_id=? FOR UPDATE",
                Integer.class, materialId);
        int version = (maximum == null ? 0 : maximum) + 1;
        jdbc.update("UPDATE material_version SET file_status='SUPERSEDED' WHERE material_id=? AND file_status='ACTIVE'",
                materialId);
        GeneratedKeyHolder versionKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO material_version(material_id,version_no,original_filename,content_type,size_bytes,
                      sha256,object_key,file_status,uploaded_by) VALUES (?,?,?,?,?,?,?,'ACTIVE',?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, materialId);
            statement.setInt(2, version);
            statement.setString(3, filename);
            statement.setString(4, "application/pdf");
            statement.setLong(5, content.length);
            statement.setString(6, hash);
            statement.setString(7, objectKey);
            statement.setLong(8, command.actorUserId());
            return statement;
        }, versionKey);
        Long materialVersionId = Objects.requireNonNull(versionKey.getKey()).longValue();
        jdbc.update("UPDATE generated_document SET material_version_id=? WHERE command_id=?", materialVersionId,
                command.commandId());
    }

    private Long material(DocumentGenerationCommand command, String typeCode, String typeName) {
        List<Long> rows = jdbc.query("""
                SELECT id FROM material WHERE project_id=? AND material_type_code=? ORDER BY id LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getLong(1), command.projectId(), typeCode);
        if (!rows.isEmpty()) return rows.getFirst();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO material(project_id,material_type_code,title,created_by) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, command.projectId());
            statement.setString(2, typeCode);
            statement.setString(3, typeName);
            statement.setLong(4, command.actorUserId());
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
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
