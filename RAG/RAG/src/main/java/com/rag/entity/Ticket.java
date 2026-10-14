package com.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单。状态机：
 * DRAFT（AI 预填草稿）→ OPEN（用户确认提交）→ PROCESSING（人工接单处理）
 * → RESOLVED（处理完成）→ CLOSED（用户确认满意关闭）
 * DRAFT → CANCELLED（用户取消）；RESOLVED → PROCESSING（用户不满意打回）
 */
@Data
@TableName("t_ticket")
public class Ticket {

    public static final String DRAFT = "DRAFT";
    public static final String OPEN = "OPEN";
    public static final String PROCESSING = "PROCESSING";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交后生成，如 GD20260801103045-1234 */
    private String ticketNo;

    /** 产生该工单的对话会话，用于状态回推与追溯 */
    private Long sessionId;

    private Long userId;

    private String category;

    private String title;

    private String description;

    /** 紧急 / 高 / 普通 / 低 */
    private String urgency;

    private String deviceInfo;

    private String status;

    private String assignee;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
