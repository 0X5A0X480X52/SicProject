package com.amatrix.sicprojectis_backend.runtime.statemachine;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.amatrix.sicprojectis_backend.expert.ExpertReviewBatchCompletedEvent;

@Component
public class ExpertReviewAutoTransitionListener {
    private final StateMachineRuntimeService stateMachineRuntimeService;

    public ExpertReviewAutoTransitionListener(StateMachineRuntimeService stateMachineRuntimeService) {
        this.stateMachineRuntimeService = stateMachineRuntimeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExpertReviewBatchCompleted(ExpertReviewBatchCompletedEvent event) {
        if (event == null || event.batchId() == null) {
            return;
        }
        stateMachineRuntimeService.autoTransitionAfterExpertReviewComplete(event.batchId());
    }
}