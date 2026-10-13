package com.np.schedule.model;

import java.util.ArrayList;
import java.util.List;

public class TemplateRequest {

    public String deptId;
    public String staffDept;
    public String preferOwn;
    public String startDate;
    public String endDate;
    public List<DailyItem> items = new ArrayList<DailyItem>();

    public static class DailyItem {
        public String name;
        public String startClock;
        public String endClock;
        public Double headcount;
        public String skill;
        public Double priority;
    }
}
