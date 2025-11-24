package com.example.molgenie.graph;

import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.chem.MoleculeValidator;

import java.util.*;

public class DrugDiscoveryState {
    private String userQuery;
    private List<MoleculeRecord> sdfMolecules = new ArrayList<>();
    private TaskType taskType;
    private List<String> candidateMolecules = new ArrayList<>();
    private List<MoleculeValidator.ValidationResult> validationResults = new ArrayList<>();
    private String finalResponse;
    private String next;

    public enum TaskType { GENERATE, ANALYZE_SDF }

    // Getters & Setters
    public String getUserQuery() { return userQuery; }
    public void setUserQuery(String userQuery) { this.userQuery = userQuery; }
    public List<MoleculeRecord> getSdfMolecules() { return sdfMolecules; }
    public void setSdfMolecules(List<MoleculeRecord> sdfMolecules) { this.sdfMolecules = sdfMolecules; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }
    public List<String> getCandidateMolecules() { return candidateMolecules; }
    public void setCandidateMolecules(List<String> candidateMolecules) { this.candidateMolecules = candidateMolecules; }
    public List<MoleculeValidator.ValidationResult> getValidationResults() { return validationResults; }
    public void setValidationResults(List<MoleculeValidator.ValidationResult> validationResults) { this.validationResults = validationResults; }
    public String getFinalResponse() { return finalResponse; }
    public void setFinalResponse(String finalResponse) { this.finalResponse = finalResponse; }
    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }
}