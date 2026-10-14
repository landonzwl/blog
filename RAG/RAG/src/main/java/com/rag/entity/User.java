package com.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String empNo;

    private String name;

    private String dept;

    private String phone;

    private String email;

    /** USER / ADMIN */
    private String role;

    /** 处理技能标签（逗号分隔），自动分配用。为空表示兜底全能 */
    private String skills;

    private LocalDateTime createdAt;
}
