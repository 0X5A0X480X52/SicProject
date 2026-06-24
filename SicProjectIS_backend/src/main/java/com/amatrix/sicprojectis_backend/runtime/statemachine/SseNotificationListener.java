package com.amatrix.sicprojectis_backend.runtime.statemachine;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SseNotificationListener {
    private final SseNotificationService service;

    public SseNotificationListener(SseNotificationService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onModuleStateChanged(ModuleStateChangedEvent event) {
        service.publish(event);
    }
}
