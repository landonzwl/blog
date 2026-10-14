package com.rag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.ApiException;
import com.rag.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客户端（对话 + 向量），全部走 HTTP API，
 * 服务器本地不部署任何模型。对话使用流式 SSE，逐 token 返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    public record ToolCall(String id, String name, String arguments) {
    }

    /** 一次流式对话的聚合结果 */
    public static class ChatResult {
        public String content = "";
        public List<ToolCall> toolCalls = new ArrayList<>();
    }

    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * 流式对话。onDelta 逐段回调文本增量（用于 SSE 转发给前端），
     * 返回聚合后的完整内容与工具调用。
     */
    public ChatResult chatStream(List<Map<String, Object>> messages,
                                 List<Map<String, Object>> tools,
                                 Consumer<String> onDelta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getChatModel());
        body.put("messages", messages);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(props.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String err = response.body().collect(Collectors.joining("\n"));
                log.error("AI 对话接口失败 {} : {}", response.statusCode(), err);
                throw new ApiException("AI 对话接口调用失败(" + response.statusCode() + ")，请检查 api-key / 模型配置");
            }
            return parseStream(response.body(), onDelta);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("AI 接口网络异常：" + e.getMessage());
        }
    }

    private ChatResult parseStream(Stream<String> lines, Consumer<String> onDelta) {
        ChatResult result = new ChatResult();
        StringBuilder content = new StringBuilder();
        Map<Integer, StringBuilder> argBuffers = new HashMap<>();
        Map<Integer, String> callIds = new HashMap<>();
        Map<Integer, String> callNames = new HashMap<>();
        List<Integer> order = new ArrayList<>();

        try {
            lines.forEach(line -> {
                if (line == null) {
                    return;
                }
                String payload = line.trim();
                if (!payload.startsWith("data:")) {
                    return;
                }
                payload = payload.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    return;
                }
                try {
                    JsonNode delta = mapper.readTree(payload).path("choices").path(0).path("delta");
                    String text = delta.path("content").asText(null);
                    if (text != null && !text.isEmpty()) {
                        content.append(text);
                        if (onDelta != null) {
                            onDelta.accept(text);
                        }
                    }
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            int idx = tc.path("index").asInt(0);
                            if (!argBuffers.containsKey(idx)) {
                                argBuffers.put(idx, new StringBuilder());
                                order.add(idx);
                            }
                            String id = tc.path("id").asText(null);
                            if (id != null && !id.isEmpty()) {
                                callIds.put(idx, id);
                            }
                            String fn = tc.path("function").path("name").asText(null);
                            if (fn != null && !fn.isEmpty()) {
                                callNames.put(idx, fn);
                            }
                            String args = tc.path("function").path("arguments").asText(null);
                            if (args != null) {
                                argBuffers.get(idx).append(args);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 SSE 行失败: {}", payload);
                }
            });
        } catch (UncheckedIOException e) {
            throw new ApiException("AI 接口连接中断：" + e.getMessage());
        }

        result.content = content.toString();
        for (Integer idx : order) {
            result.toolCalls.add(new ToolCall(
                    callIds.getOrDefault(idx, "call_" + idx),
                    callNames.getOrDefault(idx, ""),
                    argBuffers.get(idx).toString()));
        }
        return result;
    }

    /** 批量向量化 */
    public List<float[]> embed(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", props.getEmbeddingModel(),
                "input", texts);
        try {
            String baseUrl = props.getEmbeddingBaseUrl() != null && !props.getEmbeddingBaseUrl().isBlank()
                    ? props.getEmbeddingBaseUrl() : props.getBaseUrl();
            String apiKey = props.getEmbeddingApiKey() != null && !props.getEmbeddingApiKey().isBlank()
                    ? props.getEmbeddingApiKey() : props.getApiKey();

            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(baseUrl) + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Embedding 接口失败 {} : {}", response.statusCode(), response.body());
                throw new ApiException("Embedding 接口调用失败(" + response.statusCode() + ")，请检查向量模型配置");
            }
            JsonNode data = mapper.readTree(response.body()).path("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                float[] vector = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    vector[i] = (float) arr.get(i).asDouble();
                }
                vectors.add(vector);
            }
            if (vectors.size() != texts.size()) {
                throw new ApiException("Embedding 返回数量与输入不一致");
            }
            return vectors;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Embedding 接口网络异常：" + e.getMessage());
        }
    }

    /** 单条向量化 */
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
