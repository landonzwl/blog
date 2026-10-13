package com.np.schedule.service;

import com.np.schedule.common.BusinessException;
import com.np.schedule.common.DataConflict;
import com.np.schedule.config.ActorContext;
import com.np.schedule.model.AdminBoard;
import com.np.schedule.model.AssignRequest;
import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.BlockItem;
import com.np.schedule.model.BlockRequest;
import com.np.schedule.model.CustomRequest;
import com.np.schedule.model.Dept;
import com.np.schedule.model.LoadRow;
import com.np.schedule.model.StaffHome;
import com.np.schedule.model.StaffMember;
import com.np.schedule.model.TaskItem;
import com.np.schedule.model.TemplateRequest;
import com.np.schedule.model.VisitorBoard;
import com.np.schedule.solver.SkillRules;
import com.np.schedule.solver.TaskSolver;
import com.np.schedule.store.ScheduleStore;
import com.np.schedule.time.TimeRules;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleService {

    private final ScheduleStore store;

    public ScheduleService(ScheduleStore store) {
        this.store = store;
    }

    public synchronized AdminBoard board() {
        store.reload();
        return readBoard();
    }

    public synchronized AdminBoard createFromTemplate(TemplateRequest request) {
        store.reload();
        if (request == null || !hasDept(request.deptId)) {
            store.adminNotice = "请选择部门";
            return finishAdmin();
        }
        List<TemplateRequest.DailyItem> daily = request.items == null ? new ArrayList<TemplateRequest.DailyItem>() : request.items;
        if (daily.isEmpty()) {
            store.adminNotice = "请先添加至少一条每日固定任务";
            return finishAdmin();
        }
        TimeRules.Dates dates = TimeRules.eachDate(request.startDate, request.endDate);
        if (!dates.ok) {
            store.adminNotice = dates.message;
            return finishAdmin();
        }
        List<PreparedDaily> prepared = new ArrayList<PreparedDaily>();
        for (TemplateRequest.DailyItem item : daily) {
            String name = item == null || item.name == null ? "" : item.name.trim();
            if (name.isEmpty()) {
                store.adminNotice = "每日固定任务需要名称";
                return finishAdmin();
            }
            TimeRules.Clock startClock = TimeRules.parseClock(item.startClock);
            if (!startClock.ok) {
                store.adminNotice = startClock.message;
                return finishAdmin();
            }
            TimeRules.Clock endClock = TimeRules.parseClock(item.endClock);
            if (!endClock.ok) {
                store.adminNotice = endClock.message;
                return finishAdmin();
            }
            Integer headcount = whole(item.headcount);
            Integer priority = whole(item.priority);
            if (headcount == null || headcount < 1 || headcount > 20) {
                store.adminNotice = "所需人数必须是 1 到 20 的整数";
                return finishAdmin();
            }
            if (priority == null || priority < 1 || priority > 5) {
                store.adminNotice = "优先级必须是 1 到 5 的整数";
                return finishAdmin();
            }
            if (!TimeRules.clockOrderOk(startClock.text, endClock.text)) {
                store.adminNotice = "同一条每日任务的结束时刻必须晚于开始时刻";
                return finishAdmin();
            }
            PreparedDaily row = new PreparedDaily();
            row.name = name;
            row.startClock = startClock.text;
            row.endClock = endClock.text;
            row.headcount = headcount;
            row.priority = priority;
            row.skill = storedSkills(item.skill);
            prepared.add(row);
        }
        for (String day : dates.days) {
            for (PreparedDaily item : prepared) {
                TaskItem created = ScheduleStore.task(
                        "t" + store.seq++,
                        item.name,
                        request.deptId,
                        day + " " + item.startClock,
                        day + " " + item.endClock,
                        item.headcount,
                        item.skill,
                        item.priority,
                        "open");
                created.staffDept = storedDepts(request.staffDept, request.deptId);
                created.preferOwn = preferOwn(request.preferOwn);
                store.tasks.add(created);
            }
        }
        store.adminNotice = "已按 " + prepared.size() + " 条每日任务、" + dates.days.size()
                + " 天批量建立 " + (prepared.size() * dates.days.size()) + " 条任务";
        return finishAdmin();
    }

    public synchronized AdminBoard createCustom(CustomRequest request) {
        store.reload();
        if (request == null) {
            store.adminNotice = "请填写任务名称";
            return finishAdmin();
        }
        String name = request.name == null ? "" : request.name.trim();
        if (name.isEmpty()) {
            store.adminNotice = "请填写任务名称";
            return finishAdmin();
        }
        if (!hasDept(request.deptId)) {
            store.adminNotice = "请选择部门";
            return finishAdmin();
        }
        Integer headcount = whole(request.headcount);
        Integer priority = whole(request.priority);
        if (headcount == null || headcount < 1 || headcount > 20) {
            store.adminNotice = "所需人数必须是 1 到 20 的整数";
            return finishAdmin();
        }
        if (priority == null || priority < 1 || priority > 5) {
            store.adminNotice = "优先级必须是 1 到 5 的整数";
            return finishAdmin();
        }
        TimeRules.Range range = TimeRules.parseRange(request.startText, request.endText);
        if (!range.ok) {
            store.adminNotice = range.message;
            return finishAdmin();
        }
        TaskItem created = ScheduleStore.task(
                "t" + store.seq++,
                name,
                request.deptId,
                range.start.text,
                range.end.text,
                headcount,
                storedSkills(request.skill),
                priority,
                "open");
        created.staffDept = storedDepts(request.staffDept, request.deptId);
        created.preferOwn = preferOwn(request.preferOwn);
        store.tasks.add(created);
        store.adminNotice = "自定义任务已建立";
        return finishAdmin();
    }

    public synchronized AdminBoard solve(String deptId) {
        return solve(deptId, null);
    }

    public synchronized AdminBoard solve(String deptId, List<String> taskIds) {
        return solve(deptId, taskIds, null);
    }

    public synchronized AdminBoard solve(String deptId, List<String> taskIds, List<String> staffIds) {
        store.reload();
        String scope = scope(deptId);
        if (scope == null) {
            store.adminNotice = "请选择部门";
            return finishAdmin();
        }
        List<TaskItem> targets = openTasks(scope);
        boolean partial = false;
        if (taskIds != null && !taskIds.isEmpty()) {
            java.util.Set<String> wanted = new java.util.HashSet<String>();
            for (String id : taskIds) {
                if (id != null && !id.trim().isEmpty()) {
                    wanted.add(id.trim());
                }
            }
            if (!wanted.isEmpty()) {
                partial = true;
                List<TaskItem> picked = new ArrayList<TaskItem>();
                for (TaskItem task : targets) {
                    if (wanted.contains(task.id)) {
                        picked.add(task);
                    }
                }
                targets = picked;
                if (targets.isEmpty()) {
                    store.adminNotice = "没有可分配的所选任务";
                    return finishAdmin();
                }
            }
        }
        if (targets.isEmpty()) {
            store.adminNotice = "当前范围没有待分配任务";
            return finishAdmin();
        }
        List<StaffMember> people = peopleFor(targets, scope);
        if (staffIds != null && !staffIds.isEmpty()) {
            java.util.Set<String> wanted = new java.util.HashSet<String>();
            for (String id : staffIds) {
                if (id != null && !id.trim().isEmpty()) wanted.add(id.trim());
            }
            List<StaffMember> picked = new ArrayList<StaffMember>();
            for (StaffMember person : store.staff) {
                if (wanted.contains(person.id) && person.maxMinutes > 0) picked.add(person);
            }
            if (picked.isEmpty()) {
                store.adminNotice = "请选择可参与排班的员工";
                return finishAdmin();
            }
            people = picked;
        }
        TaskSolver.SolveResult result = TaskSolver.solve(targets, people, store.assignments, store.blocks, store.tasks, store.skillBook());
        store.assignments.clear();
        store.assignments.addAll(result.assignments);
        store.gaps.clear();
        store.gaps.addAll(result.gaps);
        store.variance = result.variance;
        store.adminNotice = result.gaps.isEmpty()
                ? (partial ? "已分配所选任务，并按优先级配满、压低最高工时" : "分配完成，已按优先级配满并压低最高工时")
                : "有任务人数不足：" + result.gaps.get(0);
        return finishAdmin();
    }

    public synchronized AdminBoard publish(String deptId) {
        store.reload();
        String scope = scope(deptId);
        if (scope == null) {
            store.adminNotice = "请选择部门";
            return finishAdmin();
        }
        int count = 0;
        String blocked = null;
        for (TaskItem item : store.tasks) {
            if (!"open".equals(item.status)) {
                continue;
            }
            if (!"all".equals(scope) && !item.deptId.equals(scope)) {
                continue;
            }
            int seated = countSeated(item.id);
            if (seated < item.headcount) {
                continue;
            }
            String problem = publishProblem(item);
            if (problem != null) {
                if (blocked == null) {
                    blocked = problem;
                }
                continue;
            }
            item.status = "published";
            for (AssignmentItem row : store.assignments) {
                if (item.id.equals(row.taskId)) {
                    row.status = "published";
                }
            }
            count += 1;
        }
        if (count == 0 && blocked != null) {
            store.adminNotice = blocked;
        } else if (count == 0) {
            store.adminNotice = "没有足额分配的任务可发布";
        } else if (blocked != null) {
            store.adminNotice = "已发布 " + count + " 条任务，仍有任务不满足硬约束：" + blocked;
        } else {
            store.adminNotice = "已发布 " + count + " 条任务";
        }
        return finishAdmin();
    }

    public synchronized AdminBoard assign(AssignRequest request) {
        store.reload();
        if (request == null) {
            store.adminNotice = "请选择任务和员工";
            return finishAdmin();
        }
        TaskItem item = findTask(request.taskId);
        StaffMember person = findStaff(request.staffId);
        if (item == null || person == null) {
            store.adminNotice = "请选择任务和员工";
            return finishAdmin();
        }
        if (!deptAllowed(person, item)) {
            store.adminNotice = item.staffDept == null || item.staffDept.trim().isEmpty() ? "不能跨部门指派" : "不在人员要求的部门内，不能指派";
            return finishAdmin();
        }
        int seated = countSeated(item.id);
        if (seated >= item.headcount) {
            store.adminNotice = "该任务人数已满";
            return finishAdmin();
        }
        if (alreadySeated(item.id, person.id)) {
            store.adminNotice = "该员工已在此任务中";
            return finishAdmin();
        }
        if (!SkillRules.matches(person.skillList(), item.skill, store.skillBook())) {
            store.adminNotice = "技能不匹配，不能指派";
            return finishAdmin();
        }
        if (person.maxMinutes <= 0) {
            store.adminNotice = "该员工已离职，不能指派";
            return finishAdmin();
        }
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        String conflict = TaskSolver.timeConflict(person.id, item.start, item.end, item.id, store.assignments, store.blocks, taskById);
        if (conflict != null) {
            store.adminNotice = "时间冲突，不能指派：" + conflict;
            return finishAdmin();
        }
        long added = (item.end - item.start) / 60000L;
        if (TaskSolver.loadMinutes(person.id, store.assignments, taskById) + added > person.maxMinutes) {
            store.adminNotice = "超过工时上限，不能指派";
            return finishAdmin();
        }
        String status = "open".equals(item.status) ? "draft" : "published";
        store.assignments.add(new AssignmentItem("m" + store.seq++, person.id, item.id, "manual", status));
        store.adminNotice = "已手动指派给 " + person.name;
        return finishAdmin();
    }

    public synchronized TaskItem getTask(String id) {
        store.reload();
        TaskItem item = findTask(id);
        return item == null ? null : item.copy();
    }

    public synchronized AdminBoard updateTask(String id, CustomRequest request) {
        store.reload();
        TaskItem item = findTask(id);
        if (item == null) {
            store.adminNotice = "任务不存在";
            return finishAdmin();
        }
        if ("done".equals(item.status)) {
            store.adminNotice = "已完成的任务不能修改";
            return finishAdmin();
        }
        if (!"open".equals(item.status)) {
            store.adminNotice = "running".equals(item.status) ? "正在执行的任务不能修改，请先退回" : "已发布的任务不能修改，请先撤回发布";
            return finishAdmin();
        }
        if (request == null) {
            store.adminNotice = "请填写任务名称";
            return finishAdmin();
        }
        String name = request.name == null ? "" : request.name.trim();
        if (name.isEmpty()) {
            store.adminNotice = "请填写任务名称";
            return finishAdmin();
        }
        if (!hasDept(request.deptId)) {
            store.adminNotice = "请选择部门";
            return finishAdmin();
        }
        Integer headcount = whole(request.headcount);
        Integer priority = whole(request.priority);
        if (headcount == null || headcount < 1 || headcount > 20) {
            store.adminNotice = "所需人数必须是 1 到 20 的整数";
            return finishAdmin();
        }
        if (priority == null || priority < 1 || priority > 5) {
            store.adminNotice = "优先级必须是 1 到 5 的整数";
            return finishAdmin();
        }
        TimeRules.Range range = TimeRules.parseRange(request.startText, request.endText);
        if (!range.ok) {
            store.adminNotice = range.message;
            return finishAdmin();
        }
        item.name = name;
        item.deptId = request.deptId;
        item.startText = range.start.text;
        item.endText = range.end.text;
        item.start = range.start.ms;
        item.end = range.end.ms;
        item.headcount = headcount;
        item.skill = storedSkills(request.skill);
        item.staffDept = storedDepts(request.staffDept, request.deptId);
        item.preferOwn = preferOwn(request.preferOwn);
        item.priority = priority;
        int dropped = reconcile(item);
        if (!"open".equals(item.status) && countSeated(item.id) < item.headcount) {
            item.status = "open";
            for (AssignmentItem row : store.assignments) {
                if (item.id.equals(row.taskId)) {
                    row.status = "draft";
                }
            }
            store.adminNotice = "任务已更新，人数不足，已改回待分配";
        } else if (dropped > 0) {
            store.adminNotice = "任务已更新，已移除不再满足约束的指派";
        } else {
            store.adminNotice = "任务已更新";
        }
        store.gaps.clear();
        return finishAdmin();
    }

    public synchronized AdminBoard deleteTask(String id) {
        store.reload();
        TaskItem item = findTask(id);
        if (item == null) {
            store.adminNotice = "任务不存在";
            return finishAdmin();
        }
        if ("done".equals(item.status)) {
            store.adminNotice = "已完成的任务不能删除";
            return finishAdmin();
        }
        if (!"open".equals(item.status)) {
            store.adminNotice = "running".equals(item.status) ? "正在执行的任务不能删除，请先退回" : "已发布的任务不能删除，请先撤回发布";
            return finishAdmin();
        }
        store.tasks.remove(item);
        store.assignments.removeIf(row -> item.id.equals(row.taskId));
        store.gaps.clear();
        store.adminNotice = "已删除任务";
        return finishAdmin();
    }

    public synchronized StaffHome staffHome() {
        store.reload();
        return readStaff();
    }

    public synchronized StaffHome addBlock(BlockRequest request) {
        store.reload();
        StaffMember person = findStaff(actingStaffId());
        if (person == null) {
            throw new BusinessException("当前员工不存在");
        }
        if (request == null) {
            store.staffNotice = "时间必须为 yyyy-MM-dd HH:mm，不能填写“上午”这类模糊内容";
            return finishStaff();
        }
        TimeRules.Range range = TimeRules.parseRange(request.startText, request.endText);
        if (!range.ok) {
            store.staffNotice = range.message;
            return finishStaff();
        }
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        String conflict = TaskSolver.timeConflict(person.id, range.start.ms, range.end.ms, "", store.assignments, store.blocks, taskById);
        if (conflict != null) {
            store.staffNotice = "时间冲突，不能登记：" + conflict;
            return finishStaff();
        }
        String reason = request.reason == null ? "" : request.reason.trim();
        if (reason.isEmpty()) {
            reason = "不可工作";
        }
        if (reason.length() > 20) {
            reason = reason.substring(0, 20);
        }
        BlockItem block = new BlockItem();
        block.id = "b" + store.seq++;
        block.staffId = person.id;
        block.start = range.start.ms;
        block.end = range.end.ms;
        block.startText = range.start.text;
        block.endText = range.end.text;
        block.reason = reason;
        store.blocks.add(block);
        store.staffNotice = "已写入个人不可工作时段";
        return finishStaff();
    }

    public synchronized AdminBoard startTask(String id) {
        store.reload();
        String problem = markRunning(id, false);
        store.adminNotice = problem == null ? "任务已开始执行" : problem;
        return finishAdmin();
    }

    public synchronized AdminBoard completeTask(String id) {
        store.reload();
        String problem = markDone(id, false);
        store.adminNotice = problem == null ? "任务已完成" : problem;
        return finishAdmin();
    }

    public synchronized StaffHome startOwnTask(String id) {
        store.reload();
        String problem = markRunning(id, true);
        store.staffNotice = problem == null ? "任务已开始执行" : problem;
        return finishStaff();
    }

    public synchronized StaffHome completeOwnTask(String id) {
        store.reload();
        String problem = markDone(id, true);
        store.staffNotice = problem == null ? "任务已完成" : problem;
        return finishStaff();
    }

    public synchronized AdminBoard publishTask(String id) {
        store.reload();
        TaskItem item = findTask(id);
        if (item == null) {
            store.adminNotice = "任务不存在";
            return finishAdmin();
        }
        String problem = release(item);
        store.adminNotice = problem == null ? "已发布任务" : problem;
        return finishAdmin();
    }

    public synchronized AdminBoard unassign(AssignRequest request) {
        store.reload();
        if (request == null) {
            store.adminNotice = "请选择任务和员工";
            return finishAdmin();
        }
        TaskItem item = findTask(request.taskId);
        if (item == null || findStaff(request.staffId) == null) {
            store.adminNotice = "请选择任务和员工";
            return finishAdmin();
        }
        AssignmentItem row = findAssignment(request.taskId, request.staffId);
        if (row == null) {
            store.adminNotice = "该员工不在此任务中";
            return finishAdmin();
        }
        store.assignments.remove(row);
        if (!"open".equals(item.status) && countSeated(item.id) < item.headcount) {
            item.status = "open";
            draftAssignments(item.id);
            store.adminNotice = "已取消指派，人数不足，已改回待分配";
        } else {
            store.adminNotice = "已取消指派";
        }
        store.gaps.clear();
        return finishAdmin();
    }

    public synchronized AdminBoard revertTask(String id) {
        store.reload();
        String problem = revert(id, false);
        if (problem != null) {
            store.adminNotice = problem;
        }
        return finishAdmin();
    }

    public synchronized StaffHome revertOwnTask(String id) {
        store.reload();
        String problem = revert(id, true);
        store.staffNotice = problem == null ? store.staffNotice : problem;
        return finishStaff();
    }

    public synchronized StaffHome updateBlock(String id, BlockRequest request) {
        store.reload();
        BlockItem block = findBlock(id);
        if (block == null) {
            store.staffNotice = "不可工作时段不存在";
            return finishStaff();
        }
        if (!actingStaffId().equals(block.staffId)) {
            store.staffNotice = "只能修改自己的不可工作时段";
            return finishStaff();
        }
        if (request == null) {
            store.staffNotice = "时间必须为 yyyy-MM-dd HH:mm，不能填写“上午”这类模糊内容";
            return finishStaff();
        }
        TimeRules.Range range = TimeRules.parseRange(request.startText, request.endText);
        if (!range.ok) {
            store.staffNotice = range.message;
            return finishStaff();
        }
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        String conflict = TaskSolver.timeConflict(block.staffId, range.start.ms, range.end.ms, "",
                store.assignments, store.blocks, taskById, block.id);
        if (conflict != null) {
            store.staffNotice = "时间冲突，不能修改：" + conflict;
            return finishStaff();
        }
        String reason = request.reason == null ? "" : request.reason.trim();
        if (reason.isEmpty()) {
            reason = "不可工作";
        }
        if (reason.length() > 20) {
            reason = reason.substring(0, 20);
        }
        block.start = range.start.ms;
        block.end = range.end.ms;
        block.startText = range.start.text;
        block.endText = range.end.text;
        block.reason = reason;
        store.staffNotice = "已更新不可工作时段";
        return finishStaff();
    }

    public synchronized StaffHome deleteBlock(String id) {
        store.reload();
        BlockItem block = findBlock(id);
        if (block == null) {
            store.staffNotice = "不可工作时段不存在";
            return finishStaff();
        }
        if (!actingStaffId().equals(block.staffId)) {
            store.staffNotice = "只能撤销自己的不可工作时段";
            return finishStaff();
        }
        store.blocks.remove(block);
        store.staffNotice = "已撤销不可工作时段";
        return finishStaff();
    }

    public synchronized VisitorBoard visitorBoard() {
        store.reload();
        VisitorBoard board = new VisitorBoard();
        for (Dept dept : store.depts) {
            board.depts.add(dept.copy());
        }
        for (TaskItem task : store.tasks) {
            if (!isPublic(task.status)) {
                continue;
            }
            VisitorBoard.PublicTask row = new VisitorBoard.PublicTask();
            row.id = task.id;
            row.name = task.name;
            row.deptId = task.deptId;
            row.deptName = deptName(task.deptId);
            row.startText = task.startText;
            row.endText = task.endText;
            row.status = task.status;
            for (AssignmentItem item : store.assignments) {
                if (task.id.equals(item.taskId) && "published".equals(item.status)) {
                    row.assigneeNames.add(staffName(item.staffId));
                }
            }
            board.tasks.add(row);
        }
        return board;
    }

    private AdminBoard readBoard() {
        return adminSnapshot();
    }

    private AdminBoard finishAdmin() {
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
            store.adminNotice = ex.getMessage();
        }
        return adminSnapshot();
    }

    private StaffHome readStaff() {
        return staffSnapshot();
    }

    private StaffHome finishStaff() {
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
            store.staffNotice = ex.getMessage();
        }
        return staffSnapshot();
    }

    public synchronized java.util.List<StaffMember> listStaff() {
        store.reload();
        java.util.List<StaffMember> copy = new ArrayList<StaffMember>();
        for (StaffMember person : store.staff) {
            copy.add(person.copy());
        }
        return copy;
    }

    public synchronized String upsertStaff(String staffId, String name, String deptLabel, String skill, String status) {
        java.util.List<String> skills = new ArrayList<String>();
        if (skill != null && !skill.trim().isEmpty()) skills.add(skill.trim());
        return upsertStaff(staffId, name, deptLabel, skills, status);
    }

    public synchronized String upsertStaff(String staffId, String name, String deptLabel, java.util.List<String> skills, String status) {
        store.reload();
        StaffMember person = findStaff(staffId);
        if (person == null) {
            person = findByName(name);
        }
        String deptId = deptIdFor(deptLabel);
        List<String> ids = store.skillIds(skills);
        if (person == null) {
            person = new StaffMember("ns" + store.seq++, name, deptId, "", "离职".equals(status) ? 0 : 40 * 60);
            person.replaceSkills(ids);
            store.staff.add(person);
        } else {
            person.name = name;
            person.deptId = deptId;
            person.replaceSkills(ids);
            if ("离职".equals(status)) {
                person.maxMinutes = 0;
            } else if (person.maxMinutes <= 0) {
                person.maxMinutes = 40 * 60;
            }
        }
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
            return "";
        }
        return person.id;
    }

    public synchronized Map<String, Object> createSkill(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new BusinessException("请输入技能名称");
        }
        if (value.length() > 32) {
            value = value.substring(0, 32);
        }
        String id = store.skillId(value);
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("id", id);
        data.put("name", store.skillBook().get(id));
        data.put("skills", store.skillRows());
        return data;
    }

    public synchronized Map<String, Object> deleteSkill(String id, boolean confirm, List<String> extraCallers) {
        String skillId = id == null ? "" : id.trim();
        Map<String, String> book = store.skillBook();
        if (skillId.isEmpty() || !book.containsKey(skillId)) {
            throw new BusinessException("技能不存在");
        }
        store.reload();
        String name = store.skillBook().get(skillId);
        List<String> callers = new ArrayList<String>();
        for (StaffMember person : store.staff) {
            if (person.skillList().contains(skillId)) callers.add("排班人员 · " + person.name);
        }
        for (TaskItem task : store.tasks) {
            if (holdsSkill(task.skill, skillId)) callers.add("任务 · " + task.name);
        }
        for (String caller : store.employeeSkillCallers(skillId)) addCaller(callers, caller);
        if (extraCallers != null) {
            for (String caller : extraCallers) addCaller(callers, caller);
        }
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("name", name);
        if (!confirm && !callers.isEmpty()) {
            data.put("deleted", Boolean.FALSE);
            data.put("callers", callers);
            return data;
        }
        for (StaffMember person : store.staff) {
            if (!person.skillList().contains(skillId)) continue;
            List<String> next = new ArrayList<String>();
            for (String token : new ArrayList<String>(person.skillList())) {
                if (!skillId.equals(token) && !next.contains(token)) next.add(token);
            }
            person.replaceSkills(next);
        }
        for (TaskItem task : store.tasks) {
            if (holdsSkill(task.skill, skillId)) task.skill = withoutSkill(task.skill, skillId);
        }
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
            throw new BusinessException(ex.getMessage());
        }
        store.stripEmployeeSkill(skillId, "");
        store.deleteSkill(skillId);
        data.put("deleted", Boolean.TRUE);
        data.put("replacement", "");
        data.put("skills", store.skillRows());
        return data;
    }

    private static void addCaller(List<String> callers, String caller) {
        if (caller == null || caller.trim().isEmpty() || callers.contains(caller)) return;
        callers.add(caller.trim());
    }

    public String deptLabel(String deptId) {
        return deptName(deptId);
    }

    public synchronized void ensureDeptNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        store.reload();
        int before = store.depts.size();
        for (String name : names) {
            deptIdFor(name);
        }
        if (store.depts.size() == before) {
            return;
        }
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
        }
    }

    private String deptIdFor(String label) {
        String name = label == null ? "" : label.trim();
        if (!name.isEmpty()) {
            for (Dept dept : store.depts) {
                if (name.equals(dept.name)) return dept.id;
            }
        }
        if (name.isEmpty()) {
            return store.depts.isEmpty() ? "east" : store.depts.get(0).id;
        }
        String id = "d" + store.seq++;
        store.depts.add(new Dept(id, cut(name, 64)));
        return id;
    }

    private static String cut(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    public synchronized String existingLeaveBlock(String staffId, String staffName, String startText, String endText) {
        store.reload();
        StaffMember person = findStaff(staffId);
        if (person == null) {
            person = findByName(staffName);
        }
        if (person == null) {
            return null;
        }
        TimeRules.Range range = TimeRules.parseRange(startText, endText);
        if (!range.ok) {
            return null;
        }
        for (BlockItem block : store.blocks) {
            if (person.id.equals(block.staffId) && "请假".equals(block.reason) && block.start == range.start.ms && block.end == range.end.ms) {
                return block.id;
            }
        }
        return null;
    }

    public synchronized String placeLeave(String staffId, String staffName, String startText, String endText, String leaveId) {
        store.reload();
        StaffMember person = findStaff(staffId);
        if (person == null) {
            person = findByName(staffName);
        }
        if (person == null) {
            return "该员工未纳入排班，不能同意请假";
        }
        TimeRules.Range range = TimeRules.parseRange(startText, endText);
        if (!range.ok) {
            return range.message;
        }
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        String conflict = TaskSolver.timeConflict(person.id, range.start.ms, range.end.ms, "", store.assignments, store.blocks, taskById);
        if (conflict != null) {
            return "时间冲突，不能同意请假：" + conflict;
        }
        String blockId = leaveBlockId(leaveId);
        store.blocks.removeIf(block -> blockId.equals(block.id));
        BlockItem block = new BlockItem();
        block.id = blockId;
        block.staffId = person.id;
        block.start = range.start.ms;
        block.end = range.end.ms;
        block.startText = range.start.text;
        block.endText = range.end.text;
        block.reason = "请假";
        store.blocks.add(block);
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
            return ex.getMessage();
        }
        return null;
    }

    public synchronized void clearLeave(String leaveId) {
        store.reload();
        String blockId = leaveBlockId(leaveId);
        boolean removed = store.blocks.removeIf(block -> blockId.equals(block.id));
        if (!removed) {
            return;
        }
        try {
            store.flush();
        } catch (DataConflict ex) {
            store.reload();
        }
    }

    private String actingStaffId() {
        String fromLogin = ActorContext.staffId();
        if (fromLogin != null && !fromLogin.isEmpty()) {
            return fromLogin;
        }
        return store.currentStaffId;
    }

    private static String leaveBlockId(String leaveId) {
        String id = leaveId == null ? "" : leaveId;
        String blockId = "L" + id;
        return blockId.length() <= 32 ? blockId : blockId.substring(0, 32);
    }

    private int reconcile(TaskItem item) {
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        List<AssignmentItem> kept = new ArrayList<AssignmentItem>();
        int dropped = 0;
        for (AssignmentItem row : store.assignments) {
            if (!item.id.equals(row.taskId)) {
                kept.add(row);
                continue;
            }
            StaffMember person = findStaff(row.staffId);
            boolean fits = person != null
                    && deptAllowed(person, item)
                    && SkillRules.matches(person.skillList(), item.skill, store.skillBook())
                    && TaskSolver.timeConflict(person.id, item.start, item.end, item.id, store.assignments, store.blocks, taskById) == null
                    && TaskSolver.loadMinutes(person.id, store.assignments, taskById) <= person.maxMinutes;
            if (fits) {
                kept.add(row);
            } else {
                dropped += 1;
            }
        }
        List<AssignmentItem> others = new ArrayList<AssignmentItem>();
        List<AssignmentItem> manuals = new ArrayList<AssignmentItem>();
        List<AssignmentItem> autos = new ArrayList<AssignmentItem>();
        for (AssignmentItem row : kept) {
            if (!item.id.equals(row.taskId)) {
                others.add(row);
            } else if ("manual".equals(row.source)) {
                manuals.add(row);
            } else {
                autos.add(row);
            }
        }
        List<AssignmentItem> next = new ArrayList<AssignmentItem>(others);
        int remain = item.headcount;
        for (AssignmentItem row : manuals) {
            if (remain > 0) {
                next.add(row);
                remain -= 1;
            } else {
                dropped += 1;
            }
        }
        for (AssignmentItem row : autos) {
            if (remain > 0) {
                next.add(row);
                remain -= 1;
            } else {
                dropped += 1;
            }
        }
        store.assignments.clear();
        store.assignments.addAll(next);
        return dropped;
    }

    private String markRunning(String id, boolean onlyCurrentStaff) {
        TaskItem item = findTask(id);
        if (item == null) {
            return "任务不存在";
        }
        if (onlyCurrentStaff && !assignedToCurrent(item.id)) {
            return "只能开始执行分配给自己的任务";
        }
        if (!"published".equals(item.status)) {
            return "只有已发布的任务可以开始执行";
        }
        if (countSeated(item.id) < item.headcount) {
            return "人数不足，不能开始执行";
        }
        String problem = publishProblem(item);
        if (problem != null) {
            return problem;
        }
        item.status = "running";
        return null;
    }

    private String release(TaskItem item) {
        if (!"open".equals(item.status)) {
            return "只有待分配的任务可以发布";
        }
        if (countSeated(item.id) < item.headcount) {
            return "人数不足，不能发布";
        }
        String problem = publishProblem(item);
        if (problem != null) {
            return problem;
        }
        item.status = "published";
        for (AssignmentItem row : store.assignments) {
            if (item.id.equals(row.taskId)) {
                row.status = "published";
            }
        }
        return null;
    }

    private String revert(String id, boolean onlyCurrentStaff) {
        TaskItem item = findTask(id);
        if (item == null) {
            return "任务不存在";
        }
        if (onlyCurrentStaff && !assignedToCurrent(item.id)) {
            return "不能退回分配给自己以外的任务";
        }
        if ("done".equals(item.status)) {
            item.status = "running";
            if (onlyCurrentStaff) {
                store.staffNotice = "已退回正在执行";
            } else {
                store.adminNotice = "已退回正在执行";
            }
            return null;
        }
        if ("running".equals(item.status)) {
            item.status = "published";
            if (onlyCurrentStaff) {
                store.staffNotice = "已退回已发布";
            } else {
                store.adminNotice = "已退回已发布";
            }
            return null;
        }
        if ("published".equals(item.status)) {
            if (onlyCurrentStaff) {
                return "不能撤回发布";
            }
            item.status = "open";
            draftAssignments(item.id);
            store.adminNotice = "已撤回发布，任务回到待分配";
            return null;
        }
        return "待分配任务不能退回";
    }

    private void draftAssignments(String taskId) {
        for (AssignmentItem row : store.assignments) {
            if (taskId.equals(row.taskId)) {
                row.status = "draft";
            }
        }
    }

    private String markDone(String id, boolean onlyCurrentStaff) {
        TaskItem item = findTask(id);
        if (item == null) {
            return "任务不存在";
        }
        if (onlyCurrentStaff && !assignedToCurrent(item.id)) {
            return "只能完成分配给自己的任务";
        }
        if (!"running".equals(item.status)) {
            return "只有正在执行的任务可以完成";
        }
        item.status = "done";
        return null;
    }

    private boolean assignedToCurrent(String taskId) {
        for (AssignmentItem row : store.assignments) {
            if (taskId.equals(row.taskId) && actingStaffId().equals(row.staffId) && "published".equals(row.status)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPublic(String status) {
        return "published".equals(status) || "running".equals(status) || "done".equals(status);
    }

    private String publishProblem(TaskItem item) {
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        for (AssignmentItem row : store.assignments) {
            if (!item.id.equals(row.taskId)) {
                continue;
            }
            StaffMember person = findStaff(row.staffId);
            if (person == null || !deptAllowed(person, item) || !SkillRules.matches(person.skillList(), item.skill, store.skillBook())) {
                return item.name + "不满足硬约束";
            }
            if (TaskSolver.timeConflict(person.id, item.start, item.end, item.id, store.assignments, store.blocks, taskById) != null) {
                return item.name + "不满足硬约束";
            }
            if (TaskSolver.loadMinutes(person.id, store.assignments, taskById) > person.maxMinutes) {
                return item.name + "不满足硬约束";
            }
        }
        return null;
    }

    private AdminBoard adminSnapshot() {
        AdminBoard board = new AdminBoard();
        board.notice = store.adminNotice;
        for (Dept dept : store.depts) {
            board.depts.add(dept.copy());
        }
        for (StaffMember person : store.staff) {
            board.staff.add(person.copy());
        }
        for (TaskItem task : store.tasks) {
            board.tasks.add(task.copy());
        }
        for (AssignmentItem item : store.assignments) {
            board.assignments.add(item.copy());
        }
        board.gaps.addAll(store.gaps);
        board.variance = store.variance;
        board.loads.addAll(loadRows());
        board.skills = store.skillRows();
        return board;
    }

    private StaffHome staffSnapshot() {
        StaffMember person = findStaff(actingStaffId());
        StaffHome home = new StaffHome();
        home.notice = store.staffNotice;
        if (person != null) {
            StaffHome.StaffProfile me = new StaffHome.StaffProfile();
            me.id = person.id;
            me.name = person.name;
            me.deptId = person.deptId;
            me.deptName = deptName(person.deptId);
            home.me = me;
        }
        for (AssignmentItem item : store.assignments) {
            if (!actingStaffId().equals(item.staffId) || !"published".equals(item.status)) {
                continue;
            }
            TaskItem task = findTask(item.taskId);
            if (task != null) {
                home.tasks.add(task.copy());
            }
        }
        for (BlockItem block : store.blocks) {
            if (actingStaffId().equals(block.staffId)) {
                home.blocks.add(block.copy());
            }
        }
        return home;
    }

    private List<LoadRow> loadRows() {
        Map<String, TaskItem> taskById = TaskSolver.index(store.tasks);
        List<LoadRow> rows = new ArrayList<LoadRow>();
        for (StaffMember person : store.staff) {
            int count = 0;
            long minutes = 0L;
            for (AssignmentItem item : store.assignments) {
                if (!person.id.equals(item.staffId)) {
                    continue;
                }
                TaskItem owned = taskById.get(item.taskId);
                if (owned == null) {
                    continue;
                }
                count += 1;
                minutes += (owned.end - owned.start) / 60000L;
            }
            LoadRow row = new LoadRow();
            row.id = person.id;
            row.name = person.name;
            row.deptId = person.deptId;
            row.count = count;
            row.hours = Math.round(minutes / 6.0) / 10.0;
            rows.add(row);
        }
        return rows;
    }

    private List<TaskItem> openTasks(String scope) {
        List<TaskItem> list = new ArrayList<TaskItem>();
        for (TaskItem task : store.tasks) {
            if (!"open".equals(task.status)) {
                continue;
            }
            if (!"all".equals(scope) && !task.deptId.equals(scope)) {
                continue;
            }
            list.add(task);
        }
        return list;
    }

    private List<StaffMember> peopleIn(String scope) {
        if ("all".equals(scope)) {
            return new ArrayList<StaffMember>(store.staff);
        }
        List<StaffMember> list = new ArrayList<StaffMember>();
        for (StaffMember person : store.staff) {
            if (scope.equals(person.deptId)) {
                list.add(person);
            }
        }
        return list;
    }

    private String scope(String deptId) {
        if (deptId == null || deptId.trim().isEmpty() || "all".equals(deptId)) {
            return "all";
        }
        return hasDept(deptId) ? deptId : null;
    }

    private boolean hasDept(String deptId) {
        for (Dept dept : store.depts) {
            if (dept.id.equals(deptId)) {
                return true;
            }
        }
        return false;
    }

    private int countSeated(String taskId) {
        int count = 0;
        for (AssignmentItem row : store.assignments) {
            if (taskId.equals(row.taskId)) {
                count += 1;
            }
        }
        return count;
    }

    private boolean alreadySeated(String taskId, String staffId) {
        for (AssignmentItem row : store.assignments) {
            if (taskId.equals(row.taskId) && staffId.equals(row.staffId)) {
                return true;
            }
        }
        return false;
    }

    private AssignmentItem findAssignment(String taskId, String staffId) {
        for (AssignmentItem row : store.assignments) {
            if (taskId.equals(row.taskId) && staffId.equals(row.staffId)) {
                return row;
            }
        }
        return null;
    }

    private BlockItem findBlock(String id) {
        for (BlockItem block : store.blocks) {
            if (block.id.equals(id)) {
                return block;
            }
        }
        return null;
    }

    private TaskItem findTask(String taskId) {
        for (TaskItem task : store.tasks) {
            if (task.id.equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    private StaffMember findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String wanted = name.trim();
        for (StaffMember person : store.staff) {
            if (wanted.equals(person.name)) {
                return person;
            }
        }
        return null;
    }

    private StaffMember findStaff(String staffId) {
        for (StaffMember person : store.staff) {
            if (person.id.equals(staffId)) {
                return person;
            }
        }
        return null;
    }

    private String staffName(String staffId) {
        StaffMember person = findStaff(staffId);
        return person == null ? staffId : person.name;
    }

    private String deptName(String deptId) {
        for (Dept dept : store.depts) {
            if (dept.id.equals(deptId)) {
                return dept.name;
            }
        }
        return deptId;
    }

    private String blankSkill(String skill) {
        return storedSkills(skill);
    }

    private String storedSkills(String skill) {
        if (skill == null || skill.trim().isEmpty()) return "";
        List<String> ids = new ArrayList<String>();
        for (String token : skill.split("[,，]")) {
            String id = store.skillId(token.trim());
            if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
        }
        StringBuilder text = new StringBuilder();
        for (String id : ids) {
            if (text.length() > 0) text.append(',');
            text.append(id);
        }
        return text.toString();
    }

    private String storedDepts(String listed, String fallback) {
        List<String> ids = new ArrayList<String>();
        String source = listed == null || listed.trim().isEmpty() ? fallback : listed;
        if (source != null) {
            for (String token : source.split("[,，]")) {
                String id = token.trim();
                if (hasDept(id) && !ids.contains(id)) ids.add(id);
            }
        }
        if (ids.isEmpty() && hasDept(fallback)) ids.add(fallback);
        StringBuilder text = new StringBuilder();
        for (String id : ids) {
            if (text.length() > 0) text.append(',');
            text.append(id);
        }
        return text.toString();
    }

    private static String preferOwn(String value) {
        return "是".equals(value) ? "是" : "否";
    }

    private static boolean deptAllowed(StaffMember person, TaskItem task) {
        if (person == null || task == null) return false;
        String listed = task.staffDept == null ? "" : task.staffDept.trim();
        if (listed.isEmpty()) return person.deptId.equals(task.deptId);
        for (String token : listed.split("[,，]")) {
            if (person.deptId.equals(token.trim())) return true;
        }
        return false;
    }

    private List<StaffMember> peopleFor(List<TaskItem> targets, String scope) {
        List<StaffMember> people = peopleIn(scope);
        for (StaffMember person : store.staff) {
            if (person.maxMinutes <= 0 || containsStaff(people, person.id)) continue;
            for (TaskItem task : targets) {
                if (deptAllowed(person, task)) {
                    people.add(person);
                    break;
                }
            }
        }
        return people;
    }

    private static boolean containsStaff(List<StaffMember> people, String id) {
        for (StaffMember person : people) {
            if (person.id.equals(id)) return true;
        }
        return false;
    }

    private static boolean holdsSkill(String stored, String skillId) {
        if (stored == null || skillId == null || skillId.isEmpty()) return false;
        for (String token : stored.split("[,，]")) {
            if (skillId.equals(token.trim())) return true;
        }
        return false;
    }

    private static String withoutSkill(String stored, String skillId) {
        StringBuilder text = new StringBuilder();
        if (stored != null) {
            for (String token : stored.split("[,，]")) {
                if (token.trim().isEmpty() || skillId.equals(token.trim())) continue;
                if (text.length() > 0) text.append(',');
                text.append(token.trim());
            }
        }
        return text.toString();
    }

    private static Integer whole(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) > 0.000001d) {
            return null;
        }
        return (int) rounded;
    }

    private static final class PreparedDaily {
        private String name;
        private String startClock;
        private String endClock;
        private int headcount;
        private int priority;
        private String skill;
    }
}
