package com.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工单操作流水，同时驱动聊天窗口的状态播报 */
@Data
@TableName("t_ticket_log")
public class TicketLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    /** 创建草稿 / 提交工单 / 接单处理 / 处理完成 / 用户补充 / 用户评价 / 打回重处理 / 取消草稿 */
    private String action;

    private String operator;

    private String detail;

    private LocalDateTime createdAt;
}
