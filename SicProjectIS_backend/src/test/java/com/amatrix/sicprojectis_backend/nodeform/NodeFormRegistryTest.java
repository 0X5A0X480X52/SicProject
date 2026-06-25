package com.amatrix.sicprojectis_backend.nodeform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataKind;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRegistry;

class NodeFormRegistryTest {
    @Test
    void shouldRegisterAllV2NodeFormsWithUniqueCodes() {
        NodeFormRegistry registry = new NodeFormRegistry();

        assertThat(registry.definitions()).hasSize(43);
        assertThat(registry.definitions().stream().map(definition -> definition.formCode()).distinct())
                .hasSize(registry.definitions().size());
        assertThat(registry.require("PROJECT_APPLICATION_FORM").dataKind()).isEqualTo(NodeFormDataKind.APPLICATION_DRAFT);
        assertThat(registry.require("CONTRACT_ARCHIVE_FORM").dataKind()).isEqualTo(NodeFormDataKind.ARCHIVE);
        assertThat(registry.require("SURPLUS_FUNDS_RETURN_FORM").dataKind()).isEqualTo(NodeFormDataKind.SURPLUS_RETURN);
        assertThat(registry.require("DEPT_EXPERT_ASSIGN_FORM").dataKind()).isEqualTo(NodeFormDataKind.EXPERT_REVIEW);
        assertThat(registry.require("SCIENCE_EXPERT_SUMMARY_FORM").dataKind()).isEqualTo(NodeFormDataKind.EXPERT_REVIEW);
        assertThat(registry.require("EXPERT_ACCEPTANCE_REVIEW_SUMMARY_FORM").dataKind()).isEqualTo(NodeFormDataKind.EXPERT_REVIEW);
    }
}
