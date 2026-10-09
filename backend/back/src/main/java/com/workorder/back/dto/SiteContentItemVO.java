package com.workorder.back.dto;

public class SiteContentItemVO {

    private Long id;
    private String contentKey;
    private String contentValue;
    private String label;
    private String groupName;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContentKey() { return contentKey; }
    public void setContentKey(String contentKey) { this.contentKey = contentKey; }
    public String getContentValue() { return contentValue; }
    public void setContentValue(String contentValue) { this.contentValue = contentValue; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
