package com.anmonu.gcpobservability.alertbridge.service;

import com.anmonu.gcpobservability.alertbridge.model.AlertEvent;
import com.anmonu.gcpobservability.alertbridge.model.PubsubPushRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AlertIngestionService.class);

    private final ElasticAlertIndexer elasticAlertIndexer;
    private final ObjectMapper objectMapper;
    private final Counter receivedCounter;

    public AlertIngestionService(ElasticAlertIndexer elasticAlertIndexer, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.elasticAlertIndexer = elasticAlertIndexer;
        this.objectMapper = objectMapper;
        this.receivedCounter = Counter.builder("alert_bridge_alerts_received_total").register(meterRegistry);
    }

    public AlertEvent process(PubsubPushRequest request) {
        receivedCounter.increment();
        if (request == null || request.message() == null) {
            throw new IllegalArgumentException("Pub/Sub message body is missing");
        }

        String payload = decode(request.message().data());
        AlertEvent event = new AlertEvent(
                request.message().messageId(),
                parseInstant(request.message().publishTime()),
                request.message().attributes(),
                payload,
                Instant.now()
        );

        logStructured(event);
        elasticAlertIndexer.index(event);
        return event;
    }

    private String decode(String base64Data) {
        try {
            return new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to decode Pub/Sub data", ex);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private void logStructured(AlertEvent event) {
        try {
            log.info("alert_event={}", objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.info("alert_event messageId={} payload={}", event.messageId(), event.payload());
        }
    }
}
