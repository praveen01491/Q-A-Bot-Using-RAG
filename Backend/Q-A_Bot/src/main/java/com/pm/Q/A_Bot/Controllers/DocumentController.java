package com.pm.Q.A_Bot.Controllers;

import com.pm.Q.A_Bot.Entity.DocumentEntity;
import com.pm.Q.A_Bot.Repository.DocumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/docs")
@CrossOrigin("*")
public class DocumentController {

    private final DocumentRepository documentRepository;

    // ✅ Constructor Injection (clean way)
    public DocumentController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            DocumentEntity doc = new DocumentEntity();
            doc.setName(file.getOriginalFilename());
            doc.setContent(new String(file.getBytes())); // store as text
            doc.setUploadedAt(LocalDateTime.now());
            documentRepository.save(doc);

            return ResponseEntity.ok("✅ File stored in MySQL successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public List<DocumentEntity> getHistory() {
        return documentRepository.findAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDoc(@PathVariable Long id) {
        if (documentRepository.existsById(id)) {
            documentRepository.deleteById(id);
            return ResponseEntity.ok("✅ Document deleted from MySQL");
        }

        return ResponseEntity.status(404).body("❌ Document not found");
    }
}
