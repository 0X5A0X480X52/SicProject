package com.amatrix.sic.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class WorkflowPermissionMetaAnnotationTest {

    @Test
    void workflowReadAnnotationsUseTheSharedProjectAccessPolicyBean() {
        assertPolicyBean(CanAccessModule.class);
        assertPolicyBean(CanReadNodeForm.class);
    }

    private static void assertPolicyBean(Class<?> annotationType) {
        PreAuthorize preAuthorize = annotationType.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("@projectAccessPolicy.")
                .doesNotContain("@workflowProjectAccessPolicy.");
    }
}
