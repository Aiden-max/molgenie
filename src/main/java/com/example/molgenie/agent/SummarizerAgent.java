package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SummarizerAgent {

    private final FunctionCallbackContext ctx;

    public SummarizerAgent(FunctionCallbackContext ctx) {
        this.ctx = ctx;
    }

    public void summarize(DrugDiscoveryState state) {
        String context;
        if (state.getTaskType() == DrugDiscoveryState.TaskType.GENERATE) {
            context = "候选分子: " + state.getCandidateMolecules() +
                    "\n验证结果: " + state.getValidationResults();
        } else {
            context = "SDF包含 " + state.getSdfMolecules().size() + " 个分子";
        }
        String prompt = "基于以下信息，写一份中文药物研发建议：\n{context}";
        state.setFinalResponse(
                ctx.getChatClient()
                        .call(new PromptTemplate(prompt, Map.of("context", context)).create())
                        .getResult().getOutput().getContent()
        );
    }
}