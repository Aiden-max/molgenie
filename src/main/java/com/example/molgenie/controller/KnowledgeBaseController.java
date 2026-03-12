package com.example.molgenie.controller;

import com.example.molgenie.kb.IngestResult;
import com.example.molgenie.kb.KbMolecule;
import com.example.molgenie.kb.KbService;
import com.example.molgenie.service.DocumentIngestService;
import com.example.molgenie.debug.DebugLogger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/apiGraph/kb")
public class KnowledgeBaseController {

    private final DocumentIngestService ingestService;
    private final KbService kb;

    public KnowledgeBaseController(DocumentIngestService ingestService, KbService kb) {
        this.ingestService = ingestService;
        this.kb = kb;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResult> ingest(@RequestParam("files") MultipartFile[] files) {
        List<MultipartFile> list = files == null ? List.of() : Arrays.asList(files);
        DebugLogger.log("KnowledgeBaseController#ingest", "kb ingest called", "KB1", "pre-run",
                Map.of("fileCount", list.size()));
        IngestResult res = ingestService.ingest(list);
        DebugLogger.log("KnowledgeBaseController#ingest", "kb ingest finished", "KB2", "pre-run",
                Map.of("moleculesAdded", res.moleculesAdded(), "docsProcessed", res.documentsProcessed()));
        return ResponseEntity.ok(res);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long count;
        try {
            count = kb.size();
        } catch (Exception e) {
            count = -1;
        }
        return ResponseEntity.ok(Map.of("molecules", count));
    }

    @GetMapping("/search")
    public ResponseEntity<List<KbMolecule>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int lim = Math.max(1, Math.min(200, limit));
        try {
            return ResponseEntity.ok(kb.search(q, lim));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of());
        }
    }
}

