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
            String prompt =
                    "You are a computational chemist.\n" +
                    "Generate 3 candidate small-molecule SMILES strings based on the following request, one per line:\n\n" +
                    userQuery + "\n\n" +
                    "Output SMILES only. No numbering. No explanations. No blank lines.";

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