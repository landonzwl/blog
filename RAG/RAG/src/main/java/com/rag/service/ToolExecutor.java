package com.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.ai.ToolRegistry;
import com.rag.common.ApiException;
import com.rag.entity.Ticket;
import com.rag.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行层 —— 聊天助手的"受限 API 权限"。
 * 所有工具均以当前会话用户身份执行，只能查询/操作该用户自己的工单，
 * 参数逐一白名单校验，越权与非法参数直接拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final TicketService ticketService;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 执行工具，返回结构化结果（会作为 tool 消息回传给模型）。
     *  工具内部异常不中断对话：转为失败结果回传，让模型自行补救（如向用户要工单号）。 */
    public Map<String, Object> execute(String name, String argumentsJson, User user, Long sessionId) {
        JsonNode args = parseArgs(argumentsJson);
        try {
            return switch (name) {
                case ToolRegistry.CREATE_TICKET -> createTicket(args, user, sessionId);
                case ToolRegistry.QUERY_TICKET -> queryTicket(args, user);
                case ToolRegistry.ADD_TICKET_NOTE -> addNote(args, user);
                case ToolRegistry.SUBMIT_TICKET_FEEDBACK -> feedback(args, user);
                default -> Map.of("success", false, "message", "未知工具：" + name);
            };
        } catch (Exception e) {
            log.warn("工具执行失败 tool={} args={} reason={}", name, argumentsJson, e.getMessage());
            return Map.of("success", false,
                    "message", "工具调用失败：" + e.getMessage() + "。请检查参数（如工单号需来自 query_ticket 查询结果）后重试，或向用户说明情况。");
        }
    }

    private Map<String, Object> createTicket(JsonNode args, User user, Long sessionId) {
        Ticket ticket = ticketService.createDraft(user, sessionId, toMap(args));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("draftId", ticket.getId());
        result.put("ticketNo", ticket.getTicketNo());
        result.put("status", ticket.getStatus());
        result.put("category", ticket.getCategory());
        result.put("title", ticket.getTitle());
        result.put("description", ticket.getDescription());
        result.put("urgency", ticket.getUrgency());
        result.put("deviceInfo", ticket.getDeviceInfo());
        result.put("message", "工单草稿已生成，等待用户在聊天窗口中确认补充后提交");
        return result;
    }

    private Map<String, Object> queryTicket(JsonNode args, User user) {
        String ticketNo = args.path("ticket_no").asText(null);
        List<Ticket> tickets = ticketService.queryUserTickets(user, ticketNo);
        if (tickets.isEmpty()) {
            return Map.of("success", true, "message", "当前用户暂无已提交的工单", "tickets", List.of());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Ticket t : tickets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ticket_no", t.getTicketNo());
            item.put("title", t.getTitle());
            item.put("status", t.getStatus());
            item.put("urgency", t.getUrgency());
            item.put("assignee", t.getAssignee() == null ? "" : t.getAssignee());
            item.put("updated_at", String.valueOf(t.getUpdatedAt()));
            list.add(item);
        }
        return Map.of("success", true, "tickets", list);
    }

    private Map<String, Object> addNote(JsonNode args, User user) {
        String ticketNo = requireText(args, "ticket_no");
        String note = requireText(args, "note");
        ticketService.addNote(user, ticketNo, note);
        return Map.of("success", true, "message", "补充信息已记录到工单 " + ticketNo);
    }

    private Map<String, Object> feedback(JsonNode args, User user) {
        String ticketNo = requireText(args, "ticket_no");
        boolean satisfied = args.path("satisfied").asBoolean(false);
        String comment = args.path("comment").asText("");
        Ticket ticket = ticketService.feedback(user, ticketNo, satisfied, comment);
        return Map.of("success", true, "message", "反馈已提交，工单 " + ticketNo + " 当前状态：" + ticket.getStatus());
    }

    private JsonNode parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new ApiException("工具参数解析失败：" + json);
        }
    }

    private String requireText(JsonNode args, String field) {
        String value = args.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new ApiException("缺少必填参数：" + field);
        }
        return value.trim();
    }

    private Map<String, Object> toMap(JsonNode args) {
        Map<String, Object> map = new LinkedHashMap<>();
        args.fieldNames().forEachRemaining(field -> map.put(field, args.get(field).asText()));
        return map;
    }
}
