package com.anmonu.gcpobservability.alertbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.elastic")
public record ElasticProperties(String url, String username, String password, String indexName) {
}
