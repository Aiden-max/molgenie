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

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.molgenie.node.GeneratorNode;
import com.example.molgenie.node.PlannerNode;
import com.example.molgenie.node.SummarizerNode;
import com.example.molgenie.node.ValidatorNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
@RequiredArgsConstructor
public class DrugDiscoveryGraphConfig {
    private final PlannerNode plannerNode;
    private final GeneratorNode generatorNode;
    private final ValidatorNode validatorNode;
    private final SummarizerNode summarizerNode;


    @Bean
    public StateGraph drugDiscoveryGraph()
            throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                .addPatternStrategy("user_query", new ReplaceStrategy())
                .addPatternStrategy("task_type", new ReplaceStrategy())
                .addPatternStrategy("candidate_molecules", new ReplaceStrategy())
                .addPatternStrategy("validated_molecules", new ReplaceStrategy())
                .addPatternStrategy("final_response", new ReplaceStrategy())
                .build();

        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode("planner", node_async(plannerNode))
                .addNode("generator", node_async(generatorNode))
                .addNode("validator", node_async(validatorNode))
                .addNode("summarizer", node_async(summarizerNode))

                .addEdge(START, "planner")
                .addEdge("planner", "generator")
                .addEdge("generator", "validator")
                .addEdge("validator", "summarizer")
                .addEdge("summarizer", END);

        GraphRepresentation representation = graph.getGraph(
                GraphRepresentation.Type.PLANTUML,
                "Drug Discovery Multi-Agent Workflow"
        );
        System.out.println("\n=== Drug Discovery UML Flow ===");
        System.out.println(representation.content());
        System.out.println("================================\n");

        return graph;
    }
}