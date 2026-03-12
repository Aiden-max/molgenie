package com.example.molgenie.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.molgenie.debug.DebugLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashScopeEmbeddingService {

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimensions;

    public DashScopeEmbeddingService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${molgenie.kb.embedding.baseUrl:https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings}") String baseUrl,
            @Value("${molgenie.kb.embedding.model:text-embedding-v4}") String model,
            @Value("${molgenie.kb.embedding.dimensions:1024}") int dimensions
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
    }

    public int dimensions() {
        return dimensions;
    }

    public float[] embed(String input) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DashScope API key missing: set spring.ai.dashscope.api-key");
        }
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Empty input for embedding");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", text);
        body.put("dimensions", dimensions);
        body.put("encoding_format", "float");

        String json = om.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        DebugLogger.log("DashScopeEmbeddingService#embed", "embedding response",
                "H5", "pre-run",
                Map.of("status", resp.statusCode()));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("DashScope embedding HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = om.readTree(resp.body());
        JsonNode data0 = root.path("data").isArray() && root.path("data").size() > 0 ? root.path("data").get(0) : null;
        if (data0 == null) throw new RuntimeException("DashScope embedding response missing data[0]");
        JsonNode emb = data0.path("embedding");
        if (!emb.isArray()) throw new RuntimeException("DashScope embedding response missing embedding array");
        int n = emb.size();
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (float) emb.get(i).asDouble();
        }
        return out;
    }

    public List<float[]> embedBatch(List<String> inputs) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DashScope API key missing: set spring.ai.dashscope.api-key");
        }
        if (inputs == null || inputs.isEmpty()) return List.of();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", inputs);
        body.put("dimensions", dimensions);
        body.put("encoding_format", "float");

        String json = om.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("DashScope embedding HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = om.readTree(resp.body());
        JsonNode data = root.path("data");
        if (!data.isArray()) throw new RuntimeException("DashScope embedding response missing data array");

        List<float[]> outs = new java.util.ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonNode emb = data.get(i).path("embedding");
            if (!emb.isArray()) throw new RuntimeException("DashScope embedding response missing embedding array at " + i);
            float[] out = new float[emb.size()];
            for (int j = 0; j < emb.size(); j++) {
                out[j] = (float) emb.get(j).asDouble();
            }
            outs.add(out);
        }
        return outs;
    }
}

