package com.np.schedule.model;

public class AssignmentItem {

    public String id;
    public String staffId;
    public String taskId;
    public String source;
    public String status;

    public AssignmentItem() {
    }

    public AssignmentItem(String id, String staffId, String taskId, String source, String status) {
        this.id = id;
        this.staffId = staffId;
        this.taskId = taskId;
        this.source = source;
        this.status = status;
    }

    public AssignmentItem copy() {
        return new AssignmentItem(id, staffId, taskId, source, status);
    }
}
