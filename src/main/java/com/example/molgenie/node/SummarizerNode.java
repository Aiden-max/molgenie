package com.example.molgenie.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.molgenie.agent.SummarizerAgent;
import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SummarizerNode implements NodeAction {
    private final SummarizerAgent summarizerAgent;

    public SummarizerNode(SummarizerAgent summarizerAgent) {
        this.summarizerAgent = summarizerAgent;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        DrugDiscoveryState agentState = new DrugDiscoveryState();
        agentState.setSdfMolecules((List<MoleculeRecord>)state.value("sdf_molecules").orElse(null));
        summarizerAgent.summarize(agentState);
        Map<String, Object> result = new HashMap<>();
        if (agentState.getFinalResponse() != null) {
            result.put("final_response", agentState.getFinalResponse());
            state.input(result);
        }

        return result;
    }
}