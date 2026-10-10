package com.amatrix.sic.material.service.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.amatrix.sic.contracts.DocumentGenerationCommand;

import tools.jackson.databind.ObjectMapper;

class GeneratedDocumentRendererTest {
    private final GeneratedDocumentRenderer renderer = new GeneratedDocumentRenderer(new ObjectMapper());

    @Test
    void rendersDeterministicPdfFromWorkflowSnapshot() {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", 12L);
        snapshot.put("state", "APPLICATION_APPROVED");
        DocumentGenerationCommand command = new DocumentGenerationCommand("42:8:APPLICATION_APPROVAL_FORM", 12L,
                42L, 88L, 8, "APPLICATION", "ApprovedEndEvent", "APPLICATION_APPROVED",
                "APPLICATION_APPROVAL_FORM", "Project application approval", "tpl_application_approval_form",
                null, null, "APPLICATION_APPROVAL_FORM_PDF", "Application approval PDF", true, 7L, snapshot);

        byte[] first = renderer.render(command);
        byte[] second = renderer.render(command);
        String pdf = new String(first, StandardCharsets.ISO_8859_1);

        assertThat(first).isEqualTo(second);
        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf).contains("Document type: APPLICATION_APPROVAL_FORM", "xref", "startxref");
        assertThat(pdf).endsWith("%%EOF\n");
    }
}
