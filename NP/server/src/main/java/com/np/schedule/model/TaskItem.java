package com.np.schedule.model;

public class TaskItem {

    public String id;
    public String name;
    public String deptId;
    public String startText;
    public String endText;
    public long start;
    public long end;
    public int headcount;
    public String skill;
    public String staffDept = "";
    public String preferOwn = "否";
    public int priority;
    public String status;
    public String origin = "live";

    public TaskItem() {
    }

    public TaskItem copy() {
        TaskItem copy = new TaskItem();
        copy.id = id;
        copy.name = name;
        copy.deptId = deptId;
        copy.startText = startText;
        copy.endText = endText;
        copy.start = start;
        copy.end = end;
        copy.headcount = headcount;
        copy.skill = skill;
        copy.staffDept = staffDept;
        copy.preferOwn = preferOwn;
        copy.priority = priority;
        copy.status = status;
        copy.origin = origin;
        return copy;
    }
}
