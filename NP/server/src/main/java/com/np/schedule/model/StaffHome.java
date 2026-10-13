package com.np.schedule.model;

import java.util.ArrayList;
import java.util.List;

public class StaffHome {

    public String notice = "";
    public StaffProfile me;
    public List<TaskItem> tasks = new ArrayList<TaskItem>();
    public List<BlockItem> blocks = new ArrayList<BlockItem>();

    public static class StaffProfile {
        public String id;
        public String name;
        public String deptId;
        public String deptName;
    }
}
