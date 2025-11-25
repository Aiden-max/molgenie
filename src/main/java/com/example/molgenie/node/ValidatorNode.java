package com.example.molgenie.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.molgenie.chem.MoleculeValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ValidatorNode implements NodeAction {
        private final MoleculeValidator moleculeValidator;

        public ValidatorNode(MoleculeValidator moleculeValidator) {
            this.moleculeValidator = moleculeValidator;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) {
            @SuppressWarnings("unchecked")
            List<String> candidateSmilesList = (List<String>) state.value("candidate_molecules")
                    .orElse(Collections.emptyList());

            if (candidateSmilesList.isEmpty()) {
                return Collections.emptyMap();
            }

            List<MoleculeValidator.ValidationResult> validatedResults = candidateSmilesList.stream()
                    .map(moleculeValidator::validate)
                    .collect(Collectors.toList());
            Map<String, Object> result = new HashMap<>();
            result.put("validated_molecules", validatedResults);
            return result;
        }
    }