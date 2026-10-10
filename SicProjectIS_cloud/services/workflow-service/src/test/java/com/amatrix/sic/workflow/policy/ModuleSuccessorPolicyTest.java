package com.amatrix.sic.workflow.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModuleSuccessorPolicyTest {
    private final ModuleSuccessorPolicy policy = new ModuleSuccessorPolicy();

    @Test
    void mapsOnlySuccessfulTerminalStatesToTheNextModule() {
        assertThat(policy.completion("APPLICATION", "APPLICATION_APPROVED"))
                .isEqualTo(new ModuleSuccessorPolicy.Completion("CONTRACT", "CONTRACT"));
        assertThat(policy.completion("CONTRACT", "CONTRACT_APPROVED"))
                .isEqualTo(new ModuleSuccessorPolicy.Completion("ACCEPTANCE", "ACCEPTANCE"));
        assertThat(policy.completion("ACCEPTANCE", "ACCEPTANCE_ACCEPTED"))
                .isEqualTo(new ModuleSuccessorPolicy.Completion(null, "PROJECT_FINISHED"));
        assertThat(policy.completion("ACCEPTANCE", "ACCEPTANCE_REJECTED").hasSuccessor()).isFalse();
        assertThat(policy.completion("APPLICATION", "APPLICATION_REJECTED").hasSuccessor()).isFalse();
    }
}
