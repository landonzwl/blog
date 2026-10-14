package com.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 接口配置：对话模型与向量模型可分别指向不同的 OpenAI 兼容服务。
 * 服务器（2C2G）不部署任何模型，全部通过 API 调用。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 对话模型 API 地址，如 https://api.deepseek.com/v1 */
    private String baseUrl;

    /** 对话模型 API Key */
    private String apiKey;

    /** 对话模型名称 */
    private String chatModel;

    /** 向量模型 API 地址（可与对话不同源，如 DashScope 兼容模式） */
    private String embeddingBaseUrl;

    /** 向量模型 API Key */
    private String embeddingApiKey;

    /** 向量模型名称 */
    private String embeddingModel;
}
