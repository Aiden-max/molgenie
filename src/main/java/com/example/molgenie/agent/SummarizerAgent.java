package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.client.ChatClient; // 导入 ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate; // 确保导入正确
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SummarizerAgent {

   @Autowired
   ChatClient chatClient;

    // 通过构造函数注入 ChatClient
    // 确保 Spring 上下文中已配置好 ChatClient Bean
    public SummarizerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // --- 如果选择注入 ChatModel 并在此构建 ChatClient ---
    /*
    import org.springframework.ai.chat.model.ChatModel;
    private final ChatClient chatClient;
    public SummarizerAgent(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build(); // 可添加 .defaultAdvisors(...)
    }
    */
    // --- 构建方式结束 ---


    public void summarize(DrugDiscoveryState state) {
        String context;
        if (state.getTaskType() == DrugDiscoveryState.TaskType.GENERATE) {
            // 为了安全起见，最好对集合进行非空判断
            String candidateMols = (state.getCandidateMolecules() != null) ? state.getCandidateMolecules().toString() : "[]";
            String validationRes = (state.getValidationResults() != null) ? state.getValidationResults().toString() : "[]";
            context = "候选分子: " + candidateMols +
                    "\n验证结果: " + validationRes;
        } else {
            // 同样，对 sdfMolecules 进行非空判断
            int sdfCount = (state.getSdfMolecules() != null) ? state.getSdfMolecules().size() : 0;
            context = "SDF包含 " + sdfCount + " 个分子";
        }

        String prompt = "基于以下信息，写一份中文药物研发建议：\n{"+context+"}";

        // 使用注入的 chatClient
        String summary = chatClient.prompt() // 使用流式 API
                .user(u -> u.text(prompt).params(Map.of("context", context))) // 填充模板
                // 或者 .user(new PromptTemplate(prompt, Map.of("context", context)).create())
                .call()
                .content(); // 获取生成的文本内容

        state.setFinalResponse(summary); // 设置最终响应
    }
}