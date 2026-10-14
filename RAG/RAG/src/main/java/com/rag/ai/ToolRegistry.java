package com.rag.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 可调用的工具（Function Calling）声明。
 * 安全边界：所有工具在 ToolExecutor 中以"当前会话用户"身份执行，
 * 只能操作该用户自己的工单。
 */
@Component
public class ToolRegistry {

    public static final String CREATE_TICKET = "create_ticket";
    public static final String QUERY_TICKET = "query_ticket";
    public static final String ADD_TICKET_NOTE = "add_ticket_note";
    public static final String SUBMIT_TICKET_FEEDBACK = "submit_ticket_feedback";

    public List<Map<String, Object>> tools() {
        return List.of(
                tool(CREATE_TICKET,
                        "当判断该问题需要人工处理（硬件更换、账号注销、权限开通、现场维修等），"
                                + "连续多轮你无法给出有效解决方案，或用户明确要求报修/转人工时，"
                                + "根据对话中已收集的信息创建工单草稿，返回草稿内容供用户二次确认填写。",
                        Map.ofEntries(
                                Map.entry("category", prop("问题类别：网络/硬件/账号权限/软件/打印机/其他", List.of("网络", "硬件", "账号权限", "软件", "打印机", "其他"))),
                                Map.entry("title", prop("工单标题，一句话概括故障", "string")),
                                Map.entry("description", prop("故障详细描述：现象、发生时间、影响范围、已尝试的操作", "string")),
                                Map.entry("urgency", prop("紧急程度", List.of("紧急", "高", "普通", "低"))),
                                Map.entry("device_info", prop("设备信息：电脑编号/IP/会议室号等，未知可留空", "string")))),
                tool(QUERY_TICKET,
                        "查询当前用户名下的工单列表及处理状态。用户询问进度时使用。ticket_no 不传则返回最近 5 条。"
                                + "播报工单号时必须逐字使用工具结果中返回的 ticket_no，严禁自行拼接或改写。",
                        Map.ofEntries(
                                Map.entry("ticket_no", Map.of("type", "string", "description", "准确的工单号（从查询结果中获得），可选")))),
                tool(ADD_TICKET_NOTE,
                        "为当前用户的指定工单补充说明信息（如新发现的故障现象、现场照片说明等）。"
                                + "ticket_no 必须来自 query_ticket 的查询结果，不得猜测或自行拼造。",
                        Map.ofEntries(
                                Map.entry("ticket_no", prop("工单号（从查询结果获得）", "string")),
                                Map.entry("note", prop("补充内容", "string")))),
                tool(SUBMIT_TICKET_FEEDBACK,
                        "用户对已处理完成的工单进行满意度反馈。不满意时工单会自动打回重新处理。"
                                + "ticket_no 必须来自 query_ticket 的查询结果，不得猜测或自行拼造。",
                        Map.ofEntries(
                                Map.entry("ticket_no", prop("工单号（从查询结果获得）", "string")),
                                Map.entry("satisfied", Map.of("type", "boolean", "description", "是否满意")),
                                Map.entry("comment", Map.of("type", "string", "description", "评价内容，可选")))));
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters));
    }

    private Map<String, Object> prop(String description, Object type) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type instanceof List ? "string" : type);
        p.put("description", description);
        if (type instanceof List<?> options) {
            p.put("enum", options);
        }
        return p;
    }
}
