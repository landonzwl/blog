package com.np.schedule.service;

import com.np.schedule.model.AdminBoard;
import com.np.schedule.model.AssignRequest;
import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.BlockRequest;
import com.np.schedule.model.CustomRequest;
import com.np.schedule.model.StaffHome;
import com.np.schedule.model.TaskItem;
import com.np.schedule.model.TemplateRequest;
import com.np.schedule.model.VisitorBoard;
import com.np.schedule.store.ScheduleDatabase;
import com.np.schedule.store.ScheduleStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleServiceTest {

    @Test
    void eastSolveKeepsPublishedMeetingAndBalancesSkill() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        AdminBoard board = service.solve("east");
        assertEquals("分配完成，已按优先级配满并压低最高工时", board.notice);
        assertTrue(board.gaps.isEmpty());
        assertEquals(4.13d, board.variance, 0.001d);
        assertEquals("s3", staffOf(board, "t4"));
        assertEquals("s2", staffOf(board, "t3"));
        assertEquals("s2", staffOf(board, "t5"));
        String shelf = staffOf(board, "t1");
        String cashier = staffOf(board, "t2");
        assertTrue(("s1".equals(shelf) && "s6".equals(cashier)) || ("s6".equals(shelf) && "s1".equals(cashier)));
        assertEquals(5, board.assignments.size());
    }

    @Test
    void deletingAnUnusedSkillRemovesIt() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        service.createSkill("收银");
        java.util.Map<String, Object> removed = service.deleteSkill("sk1", false, null);
        assertEquals(Boolean.TRUE, removed.get("deleted"));
        assertFalse(skillNames(service.board()).contains("收银"));
    }

    @Test
    void deletingAUsedSkillWaitsThenClearsTheReference() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        service.createSkill("收银");
        String id = service.upsertStaff("", "新人", "东区门店", "sk1", "在职");
        java.util.Map<String, Object> preview = service.deleteSkill("sk1", false, null);
        assertEquals(Boolean.FALSE, preview.get("deleted"));
        assertTrue(String.valueOf(preview.get("callers")).contains("排班人员 · 新人"));
        assertEquals("收银", skillOf(service.board(), id));
        java.util.Map<String, Object> removed = service.deleteSkill("sk1", true, null);
        assertEquals(Boolean.TRUE, removed.get("deleted"));
        assertEquals("", skillOf(service.board(), id));
        assertFalse(skillNames(service.board()).contains("收银"));
    }

    @Test
    void deletingBuiltinSkillsStaysDeleted() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        java.util.Map<String, Object> night = service.deleteSkill("sk-night", true, null);
        assertEquals(Boolean.TRUE, night.get("deleted"));
        assertEquals("", skillOf(service.board(), "s3"));
        java.util.Map<String, Object> general = service.deleteSkill("sk-general", true, null);
        assertEquals(Boolean.TRUE, general.get("deleted"));
        java.util.List<String> names = skillNames(service.board());
        assertFalse(names.contains("夜班"));
        assertFalse(names.contains("通用"));
        assertFalse(skillNames(service.board()).contains("夜班"));
        assertFalse(skillNames(service.board()).contains("通用"));
    }

    @Test
    void creatingASkillStoresAnId() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        java.util.Map<String, Object> created = service.createSkill("收银");
        assertEquals("sk1", created.get("id"));
        assertEquals("收银", created.get("name"));
        assertEquals("sk1", service.createSkill("收银").get("id"));
        boolean listed = false;
        for (java.util.Map<String, String> row : service.board().skills) {
            if ("sk1".equals(row.get("id")) && "收银".equals(row.get("name"))) listed = true;
        }
        assertTrue(listed);
        try {
            service.createSkill("  ");
            assertTrue(false);
        } catch (com.np.schedule.common.BusinessException ex) {
            assertEquals("请输入技能名称", ex.getMessage());
        }
    }

    @Test
    void namedDeptAndCustomSkillStayOnThePerson() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        String id = service.upsertStaff("", "新人", "南区门店", "收银", "在职");
        AdminBoard board = service.board();
        assertEquals("收银", skillOf(board, id));
        assertEquals("南区门店", deptOf(board, id));
    }

    @Test
    void twoSkillsMatchEitherShift() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        String id = service.upsertStaff("", "双技能", "东区门店", java.util.Arrays.asList("白班", "夜班"), "在职");
        AdminBoard board = service.board();
        String skills = skillOf(board, id);
        assertTrue(skills.contains("白班") && skills.contains("夜班"));
        AssignRequest day = new AssignRequest();
        day.taskId = "t1";
        day.staffId = id;
        assertEquals("已手动指派给 双技能", service.assign(day).notice);
        AssignRequest night = new AssignRequest();
        night.taskId = "t4";
        night.staffId = id;
        assertEquals("已手动指派给 双技能", service.assign(night).notice);
    }

    @Test
    void savedDepartmentAppearsBesideTheSeedStores() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        service.ensureDeptNames(java.util.Arrays.asList("东区门店", "济南店", "东区门店"));
        AdminBoard board = service.board();
        assertTrue(deptNames(board).contains("东区门店"));
        assertTrue(deptNames(board).contains("西区门店"));
        assertTrue(deptNames(board).contains("济南店"));
        assertEquals(1, java.util.Collections.frequency(deptNames(board), "东区门店"));
        assertEquals(1, java.util.Collections.frequency(deptNames(board), "济南店"));
    }

    @Test
    void staffScopeKeepsManualAndSkipsUncheckedPeople() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        AdminBoard board = service.solve("east", null, java.util.Collections.singletonList("s3"));
        assertEquals("s3", staffOf(board, "t4"));
        assertEquals("s2", staffOf(board, "t5"));
        assertEquals("", staffOf(board, "t1"));
        assertEquals("", staffOf(board, "t2"));
    }

    @Test
    void manualAssignRejectsSkillAndHourCap() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        AssignRequest nightToDay = new AssignRequest();
        nightToDay.taskId = "t1";
        nightToDay.staffId = "s3";
        AdminBoard skill = service.assign(nightToDay);
        assertEquals("技能不匹配，不能指派", skill.notice);
        assertEquals(1, skill.assignments.size());

        CustomRequest custom = new CustomRequest();
        custom.name = "西区长班";
        custom.deptId = "west";
        custom.startText = "2026-03-10 08:00";
        custom.endText = "2026-03-11 20:00";
        custom.headcount = 1d;
        custom.skill = "白班";
        custom.priority = 2d;
        AdminBoard created = service.createCustom(custom);
        String taskId = created.tasks.get(created.tasks.size() - 1).id;
        AssignRequest tooLong = new AssignRequest();
        tooLong.taskId = taskId;
        tooLong.staffId = "s5";
        AdminBoard capped = service.assign(tooLong);
        assertEquals("超过工时上限，不能指派", capped.notice);
        assertEquals(1, capped.assignments.size());
    }

    @Test
    void blockCannotCoverAnExistingAssignment() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        BlockRequest conflict = new BlockRequest();
        conflict.startText = "2026-03-02 09:00";
        conflict.endText = "2026-03-02 10:00";
        conflict.reason = "请假";
        StaffHome denied = service.addBlock(conflict);
        assertTrue(denied.notice.contains("时间冲突"));
        assertTrue(denied.blocks.isEmpty());

        BlockRequest clear = new BlockRequest();
        clear.startText = "2026-03-04 08:00";
        clear.endText = "2026-03-04 12:00";
        clear.reason = "请假";
        StaffHome saved = service.addBlock(clear);
        assertEquals(1, saved.blocks.size());
        assertEquals("请假", saved.blocks.get(0).reason);
    }

    @Test
    void templateUsesCallerClocksAndDateSpan() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        TemplateRequest bad = new TemplateRequest();
        bad.deptId = "east";
        bad.startDate = "2026-03-03";
        bad.endDate = "2026-03-05";
        TemplateRequest.DailyItem emptyClock = new TemplateRequest.DailyItem();
        emptyClock.name = "开门准备";
        emptyClock.startClock = "";
        emptyClock.endClock = "11:00";
        emptyClock.headcount = 1d;
        emptyClock.priority = 2d;
        bad.items.add(emptyClock);
        AdminBoard rejected = service.createFromTemplate(bad);
        assertEquals(5, rejected.tasks.size());
        assertTrue(rejected.notice.contains("HH:mm"));

        TemplateRequest request = new TemplateRequest();
        request.deptId = "east";
        request.startDate = "2026-03-03";
        request.endDate = "2026-03-05";
        TemplateRequest.DailyItem item = new TemplateRequest.DailyItem();
        item.name = "开门准备";
        item.startClock = "08:30";
        item.endClock = "11:00";
        item.headcount = 1d;
        item.skill = "白班";
        item.priority = 2d;
        request.items.add(item);
        AdminBoard board = service.createFromTemplate(request);
        assertEquals(8, board.tasks.size());
        assertTrue(board.notice.contains("批量建立 3"));
        TaskItem created = board.tasks.get(5);
        assertEquals("2026-03-03 08:30", created.startText);
        assertEquals("2026-03-03 11:00", created.endText);
    }

    @Test
    void publishThenStaffSeesOnlyOwnPublishedTasks() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        service.solve("east");
        AdminBoard published = service.publish("east");
        assertEquals("已发布 4 条任务", published.notice);
        StaffHome home = service.staffHome();
        assertEquals("李四", home.me.name);
        assertEquals(2, home.tasks.size());
        assertEquals("李四已有早会", home.tasks.get(0).name);
        assertEquals("东区收货", home.tasks.get(1).name);

        VisitorBoard visitor = service.visitorBoard();
        assertEquals(5, visitor.tasks.size());
        String meeting = "";
        for (VisitorBoard.PublicTask task : visitor.tasks) {
            if ("李四已有早会".equals(task.name)) {
                meeting = task.assigneeNames.get(0);
            }
        }
        assertEquals("李四", meeting);
    }

    @Test
    void publishedTaskCanStartAndThenComplete() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        AdminBoard started = service.startTask("t5");
        assertEquals("任务已开始执行", started.notice);
        assertEquals("running", statusOf(started, "t5"));
        AdminBoard rejected = service.startTask("t1");
        assertEquals("只有已发布的任务可以开始执行", rejected.notice);
        AdminBoard done = service.completeTask("t5");
        assertEquals("任务已完成", done.notice);
        assertEquals("done", statusOf(done, "t5"));
        AdminBoard sealed = service.deleteTask("t5");
        assertEquals("已完成的任务不能删除", sealed.notice);
        assertEquals("done", statusOf(sealed, "t5"));
        assertEquals("已完成的任务不能修改", service.updateTask("t5", null).notice);

        StaffHome home = service.staffHome();
        assertEquals("done", home.tasks.get(0).status);
        VisitorBoard visitor = service.visitorBoard();
        assertEquals("done", visitor.tasks.get(0).status);
        assertEquals("李四", visitor.tasks.get(0).assigneeNames.get(0));
    }

    @Test
    void visitorHidesOpenTasks() {
        VisitorBoard board = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory())).visitorBoard();
        assertEquals(1, board.tasks.size());
        assertEquals("李四已有早会", board.tasks.get(0).name);
    }

    @Test
    void partialSolveAssignsOnlyTheChosenOpenTask() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        AdminBoard board = service.solve("east", java.util.Collections.singletonList("t4"));
        assertEquals("已分配所选任务，并按优先级配满、压低最高工时", board.notice);
        assertEquals("s3", staffOf(board, "t4"));
        assertEquals("", staffOf(board, "t1"));
        assertEquals("", staffOf(board, "t2"));
        assertEquals("", staffOf(board, "t3"));
        assertEquals("s2", staffOf(board, "t5"));
    }

    @Test
    void updateAndDeletePersistAcrossANewStore() {
        ScheduleDatabase database = ScheduleDatabase.memory();
        ScheduleService service = new ScheduleService(new ScheduleStore(database));
        CustomRequest request = new CustomRequest();
        request.name = "货架改名";
        request.deptId = "east";
        request.startText = "2026-03-02 09:00";
        request.endText = "2026-03-02 11:00";
        request.headcount = 1d;
        request.skill = "白班";
        request.priority = 2d;
        AdminBoard updated = service.updateTask("t1", request);
        assertEquals("任务已更新", updated.notice);
        assertEquals("货架改名", nameOf(updated, "t1"));

        ScheduleService reloaded = new ScheduleService(new ScheduleStore(database));
        assertEquals("货架改名", nameOf(reloaded.board(), "t1"));
        AdminBoard deleted = reloaded.deleteTask("t1");
        assertEquals("已删除任务", deleted.notice);
        assertFalse(hasTask(deleted, "t1"));
        assertFalse(hasTask(new ScheduleService(new ScheduleStore(database)).board(), "t1"));
    }

    @Test
    void assignmentAndPublishCanStepBack() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        AdminBoard withdrawn = service.revertTask("t5");
        assertEquals("已撤回发布，任务回到待分配", withdrawn.notice);
        assertEquals("open", statusOf(withdrawn, "t5"));
        assertEquals("draft", assignmentStatus(withdrawn, "t5"));
        assertEquals(0, service.staffHome().tasks.size());

        AdminBoard published = service.publishTask("t5");
        assertEquals("已发布任务", published.notice);
        assertEquals("published", statusOf(published, "t5"));
        assertEquals(1, service.staffHome().tasks.size());

        AdminBoard notFull = service.publishTask("t1");
        assertEquals("人数不足，不能发布", notFull.notice);
        assertEquals("open", statusOf(notFull, "t1"));

        service.startTask("t5");
        service.completeTask("t5");
        AdminBoard backToRunning = service.revertTask("t5");
        assertEquals("已退回正在执行", backToRunning.notice);
        assertEquals("running", statusOf(backToRunning, "t5"));
        AdminBoard backToPublished = service.revertTask("t5");
        assertEquals("已退回已发布", backToPublished.notice);
        assertEquals("published", statusOf(backToPublished, "t5"));

        StaffHome staffDenied = service.revertOwnTask("t5");
        assertEquals("不能撤回发布", staffDenied.notice);
        assertEquals("published", statusOf(service.board(), "t5"));

        AssignRequest cancel = new AssignRequest();
        cancel.taskId = "t5";
        cancel.staffId = "s2";
        AdminBoard cleared = service.unassign(cancel);
        assertEquals("已取消指派，人数不足，已改回待分配", cleared.notice);
        assertEquals("open", statusOf(cleared, "t5"));
        assertEquals("", staffOf(cleared, "t5"));
    }

    @Test
    void staffCanChangeAndRemoveOwnBlock() {
        ScheduleService service = new ScheduleService(new ScheduleStore(ScheduleDatabase.memory()));
        BlockRequest clear = new BlockRequest();
        clear.startText = "2026-03-04 08:00";
        clear.endText = "2026-03-04 12:00";
        clear.reason = "请假";
        StaffHome saved = service.addBlock(clear);
        String id = saved.blocks.get(0).id;

        BlockRequest conflict = new BlockRequest();
        conflict.startText = "2026-03-02 09:00";
        conflict.endText = "2026-03-02 10:00";
        conflict.reason = "改期";
        StaffHome denied = service.updateBlock(id, conflict);
        assertTrue(denied.notice.contains("不能修改"));
        assertEquals("2026-03-04 08:00", denied.blocks.get(0).startText);

        BlockRequest moved = new BlockRequest();
        moved.startText = "2026-03-06 08:00";
        moved.endText = "2026-03-06 12:00";
        moved.reason = "外出";
        StaffHome updated = service.updateBlock(id, moved);
        assertEquals("已更新不可工作时段", updated.notice);
        assertEquals("外出", updated.blocks.get(0).reason);

        StaffHome removed = service.deleteBlock(id);
        assertEquals("已撤销不可工作时段", removed.notice);
        assertTrue(removed.blocks.isEmpty());
        assertTrue(service.staffHome().blocks.isEmpty());
    }

    private static String skillOf(AdminBoard board, String staffId) {
        for (com.np.schedule.model.StaffMember person : board.staff) {
            if (staffId.equals(person.id)) return skillLabel(board, person.skill);
        }
        return "";
    }

    private static java.util.List<String> skillNames(AdminBoard board) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (java.util.Map<String, String> row : board.skills) names.add(row.get("name"));
        return names;
    }

    private static String skillLabel(AdminBoard board, String stored) {
        if (stored == null || stored.trim().isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (String id : stored.split("[,，]")) {
            String name = id.trim();
            if (name.isEmpty()) continue;
            if (board.skills != null) {
                for (java.util.Map<String, String> row : board.skills) {
                    if (name.equals(row.get("id"))) name = row.get("name");
                }
            }
            if (text.length() > 0) text.append('、');
            text.append(name);
        }
        return text.toString();
    }

    private static java.util.List<String> deptNames(AdminBoard board) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (com.np.schedule.model.Dept dept : board.depts) {
            names.add(dept.name);
        }
        return names;
    }

    private static String deptOf(AdminBoard board, String staffId) {
        String deptId = "";
        for (com.np.schedule.model.StaffMember person : board.staff) {
            if (staffId.equals(person.id)) deptId = person.deptId;
        }
        for (com.np.schedule.model.Dept dept : board.depts) {
            if (deptId.equals(dept.id)) return dept.name;
        }
        return "";
    }

    private static String staffOf(AdminBoard board, String taskId) {
        for (AssignmentItem item : board.assignments) {
            if (taskId.equals(item.taskId)) {
                return item.staffId;
            }
        }
        return "";
    }

    private static String nameOf(AdminBoard board, String taskId) {
        for (TaskItem item : board.tasks) {
            if (taskId.equals(item.id)) {
                return item.name;
            }
        }
        return "";
    }

    private static boolean hasTask(AdminBoard board, String taskId) {
        return !nameOf(board, taskId).isEmpty();
    }

    private static String assignmentStatus(AdminBoard board, String taskId) {
        for (AssignmentItem item : board.assignments) {
            if (taskId.equals(item.taskId)) {
                return item.status;
            }
        }
        return "";
    }

    private static String statusOf(AdminBoard board, String taskId) {
        for (TaskItem item : board.tasks) {
            if (taskId.equals(item.id)) {
                return item.status;
            }
        }
        return "";
    }
}
