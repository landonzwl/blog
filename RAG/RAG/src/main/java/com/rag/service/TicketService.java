package com.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.ApiException;
import com.rag.entity.Ticket;
import com.rag.entity.TicketLog;
import com.rag.entity.User;
import com.rag.mapper.TicketLogMapper;
import com.rag.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单状态机：
 * DRAFT →(用户确认) OPEN →(管理员接单) PROCESSING →(处理完成) RESOLVED
 * →(用户满意) CLOSED / →(用户不满意) PROCESSING（打回）
 * DRAFT →(用户取消) CANCELLED
 * 所有状态变更写入流水并实时推回产生工单的聊天会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 合法状态流转表：from → 允许的 to 集合 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            Ticket.DRAFT,      Set.of(Ticket.OPEN, Ticket.CANCELLED),
            Ticket.OPEN,       Set.of(Ticket.PROCESSING, Ticket.CLOSED),
            Ticket.PROCESSING, Set.of(Ticket.RESOLVED, Ticket.CLOSED),
            Ticket.RESOLVED,   Set.of(Ticket.CLOSED, Ticket.PROCESSING),
            Ticket.CLOSED,     Set.of(),
            Ticket.CANCELLED,  Set.of()
    );

    private final TicketMapper ticketMapper;
    private final TicketLogMapper logMapper;
    private final SseService sseService;
    private final AssignService assignService;

    /** AI 调用：创建预填写草稿 */
    public Ticket createDraft(User user, Long sessionId, Map<String, Object> args) {
        Ticket ticket = new Ticket();
        ticket.setSessionId(sessionId);
        ticket.setUserId(user.getId());
        ticket.setCategory(str(args.get("category"), "其他"));
        ticket.setTitle(str(args.get("title"), "IT 报修"));
        ticket.setDescription(str(args.get("description"), ""));
        ticket.setUrgency(str(args.get("urgency"), "普通"));
        ticket.setDeviceInfo(str(args.get("device_info"), ""));
        ticket.setStatus(Ticket.DRAFT);
        ticketMapper.insert(ticket);
        writeLog(ticket.getId(), "创建草稿", user.getName(), "AI 助手根据对话预填写工单");
        return ticket;
    }

    /** 用户二次确认（可修改预填内容）后正式提交 */
    public Ticket confirmDraft(Long ticketId, User user, Map<String, Object> patch) {
        Ticket ticket = requireOwn(ticketId, user, Ticket.DRAFT, "草稿不存在或已处理");
        if (patch.get("category") != null) {
            ticket.setCategory(str(patch.get("category"), ticket.getCategory()));
        }
        if (patch.get("title") != null && !str(patch.get("title"), "").isBlank()) {
            ticket.setTitle(str(patch.get("title"), ticket.getTitle()));
        }
        if (patch.get("description") != null) {
            ticket.setDescription(str(patch.get("description"), ticket.getDescription()));
        }
        if (patch.get("urgency") != null) {
            ticket.setUrgency(str(patch.get("urgency"), ticket.getUrgency()));
        }
        if (patch.get("device_info") != null) {
            ticket.setDeviceInfo(str(patch.get("device_info"), ticket.getDeviceInfo()));
        }
        ticket.setStatus(Ticket.OPEN);
        ticket.setTicketNo(generateTicketNo());
        // 自动分配：按类别匹配技能的管理员，负载均衡选人
        String assignee = assignService.pickAssignee(ticket.getCategory());
        ticket.setAssignee(assignee);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        writeLog(ticket.getId(), "提交工单", user.getName(), "用户确认 AI 预填内容并提交");
        if (assignee != null) {
            writeLog(ticket.getId(), "自动分配", "system", "按类别[" + ticket.getCategory() + "]负载均衡分配给 " + assignee);
            notifySession(ticket, "submitted", "工单已提交，已自动分配给 " + assignee + " 处理");
        } else {
            notifySession(ticket, "submitted", "工单已提交，等待受理");
        }
        return ticket;
    }

    /** 用户取消草稿 */
    public void cancelDraft(Long ticketId, User user) {
        Ticket ticket = requireOwn(ticketId, user, Ticket.DRAFT, "草稿不存在或已处理");
        ticket.setStatus(Ticket.CANCELLED);
        ticketMapper.updateById(ticket);
        writeLog(ticket.getId(), "取消草稿", user.getName(), "用户取消工单草稿");
    }

    /** AI 调用：查询当前用户工单 */
    public List<Ticket> queryUserTickets(User user, String ticketNo) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getUserId, user.getId())
                .ne(Ticket::getStatus, Ticket.DRAFT)
                .orderByDesc(Ticket::getId);
        if (ticketNo != null && !ticketNo.isBlank()) {
            wrapper.eq(Ticket::getTicketNo, ticketNo.trim());
        } else {
            wrapper.last("LIMIT 5");
        }
        return ticketMapper.selectList(wrapper);
    }

    /** AI 调用：用户补充工单信息 */
    public Ticket addNote(User user, String ticketNo, String note) {
        Ticket ticket = requireOwnByNo(ticketNo, user);
        writeLog(ticket.getId(), "用户补充", user.getName(), note);
        return ticket;
    }

    /** AI 调用：用户反馈满意度 */
    public Ticket feedback(User user, String ticketNo, boolean satisfied, String comment) {
        Ticket ticket = requireOwnByNo(ticketNo, user);
        if (!Ticket.RESOLVED.equals(ticket.getStatus())) {
            throw new ApiException("工单 " + ticketNo + " 尚未处理完成，暂不能评价");
        }
        if (satisfied) {
            ticket.setStatus(Ticket.CLOSED);
            ticketMapper.updateById(ticket);
            writeLog(ticket.getId(), "用户评价", user.getName(), "满意" + suffix(comment));
            notifySession(ticket, "closed", "工单已确认关闭，感谢您的反馈");
        } else {
            ticket.setStatus(Ticket.PROCESSING);
            ticketMapper.updateById(ticket);
            writeLog(ticket.getId(), "打回重处理", user.getName(), "用户不满意" + suffix(comment));
            notifySession(ticket, "reopened", "工单已打回重新处理，工作人员会尽快跟进");
        }
        return ticket;
    }

    /** 管理端：更新状态（受理/处理/完成），推送回聊天窗口 */
    public Ticket adminUpdate(Long ticketId, String status, String assignee, String note, String operator) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || Ticket.DRAFT.equals(ticket.getStatus())) {
            throw new ApiException("工单不存在或仍是草稿");
        }
        if (status == null || !List.of(Ticket.OPEN, Ticket.PROCESSING, Ticket.RESOLVED, Ticket.CLOSED).contains(status)) {
            throw new ApiException("非法状态：" + status);
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(ticket.getStatus(), Set.of());
        if (!allowed.contains(status)) {
            throw new ApiException("非法流转：当前状态 " + ticket.getStatus() + " 不能变更为 " + status);
        }
        ticket.setStatus(status);
        if (assignee != null && !assignee.isBlank()) {
            ticket.setAssignee(assignee.trim());
        }
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        String action = switch (status) {
            case Ticket.PROCESSING -> "接单处理";
            case Ticket.RESOLVED -> "处理完成";
            case Ticket.CLOSED -> "关闭工单";
            default -> "受理工单";
        };
        writeLog(ticketId, action, operator, note);
        notifySession(ticket, "status", switch (status) {
            case Ticket.PROCESSING -> "工单处理中" + (ticket.getAssignee() != null ? "（处理人：" + ticket.getAssignee() + "）" : "");
            case Ticket.RESOLVED -> "处理完成，请回复确认问题是否解决";
            case Ticket.CLOSED -> "工单已关闭";
            default -> "工单已受理";
        } + (note == null || note.isBlank() ? "" : "：" + note));
        return ticket;
    }

    /** 工单列表（管理端），带用户姓名 */
    public List<Map<String, Object>> listForAdmin(String status, Map<Long, String> userNames) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .orderByDesc(Ticket::getId)
                .last("LIMIT 200");
        if (status != null && !status.isBlank()) {
            wrapper.eq(Ticket::getStatus, status);
        }
        return ticketMapper.selectList(wrapper).stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("ticketNo", t.getTicketNo());
            map.put("userName", userNames.getOrDefault(t.getUserId(), "用户" + t.getUserId()));
            map.put("category", t.getCategory());
            map.put("title", t.getTitle());
            map.put("description", t.getDescription());
            map.put("urgency", t.getUrgency());
            map.put("deviceInfo", t.getDeviceInfo());
            map.put("status", t.getStatus());
            map.put("assignee", t.getAssignee());
            map.put("createdAt", t.getCreatedAt());
            return map;
        }).toList();
    }

    public List<TicketLog> logsOf(Long ticketId) {
        return logMapper.selectList(new LambdaQueryWrapper<TicketLog>()
                .eq(TicketLog::getTicketId, ticketId)
                .orderByAsc(TicketLog::getId));
    }

    /** 状态变更实时推回产生工单的会话 */
    private void notifySession(Ticket ticket, String type, String message) {
        if (ticket.getSessionId() == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("ticketId", ticket.getId());
        data.put("ticketNo", ticket.getTicketNo());
        data.put("title", ticket.getTitle());
        data.put("status", ticket.getStatus());
        data.put("assignee", ticket.getAssignee());
        data.put("message", message);
        sseService.push(ticket.getSessionId(), "ticket", data);
    }

    private Ticket requireOwn(Long ticketId, User user, String expectStatus, String errMsg) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || !ticket.getUserId().equals(user.getId()) || !expectStatus.equals(ticket.getStatus())) {
            throw new ApiException(errMsg);
        }
        return ticket;
    }

    private Ticket requireOwnByNo(String ticketNo, User user) {
        if (ticketNo == null || ticketNo.isBlank()) {
            throw new ApiException("请提供工单号");
        }
        Ticket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getTicketNo, ticketNo.trim())
                .last("LIMIT 1"));
        if (ticket == null || !ticket.getUserId().equals(user.getId())) {
            throw new ApiException("未找到您名下的工单：" + ticketNo);
        }
        return ticket;
    }

    private void writeLog(Long ticketId, String action, String operator, String detail) {
        TicketLog entry = new TicketLog();
        entry.setTicketId(ticketId);
        entry.setAction(action);
        entry.setOperator(operator);
        entry.setDetail(detail);
        logMapper.insert(entry);
    }

    private String generateTicketNo() {
        return "GD" + NO_FORMATTER.format(LocalDateTime.now()) + "-" +
               ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private static String str(Object value, String def) {
        if (value == null) {
            return def;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? def : s;
    }

    private static String suffix(String comment) {
        return comment == null || comment.isBlank() ? "" : "：" + comment;
    }
}
