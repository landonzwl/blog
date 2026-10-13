package com.np.schedule.model;

import java.util.ArrayList;
import java.util.List;

public class VisitorBoard {

    public List<Dept> depts = new ArrayList<Dept>();
    public List<PublicTask> tasks = new ArrayList<PublicTask>();

    public static class PublicTask {
        public String id;
        public String name;
        public String deptId;
        public String deptName;
        public String startText;
        public String endText;
        public String status;
        public List<String> assigneeNames = new ArrayList<String>();
    }
}
