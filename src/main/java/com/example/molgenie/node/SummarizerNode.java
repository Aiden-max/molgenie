package com.example.molgenie.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.molgenie.agent.SummarizerAgent;
import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.graph.DrugDiscoveryState;
import com.example.molgenie.kb.KbMolecule;
import com.example.molgenie.kb.KbService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SummarizerNode implements NodeAction {
    private final SummarizerAgent summarizerAgent;
    private final KbService kbService;

    public SummarizerNode(SummarizerAgent summarizerAgent, KbService kbService) {
        this.summarizerAgent = summarizerAgent;
        this.kbService = kbService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        DrugDiscoveryState agentState = new DrugDiscoveryState();
        agentState.setSdfMolecules((List<MoleculeRecord>) state.value("sdf_molecules").orElse(null));
        agentState.setUserQuery((String) state.value("user_query").orElse(null));

        // 构造知识库查询：优先使用“需求描述 + 上传的分子 SMILES”
        List<KbMolecule> kbMatches = new ArrayList<>();
        try {
            String query = agentState.getUserQuery();
            if (query != null && !query.trim().isEmpty()) {
                // 文本语义检索
                kbMatches.addAll(kbService.search(query, 5));
            }

            // 根据上传的 SDF 分子 SMILES 做相似检索
            List<MoleculeRecord> sdfMolecules = agentState.getSdfMolecules();
            if (sdfMolecules != null && !sdfMolecules.isEmpty()) {
                int max = Math.min(3, sdfMolecules.size());
                for (int i = 0; i < max; i++) {
                    MoleculeRecord m = sdfMolecules.get(i);
                    if (m.smiles() == null || m.smiles().isBlank()) {
                        continue;
                    }
                    kbMatches.addAll(kbService.search(m.smiles(), 5));
                }
            }
        } catch (Exception ignored) {
            // KB 查询失败不影响主流程
        }

        if (!kbMatches.isEmpty()) {
            agentState.setKbMatches(kbMatches);
        }

        summarizerAgent.summarize(agentState);
        Map<String, Object> result = new HashMap<>();
        if (agentState.getFinalResponse() != null) {
            result.put("final_response", agentState.getFinalResponse());
            state.input(result);
        }

        return result;
    }
}