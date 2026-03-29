package com.anmonu.gcpobservability.monitoringadmin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MonitoringProperties.class)
public class AppConfig {
}
