package com.anmonu.gcpobservability.chatui.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VertexAiProperties.class)
public class AppConfig {
}
