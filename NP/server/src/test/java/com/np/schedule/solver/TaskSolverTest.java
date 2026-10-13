package com.np.schedule.solver;

import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.BlockItem;
import com.np.schedule.model.StaffMember;
import com.np.schedule.model.TaskItem;
import com.np.schedule.store.ScheduleStore;
import com.np.schedule.time.TimeRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSolverTest {

    @Test
    void keepsSpecialistForTheLaterSpecificTask() {
        TaskItem generic = task("g", "通用岗", "通用", 5, "2026-03-02 09:00", "2026-03-02 12:00");
        TaskItem night = task("n", "夜班岗", "夜班", 4, "2026-03-02 09:00", "2026-03-02 12:00");
        StaffMember nightPerson = new StaffMember("a", "夜", "east", "夜班", 2400);
        StaffMember dayPerson = new StaffMember("b", "白", "east", "白班", 2400);
        TaskSolver.SolveResult result = TaskSolver.solve(
                Arrays.asList(generic, night),
                Arrays.asList(nightPerson, dayPerson),
                new ArrayList<AssignmentItem>(),
                new ArrayList<BlockItem>(),
                Arrays.asList(generic, night));
        assertTrue(result.gaps.isEmpty());
        assertEquals("b", staffOf(result, "g"));
        assertEquals("a", staffOf(result, "n"));
    }

    @Test
    void exactSkillIsChosenBeforeGeneralist() {
        TaskItem night = task("n", "夜班岗", "夜班", 5, "2026-03-02 09:00", "2026-03-02 12:00");
        TaskItem day = task("d", "白班岗", "白班", 4, "2026-03-02 09:00", "2026-03-02 12:00");
        StaffMember general = new StaffMember("a", "全能", "east", "全班", 2400);
        StaffMember nightPerson = new StaffMember("b", "夜", "east", "夜班", 2400);
        TaskSolver.SolveResult result = TaskSolver.solve(
                Arrays.asList(day, night),
                Arrays.asList(general, nightPerson),
                new ArrayList<AssignmentItem>(),
                new ArrayList<BlockItem>(),
                Arrays.asList(day, night));
        assertTrue(result.gaps.isEmpty());
        assertEquals("b", staffOf(result, "n"));
        assertEquals("a", staffOf(result, "d"));
    }

    @Test
    void secondOverlappingTaskStaysOpenWhenOnlyOnePersonFits() {
        TaskItem first = task("a", "先", "白班", 5, "2026-03-02 09:00", "2026-03-02 12:00");
        TaskItem second = task("b", "后", "白班", 4, "2026-03-02 10:00", "2026-03-02 11:00");
        StaffMember person = new StaffMember("s", "甲", "east", "白班", 2400);
        TaskSolver.SolveResult result = TaskSolver.solve(
                Arrays.asList(first, second),
                Collections.singletonList(person),
                new ArrayList<AssignmentItem>(),
                new ArrayList<BlockItem>(),
                Arrays.asList(first, second));
        assertEquals("s", staffOf(result, "a"));
        assertEquals("", staffOf(result, "b"));
        assertEquals(1, result.gaps.size());
    }

    @Test
    void abuttingTasksAreBothAllowed() {
        TaskItem held = task("held", "已有", "白班", 1, "2026-03-02 11:00", "2026-03-02 12:00");
        TaskItem next = task("next", "紧接", "白班", 1, "2026-03-02 09:00", "2026-03-02 11:00");
        StaffMember person = new StaffMember("s", "甲", "east", "白班", 2400);
        List<AssignmentItem> existing = new ArrayList<AssignmentItem>();
        existing.add(new AssignmentItem("m1", "s", "held", "manual", "published"));
        TaskSolver.SolveResult result = TaskSolver.solve(
                Collections.singletonList(next),
                Collections.singletonList(person),
                existing,
                new ArrayList<BlockItem>(),
                Arrays.asList(held, next));
        assertEquals("s", staffOf(result, "next"));
        assertEquals("s", staffOf(result, "held"));
        assertTrue(result.gaps.isEmpty());
    }

    @Test
    void maxMinutesIsHard() {
        TaskItem held = task("held", "已有", "白班", 1, "2026-03-02 08:00", "2026-03-02 09:00");
        TaskItem extra = task("extra", "再加", "白班", 1, "2026-03-02 10:00", "2026-03-02 11:00");
        StaffMember person = new StaffMember("s", "甲", "east", "白班", 60);
        List<AssignmentItem> existing = new ArrayList<AssignmentItem>();
        existing.add(new AssignmentItem("m1", "s", "held", "manual", "published"));
        TaskSolver.SolveResult result = TaskSolver.solve(
                Collections.singletonList(extra),
                Collections.singletonList(person),
                existing,
                new ArrayList<BlockItem>(),
                Arrays.asList(held, extra));
        assertEquals("", staffOf(result, "extra"));
        assertEquals(1, result.gaps.size());
    }

    @Test
    void personalBlockRejectsThatRange() {
        TaskItem job = task("job", "班", "白班", 1, "2026-03-02 09:00", "2026-03-02 11:00");
        StaffMember person = new StaffMember("s", "甲", "east", "白班", 2400);
        TimeRules.Range range = TimeRules.parseRange("2026-03-02 09:30", "2026-03-02 10:00");
        BlockItem block = new BlockItem();
        block.staffId = "s";
        block.start = range.start.ms;
        block.end = range.end.ms;
        block.reason = "请假";
        TaskSolver.SolveResult result = TaskSolver.solve(
                Collections.singletonList(job),
                Collections.singletonList(person),
                new ArrayList<AssignmentItem>(),
                Collections.singletonList(block),
                Collections.singletonList(job));
        assertEquals("", staffOf(result, "job"));
    }

    @Test
    void manualSeatIsKeptAndNotDuplicated() {
        TaskItem job = task("job", "班", "白班", 1, "2026-03-02 09:00", "2026-03-02 11:00");
        StaffMember person = new StaffMember("s", "甲", "east", "白班", 2400);
        StaffMember other = new StaffMember("o", "乙", "east", "白班", 2400);
        List<AssignmentItem> existing = new ArrayList<AssignmentItem>();
        existing.add(new AssignmentItem("m1", "s", "job", "manual", "draft"));
        TaskSolver.SolveResult result = TaskSolver.solve(
                Collections.singletonList(job),
                Arrays.asList(person, other),
                existing,
                new ArrayList<BlockItem>(),
                Collections.singletonList(job));
        assertEquals(1, result.assignments.size());
        assertEquals("manual", result.assignments.get(0).source);
        assertEquals("s", result.assignments.get(0).staffId);
    }

    private static TaskItem task(String id, String name, String skill, int priority, String start, String end) {
        return ScheduleStore.task(id, name, "east", start, end, 1, skill, priority, "open");
    }

    private static String staffOf(TaskSolver.SolveResult result, String taskId) {
        for (AssignmentItem item : result.assignments) {
            if (taskId.equals(item.taskId)) {
                return item.staffId;
            }
        }
        return "";
    }
}
