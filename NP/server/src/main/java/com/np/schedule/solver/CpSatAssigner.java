package com.np.schedule.solver;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.BlockItem;
import com.np.schedule.model.StaffMember;
import com.np.schedule.model.TaskItem;
import com.np.schedule.time.TimeRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * CP-SAT 分配。
 * 决策变量是「某人是否承担某任务」。
 * 先按优先级从高到低把缺口压到最小，再压低最高工时，最后减少「全班」顶专岗的次数。
 * 每一层求出最优值后收成约束，下一层不能把它变差。
 */
final class CpSatAssigner {

    private static final long MAX_LOAD_MINUTES = 200000L;
    private static boolean nativesLoaded;

    private CpSatAssigner() {
    }

    static TaskSolver.SolveResult solve(List<TaskItem> tasks, List<StaffMember> people,
                                         List<AssignmentItem> assignments, List<BlockItem> blocks,
                                         List<TaskItem> allTasks) {
        return solve(tasks, people, assignments, blocks, allTasks, null);
    }

    static TaskSolver.SolveResult solve(List<TaskItem> tasks, List<StaffMember> people,
                                         List<AssignmentItem> assignments, List<BlockItem> blocks,
                                         List<TaskItem> allTasks, Map<String, String> skillNames) {
        loadNatives();
        Map<String, TaskItem> taskById = TaskSolver.index(allTasks);
        Set<String> openIds = new HashSet<String>();
        for (TaskItem task : tasks) {
            openIds.add(task.id);
        }
        List<AssignmentItem> kept = new ArrayList<AssignmentItem>();
        for (AssignmentItem item : assignments) {
            boolean dropAuto = "auto".equals(item.source) && openIds.contains(item.taskId);
            if (!dropAuto) {
                kept.add(item.copy());
            }
        }
        if (tasks.isEmpty()) {
            return finish(kept, new ArrayList<String>(), people, taskById);
        }

        CpModel model = new CpModel();
        List<Choice> choices = new ArrayList<Choice>();
        Map<String, List<Choice>> byPerson = new HashMap<String, List<Choice>>();
        IntVar[] unfilled = new IntVar[tasks.size()];
        Map<Integer, List<IntVar>> unfilledByPriority = new HashMap<Integer, List<IntVar>>();

        for (int i = 0; i < tasks.size(); i++) {
            TaskItem task = tasks.get(i);
            List<String> manualIds = manualStaff(task.id, kept);
            if (manualIds.size() > task.headcount) {
                return unfinished(tasks, kept, people, taskById);
            }
            List<IntVar> seats = new ArrayList<IntVar>();
            Set<String> seated = new HashSet<String>();
            for (String staffId : manualIds) {
                StaffMember person = findPerson(people, staffId);
                IntVar fixed = model.newBoolVar("m-" + task.id + "-" + staffId);
                model.addEquality(fixed, 1);
                seats.add(fixed);
                seated.add(staffId);
                if (person != null) {
                    addChoice(choices, byPerson, new Choice(person, task, fixed, true, minutes(task)));
                }
            }
            for (StaffMember person : people) {
                if (seated.contains(person.id) || !eligible(person, task, kept, blocks, taskById, openIds, skillNames)) {
                    continue;
                }
                IntVar variable = model.newBoolVar("x-" + task.id + "-" + person.id);
                seats.add(variable);
                addChoice(choices, byPerson, new Choice(person, task, variable, false, minutes(task)));
            }
            int room = task.headcount - manualIds.size();
            IntVar gap = model.newIntVar(0, room, "gap-" + task.id);
            unfilled[i] = gap;
            List<IntVar> priorityGroup = unfilledByPriority.get(task.priority);
            if (priorityGroup == null) {
                priorityGroup = new ArrayList<IntVar>();
                unfilledByPriority.put(task.priority, priorityGroup);
            }
            priorityGroup.add(gap);
            IntVar[] terms = new IntVar[seats.size() + 1];
            for (int s = 0; s < seats.size(); s++) {
                terms[s] = seats.get(s);
            }
            terms[seats.size()] = gap;
            model.addEquality(LinearExpr.sum(terms), task.headcount);
        }

        for (List<Choice> personChoices : byPerson.values()) {
            for (int left = 0; left < personChoices.size(); left++) {
                for (int right = left + 1; right < personChoices.size(); right++) {
                    Choice first = personChoices.get(left);
                    Choice second = personChoices.get(right);
                    if (TimeRules.overlaps(first.task.start, first.task.end, second.task.start, second.task.end)) {
                        model.addLessOrEqual(LinearExpr.sum(new IntVar[]{first.variable, second.variable}), 1);
                    }
                }
            }
        }

        IntVar maxLoad = model.newIntVar(0, MAX_LOAD_MINUTES, "maxLoad");
        for (StaffMember person : people) {
            long outside = outsideLoad(person.id, kept, openIds, taskById);
            List<Choice> personChoices = byPerson.get(person.id);
            if (personChoices == null || personChoices.isEmpty()) {
                model.addGreaterOrEqual(maxLoad, outside);
                continue;
            }
            IntVar[] vars = new IntVar[personChoices.size() + 1];
            long[] coefficients = new long[personChoices.size() + 1];
            for (int i = 0; i < personChoices.size(); i++) {
                vars[i] = personChoices.get(i).variable;
                coefficients[i] = personChoices.get(i).minutes;
            }
            vars[personChoices.size()] = maxLoad;
            coefficients[personChoices.size()] = -1L;
            model.addLessOrEqual(LinearExpr.weightedSum(vars, coefficients), -outside);
        }

        List<IntVar> inexact = new ArrayList<IntVar>();
        for (Choice choice : choices) {
            boolean looseSkill = SkillRules.rank(choice.person.skillList(), choice.task.skill, skillNames) > 0;
            boolean otherDept = "是".equals(choice.task.preferOwn) && !choice.person.deptId.equals(choice.task.deptId);
            if (looseSkill || otherDept) {
                inexact.add(choice.variable);
            }
        }

        CpSolver solver = new CpSolver();
        solver.getParameters().setNumSearchWorkers(1);
        solver.getParameters().setRandomSeed(1);
        solver.getParameters().setMaxTimeInSeconds(5);
        solver.getParameters().setLogSearchProgress(false);

        TreeSet<Integer> priorities = new TreeSet<Integer>(unfilledByPriority.keySet());
        for (int priority : priorities.descendingSet()) {
            if (!minimizeAndLock(model, solver, unfilledByPriority.get(priority))) {
                return unfinished(tasks, kept, people, taskById);
            }
        }
        if (!minimizeAndLock(model, solver, single(maxLoad))) {
            return unfinished(tasks, kept, people, taskById);
        }
        if (!inexact.isEmpty() && !minimizeAndLock(model, solver, inexact)) {
            return unfinished(tasks, kept, people, taskById);
        }

        List<AssignmentItem> next = new ArrayList<AssignmentItem>(kept);
        for (Choice choice : choices) {
            if (choice.manual || solver.value(choice.variable) != 1L) {
                continue;
            }
            next.add(new AssignmentItem("a-" + choice.task.id + "-" + choice.person.id, choice.person.id, choice.task.id, "auto", "draft"));
        }
        List<String> gaps = new ArrayList<String>();
        for (int i = 0; i < tasks.size(); i++) {
            long missing = solver.value(unfilled[i]);
            if (missing <= 0) {
                continue;
            }
            TaskItem task = tasks.get(i);
            gaps.add(task.name + "需要 " + task.headcount + " 人，当前可排 " + (task.headcount - missing) + " 人");
        }
        return finish(next, gaps, people, taskById);
    }

    private static boolean minimizeAndLock(CpModel model, CpSolver solver, List<IntVar> terms) {
        if (terms.isEmpty()) {
            return true;
        }
        LinearExpr objective = LinearExpr.sum(terms.toArray(new IntVar[0]));
        model.minimize(objective);
        CpSolverStatus status = solver.solve(model);
        if (status == CpSolverStatus.MODEL_INVALID) {
            throw new IllegalStateException("CP-SAT 模型无效");
        }
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return false;
        }
        model.addLessOrEqual(objective, Math.round(solver.objectiveValue()));
        return true;
    }

    private static boolean deptAllowed(StaffMember person, TaskItem task) {
        String listed = task.staffDept == null ? "" : task.staffDept.trim();
        if (listed.isEmpty()) return person.deptId.equals(task.deptId);
        for (String token : listed.split("[,，]")) {
            if (person.deptId.equals(token.trim())) return true;
        }
        return false;
    }

    private static boolean eligible(StaffMember person, TaskItem task, List<AssignmentItem> kept,
                                    List<BlockItem> blocks, Map<String, TaskItem> taskById, Set<String> openIds,
                                    Map<String, String> skillNames) {
        if (!deptAllowed(person, task) || !SkillRules.matches(person.skillList(), task.skill, skillNames)) {
            return false;
        }
        long added = minutes(task);
        if (added <= 0 || outsideLoad(person.id, kept, openIds, taskById) + added > person.maxMinutes) {
            return false;
        }
        return TaskSolver.timeConflict(person.id, task.start, task.end, task.id, kept, blocks, taskById) == null;
    }

    private static long outsideLoad(String staffId, List<AssignmentItem> kept, Set<String> openIds,
                                    Map<String, TaskItem> taskById) {
        long sum = 0L;
        for (AssignmentItem item : kept) {
            if (!staffId.equals(item.staffId) || openIds.contains(item.taskId)) {
                continue;
            }
            TaskItem task = taskById.get(item.taskId);
            if (task != null) {
                sum += minutes(task);
            }
        }
        return sum;
    }

    private static List<String> manualStaff(String taskId, List<AssignmentItem> kept) {
        List<String> ids = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (AssignmentItem item : kept) {
            if (taskId.equals(item.taskId) && seen.add(item.staffId)) {
                ids.add(item.staffId);
            }
        }
        return ids;
    }

    private static StaffMember findPerson(List<StaffMember> people, String staffId) {
        for (StaffMember person : people) {
            if (person.id.equals(staffId)) {
                return person;
            }
        }
        return null;
    }

    private static void addChoice(List<Choice> choices, Map<String, List<Choice>> byPerson, Choice choice) {
        choices.add(choice);
        List<Choice> owned = byPerson.get(choice.person.id);
        if (owned == null) {
            owned = new ArrayList<Choice>();
            byPerson.put(choice.person.id, owned);
        }
        owned.add(choice);
    }

    private static List<IntVar> single(IntVar variable) {
        List<IntVar> terms = new ArrayList<IntVar>();
        terms.add(variable);
        return terms;
    }

    private static long minutes(TaskItem task) {
        return (task.end - task.start) / 60000L;
    }

    private static TaskSolver.SolveResult unfinished(List<TaskItem> tasks, List<AssignmentItem> kept,
                                                      List<StaffMember> people, Map<String, TaskItem> taskById) {
        List<String> gaps = new ArrayList<String>();
        for (TaskItem task : tasks) {
            int seated = manualStaff(task.id, kept).size();
            if (seated < task.headcount) {
                gaps.add(task.name + "需要 " + task.headcount + " 人，当前可排 " + seated + " 人");
            }
        }
        return finish(kept, gaps, people, taskById);
    }

    private static TaskSolver.SolveResult finish(List<AssignmentItem> assignments, List<String> gaps,
                                                  List<StaffMember> people, Map<String, TaskItem> taskById) {
        List<Long> loads = new ArrayList<Long>();
        for (StaffMember person : people) {
            loads.add(TaskSolver.loadMinutes(person.id, assignments, taskById));
        }
        return new TaskSolver.SolveResult(assignments, gaps, TaskSolver.varianceHours(loads));
    }

    private static synchronized void loadNatives() {
        if (!nativesLoaded) {
            Loader.loadNativeLibraries();
            nativesLoaded = true;
        }
    }

    private static final class Choice {
        private final StaffMember person;
        private final TaskItem task;
        private final IntVar variable;
        private final boolean manual;
        private final long minutes;

        private Choice(StaffMember person, TaskItem task, IntVar variable, boolean manual, long minutes) {
            this.person = person;
            this.task = task;
            this.variable = variable;
            this.manual = manual;
            this.minutes = minutes;
        }
    }
}
