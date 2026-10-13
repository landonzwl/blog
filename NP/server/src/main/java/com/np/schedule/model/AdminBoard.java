package com.np.schedule.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminBoard {

    public String notice = "";
    public List<Dept> depts = new ArrayList<Dept>();
    public List<StaffMember> staff = new ArrayList<StaffMember>();
    public List<TaskItem> tasks = new ArrayList<TaskItem>();
    public List<AssignmentItem> assignments = new ArrayList<AssignmentItem>();
    public List<String> gaps = new ArrayList<String>();
    public double variance;
    public List<LoadRow> loads = new ArrayList<LoadRow>();
    public List<Map<String, String>> skills = new ArrayList<Map<String, String>>();
}
