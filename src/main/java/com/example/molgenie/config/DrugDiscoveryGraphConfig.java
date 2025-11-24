/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.molgenie.config;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.molgenie.chem.MoleculeValidator; // 导入修正后的 Validator
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class DrugDiscoveryGraphConfig {

    @Bean
    public StateGraph drugDiscoveryGraph(ChatModel chatModel, MoleculeValidator moleculeValidator)
            throws GraphStateException {

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        // 定义状态字段更新策略（全部覆盖）
        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                .addPatternStrategy("user_query", new ReplaceStrategy())
                .addPatternStrategy("task_type", new ReplaceStrategy())
                .addPatternStrategy("candidate_molecules", new ReplaceStrategy())
                .addPatternStrategy("validated_molecules", new ReplaceStrategy())
                .addPatternStrategy("final_response", new ReplaceStrategy())
                .build();

        // --- 使用 node_async 包装 NodeAction ---
        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode("planner", node_async(new PlannerNode(chatClient))) // <--- 修改 here
                .addNode("generator", node_async(new GeneratorNode(chatClient))) // <--- 修改 here
                .addNode("validator", node_async(new ValidatorNode(moleculeValidator))) // <--- 修改 here
                .addNode("summarizer", node_async(new SummarizerNode(chatClient))) // <--- 修改 here
                // --- 包装结束 ---

                .addEdge(START, "planner")
                .addEdge("planner", "generator")
                .addEdge("generator", "validator")
                .addEdge("validator", "summarizer")
                .addEdge("summarizer", END);

        // 打印流程图（面试演示利器）
        GraphRepresentation representation = graph.getGraph(
                GraphRepresentation.Type.PLANTUML,
                "Drug Discovery Multi-Agent Workflow"
        );
        System.out.println("\n=== Drug Discovery UML Flow ===");
        System.out.println(representation.content());
        System.out.println("================================\n");

        return graph;
    }

    // ====== Node Implementations (PlannerNode & GeneratorNode 保持不变) ======

    static class PlannerNode implements NodeAction {
        private final ChatClient chatClient;

        public PlannerNode(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) {
            String userQuery = (String) state.value("user_query").orElse("");
            String prompt = """
                    你是一个药物发现专家。请根据用户需求判断任务类型：
                    - 如果涉及“设计”、“生成”、“创建”新分子，请返回 "GENERATE"
                    - 如果涉及“分析”、“评估”、“检查”已有分子，请返回 "ANALYZE_SDF"
                    
                    用户输入：%s
                    
                    仅返回任务类型关键词，不要解释。
                    """.formatted(userQuery);

            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String taskType = response.getResult().getOutput().getText().trim().toUpperCase();

            if (!Arrays.asList("GENERATE", "ANALYZE_SDF").contains(taskType)) {
                taskType = "GENERATE"; // 默认
            }

            Map<String, Object> result = new HashMap<>();
            result.put("task_type", taskType);
            return result;
        }
    }

    static class GeneratorNode implements NodeAction {
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

    // ====== 修正后的 ValidatorNode 和 SummarizerNode ======

    /**
     * 修正后的 ValidatorNode，使用 MoleculeValidator 的 validate 方法。
     */
    static class ValidatorNode implements NodeAction {
        private final MoleculeValidator moleculeValidator;

        public ValidatorNode(MoleculeValidator moleculeValidator) {
            this.moleculeValidator = moleculeValidator;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) {
            // 1. 从状态中获取候选 SMILES 列表
            @SuppressWarnings("unchecked")
            List<String> candidateSmilesList = (List<String>) state.value("candidate_molecules")
                    .orElse(Collections.emptyList());

            if (candidateSmilesList.isEmpty()) {
                // 如果没有候选分子，则返回空映射，不更新状态
                return Collections.emptyMap();
            }

            // 2. 对每个 SMILES 进行验证
            List<MoleculeValidator.ValidationResult> validatedResults = candidateSmilesList.stream()
                    .map(moleculeValidator::validate) // 使用我们修正好的 validate 方法
                    .collect(Collectors.toList());

            // 3. 将验证结果存入状态
            Map<String, Object> result = new HashMap<>();
            // 注意：这里我们将整个 ValidationResult 对象列表存入状态，
            // SummarizerNode 需要知道如何处理它。
            result.put("validated_molecules", validatedResults);
            return result;
        }
    }

    /**
     * 修正后的 SummarizerNode，处理 MoleculeValidator.ValidationResult 列表。
     */
    static class SummarizerNode implements NodeAction {
        private final ChatClient chatClient;

        public SummarizerNode(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) {
            // 1. 从状态中获取验证结果
            @SuppressWarnings("unchecked")
            List<MoleculeValidator.ValidationResult> validatedResults =
                    (List<MoleculeValidator.ValidationResult>) state.value("validated_molecules")
                            .orElse(Collections.emptyList());

            // 2. 构建总结文本
            StringBuilder summaryBuilder = new StringBuilder();
            if (validatedResults.isEmpty()) {
                summaryBuilder.append("未找到任何待验证的候选分子。");
            } else {
                summaryBuilder.append("药物发现候选分子验证结果总结：\n\n");
                for (MoleculeValidator.ValidationResult vr : validatedResults) {
                    summaryBuilder.append("- SMILES: ").append(vr.smiles()).append("\n");
                    if (vr.isValid()) {
                        summaryBuilder.append("  状态: ✅ 有效\n");
                        summaryBuilder.append(String.format("  分子量 (MW): %.2f\n", vr.mw() != null ? vr.mw() : Float.NaN));
                        summaryBuilder.append(String.format("  LogP: %.2f\n", vr.logP() != null ? vr.logP() : Float.NaN));
                    } else {
                        summaryBuilder.append("  状态: ❌ 无效\n");
                        summaryBuilder.append("  原因: ").append(vr.reason()).append("\n");
                    }
                    summaryBuilder.append("\n");
                }
            }

            // 3. 存入最终响应
            Map<String, Object> result = new HashMap<>();
            result.put("final_response", summaryBuilder.toString());
            return result;
        }
    }
}