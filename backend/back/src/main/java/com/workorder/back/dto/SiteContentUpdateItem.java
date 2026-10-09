package com.workorder.back.dto;

import javax.validation.constraints.NotBlank;

public class SiteContentUpdateItem {

    @NotBlank(message = "contentKey 不能为空")
    private String contentKey;

    private String contentValue;

    public String getContentKey() { return contentKey; }
    public void setContentKey(String contentKey) { this.contentKey = contentKey; }
    public String getContentValue() { return contentValue; }
    public void setContentValue(String contentValue) { this.contentValue = contentValue; }
}
