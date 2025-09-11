package com.pm.Q.A_Bot.Controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.document.Document;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    @Value("${llm.service.url:http://localhost:11434/api/generate}")
    private String llmServiceUrl;

    @Value("${llm.service.model:llama3.2:1b}")
    private String llmModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RestTemplate restTemplate;

    @CrossOrigin(origins ="*")
    @GetMapping("/ask")
    public ResponseEntity<String> askQuestion(@RequestParam String question) {
        try {
            System.out.println("=== DEBUG: Question received: " + question);

            // 1. Perform vector search WITHOUT similarity threshold first
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(question)
                    .topK(10)  // Get more results to debug
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            System.out.println("=== DEBUG: Found " + results.size() + " documents");

            // Debug: Print what was found
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String preview = doc.getText() == null ? "null" :
                        (doc.getText().length() > 100 ? doc.getText().substring(0, 100) + "..." : doc.getText());
                System.out.println("=== DEBUG: Doc " + i + " preview: " + preview);
            }

            // 2. If no documents found at all
            if (results.isEmpty()) {
                System.out.println("=== DEBUG: No documents found in vector store");
                return ResponseEntity.ok("No documents found in the database. Please upload documents first.");
            }

            // 3. Take top 5 for context
            List<Document> topDocs = results.stream().limit(5).collect(Collectors.toList());

            // 4. Combine retrieved context
            String context = topDocs.stream()
                    .map(doc -> {
                        String text = doc.getText() == null ? "" : doc.getText();
                        return text.length() > 1500 ? text.substring(0, 1500) + "..." : text;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            System.out.println("=== DEBUG: Context length: " + context.length());
            System.out.println("=== DEBUG: Context preview: " +
                    (context.length() > 200 ? context.substring(0, 200) + "..." : context));

            // 5. Prepare prompt
            String prompt =
                    "CONTEXT: " + context + "\n\n" +
                            "Based ONLY on the context above, answer: " + question + "\n\n" +
                            "If the answer is not in the context, respond exactly: 'I cannot answer this question based on the available documents.'\n\n" +
                            "Answer:";

            System.out.println("=== DEBUG: Sending to LLM service...");

            // 6. Call LLM service using RestTemplate
            String answer = callLLMWithRestTemplate(prompt);

            System.out.println("=== DEBUG: Got answer: " + answer);

            return ResponseEntity.ok(answer);

        } catch (Exception e) {
            System.err.println("=== ERROR: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body("Error processing request: " + e.getMessage());
        }
    }

    /**
     * Call LLM service using RestTemplate with robust error handling
     */
    private String callLLMWithRestTemplate(String prompt) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                System.out.println("=== Attempt " + (retryCount + 1) + " - Calling LLM service: " + llmServiceUrl + " ===");

                // Prepare request body for Ollama API
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", llmModel);
                requestBody.put("prompt", prompt);
                requestBody.put("stream", false);

                // Add options to control response
                Map<String, Object> options = new HashMap<>();
                options.put("temperature", 0.1);
                options.put("num_predict", 500);
                options.put("top_p", 0.9);
                requestBody.put("options", options);

                // Set headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                // Make the REST call
                ResponseEntity<Map> response = restTemplate.exchange(
                        llmServiceUrl,
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (response.getBody() != null && response.getBody().containsKey("response")) {
                    String result = (String) response.getBody().get("response");
                    System.out.println("=== LLM Response received successfully ===");
                    return result;
                } else {
                    System.err.println("Invalid response format from LLM service");
                    throw new RuntimeException("Invalid response format from LLM service");
                }

            } catch (ResourceAccessException e) {
                retryCount++;
                System.err.println("Connection error (attempt " + retryCount + "/" + maxRetries + "): " + e.getMessage());

                if (retryCount >= maxRetries) {
                    return "❌ LLM service is currently unavailable after " + maxRetries + " attempts. Please try again later.";
                }

                // Wait before retrying
                try {
                    Thread.sleep(1000 * retryCount); // Progressive backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

            } catch (Exception e) {
                retryCount++;
                System.err.println("Error calling LLM service (attempt " + retryCount + "/" + maxRetries + "): " + e.getMessage());

                if (retryCount >= maxRetries) {
                    e.printStackTrace();
                    return "❌ Error communicating with LLM service: " + e.getMessage();
                }

                // Wait before retrying
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return "❌ Maximum retry attempts exceeded";
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Test vector store
            SearchRequest testRequest = SearchRequest.builder()
                    .query("test")
                    .topK(1)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(testRequest);

            health.put("vectorStore", "✅ Connected");
            health.put("documentsCount", docs.size());

            // Test LLM service
            String testResponse = callLLMWithRestTemplate("Say 'Hello' only");
            if (!testResponse.startsWith("❌")) {
                health.put("llmService", "✅ Connected");
            } else {
                health.put("llmService", "❌ Not responding");
            }

            health.put("status", "healthy");

        } catch (Exception e) {
            health.put("vectorStore", "❌ Error: " + e.getMessage());
            health.put("status", "unhealthy");
        }

        return ResponseEntity.ok(health);
    }
}