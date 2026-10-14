package com.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话级 SSE 推送。聊天流式输出、工具调用播报、工单草稿卡片、
 * 工单状态变更全部通过这里推给前端聊天窗口。
 */
@Slf4j
@Service
public class SseService {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(e -> remove(sessionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"ok\":true}", MediaType.APPLICATION_JSON));
            log.info("SSE 注册成功 session={}，connected 事件已发送，当前该会话连接数={}", sessionId,
                    emitters.get(sessionId) == null ? 1 : emitters.get(sessionId).size());
        } catch (Exception e) {
            log.error("SSE 注册 session={} 时 connected 事件发送失败", sessionId, e);
        }
        return emitter;
    }

    public void push(Long sessionId, String event, Object data) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list == null || list.isEmpty()) {
            log.warn("SSE push 丢弃 session={} event={}（该会话无活跃连接）", sessionId, event);
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.error("SSE push 失败 session={} event={}", sessionId, event, e);
                remove(sessionId, emitter);
            }
        }
    }

    /** 心跳，防止代理断开空闲连接 */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.forEach((sessionId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(sessionId, emitter);
                }
            }
        });
    }

    private void remove(Long sessionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(sessionId);
            }
        }
    }
}
