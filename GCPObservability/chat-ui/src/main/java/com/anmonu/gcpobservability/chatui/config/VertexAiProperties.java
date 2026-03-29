package com.anmonu.gcpobservability.chatui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vertex-ai")
public record VertexAiProperties(String projectId, String location, String model) {
}
