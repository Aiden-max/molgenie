package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlannerAgent {

    private final ChatClient chatClient; // 直接持有 ChatClient

    // 通过构造函数注入 ChatClient
    // 注意：需要确保 Spring 上下文中存在一个 ChatClient bean，
    // 或者你可以在这里使用 ChatClient.builder(chatModel).build() 来创建一个。
    public PlannerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // 如果你不方便直接注入 ChatClient，也可以注入 ChatModel 并在此处构建
    /*
    private final ChatClient chatClient;
    public PlannerAgent(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build(); // 或加上 .defaultAdvisors(...)
    }
    */


    public void route(DrugDiscoveryState state) {
        String prompt = """
            用户请求："{query}"
            如果涉及“设计”、“生成”、“创建新分子”，回答 GENERATE；
            如果提到“SDF”、“上传”、“分析这批”，回答 ANALYZE_SDF。
            只输出一个词。
            """;

        // 使用注入的 chatClient
        String res = chatClient.prompt() // 使用 prompt() 流式 API
                .user(u -> u.text(prompt).params(Map.of("query", state.getUserQuery()))) // 填充模板
                // 或者 .user(new PromptTemplate(prompt, Map.of("query", state.getUserQuery())).create())
                .call()
                .content() // 获取响应文本内容
                .trim();

        // 根据响应设置任务类型
        state.setTaskType(res.contains("GENERATE") ?
                DrugDiscoveryState.TaskType.GENERATE :
                DrugDiscoveryState.TaskType.ANALYZE_SDF);
    }
}