package com.example.molgenie.chem;

import java.util.Map;

public record MoleculeRecord(String smiles, Map<String, String> properties) {}