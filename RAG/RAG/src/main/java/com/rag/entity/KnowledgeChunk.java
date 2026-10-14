package com.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docTitle;

    private Integer chunkIndex;

    private String content;

    /** 向量，float[] 的小端字节序列 */
    private byte[] embedding;

    private LocalDateTime createdAt;
}
