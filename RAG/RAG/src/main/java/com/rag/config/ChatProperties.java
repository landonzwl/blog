package com.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 会话 / Agent 编排参数 */
@Data
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {

    /** 每轮带入对话历史的消息条数 */
    private int historyLimit = 12;

    /** 单轮对话内工具调用最大轮数，防止死循环 */
    private int maxToolRounds = 4;
}
