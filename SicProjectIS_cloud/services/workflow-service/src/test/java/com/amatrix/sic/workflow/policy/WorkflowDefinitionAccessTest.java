package com.amatrix.sic.workflow.policy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.stream.LongStream;
import com.amatrix.sic.contracts.*;
import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;
import com.amatrix.sic.workflow.dao.WorkflowScopeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionAccessTest {
    private WorkflowScopeMapper mapper;
    private ProjectAccessClient projects;
    private WorkflowProjectAccessPolicy policy;
    private final ActorContext leader=new ActorContext(7L,"leader",null,null,List.of("PROJECT_LEADER"),List.of(),"test",1);
    @BeforeEach void setup() {
        mapper=mock(WorkflowScopeMapper.class);projects=mock(ProjectAccessClient.class);
        var tokens=mock(WorkflowInternalTokenProvider.class);
        when(tokens.authorization()).thenReturn("Bearer internal");
        policy=new WorkflowProjectAccessPolicy(projects,tokens,mapper);
    }
    @Test void departmentlessOwnerReadsOnlyDefinitionUsedByAccessibleProject() {
        when(mapper.definitionProjects(1L,0)).thenReturn(List.of(12L));
        when(projects.decideBatch(anyString(),any())).thenAnswer(i -> {
            ProjectAccessBatchRequest request=i.getArgument(1);
            assertThat(request.actor()).isEqualTo(leader);
            assertThat(request.targets()).containsExactly(new ProjectOperationTarget("definition-12",12L,"VIEW_WORKFLOW",null,null,null));
            return ApiResponse.ok(List.of(new ProjectAccessResult("definition-12",12L,true,1,null,7L,"leader",null)));
        });
        assertThat(policy.canReadDefinition(leader,1L)).isTrue();
        assertThat(policy.canReadDefinition(leader,2L)).isFalse();
    }
    @Test void inaccessibleProjectAndForeignDecisionDoNotGrantRead() {
        when(mapper.definitionProjects(1L,0)).thenReturn(List.of(12L));
        when(projects.decideBatch(anyString(),any())).thenReturn(ApiResponse.ok(List.of(
                new ProjectAccessResult("definition-12",12L,false,1,"DENIED",null,null,null),
                new ProjectAccessResult("definition-13",13L,true,1,null,null,null,null),
                new ProjectAccessResult("wrong-id",12L,true,1,null,null,null,null))));
        assertThat(policy.canReadDefinition(leader,1L)).isFalse();
    }
    @Test void projectsAreCheckedInBoundedPagesIncludingLaterAccessibleProject() {
        when(mapper.definitionProjects(1L,0)).thenReturn(LongStream.rangeClosed(1,100).boxed().toList());
        when(mapper.definitionProjects(1L,100)).thenReturn(List.of(101L));
        when(projects.decideBatch(anyString(),any())).thenAnswer(i -> {
            ProjectAccessBatchRequest request=i.getArgument(1);
            assertThat(request.targets()).hasSizeLessThanOrEqualTo(100);
            return ApiResponse.ok(request.targets().stream().map(t -> new ProjectAccessResult(t.decisionId(),t.projectId(),
                    t.projectId()==101L,1,null,null,null,null)).toList());
        });
        assertThat(policy.canReadDefinition(leader,1L)).isTrue();
        verify(projects,times(2)).decideBatch(anyString(),any());
    }
}
