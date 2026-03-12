package com.example.molgenie.kb;

import java.util.List;
import java.util.Map;

public interface KbService {
    String addMolecule(String smiles, Map<String, String> properties, String sourceFileName, String sourceType) throws Exception;

    /**
     * If q looks like SMILES, do vector search; otherwise implementation may fallback.
     */
    List<KbMolecule> search(String q, int limit) throws Exception;

    long size() throws Exception;
}

