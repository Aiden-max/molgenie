package com.example.molgenie.controller;


import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.example.molgenie.chem.SdfParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/apiGraph")
public class DrugDiscoveryController {

    @Autowired
    @Qualifier("drugDiscoveryGraph")
    private StateGraph drugDiscoveryGraph;

    @Autowired
    @Qualifier("sdfParser")
    private SdfParser sdfParser;


    @PostMapping("/discover")
    public ResponseEntity<Object> discover(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MultipartFile file) {

        Map<String, Object> initialState = new HashMap<>();
        try {
            if (file != null) {
                List<com.example.molgenie.chem.MoleculeRecord> molecules = sdfParser.parseSdf(file.getInputStream());
                 initialState.put("sdf_molecules", molecules);
            }
            initialState.put("user_query", query);
            CompiledGraph compile = drugDiscoveryGraph.compile();
            Optional<OverAllState> call = compile.call(initialState);
            return ResponseEntity.ok(compile.call(initialState).get().data().get("final_response"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error during graph execution: " + e.getMessage());
        }
    }
}