package com.amatrix.sic.workflow;

import com.amatrix.sic.workflow.tools.seed.WorkflowSeedExporter;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowReleaseSeedTest {
    @TempDir Path temporary;
    @Test void committedSqlMatchesTheRuntimeBpmnParser() throws Exception {
        Path fresh=temporary.resolve("fresh.sql"),adoption=temporary.resolve("adoption.sql");
        WorkflowSeedExporter.main(new String[]{fresh.toString()});
        WorkflowSeedExporter.main(new String[]{adoption.toString(),"--missing-only"});
        assertThat(Files.readString(fresh)).isEqualTo(Files.readString(Path.of("../../infra/mysql/seed/workflow-assets.sql")));
        assertThat(Files.readString(adoption)).isEqualTo(Files.readString(Path.of("../../infra/mysql/seed/workflow-missing-assets.sql")));
    }
}
