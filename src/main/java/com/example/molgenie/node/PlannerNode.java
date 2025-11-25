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
            // 1. 从 OverAllState 提取信息，转换为 DrugDiscoveryState
            DrugDiscoveryState agentState = new DrugDiscoveryState();
            // 假设 Agent 需要 user_query
            state.value("user_query").ifPresent(obj -> agentState.setUserQuery((String)obj));
            // 如果 Agent 需要其他初始状态，也在这里设置...

            // 2. 调用原有 Agent 的方法
            plannerAgent.route(agentState); // 假设 route 方法修改了 agentState

            // 3. 将 Agent 修改后的状态（如果有需要传递给下一个节点的部分）转换回 Map
            Map<String, Object> result = new HashMap<>();
            // 假设 route 方法设置了 task_type
            if (agentState.getTaskType() != null) {
                 result.put("task_type", agentState.getTaskType().name()); // 转换为字符串
            }
            // 如果有其他需要传递的状态...
            return result; // 返回的结果会根据 KeyStrategyFactory 更新到图的共享状态中
        }
    }