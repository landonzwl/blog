package com.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.auth.UserContext;
import com.rag.common.ApiException;
import com.rag.entity.User;
import com.rag.mapper.UserMapper;
import com.rag.service.RagService;
import com.rag.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端：工单处理（模拟后端人工接单）+ 知识库管理 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TicketService ticketService;
    private final RagService ragService;
    private final UserMapper userMapper;

    @GetMapping("/tickets")
    public List<Map<String, Object>> tickets(@RequestParam(required = false) String status) {
        requireAdmin();
        Map<Long, String> userNames = new LinkedHashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<User>().last("LIMIT 1000"))
                .forEach(u -> userNames.put(u.getId(), u.getName() + "（" + u.getEmpNo() + "）"));
        return ticketService.listForAdmin(status, userNames);
    }

    /** 受理/处理/完成/关闭，变更实时推送回聊天窗口 */
    @PostMapping("/tickets/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        User admin = requireAdmin();
        ticketService.adminUpdate(id,
                body.get("status"),
                body.get("assignee"),
                body.get("note"),
                admin.getName());
        return Map.of("ok", true);
    }

    @GetMapping("/tickets/{id}/logs")
    public List<Map<String, Object>> ticketLogs(@PathVariable Long id) {
        requireAdmin();
        return ticketService.logsOf(id).stream()
                .map(l -> Map.<String, Object>of(
                        "action", l.getAction(),
                        "operator", l.getOperator(),
                        "detail", l.getDetail() == null ? "" : l.getDetail(),
                        "createdAt", String.valueOf(l.getCreatedAt())))
                .toList();
    }

    /** 知识库文档列表（按标题聚合） */
    @GetMapping("/knowledge")
    public List<Map<String, Object>> knowledge() {
        requireAdmin();
        Map<String, int[]> stats = new LinkedHashMap<>();
        ragService.allChunks().forEach(c -> {
            int[] arr = stats.computeIfAbsent(c.getDocTitle(), k -> new int[2]);
            arr[0]++;
            if (c.getEmbedding() != null) {
                arr[1]++;
            }
        });
        List<Map<String, Object>> list = new ArrayList<>();
        stats.forEach((title, arr) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("chunks", arr[0]);
            item.put("embedded", arr[1]);
            list.add(item);
        });
        return list;
    }

    /** 新增知识文档：切片 + 向量化入库 */
    @PostMapping("/knowledge")
    public Map<String, Object> addKnowledge(@RequestBody Map<String, String> body) {
        requireAdmin();
        String title = body.getOrDefault("title", "").trim();
        String content = body.getOrDefault("content", "").trim();
        if (title.isEmpty() || content.isEmpty()) {
            throw new ApiException("标题与内容不能为空");
        }
        int chunks = ragService.ingest(title, content);
        return Map.of("ok", true, "chunks", chunks);
    }

    /** 为种子数据等缺失向量的切片补齐 embedding */
    @PostMapping("/knowledge/embed-all")
    public Map<String, Object> embedAll() {
        requireAdmin();
        int count = ragService.embedAll();
        return Map.of("ok", true, "embedded", count);
    }

    private User requireAdmin() {
        User user = UserContext.require();
        if (!"ADMIN".equals(user.getRole())) {
            throw new ApiException("需要管理员权限");
        }
        return user;
    }
}
