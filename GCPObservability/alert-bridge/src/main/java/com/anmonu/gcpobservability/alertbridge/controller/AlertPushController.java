package com.anmonu.gcpobservability.alertbridge.controller;

import com.anmonu.gcpobservability.alertbridge.model.AlertEvent;
import com.anmonu.gcpobservability.alertbridge.model.PubsubPushRequest;
import com.anmonu.gcpobservability.alertbridge.service.AlertIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pubsub")
public class AlertPushController {

    private final AlertIngestionService alertIngestionService;

    public AlertPushController(AlertIngestionService alertIngestionService) {
        this.alertIngestionService = alertIngestionService;
    }

    @PostMapping("/alerts")
    public ResponseEntity<AlertEvent> receiveAlert(@Valid @RequestBody PubsubPushRequest request) {
        return ResponseEntity.ok(alertIngestionService.process(request));
    }
}
