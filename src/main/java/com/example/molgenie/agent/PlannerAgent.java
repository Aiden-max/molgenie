package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlannerAgent {

    private final FunctionCallbackContext ctx;

    public PlannerAgent(FunctionCallbackContext ctx) {
        this.ctx = ctx;
    }

    public void route(DrugDiscoveryState state) {
        String prompt = """
            用户请求："{query}"
            如果涉及“设计”、“生成”、“创建新分子”，回答 GENERATE；
            如果提到“SDF”、“上传”、“分析这批”，回答 ANALYZE_SDF。
            只输出一个词。
            """;
        String res = ctx.getChatClient()
                .call(new PromptTemplate(prompt, Map.of("query", state.getUserQuery())).create())
                .getResult().getOutput().getContent().trim();
        state.setTaskType(res.contains("GENERATE") ?
                DrugDiscoveryState.TaskType.GENERATE : DrugDiscoveryState.TaskType.ANALYZE_SDF);
    }
}