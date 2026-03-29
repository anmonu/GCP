package com.anmonu.gcpobservability.monitoringadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.monitoring")
public record MonitoringProperties(String projectId) {
}
