package com.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.ai.AiClient;
import com.rag.ai.ToolRegistry;
import com.rag.config.ChatProperties;
import com.rag.entity.ChatMessage;
import com.rag.entity.ChatSession;
import com.rag.entity.User;
import com.rag.mapper.ChatMessageMapper;
import com.rag.mapper.ChatSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 编排核心。单轮对话流程：
 * 1. 保存用户消息，RAG 预检索相关知识
 * 2. system（角色+规则+知识库）+ 历史 → 流式调用对话模型
 * 3. 模型若发起工具调用（建单/查单/补充/评价）→ 本地受限执行 → 结果回传 → 继续生成
 * 4. 全程通过 SSE 向前端推送 delta / tool / ticket_draft / done 事件
 */
@Slf4j
@Service
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AiClient aiClient;
    private final RagService ragService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final SseService sseService;
    private final ChatProperties props;

    private ExecutorService executor;
    private final AtomicLong threadCounter = new AtomicLong();

    public ChatService(ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper,
                       AiClient aiClient,
                       RagService ragService,
                       ToolRegistry toolRegistry,
                       ToolExecutor toolExecutor,
                       SseService sseService,
                       ChatProperties props) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.aiClient = aiClient;
        this.ragService = ragService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.sseService = sseService;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "chat-turn-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /** 异步执行一轮对话 */
    public void submitTurn(ChatSession session, User user, String content) {
        executor.submit(() -> {
            try {
                runTurn(session, user, content);
            } catch (Exception e) {
                log.error("对话处理失败 session={}", session.getId(), e);
                sseService.push(session.getId(), "error",
                        Map.of("message", "处理失败：" + e.getMessage()));
                sseService.push(session.getId(), "done", Map.of());
            }
        });
    }

    private void runTurn(ChatSession session, User user, String content) {
        Long sessionId = session.getId();
        // 同一会话串行处理，避免上下文交错
        synchronized (("chat-session-" + sessionId).intern()) {
            saveMessage(sessionId, "user", content);

            // RAG 预检索
            List<RagService.Hit> hits;
            try {
                hits = ragService.search(content);
            } catch (Exception e) {
                log.warn("知识检索失败，降级为无知识库模式：{}", e.getMessage());
                hits = List.of();
            }

            // 组装上下文
            List<Map<String, Object>> convo = new ArrayList<>();
            convo.add(message("system", systemPrompt(user, hits)));
            convo.addAll(loadHistory(sessionId));

            for (int round = 0; round < props.getMaxToolRounds(); round++) {
                AiClient.ChatResult result = aiClient.chatStream(convo, toolRegistry.tools(),
                        delta -> sseService.push(sessionId, "delta", Map.of("text", delta)));

                if (!result.content.isBlank()) {
                    saveMessage(sessionId, "assistant", result.content);
                }
                if (result.toolCalls.isEmpty()) {
                    break;
                }

                // 记录 assistant 的工具调用消息，逐个执行并回填结果
                convo.add(assistantToolCallMessage(result));
                for (AiClient.ToolCall call : result.toolCalls) {
                    Map<String, Object> toolResult = toolExecutor.execute(
                            call.name(), call.arguments(), user, sessionId);
                    log.info("工具执行 session={} tool={} result={}", sessionId, call.name(), toolResult);
                    sseService.push(sessionId, "tool",
                            Map.of("name", call.name(), "result", toolResult));
                    // 建单成功 → 额外推送草稿卡片事件（前端渲染可编辑确认表单）
                    if (ToolRegistry.CREATE_TICKET.equals(call.name())) {
                        sseService.push(sessionId, "ticket_draft", toolResult);
                    }
                    convo.add(toolMessage(call.id(), toolResult));
                }
            }
            sseService.push(sessionId, "done", Map.of());
        }
    }

    private String systemPrompt(User user, List<RagService.Hit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业 IT 运维智能助手「小修」。当前用户：")
                .append(user.getName()).append("（工号 ").append(user.getEmpNo())
                .append("，部门 ").append(user.getDept() == null ? "未知" : user.getDept())
                .append("）。\n\n")
                .append("工作规则：\n")
                .append("1. 优先依据【知识库检索结果】回答，给出清晰、可执行的解决步骤；知识库未覆盖的问题基于常识简要解答，并说明不确定。\n")
                .append("2. 出现以下任一情况时，调用 create_ticket 工具为用户创建工单草稿（严禁自己编造工单号）：\n")
                .append("   - 问题必须人工处理（硬件更换、账号注销、权限开通、现场维修等）；\n")
                .append("   - 你连续两轮无法给出有效解决方案；\n")
                .append("   - 用户明确要求报修、转人工。\n")
                .append("3. 信息收集要克制：最多追问一轮。用户只要说清了【故障现象】就可以调用 create_ticket 建单，不要为凑齐发生时间/设备型号等反复追问——缺失字段留空即可，用户会在确认卡片中补充。类别选择要具体：打印机、投影仪等外设故障选「打印机」而非「硬件」，只有电脑主机/显示器等才选「硬件」。\n")
                .append("4. 用户询问工单进度 → 调用 query_ticket；用户想补充信息 → 先用 query_ticket 找到对应工单，再调用 add_ticket_note。\n")
                .append("5. 用户表达感谢或确认问题解决（如「修好了」「谢谢」）→ 先用 query_ticket 查询，选择其中【更新时间最近】的一条 RESOLVED（已解决）工单，直接调用 submit_ticket_feedback（satisfied=true），不要追问是哪一条。用户表示不满意时同理（satisfied=false）。\n")
                .append("6. 调用 create_ticket 后，告知用户草稿已生成，请在聊天窗口的卡片中核对、补充并确认提交。\n")
                .append("7. 语气专业友好，回复简洁，使用中文。称呼用户时使用其真实姓名。\n\n");

        sb.append("【知识库检索结果】（按与用户最新问题的相关度排序）：\n");
        if (hits.isEmpty()) {
            sb.append("（无相关知识）\n");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                RagService.Hit hit = hits.get(i);
                sb.append(i + 1).append(". [").append(hit.docTitle()).append("]\n")
                        .append(hit.content()).append("\n---\n");
            }
        }
        return sb.toString();
    }

    private List<Map<String, Object>> loadHistory(Long sessionId) {
        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + props.getHistoryLimit()));
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            history.add(message(m.getRole(), m.getContent()));
        }
        return history;
    }

    private void saveMessage(Long sessionId, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        messageMapper.insert(m);
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static Map<String, Object> assistantToolCallMessage(AiClient.ChatResult result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("content", result.content == null ? "" : result.content);
        List<Map<String, Object>> calls = new ArrayList<>();
        for (AiClient.ToolCall call : result.toolCalls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.name());
            function.put("arguments", call.arguments());
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", call.id());
            c.put("type", "function");
            c.put("function", function);
            calls.add(c);
        }
        m.put("tool_calls", calls);
        return m;
    }

    private Map<String, Object> toolMessage(String toolCallId, Map<String, Object> toolResult) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", "tool");
            m.put("tool_call_id", toolCallId);
            m.put("content", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(toolResult));
            return m;
        } catch (Exception e) {
            return Map.of("role", "tool", "tool_call_id", toolCallId, "content", "{\"success\":false}");
        }
    }
}
