package com.example.molgenie.controller;

import com.example.molgenie.agent.*;
import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.chem.SdfParser;
import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DrugDiscoveryController {

    @Autowired
    private PlannerAgent planner;
    @Autowired
    private GeneratorAgent generator;
    @Autowired
    private ValidatorAgent validator;
    @Autowired
    private SummarizerAgent summarizer;
    @Autowired
    private SdfParser sdfParser;

    @PostMapping("/discover")
    public ResponseEntity<String> discover(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MultipartFile file) {

        DrugDiscoveryState state = new DrugDiscoveryState();

        try {
            if (file != null) {
                state.setSdfMolecules(sdfParser.parseSdf(file.getInputStream()));
                state.setTaskType(DrugDiscoveryState.TaskType.ANALYZE_SDF);
            } else {
                state.setUserQuery(query);
                planner.route(state);
                if (state.getTaskType() == DrugDiscoveryState.TaskType.GENERATE) {
                    generator.generate(state);
                    validator.validate(state);
                }
            }
            summarizer.summarize(state);
            return ResponseEntity.ok(state.getFinalResponse());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}