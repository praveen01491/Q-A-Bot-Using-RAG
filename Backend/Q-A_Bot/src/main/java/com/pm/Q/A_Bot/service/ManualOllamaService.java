package com.pm.Q.A_Bot.service;


import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class ManualOllamaService {

    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "qwen2.5:0.5b";

    public static String generateResponse(String prompt) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", MODEL);
            request.put("prompt", prompt);
            request.put("stream", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(OLLAMA_URL, request, Map.class);

            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            } else {
                return "No response received from model";
            }

        } catch (Exception e) {
            System.err.println("Manual Ollama call failed: " + e.getMessage());
            return "Error calling Ollama: " + e.getMessage();
        }
    }
}
