package com.np.schedule.solver;

import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.BlockItem;
import com.np.schedule.model.StaffMember;
import com.np.schedule.model.TaskItem;
import com.np.schedule.time.TimeRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务分配入口。求解使用 CP-SAT，手动指派保留，范围内的自动分配重算。
 */
public final class TaskSolver {

    private TaskSolver() {
    }

    public static final class SolveResult {
        public final List<AssignmentItem> assignments;
        public final List<String> gaps;
        public final double variance;

        public SolveResult(List<AssignmentItem> assignments, List<String> gaps, double variance) {
            this.assignments = assignments;
            this.gaps = gaps;
            this.variance = variance;
        }
    }

    public static SolveResult solve(List<TaskItem> tasks, List<StaffMember> people,
                                     List<AssignmentItem> assignments, List<BlockItem> blocks,
                                     List<TaskItem> allTasks) {
        return solve(tasks, people, assignments, blocks, allTasks, null);
    }

    public static SolveResult solve(List<TaskItem> tasks, List<StaffMember> people,
                                     List<AssignmentItem> assignments, List<BlockItem> blocks,
                                     List<TaskItem> allTasks, Map<String, String> skillNames) {
        return CpSatAssigner.solve(tasks, people, assignments, blocks, allTasks, skillNames);
    }

    public static String timeConflict(String staffId, long start, long end, String ignoreTaskId,
                                      List<AssignmentItem> assignments, List<BlockItem> blocks,
                                      Map<String, TaskItem> taskById) {
        return timeConflict(staffId, start, end, ignoreTaskId, assignments, blocks, taskById, null);
    }

    public static String timeConflict(String staffId, long start, long end, String ignoreTaskId,
                                      List<AssignmentItem> assignments, List<BlockItem> blocks,
                                      Map<String, TaskItem> taskById, String ignoreBlockId) {
        for (AssignmentItem row : assignments) {
            if (!staffId.equals(row.staffId) || row.taskId.equals(ignoreTaskId)) {
                continue;
            }
            TaskItem other = taskById.get(row.taskId);
            if (other != null && TimeRules.overlaps(start, end, other.start, other.end)) {
                return other.name;
            }
        }
        for (BlockItem block : blocks) {
            if (ignoreBlockId != null && ignoreBlockId.equals(block.id)) {
                continue;
            }
            if (staffId.equals(block.staffId) && TimeRules.overlaps(start, end, block.start, block.end)) {
                return block.reason == null || block.reason.trim().isEmpty() ? "不可工作时段" : block.reason;
            }
        }
        return null;
    }

    public static long loadMinutes(String staffId, List<AssignmentItem> assignments, Map<String, TaskItem> taskById) {
        long sum = 0L;
        for (AssignmentItem item : assignments) {
            if (!staffId.equals(item.staffId)) {
                continue;
            }
            TaskItem task = taskById.get(item.taskId);
            if (task == null) {
                continue;
            }
            sum += (task.end - task.start) / 60000L;
        }
        return sum;
    }

    public static double varianceHours(Collection<Long> minuteValues) {
        if (minuteValues.isEmpty()) {
            return 0D;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Long value : minuteValues) {
            sum = sum.add(BigDecimal.valueOf(value));
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(minuteValues.size()), 8, RoundingMode.HALF_UP);
        BigDecimal square = BigDecimal.ZERO;
        for (Long value : minuteValues) {
            BigDecimal diff = BigDecimal.valueOf(value).subtract(mean);
            square = square.add(diff.multiply(diff));
        }
        BigDecimal varianceMinutes = square.divide(BigDecimal.valueOf(minuteValues.size()), 8, RoundingMode.HALF_UP);
        return varianceMinutes.divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP).doubleValue();
    }

    public static Map<String, TaskItem> index(List<TaskItem> tasks) {
        Map<String, TaskItem> map = new HashMap<String, TaskItem>();
        for (TaskItem task : tasks) {
            map.put(task.id, task);
        }
        return map;
    }
}
