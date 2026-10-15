package com.amatrix.sic.migration;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

/** One-shot, idempotent local-upload to MinIO migration and byte-level verifier. */
public final class LegacyFileMigrationTool {
    private static final String LEGACY_PREFIX = "/api/material-versions/";

    private LegacyFileMigrationTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("prepare-files") || args[0].equals("verify-files"))) {
            throw new IllegalArgumentException("Expected one command: prepare-files or verify-files");
        }
        Config config = Config.fromEnvironment();
        MinioClient minio = MinioClient.builder().endpoint(config.minioEndpoint())
                .credentials(config.minioAccessKey(), config.minioSecretKey()).build();
        if (args[0].equals("prepare-files")) prepare(config, minio); else verify(config, minio);
    }

    private static void prepare(Config config, MinioClient minio) throws Exception {
        Path uploadsRoot = config.uploadsRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(uploadsRoot)) throw new IllegalStateException("Legacy uploads directory is missing: " + uploadsRoot);
        ensureBucket(minio, config.bucket());
        try (Connection connection = connect(config)) {
            createManifest(connection);
            List<LegacyFile> files = legacyFiles(connection);
            if (files.isEmpty()) {
                System.out.println("No legacy material files were found; manifest remains empty.");
                return;
            }
            int uploaded = 0;
            for (LegacyFile legacy : files) {
                Path source = resolveSource(uploadsRoot, legacy.fileUrl());
                if (!Files.isRegularFile(source)) throw new IllegalStateException(
                        "Missing legacy file for materialVersionId=" + legacy.id() + ": " + source);
                long size = Files.size(source);
                String sha256 = sha256(Files.newInputStream(source));
                if (legacy.expectedHash() != null && !legacy.expectedHash().isBlank()
                        && !sha256.equalsIgnoreCase(legacy.expectedHash())) {
                    throw new IllegalStateException("Legacy SHA-256 mismatch for materialVersionId=" + legacy.id());
                }
                String filename = safeFilename(legacy.filename(), source);
                String objectKey = "legacy/material-versions/" + legacy.id() + "/" + filename;
                String contentType = Files.probeContentType(source);
                if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
                try (InputStream input = Files.newInputStream(source)) {
                    minio.putObject(PutObjectArgs.builder().bucket(config.bucket()).object(objectKey)
                            .stream(input, size, -1).contentType(contentType)
                            .userMetadata(Map.of("sha256", sha256)).build());
                }
                upsertManifest(connection, legacy, source, objectKey, sha256, size, contentType);
                uploaded++;
            }
            assertManifestComplete(connection);
            System.out.println("Prepared and verified " + uploaded + " legacy file(s) for SQL import.");
        }
    }

    private static void verify(Config config, MinioClient minio) throws Exception {
        int verified = 0;
        try (Connection connection = connect(config);
                var statement = connection.prepareStatement("""
                        SELECT v.id,v.object_key,v.sha256,v.size_bytes
                          FROM sic_material.material_version v
                         WHERE v.object_key LIKE 'legacy/material-versions/%' ORDER BY v.id
                        """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String objectKey = rows.getString("object_key");
                String expectedHash = rows.getString("sha256");
                long expectedSize = rows.getLong("size_bytes");
                try (InputStream input = minio.getObject(GetObjectArgs.builder()
                        .bucket(config.bucket()).object(objectKey).build())) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long actualSize = 0;
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        digest.update(buffer, 0, read);
                        actualSize += read;
                    }
                    String actualHash = HexFormat.of().formatHex(digest.digest());
                    if (actualSize != expectedSize || !actualHash.equalsIgnoreCase(expectedHash)) {
                        throw new IllegalStateException("MinIO verification failed for materialVersionId=" + id);
                    }
                }
                verified++;
            }
            int expected = scalar(connection, "SELECT COUNT(*) FROM sic_legacy.migration_file_manifest");
            if (verified != expected) throw new IllegalStateException(
                    "MinIO object count mismatch: expected=" + expected + ", verified=" + verified);
        }
        System.out.println("Byte-verified " + verified + " migrated MinIO object(s).");
    }

    static Path resolveSource(Path uploadsRoot, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) throw new IllegalStateException("Legacy file URL is empty");
        String path = fileUrl.trim().replace('\\', '/');
        if (path.startsWith("http://") || path.startsWith("https://")) path = URI.create(path).getPath();
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        if (path.startsWith(LEGACY_PREFIX)) path = path.substring(LEGACY_PREFIX.length());
        while (path.startsWith("/")) path = path.substring(1);
        Path resolved = uploadsRoot.resolve(path).normalize();
        if (!resolved.startsWith(uploadsRoot)) throw new IllegalStateException("Legacy file path escapes uploads root: " + fileUrl);
        return resolved;
    }

    private static String safeFilename(String databaseName, Path source) {
        String value = databaseName == null || databaseName.isBlank()
                ? source.getFileName().toString() : databaseName;
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
        return value.isBlank() ? "legacy-file.bin" : value;
    }

    private static void ensureBucket(MinioClient minio, String bucket) throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static Connection connect(Config config) throws Exception {
        return DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
    }

    private static void createManifest(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sic_legacy.migration_file_manifest (
                      material_version_id BIGINT PRIMARY KEY,
                      source_file_url VARCHAR(1000) NOT NULL,
                      source_absolute_path VARCHAR(2000) NOT NULL,
                      object_key VARCHAR(500) NOT NULL UNIQUE,
                      sha256 CHAR(64) NOT NULL,
                      size_bytes BIGINT NOT NULL,
                      content_type VARCHAR(128) NOT NULL,
                      migrated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    )
                    """);
        }
    }

    private static List<LegacyFile> legacyFiles(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT material_version_id,file_url,file_hash,file_name
                  FROM sic_legacy.material_version ORDER BY material_version_id
                """)) {
            try (var rows = statement.executeQuery()) {
                var result = new java.util.ArrayList<LegacyFile>();
                while (rows.next()) result.add(new LegacyFile(rows.getLong(1), rows.getString(2),
                        rows.getString(3), rows.getString(4)));
                return result;
            }
        }
    }

    private static void upsertManifest(Connection connection, LegacyFile legacy, Path source, String objectKey,
            String sha256, long size, String contentType) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO sic_legacy.migration_file_manifest(material_version_id,source_file_url,
                  source_absolute_path,object_key,sha256,size_bytes,content_type)
                VALUES (?,?,?,?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE source_file_url=incoming.source_file_url,
                  source_absolute_path=incoming.source_absolute_path,object_key=incoming.object_key,
                  sha256=incoming.sha256,size_bytes=incoming.size_bytes,content_type=incoming.content_type,
                  migrated_at=CURRENT_TIMESTAMP(6)
                """)) {
            statement.setLong(1, legacy.id()); statement.setString(2, legacy.fileUrl());
            statement.setString(3, source.toString()); statement.setString(4, objectKey);
            statement.setString(5, sha256); statement.setLong(6, size); statement.setString(7, contentType);
            statement.executeUpdate();
        }
    }

    private static void assertManifestComplete(Connection connection) throws Exception {
        int source = scalar(connection, "SELECT COUNT(*) FROM sic_legacy.material_version");
        int manifest = scalar(connection, "SELECT COUNT(*) FROM sic_legacy.migration_file_manifest");
        if (source != manifest) throw new IllegalStateException(
                "Migration manifest is incomplete: source=" + source + ", manifest=" + manifest);
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getInt(1);
        }
    }

    private static String sha256(InputStream input) throws Exception {
        try (input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024]; int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private record LegacyFile(long id, String fileUrl, String expectedHash, String filename) {}

    private record Config(String jdbcUrl, String dbUser, String dbPassword, String minioEndpoint,
            String minioAccessKey, String minioSecretKey, String bucket, Path uploadsRoot) {
        static Config fromEnvironment() {
            return new Config(env("MIGRATION_JDBC_URL",
                            "jdbc:mysql://mysql:3306/sic_legacy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"),
                    env("MIGRATION_DB_USER", "root"), required("MYSQL_ROOT_PASSWORD"),
                    env("MINIO_ENDPOINT", "http://minio:9000"), required("MINIO_ACCESS_KEY"),
                    required("MINIO_SECRET_KEY"), env("MINIO_BUCKET", "sic-materials"),
                    Path.of(env("LEGACY_UPLOADS_ROOT", "/legacy-uploads")));
        }
        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank() || value.startsWith("CHANGE_ME"))
                throw new IllegalStateException("Missing secure environment value: " + name);
            return value;
        }
        private static String env(String name, String fallback) {
            String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value;
        }
    }
}
