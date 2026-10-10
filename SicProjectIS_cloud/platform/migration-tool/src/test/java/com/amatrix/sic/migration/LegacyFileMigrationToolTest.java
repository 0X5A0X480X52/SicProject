package com.amatrix.sic.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LegacyFileMigrationToolTest {
    private final Path root = Path.of("C:/migration/uploads").toAbsolutePath().normalize();

    @Test void resolvesLegacyApiUrlInsideUploadRoot() {
        assertThat(LegacyFileMigrationTool.resolveSource(root,
                "/api/material-versions/10/APPLICATION/a%20b.pdf"))
                .isEqualTo(root.resolve("10/APPLICATION/a b.pdf"));
    }

    @Test void rejectsPathTraversal() {
        assertThatThrownBy(() -> LegacyFileMigrationTool.resolveSource(root,
                "/api/material-versions/../../secret.txt"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("escapes uploads root");
    }
}
