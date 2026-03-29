package com.anmonu.gcpobservability.alertbridge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PubsubPushRequest(PubsubMessage message, String subscription) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubsubMessage(String data, Map<String, String> attributes, String messageId, String publishTime) {
    }
}
