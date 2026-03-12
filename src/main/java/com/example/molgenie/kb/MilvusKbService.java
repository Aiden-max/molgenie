package com.example.molgenie.kb;

import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MilvusKbService implements KbService {

    private final MilvusKnowledgeBase milvus;
    private final SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());

    public MilvusKbService(MilvusKnowledgeBase milvus) {
        this.milvus = milvus;
    }

    @Override
    public String addMolecule(String smiles, Map<String, String> properties, String sourceFileName, String sourceType) throws Exception {
        return milvus.addMolecule(smiles, properties, sourceFileName, sourceType);
    }

    @Override
    public List<KbMolecule> search(String q, int limit) throws Exception {
        String query = q == null ? "" : q.trim();
        int lim = Math.max(1, Math.min(200, limit));
        if (query.isEmpty()) {
            // Milvus doesn't guarantee ordering without additional design; return empty for now
            return List.of();
        }
        if (looksLikeSmiles(query)) {
            return milvus.vectorSearchBySmiles(query, lim);
        }
        return milvus.vectorSearchByText(query, lim);
    }

    @Override
    public long size() {
        try {
            return milvus.count(false);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean looksLikeSmiles(String s) {
        try {
            parser.parseSmiles(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}

