package com.amatrix.sicprojectis_backend.runtime.statemachine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SseNotificationController {
    private final SseNotificationService service;

    public SseNotificationController(SseNotificationService service) {
        this.service = service;
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe() {
        return service.subscribe();
    }
}
