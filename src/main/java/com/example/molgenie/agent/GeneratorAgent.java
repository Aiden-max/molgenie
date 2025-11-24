package com.example.molgenie.agent;

import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GeneratorAgent {

    private final FunctionCallbackContext ctx;

    public GeneratorAgent(FunctionCallbackContext ctx) {
        this.ctx = ctx;
    }

    public void generate(DrugDiscoveryState state) {
        String prompt = """
            用户需求："{query}"
            生成3个新颖的药物候选分子SMILES，每行一个，只输出SMILES。
            """;
        String res = ctx.getChatClient()
                .call(new PromptTemplate(prompt, Map.of("query", state.getUserQuery())).create())
                .getResult().getOutput().getContent();
        List<String> smiles = Arrays.stream(res.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.matches("[A-Za-z0-9@=\\(\\)\\[\\]]+"))
                .limit(3)
                .toList();
        state.setCandidateMolecules(smiles);
    }
}