package com.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.auth.UserContext;
import com.rag.common.ApiException;
import com.rag.entity.ChatMessage;
import com.rag.entity.ChatSession;
import com.rag.entity.User;
import com.rag.mapper.ChatMessageMapper;
import com.rag.mapper.ChatSessionMapper;
import com.rag.service.ChatService;
import com.rag.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** 会话与聊天：发送消息（POST）+ 事件流（GET SSE） */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatService chatService;
    private final SseService sseService;

    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody(required = false) Map<String, String> body) {
        User user = UserContext.require();
        ChatSession session = new ChatSession();
        session.setUserId(user.getId());
        String title = body == null ? null : body.get("title");
        session.setTitle(title == null || title.isBlank() ? "新对话" : title.trim());
        session.setStatus("OPEN");
        sessionMapper.insert(session);
        return Map.of("id", session.getId(), "title", session.getTitle());
    }

    @GetMapping("/sessions")
    public List<ChatSession> mySessions() {
        User user = UserContext.require();
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, user.getId())
                .orderByDesc(ChatSession::getId)
                .last("LIMIT 50"));
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatMessage> messages(@PathVariable Long id) {
        requireOwnSession(id);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, id)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT 500"));
    }

    /** SSE 事件流：delta / tool / ticket_draft / ticket / done / error */
    @GetMapping(value = "/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        requireOwnSession(id);
        return sseService.register(id);
    }

    /** 发送消息：立即返回，回复通过 SSE 流推送 */
    @PostMapping("/sessions/{id}/send")
    public Map<String, Object> send(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ChatSession session = requireOwnSession(id);
        String content = body.getOrDefault("content", "").trim();
        if (content.isEmpty()) {
            throw new ApiException("消息内容不能为空");
        }
        if (content.length() > 2000) {
            throw new ApiException("消息过长，请精简后发送");
        }
        // 首条消息自动更新会话标题
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, id));
        if (count == 0) {
            session.setTitle(content.length() > 20 ? content.substring(0, 20) + "…" : content);
            sessionMapper.updateById(session);
        }
        chatService.submitTurn(session, UserContext.require(), content);
        return Map.of("ok", true);
    }

    private ChatSession requireOwnSession(Long sessionId) {
        User user = UserContext.require();
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(user.getId())) {
            throw new ApiException("会话不存在");
        }
        return session;
    }
}
