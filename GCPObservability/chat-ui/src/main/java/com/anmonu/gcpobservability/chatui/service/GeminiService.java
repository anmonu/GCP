package com.anmonu.gcpobservability.chatui.service;

import com.anmonu.gcpobservability.chatui.config.VertexAiProperties;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {

    private final VertexAiProperties properties;
    private final Client client;

    public GeminiService(VertexAiProperties properties) {
        this.properties = properties;
        this.client = Client.builder()
                .project(properties.projectId())
                .location(properties.location())
                .vertexAI(true)
                .build();
    }

    public String generateText(String prompt) {
        try {
            GenerateContentResponse response = client.models.generateContent(properties.model(), prompt, null);
            String text = response.text();
            return (text == null || text.isBlank()) ? "(empty response)" : text;
        } catch (Exception ex) {
            throw new IllegalStateException("Gemini request failed", ex);
        }
    }
}
