package com.anmonu.gcpobservability.alertbridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ElasticProperties.class)
public class AppConfig {
}
