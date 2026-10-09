package com.workorder.back.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ProjectSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多 200 字")
    private String title;

    @Size(max = 500, message = "摘要最多 500 字")
    private String summary;

    @NotBlank(message = "正文不能为空")
    private String content;

    @Size(max = 500, message = "封面 URL 过长")
    private String coverUrl;

    @Size(max = 100, message = "标签过长")
    private String tag;

    @Size(max = 100, message = "时间文案过长")
    private String dateLabel;

    @NotNull(message = "状态不能为空")
    @Min(0) @Max(1)
    private Integer status;

    private Integer sortOrder;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getDateLabel() { return dateLabel; }
    public void setDateLabel(String dateLabel) { this.dateLabel = dateLabel; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
