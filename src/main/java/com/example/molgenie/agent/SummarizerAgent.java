package com.example.molgenie.agent;

import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.graph.DrugDiscoveryState;
import com.example.molgenie.kb.KbMolecule;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SummarizerAgent {

    @Autowired
    ChatClient chatClient;

    public SummarizerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void summarize(DrugDiscoveryState state) {
        StringBuilder contextBuilder = new StringBuilder();

        if (state.getTaskType() == DrugDiscoveryState.TaskType.GENERATE) {
            String candidateMols = (state.getCandidateMolecules() != null) ? state.getCandidateMolecules().toString() : "[]";
            String validationRes = (state.getValidationResults() != null) ? state.getValidationResults().toString() : "[]";
            contextBuilder.append("候选分子: ").append(candidateMols)
                    .append("\n验证结果: ").append(validationRes);
        } else {
            List<MoleculeRecord> sdfMolecules = state.getSdfMolecules();
            int sdfCount = (sdfMolecules != null) ? sdfMolecules.size() : 0;

            contextBuilder.append("SDF包含 ").append(sdfCount).append(" 个分子。\n");
            contextBuilder.append("当前任务类型: ").append(state.getTaskType()).append("\n");

            if (sdfMolecules != null && !sdfMolecules.isEmpty()) {
                contextBuilder.append("分子列表 (展示部分关键属性):\n");
                int maxToShow = Math.min(5, sdfMolecules.size());
                for (int i = 0; i < maxToShow; i++) {
                    MoleculeRecord mol = sdfMolecules.get(i);
                    contextBuilder.append((i + 1)).append(". ");

                    if (mol.smiles() != null) {
                        String smilesPreview = mol.smiles().length() > 100
                                ? mol.smiles().substring(0, 97) + "..."
                                : mol.smiles();
                        contextBuilder.append("SMILES: ").append(smilesPreview).append("\n   ");
                    }

                    if (mol.properties() != null && !mol.properties().isEmpty()) {
                        contextBuilder.append("属性: ");
                        String[] keyProperties = {"MW", "LogP", "HBA", "HBD"};
                        boolean hasPrintedProperty = false;
                        for (String key : keyProperties) {
                            String value = mol.properties().get(key);
                            if (value != null) {
                                if (hasPrintedProperty) {
                                    contextBuilder.append(", ");
                                }
                                contextBuilder.append(key).append(": ").append(value);
                                hasPrintedProperty = true;
                            }
                        }
                        if (!hasPrintedProperty) {
                            int propCount = 0;
                            for (Map.Entry<String, String> entry : mol.properties().entrySet()) {
                                if (propCount > 0) contextBuilder.append(", ");
                                contextBuilder.append(entry.getKey()).append(": ").append(entry.getValue());
                                propCount++;
                                if (propCount >= 3) break;
                            }
                        }
                    } else {
                        contextBuilder.append("无属性信息");
                    }
                    contextBuilder.append("\n");
                }
                if (sdfMolecules.size() > maxToShow) {
                    contextBuilder.append("... (还有 ").append(sdfMolecules.size() - maxToShow).append(" 个分子)\n");
                }
            }
        }

        // 附加知识库召回的上下文
        List<KbMolecule> kbMatches = state.getKbMatches();
        if (kbMatches != null && !kbMatches.isEmpty()) {
            contextBuilder.append("\n=== 知识库召回的相关分子（前 5 条） ===\n");
            int max = Math.min(5, kbMatches.size());
            for (int i = 0; i < max; i++) {
                KbMolecule m = kbMatches.get(i);
                contextBuilder.append(i + 1).append(". SMILES: ")
                        .append(m.smiles() != null ? m.smiles() : "")
                        .append(" | 来源: ")
                        .append(m.sourceType() != null ? m.sourceType() : "")
                        .append(" @ ")
                        .append(m.sourceFileName() != null ? m.sourceFileName() : "")
                        .append("\n");
            }
        }

        String fullPromptText = "你是一名药物化学家，基于以下信息，给出一份结构化的中文药物研发建议：\n" + contextBuilder;

        String summary = chatClient.prompt()
                .user(fullPromptText)
                .call()
                .content();

        state.setFinalResponse(summary);
    }
}