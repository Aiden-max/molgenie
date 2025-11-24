package com.example.molgenie.agent;

import com.example.molgenie.chem.MoleculeValidator;
import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ValidatorAgent {

    private final MoleculeValidator validator;

    public ValidatorAgent(MoleculeValidator validator) {
        this.validator = validator;
    }

    public void validate(DrugDiscoveryState state) {
        var results = state.getCandidateMolecules().stream()
                .map(validator::validate)
                .collect(Collectors.toList());
        state.setValidationResults(results);
    }
}