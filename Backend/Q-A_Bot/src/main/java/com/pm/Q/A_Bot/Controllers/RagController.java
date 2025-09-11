package com.pm.Q.A_Bot.Controllers;

import jakarta.annotation.PostConstruct;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagController {

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    @Value("${llm.service.url:http://localhost:11434/api/generate}")
    private String llmServiceUrl;

    @Value("${llm.service.model:llama3.2:1b}")
    private String llmModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RestTemplate restTemplate;

    private final Tika tika = new Tika();

    @PostConstruct
    public void checkConfig() {
        System.out.println("=== RAG Controller Initialized ===");
        System.out.println("Pinecone Index: " + indexName);
        System.out.println("LLM Service URL: " + llmServiceUrl);
        System.out.println("LLM Model: " + llmModel);

        try {
            SearchRequest testRequest = SearchRequest.builder()
                    .query("test")
                    .topK(1)
                    .build();
            List<Document> existingDocs = vectorStore.similaritySearch(testRequest);
            System.out.println("Existing documents in vector store: " + existingDocs.size());
        } catch (Exception e) {
            System.out.println("Could not check existing documents: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("=== Starting file upload: " + file.getOriginalFilename() + " ===");

            String text = tika.parseToString(file.getInputStream());
            System.out.println("Extracted text length: " + text.length());

            if (text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("File appears empty or unreadable");
            }

            List<Document> documents = splitIntoChunks(text, file.getOriginalFilename());
            vectorStore.add(documents);

            return ResponseEntity.ok(String.format(
                    "✅ File '%s' uploaded successfully! Created %d document chunks.",
                    file.getOriginalFilename(), documents.size()));

        } catch (IOException | TikaException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }



    // this is for my testing purpose that llm is working or not
//    @GetMapping("/test-search")
//    public ResponseEntity<String> testSearch() {
//        try {
//            SearchRequest request = SearchRequest.builder()
//                    .query("dress code")
//                    .topK(2)
//                    .build();
//
//            List<Document> docs = vectorStore.similaritySearch(request);
//
//            if (docs.isEmpty()) return ResponseEntity.ok("❌ No documents found");
//
//            String resultText = docs.stream()
//                    .map(Document::getText)
//                    .collect(Collectors.joining("\n---\n"));
//
//            return ResponseEntity.ok("✅ Retrieved documents:\n" + resultText);
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Search error: " + e.getMessage());
//        }
//    }

    // Async question handler with RestTemplate
    @Async("taskExecutor")
    public CompletableFuture<String> askAsync(String question) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(10)
                    .similarityThreshold(0.1)
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);
            if (docs.isEmpty()) return CompletableFuture.completedFuture("❌ No relevant documents found");

            String context = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));
            if (context.length() > 2000) context = context.substring(0, 2000) + "...";

            String prompt = """
Answer strictly using the context below. If answer not found, say: "I don't know based on the document."
Context:
""" + context + "\n\nQuestion: " + question;

            CompletableFuture<String> responseFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return callLLMWithRestTemplate(prompt);
                } catch (Exception e) {
                    System.err.println("LLM call failed: " + e.getMessage());
                    return "❌ LLM call failed: " + e.getMessage();
                }
            });

            return responseFuture.completeOnTimeout("⚠️ LLM request timed out.", 30, TimeUnit.SECONDS);

        } catch (Exception e) {
            return CompletableFuture.completedFuture("❌ Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteDocument(@RequestParam("filename") String filename) {
        try {
            System.out.println("=== Attempting to delete documents for filename: " + filename + " ===");

            // Search using the filename itself as query to find related documents
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(filename)  // Use filename as query
                    .topK(100)        // Increase limit
                    .similarityThreshold(0.0)  // Get all results regardless of similarity
                    .build();

            List<Document> foundDocs = vectorStore.similaritySearch(searchRequest);
            System.out.println("Found " + foundDocs.size() + " documents from filename search");

            // Also try searching with common terms from the document
            SearchRequest broadSearch = SearchRequest.builder()
                    .query("policy leave work")  // Use common terms
                    .topK(100)
                    .similarityThreshold(0.0)
                    .build();

            List<Document> broadDocs = vectorStore.similaritySearch(broadSearch);
            System.out.println("Found " + broadDocs.size() + " documents from broad search");

            // Combine and deduplicate
            Set<Document> allDocs = new HashSet<>();
            allDocs.addAll(foundDocs);
            allDocs.addAll(broadDocs);

            // Filter documents by filename in metadata
            List<String> idsToDelete = allDocs.stream()
                    .filter(doc -> {
                        Object source = doc.getMetadata().get("source");
                        System.out.println("Checking document with source: " + source);
                        return filename.equals(source);
                    })
                    .map(Document::getId)
                    .collect(Collectors.toList());

            System.out.println("Found " + idsToDelete.size() + " documents to delete");

            if (idsToDelete.isEmpty()) {
                return ResponseEntity.ok("❌ No documents found for filename: " + filename);
            }

            // Delete by IDs
            vectorStore.delete(idsToDelete);

            System.out.println("Delete operation completed for " + idsToDelete.size() + " documents");

            return ResponseEntity.ok(String.format(
                    "✅ Successfully deleted %d chunks for file '%s'",
                    idsToDelete.size(), filename));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Delete failed: " + e.getMessage());
        }
    }

   // just other method for ask which is used  for trying the threshold values  and topk when that
//    @GetMapping("/ask")
//    public ResponseEntity<String> ask(@RequestParam("q") String question) {
//        try {
//            // Similarity search
//            SearchRequest request = SearchRequest.builder()
//                    .query(question)
//                    .topK(5)
//                    .similarityThreshold(0.1)
//                    .build();
//
//            List<Document> docs = vectorStore.similaritySearch(request);
//
//            if (docs.isEmpty()) {
//                return ResponseEntity.ok("❌ No relevant documents found.");
//            }
//
//            String context = docs.stream()
//                    .map(Document::getText)
//                    .collect(Collectors.joining("\n---\n"));
//
//            if (context.length() > 2000) context = context.substring(0, 2000) + "...";
//
//            String prompt = """
//                Answer the question strictly based on the context below.
//                If answer not in context, say "I don't know based on the document."
//
//                Context:
//                """ + context + "\n\nQuestion: " + question;
//
//            // Call LLM via RestTemplate with error handling
//            String answer = callLLMWithRestTemplate(prompt);
//            return ResponseEntity.ok(answer);
//
//        } catch (Exception e) {
//            System.err.println("Error in ask endpoint: " + e.getMessage());
//            return ResponseEntity.status(500).body("Error: " + e.getMessage());
//        }
//    }

    /**
     * Call LLM service using RestTemplate with proper error handling
     */
    private String callLLMWithRestTemplate(String prompt) {
        try {
            System.out.println("=== Calling LLM service: " + llmServiceUrl + " ===");

            // Prepare request body for Ollama API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", llmModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("options", Map.of(
                    "temperature", 0.1,
                    "num_predict", 500
            ));

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make the REST call with retry logic
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
                return "❌ Invalid response from LLM service";
            }

        } catch (ResourceAccessException e) {
            System.err.println("Connection timeout or network error: " + e.getMessage());
            return "❌ LLM service is currently unavailable. Please try again later.";
        } catch (Exception e) {
            System.err.println("Error calling LLM service: " + e.getMessage());
            e.printStackTrace();
            return "❌ Error communicating with LLM service: " + e.getMessage();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            SearchRequest testRequest = SearchRequest.builder()
                    .query("test")
                    .topK(5)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(testRequest);

            status.put("vectorStoreConnected", true);
            status.put("documentsCount", docs.size());
            status.put("indexName", indexName);
            status.put("llmServiceUrl", llmServiceUrl);
            status.put("status", "✅ System ready");

            // Test LLM connectivity
            try {
                String testResponse = callLLMWithRestTemplate("Test connection");
                status.put("llmServiceConnected", !testResponse.startsWith("❌"));
            } catch (Exception e) {
                status.put("llmServiceConnected", false);
                status.put("llmError", e.getMessage());
            }

        } catch (Exception e) {
            status.put("vectorStoreConnected", false);
            status.put("error", e.getMessage());
            status.put("status", "❌ System error");
        }
        return ResponseEntity.ok(status);
    }

    private List<Document> splitIntoChunks(String text, String filename) {
        List<Document> documents = new ArrayList<>();
        int chunkSize = 1000;
        int overlap = 200;

        for (int i = 0; i < text.length(); i += (chunkSize - overlap)) {
            int endIndex = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, endIndex);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filename);
            metadata.put("chunk_index", documents.size());
            metadata.put("total_length", text.length());

            Document doc = Document.builder()
                    .id(UUID.randomUUID().toString())
                    .text(chunk.trim())
                    .metadata(metadata)
                    .build();

            documents.add(doc);
            if (endIndex >= text.length()) break;
        }
        return documents;
    }
}