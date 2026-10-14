package com.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** RAG 检索参数 */
@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 检索返回条数 */
    private int topK = 5;

    /** 知识切片长度（字符） */
    private int chunkSize = 500;

    /** 相邻切片重叠（字符） */
    private int chunkOverlap = 80;

    /** 相似度阈值，低于该分数的结果不进入提示词 */
    private double minScore = 0.25;
}
