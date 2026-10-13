package com.np.schedule.model;

import java.util.ArrayList;
import java.util.List;

public class StaffMember {

    public String id;
    public String name;
    public String deptId;
    public String skill;
    public List<String> skills = new ArrayList<String>();
    public int maxMinutes;

    public StaffMember() {
    }

    public StaffMember(String id, String name, String deptId, String skill, int maxMinutes) {
        this.id = id;
        this.name = name;
        this.deptId = deptId;
        this.skill = skill == null ? "" : skill.trim();
        this.maxMinutes = maxMinutes;
        this.skills = split(this.skill);
    }

    public List<String> skillList() {
        if (skills != null && !skills.isEmpty()) return skills;
        return split(skill);
    }

    public void replaceSkills(List<String> values) {
        List<String> next = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (value == null) continue;
                for (String part : split(value)) {
                    if (next.contains(part)) continue;
                    next.add(part.length() > 32 ? part.substring(0, 32) : part);
                    if (next.size() >= 16) break;
                }
                if (next.size() >= 16) break;
            }
        }
        this.skills = next;
        StringBuilder stored = new StringBuilder();
        for (String id : next) {
            if (stored.length() > 0) stored.append(',');
            stored.append(id);
        }
        this.skill = stored.toString();
    }

    private static List<String> split(String raw) {
        List<String> list = new ArrayList<String>();
        if (raw == null) return list;
        for (String part : raw.split("[,，]")) {
            String item = part.trim();
            if (item.isEmpty() || list.contains(item)) continue;
            list.add(item);
        }
        return list;
    }

    public StaffMember copy() {
        StaffMember copy = new StaffMember(id, name, deptId, skill, maxMinutes);
        copy.skills = new ArrayList<String>(skillList());
        copy.skill = skill;
        return copy;
    }
}
