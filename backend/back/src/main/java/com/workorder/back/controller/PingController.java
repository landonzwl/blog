package com.workorder.back.controller;

import com.workorder.back.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 联调测试接口
 * 验证：Spring Boot 启动 + context-path /api + 统一响应体 + Knife4j 文档
 */
@Tag(name = "测试")
@RestController
@RequestMapping("/ping")
public class PingController {

    @Operation(summary = "ping 健康检查")
    @GetMapping
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "blog-api");
        data.put("status", "ok");
        data.put("timestamp", System.currentTimeMillis());
        return Result.ok(data);
    }
}
