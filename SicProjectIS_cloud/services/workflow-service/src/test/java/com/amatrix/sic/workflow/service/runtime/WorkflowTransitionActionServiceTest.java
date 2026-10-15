package com.amatrix.sic.workflow.service.runtime;

import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.policy.ModuleSuccessorPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.DocumentConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.TransitionConfig;

class WorkflowTransitionActionServiceTest {
    private final OutboxService outbox = mock(OutboxService.class);
    private final WorkflowTransitionActionService service = new WorkflowTransitionActionService(outbox);

    @Test
    void emitsNodeAndProcessDocumentsAndLifecycleEvent() {
        NodeConfig completed = node("ArchiveTask", "USER_TASK", "SCIENCE_ADMIN", "ON_NODE_COMPLETE",
                "CONTRACT_ARCHIVE_FORM", "CONTRACT_ARCHIVE_FORM_PDF");
        NodeConfig target = node("ApprovedEndEvent", "END_EVENT", null, "ON_PROCESS_END",
                "SIGNED_CONTRACT_DOCUMENT", "SIGNED_CONTRACT_DOCUMENT_PDF");
        TransitionConfig transition = new TransitionConfig("flow", "ArchiveTask", "ApprovedEndEvent",
                "CONTRACT_ARCHIVING", "CONTRACT_APPROVED", "CONTRACT_ARCHIVED", "PROCESS_COMPLETED", "NONE",
                null, null, null, null, List.of("GENERATE_CONTRACT_DOCUMENTS"));
        StateRecordContract record = new StateRecordContract(81L, 44L, 9, 1, "CONTRACT_ARCHIVED",
                "CONTRACT_ARCHIVING", "CONTRACT_APPROVED", "ArchiveTask", "ApprovedEndEvent",
                "PROCESS_COMPLETED", "done", LocalDateTime.now());
        ActorContext actor = new ActorContext(7L, "admin", 1L, "Science", List.of("SCIENCE_ADMIN"),
                List.of(), "token", 1);

        service.execute(12L, 44L, "CONTRACT", completed, target, record, List.of(transition), true, actor,
                new ModuleSuccessorPolicy.Completion("ACCEPTANCE", "ACCEPTANCE"), 45L);

        ArgumentCaptor<String> routes = ArgumentCaptor.forClass(String.class);
        verify(outbox, times(3)).enqueue(routes.capture(), any());
        assertThat(routes.getAllValues()).containsExactly("document.generation.requested",
                "document.generation.requested", "workflow.project-lifecycle-changed");
    }

    private static NodeConfig node(String id, String type, String role, String timing, String documentType,
            String outputType) {
        DocumentConfig document = new DocumentConfig(documentType, documentType, timing, "template", null, null,
                outputType, outputType, true, true);
        return new NodeConfig(id, id, type, type.equals("END_EVENT") ? "FINISHED" : "ACTIVE", null, null, null,
                role, null, null, null, List.of(), List.of(document));
    }
}
