package com.example.molgenie.service;

import com.example.molgenie.doc.MilvusDocumentStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentVectorService {

    private final MilvusDocumentStore store;

    public DocumentVectorService(MilvusDocumentStore store) {
        this.store = store;
    }

    public void index(String fileName, String fileType, String text) {
        try {
            store.addDocument(fileName, fileType, text);
        } catch (Exception e) {
            // 向量化失败不阻断主流程，后续可在日志中排查
        }
    }
}

