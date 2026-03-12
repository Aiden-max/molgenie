package com.example.molgenie.kb;

import java.util.List;

public record IngestResult(
        int moleculesAdded,
        int documentsProcessed,
        List<String> warnings
) {}

