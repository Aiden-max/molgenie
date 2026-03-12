package com.example.molgenie.kb;

import java.time.Instant;
import java.util.Map;

public record KbMolecule(
        String id,
        String smiles,
        Map<String, String> properties,
        String sourceFileName,
        String sourceType,
        Instant ingestedAt
) {}

