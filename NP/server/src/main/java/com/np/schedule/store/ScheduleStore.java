package com.np.schedule.store;

import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.Dept;
import com.np.schedule.model.StaffMember;
import com.np.schedule.model.TaskItem;
import com.np.schedule.time.TimeRules;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScheduleStore {

    public final List<Dept> depts = new ArrayList<Dept>();
    public final List<StaffMember> staff = new ArrayList<StaffMember>();
    public final List<TaskItem> tasks = new ArrayList<TaskItem>();
    public final List<AssignmentItem> assignments = new ArrayList<AssignmentItem>();
    public final List<com.np.schedule.model.BlockItem> blocks = new ArrayList<com.np.schedule.model.BlockItem>();
    public final List<String> gaps = new ArrayList<String>();
    public double variance;
    public String adminNotice = "";
    public String staffNotice = "";
    public int seq = 6;
    public int version;
    public String currentStaffId = "s2";
    private final ScheduleDatabase database;

    public ScheduleStore(ScheduleDatabase database) {
        this.database = database;
        database.prepare(this);
    }

    public void reload() {
        database.loadInto(this);
    }

    public void flush() {
        database.save(this);
    }

    public String skillId(String token) {
        return database.skillId(token);
    }

    public List<String> skillIds(List<String> tokens) {
        return database.skillIds(tokens);
    }

    public java.util.Map<String, String> skillBook() {
        return database.skillBook();
    }

    public List<java.util.Map<String, String>> skillRows() {
        return database.skillRows();
    }

    public List<String> employeeSkillCallers(String skillId) {
        return database.employeeSkillCallers(skillId);
    }

    public void stripEmployeeSkill(String skillId, String replacement) {
        database.stripEmployeeSkill(skillId, replacement);
    }

    public void deleteSkill(String id) {
        database.deleteSkill(id);
    }

    static void fillSeed(ScheduleStore store) {
        store.depts.add(new Dept("east", "东区门店"));
        store.depts.add(new Dept("west", "西区门店"));
        store.staff.add(new StaffMember("s1", "陈晨", "east", "全班", 40 * 60));
        store.staff.add(new StaffMember("s2", "李四", "east", "白班", 40 * 60));
        store.staff.add(new StaffMember("s6", "周敏", "east", "白班", 40 * 60));
        store.staff.add(new StaffMember("s3", "王五", "east", "夜班", 40 * 60));
        store.staff.add(new StaffMember("s4", "赵六", "west", "全班", 40 * 60));
        store.staff.add(new StaffMember("s5", "孙七", "west", "白班", 32 * 60));
        store.staff.add(new StaffMember("s7", "吴九", "west", "夜班", 40 * 60));
        store.tasks.add(task("t1", "东区货架整理", "east", "2026-03-02 09:00", "2026-03-02 11:00", 1, "白班", 2, "open"));
        store.tasks.add(task("t2", "东区收银", "east", "2026-03-02 09:30", "2026-03-02 12:00", 1, "白班", 3, "open"));
        store.tasks.add(task("t3", "东区收货", "east", "2026-03-02 10:00", "2026-03-02 11:30", 1, "白班", 1, "open"));
        store.tasks.add(task("t4", "东区夜班巡检", "east", "2026-03-02 16:00", "2026-03-02 23:00", 1, "夜班", 3, "open"));
        store.tasks.add(task("t5", "李四已有早会", "east", "2026-03-02 09:00", "2026-03-02 10:00", 1, "白班", 3, "published"));
        for (TaskItem task : store.tasks) {
            task.origin = "seed";
        }
        store.assignments.add(new AssignmentItem("a-seed", "s2", "t5", "manual", "published"));
        store.seq = 6;
        store.currentStaffId = "s2";
        store.adminNotice = "";
        store.staffNotice = "";
        store.variance = 0;
    }

    public static TaskItem task(String id, String name, String deptId, String startText, String endText,
                                int headcount, String skill, int priority, String status) {
        TimeRules.Range range = TimeRules.parseRange(startText, endText);
        if (!range.ok) {
            throw new IllegalStateException(range.message);
        }
        TaskItem item = new TaskItem();
        item.id = id;
        item.name = name;
        item.deptId = deptId;
        item.startText = range.start.text;
        item.endText = range.end.text;
        item.start = range.start.ms;
        item.end = range.end.ms;
        item.headcount = headcount;
        item.skill = skill;
        item.priority = priority;
        item.status = status;
        return item;
    }
}
