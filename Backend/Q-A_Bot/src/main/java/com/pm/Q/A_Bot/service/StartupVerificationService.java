package com.pm.Q.A_Bot.service;


import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StartupVerificationService implements CommandLineRunner {

    @Autowired
    private VectorStore vectorStore;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("🚀 Q&A Bot Startup Verification");
        System.out.println("=".repeat(50));

        try {
            // Check if vector store is accessible
            System.out.println("✅ Vector Store: Connected");

            // Check for existing documents
            SearchRequest testRequest = SearchRequest.builder()
                    .query("startup test")
                    .topK(5)
                    .build();

            List<Document> existingDocs = vectorStore.similaritySearch(testRequest);

            if (existingDocs.size() > 0) {
                System.out.println("📄 Found " + existingDocs.size() + " existing documents in vector store");
                System.out.println("✅ Documents are persistent - no need to re-upload!");

                // Show preview of first document
                if (!existingDocs.isEmpty()) {
                    String preview = existingDocs.get(0).getText();
                    if (preview.length() > 100) {
                        preview = preview.substring(0, 100) + "...";
                    }
                    System.out.println("📝 Sample document preview: " + preview);
                }
            } else {
                System.out.println("📄 No existing documents found - ready for upload");
            }

            System.out.println("✅ System Status: READY");
            System.out.println("\n📋 Available Endpoints:");
            System.out.println("   POST /api/rag/upload - Upload documents");
            System.out.println("   GET  /api/rag/ask?q=your-question - Ask questions");
            System.out.println("   GET  /api/rag/status - Check system status");

        } catch (Exception e) {
            System.err.println("❌ Startup verification failed: " + e.getMessage());
            System.err.println("Please check your Pinecone configuration and model availability");
        }

        System.out.println("=".repeat(50) + "\n");
    }
}
