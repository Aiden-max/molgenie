package com.example.molgenie.agent;

import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.graph.DrugDiscoveryState;
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
            contextBuilder.append("候选分子: " + candidateMols +
                    "\n验证结果: " + validationRes);
        } else {
            List<MoleculeRecord> sdfMolecules = state.getSdfMolecules();
            int sdfCount = (sdfMolecules != null) ? sdfMolecules.size() : 0;

            contextBuilder.append("SDF包含 ").append(sdfCount).append(" 个分子。\n");

            contextBuilder.append("当前任务类型: ").append(state.getTaskType()).append("\n");

            if (sdfMolecules != null && !sdfMolecules.isEmpty()) {
                contextBuilder.append("分子列表 (展示部分关键属性):\n");
                // 为了简洁，这里只列出前 N 个分子 (例如前 5 个)
                int maxToShow = Math.min(5, sdfMolecules.size());
                for (int i = 0; i < maxToShow; i++) {
                    MoleculeRecord mol = sdfMolecules.get(i);
                    contextBuilder.append((i + 1)).append(". ");

                    // 添加 SMILES
                    if (mol.smiles() != null) {
                        // 限制 SMILES 长度以防过长
                        String smilesPreview = mol.smiles().length() > 100 ?
                                mol.smiles().substring(0, 97) + "..." :
                                mol.smiles();
                        contextBuilder.append("SMILES: ").append(smilesPreview).append("\n   ");
                    }

                    // 添加 Properties 中的一些关键信息
                    if (mol.properties() != null && !mol.properties().isEmpty()) {
                        contextBuilder.append("属性: ");
                        // 可以选择性地列出感兴趣的属性，而不是全部
                        // 例如，只显示 MW, LogP, HBA, HBD 等常见属性
                        String[] keyProperties = {"MW", "LogP", "HBA", "HBD"}; // 定义感兴趣的关键属性键
                        boolean hasPrintedProperty = false;
                        for (String key : keyProperties) {
                            String value = mol.properties().get(key);
                            if (value != null) {
                                if (hasPrintedProperty) {
                                    contextBuilder.append(", "); // 属性之间用逗号分隔
                                }
                                contextBuilder.append(key).append(": ").append(value);
                                hasPrintedProperty = true;
                            }
                        }
                        if (!hasPrintedProperty) {
                            // 打印前3个属性作为示例
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
                    contextBuilder.append("\n"); // 每个分子后换行
                }
                if (sdfMolecules.size() > maxToShow) {
                    contextBuilder.append("... (还有 ").append(sdfMolecules.size() - maxToShow).append(" 个分子)\n");
                }
            }
        }
        String fullPromptText = "基于以下信息，写一份中文药物研发建议：\n" + contextBuilder.toString();

        String summary = chatClient.prompt()
                .user(fullPromptText)
                .call()
                .content();

        state.setFinalResponse(summary);
    }
}