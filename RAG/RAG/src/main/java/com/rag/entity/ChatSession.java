package com.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** OPEN / CLOSED */
    private String status;

    private LocalDateTime createdAt;
}
