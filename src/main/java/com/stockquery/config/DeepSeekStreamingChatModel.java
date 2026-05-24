package com.stockquery.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自定义 StreamingChatModel，直接调用 DeepSeek API 并注入 "thinking": false，
 * 避免 LangChain4j OpenAI 客户端无法处理 reasoning_content 的问题。
 */
@Slf4j
public class DeepSeekStreamingChatModel implements StreamingChatModel {

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DeepSeekStreamingChatModel(String apiKey, String baseUrl, String modelName,
                                       double temperature, int maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        CompletableFuture.runAsync(() -> {
            try {
                doChat(chatRequest, handler);
            } catch (Exception e) {
                log.error("DeepSeek streaming chat failed", e);
                handler.onError(e);
            }
        }, executor);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelName);
            body.put("stream", true);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);

            ArrayNode messages = body.putArray("messages");
            for (ChatMessage msg : chatRequest.messages()) {
                ObjectNode msgNode = messages.addObject();
                switch (msg.type()) {
                    case SYSTEM -> {
                        msgNode.put("role", "system");
                        msgNode.put("content", ((SystemMessage) msg).text());
                    }
                    case USER -> {
                        msgNode.put("role", "user");
                        msgNode.put("content", ((UserMessage) msg).singleText());
                    }
                    case AI -> {
                        msgNode.put("role", "assistant");
                        AiMessage ai = (AiMessage) msg;
                        if (ai.hasToolExecutionRequests()) {
                            msgNode.put("content", (String) null);
                            ArrayNode toolCalls = msgNode.putArray("tool_calls");
                            for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                                ObjectNode tc = toolCalls.addObject();
                                tc.put("id", req.id());
                                tc.put("type", "function");
                                ObjectNode func = tc.putObject("function");
                                func.put("name", req.name());
                                func.put("arguments", req.arguments());
                            }
                        } else {
                            msgNode.put("content", ai.text());
                        }
                    }
                    case TOOL_EXECUTION_RESULT -> {
                        msgNode.put("role", "tool");
                        ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) msg;
                        msgNode.put("tool_call_id", toolMsg.id());
                        msgNode.put("content", toolMsg.text());
                    }
                    default -> {
                        // skip unsupported types (CUSTOM etc.)
                        messages.remove(messages.size() - 1);
                        continue;
                    }
                }
            }

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("DeepSeek request: {}", requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("DeepSeek API error " + response.statusCode() + ": " + errorBody);
            }

            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;

                    try {
                        JsonNode json = objectMapper.readTree(data);
                        JsonNode choices = json.path("choices");
                        if (!choices.isArray() || choices.isEmpty()) continue;

                        JsonNode delta = choices.get(0).path("delta");
                        String content = delta.path("content").asText(null);
                        if (content != null && !content.isEmpty()) {
                            contentBuilder.append(content);
                            handler.onPartialResponse(content);
                        }
                    } catch (Exception ignored) {}
                }
            }

            String fullContent = contentBuilder.toString();
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(fullContent))
                    .build());
        } catch (Exception e) {
            log.error("DeepSeek streaming chat failed", e);
            handler.onError(e);
        }
    }
}
