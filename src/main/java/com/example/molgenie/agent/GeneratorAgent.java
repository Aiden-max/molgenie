package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.client.ChatClient; // 导入 ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate; // 确保导入正确
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GeneratorAgent {

    private final ChatClient chatClient; // 直接持有 ChatClient

    // 通过构造函数注入 ChatClient
    // 确保 Spring 上下文中已配置好 ChatClient Bean
    public GeneratorAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // --- 如果选择注入 ChatModel 并在此构建 ChatClient ---
    /*
    import org.springframework.ai.chat.model.ChatModel;
    private final ChatClient chatClient;
    public GeneratorAgent(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build(); // 可添加 .defaultAdvisors(...)
    }
    */
    // --- 构建方式结束 ---


    public void generate(DrugDiscoveryState state) {
        String prompt = """
            用户需求："{query}"
            生成3个新颖的药物候选分子SMILES，每行一个，只输出SMILES。
            """;

        // 使用注入的 chatClient
        String res = chatClient.prompt() // 使用流式 API
                .user(u -> u.text(prompt).params(Map.of("query", state.getUserQuery()))) // 填充模板
                // 或者 .user(new PromptTemplate(prompt, Map.of("query", state.getUserQuery())).create())
                .call()
                .content(); // 获取生成的文本内容

        // 处理响应，提取 SMILES
        List<String> smiles = Arrays.stream(res.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.matches("[A-Za-z0-9@=\\(\\)\\[\\]#+\\-\\\\/:]+")) // 放宽一点正则，包含更多合法SMILES字符
                .limit(3)
                .collect(Collectors.toList()); // 使用 collect(Collectors.toList()) 保证兼容性，虽然 .toList() 在 Java 16+ 也可用

        state.setCandidateMolecules(smiles); // 设置候选分子列表
    }
}