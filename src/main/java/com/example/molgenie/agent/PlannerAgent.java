package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlannerAgent {

    private final ChatClient chatClient;

    public PlannerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    public void route(DrugDiscoveryState state) {
        String prompt =
                "User request: \"{query}\"\n" +
                "If the request is about designing/generating/creating new molecules, answer GENERATE.\n" +
                "If the request mentions SDF/upload/analyzing a batch, answer ANALYZE_SDF.\n" +
                "Output exactly one word.";

        String res = chatClient.prompt()
                .user(u -> u.text(prompt).params(Map.of("query", state.getUserQuery() == null ? "" : state.getUserQuery())))
                .call()
                .content()
                .trim();

        state.setTaskType(res.contains("GENERATE") ?
                DrugDiscoveryState.TaskType.GENERATE :
                DrugDiscoveryState.TaskType.ANALYZE_SDF);
    }
}