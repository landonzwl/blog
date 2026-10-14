package com.rag.controller;

import com.rag.auth.UserContext;
import com.rag.common.ApiException;
import com.rag.entity.Ticket;
import com.rag.entity.User;
import com.rag.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 用户侧工单接口：草稿确认 / 取消 / 我的工单 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /** 用户在确认卡片中核对/修改后提交（二次填写确认） */
    @PostMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        User user = UserContext.require();
        Ticket ticket = ticketService.confirmDraft(id, user, body == null ? Map.of() : body);
        return Map.of("ok", true, "ticketNo", ticket.getTicketNo(), "status", ticket.getStatus());
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        ticketService.cancelDraft(id, UserContext.require());
        return Map.of("ok", true);
    }

    @GetMapping("/my")
    public List<Map<String, Object>> myTickets() {
        User user = UserContext.require();
        return ticketService.queryUserTickets(user, null).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "ticketNo", t.getTicketNo(),
                        "title", t.getTitle(),
                        "category", t.getCategory(),
                        "urgency", t.getUrgency(),
                        "status", t.getStatus(),
                        "assignee", t.getAssignee() == null ? "" : t.getAssignee(),
                        "createdAt", String.valueOf(t.getCreatedAt())))
                .toList();
    }

    @GetMapping("/{id}/logs")
    public List<Map<String, Object>> logs(@PathVariable Long id) {
        return ticketService.logsOf(id).stream()
                .map(l -> Map.<String, Object>of(
                        "action", l.getAction(),
                        "operator", l.getOperator(),
                        "detail", l.getDetail() == null ? "" : l.getDetail(),
                        "createdAt", String.valueOf(l.getCreatedAt())))
                .toList();
    }
}
