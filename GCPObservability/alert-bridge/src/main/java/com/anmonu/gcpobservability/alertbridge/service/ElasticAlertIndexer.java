package com.anmonu.gcpobservability.alertbridge.service;

import com.anmonu.gcpobservability.alertbridge.config.ElasticProperties;
import com.anmonu.gcpobservability.alertbridge.model.AlertEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ElasticAlertIndexer {

    private static final Logger log = LoggerFactory.getLogger(ElasticAlertIndexer.class);

    private final ElasticProperties properties;
    private final WebClient webClient;
    private final Counter successCounter;
    private final Counter failureCounter;

    public ElasticAlertIndexer(ElasticProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.webClient = WebClient.builder().baseUrl(properties.url()).build();
        this.successCounter = Counter.builder("alert_bridge_elastic_index_success_total").register(meterRegistry);
        this.failureCounter = Counter.builder("alert_bridge_elastic_index_failure_total").register(meterRegistry);
    }

    public void index(AlertEvent event) {
        if (properties.url() == null || properties.url().isBlank()) {
            log.warn("ELASTIC_URL is not configured. Skipping index for messageId={}", event.messageId());
            failureCounter.increment();
            return;
        }

        try {
            String indexPath = "/" + properties.indexName() + "/_doc";
            WebClient.RequestBodySpec request = webClient.post()
                    .uri(indexPath)
                    .contentType(MediaType.APPLICATION_JSON);

            if (properties.username() != null && !properties.username().isBlank()) {
                request.headers(headers -> headers.setBasicAuth(properties.username(), properties.password() == null ? "" : properties.password()));
            }

            HttpStatusCode code = request
                    .bodyValue(event)
                    .retrieve()
                    .toBodilessEntity()
                    .block()
                    .getStatusCode();

            if (code.is2xxSuccessful()) {
                successCounter.increment();
                return;
            }

            failureCounter.increment();
            log.warn("Elasticsearch indexing returned non-2xx status={} for messageId={}", code, event.messageId());
        } catch (Exception ex) {
            failureCounter.increment();
            log.warn("Failed to index alert event messageId={} into Elasticsearch: {}", event.messageId(), ex.getMessage());
        }
    }
}
