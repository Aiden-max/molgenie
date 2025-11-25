package com.example.molgenie.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GeneratorNode implements NodeAction {
        private final ChatClient chatClient;

        public GeneratorNode(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) {
            String taskType = (String) state.value("task_type").orElse("");
            if (!"GENERATE".equals(taskType)) {
                return Collections.emptyMap(); // 跳过
            }

            String userQuery = (String) state.value("user_query").orElse("");
            String prompt = """
                    你是一个计算化学家。请根据以下需求生成 3 个候选小分子的 SMILES 字符串，每行一个：
                    
                    %s
                    
                    只输出 SMILES，不要编号、不要解释、不要空行。
                    """.formatted(userQuery);

            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String output = response.getResult().getOutput().getText();
            List<String> smilesList = Arrays.stream(output.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && s.length() > 5)
                    .limit(3)
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("candidate_molecules", smilesList);
            return result;
        }
    }