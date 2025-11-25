package com.example.molgenie.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.molgenie.agent.PlannerAgent;
import com.example.molgenie.graph.DrugDiscoveryState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PlannerNode implements NodeAction {
        private final PlannerAgent plannerAgent;

        public PlannerNode(PlannerAgent plannerAgent) {
            this.plannerAgent = plannerAgent;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) {
            DrugDiscoveryState agentState = new DrugDiscoveryState();
            state.value("user_query").ifPresent(obj -> agentState.setUserQuery((String)obj));
            plannerAgent.route(agentState);

            Map<String, Object> result = new HashMap<>();
            if (agentState.getTaskType() != null) {
                 result.put("task_type", agentState.getTaskType().name());
            }
            return result;
        }
    }