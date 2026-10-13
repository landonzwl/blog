package com.np.schedule.web;

import com.np.schedule.common.Result;
import com.np.schedule.model.AdminBoard;
import com.np.schedule.model.AssignRequest;
import com.np.schedule.model.CustomRequest;
import com.np.schedule.model.SolveRequest;
import com.np.schedule.model.TaskItem;
import com.np.schedule.model.TemplateRequest;
import com.np.schedule.service.ErpService;
import com.np.schedule.service.ScheduleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/np/admin")
public class AdminController {

    private final ScheduleService scheduleService;
    private final ErpService erpService;

    public AdminController(ScheduleService scheduleService, ErpService erpService) {
        this.scheduleService = scheduleService;
        this.erpService = erpService;
    }

    @GetMapping("/board")
    public Result<AdminBoard> board() {
        return Result.ok(scheduleService.board());
    }

    @PostMapping("/tasks/template")
    public Result<AdminBoard> template(@RequestBody TemplateRequest request) {
        return Result.ok(scheduleService.createFromTemplate(request));
    }

    @PostMapping("/tasks/custom")
    public Result<AdminBoard> custom(@RequestBody CustomRequest request) {
        return Result.ok(scheduleService.createCustom(request));
    }

    @GetMapping("/tasks/{id}")
    public Result<TaskItem> task(@PathVariable String id) {
        TaskItem item = scheduleService.getTask(id);
        if (item == null) {
            return Result.fail(404, "任务不存在");
        }
        return Result.ok(item);
    }

    @PutMapping("/tasks/{id}")
    public Result<AdminBoard> update(@PathVariable String id, @RequestBody CustomRequest request) {
        return Result.ok(scheduleService.updateTask(id, request));
    }

    @DeleteMapping("/tasks/{id}")
    public Result<AdminBoard> delete(@PathVariable String id) {
        return Result.ok(scheduleService.deleteTask(id));
    }

    @PostMapping("/tasks/{id}/start")
    public Result<AdminBoard> start(@PathVariable String id) {
        return Result.ok(scheduleService.startTask(id));
    }

    @PostMapping("/tasks/{id}/complete")
    public Result<AdminBoard> complete(@PathVariable String id) {
        return Result.ok(scheduleService.completeTask(id));
    }

    @PostMapping("/tasks/{id}/publish")
    public Result<AdminBoard> publishOne(@PathVariable String id) {
        return Result.ok(scheduleService.publishTask(id));
    }

    @PostMapping("/tasks/{id}/revert")
    public Result<AdminBoard> revert(@PathVariable String id) {
        return Result.ok(scheduleService.revertTask(id));
    }

    @PostMapping("/assignments/cancel")
    public Result<AdminBoard> unassign(@RequestBody AssignRequest request) {
        return Result.ok(scheduleService.unassign(request));
    }

    @PostMapping("/solve")
    public Result<AdminBoard> solve(@RequestBody(required = false) SolveRequest request) {
        if (request == null) {
            request = new SolveRequest();
        }
        String deptId = request.deptId == null || request.deptId.trim().isEmpty() ? "all" : request.deptId;
        return Result.ok(scheduleService.solve(deptId, request.taskIds, request.staffIds));
    }

    @PostMapping("/publish")
    public Result<AdminBoard> publish(@RequestParam(defaultValue = "all") String deptId) {
        return Result.ok(scheduleService.publish(deptId));
    }

    @PostMapping("/assignments")
    public Result<AdminBoard> assign(@RequestBody AssignRequest request) {
        return Result.ok(scheduleService.assign(request));
    }

    @PostMapping("/posts")
    public Result<Map<String, Object>> createPost(@RequestBody Map<String, String> body) {
        String name = body == null ? "" : body.get("name");
        return Result.ok(erpService.createPost(name));
    }

    @PostMapping("/posts/{id}/delete")
    public Result<Map<String, Object>> deletePost(@PathVariable String id, @RequestBody Map<String, Object> body) {
        boolean confirm = body != null && Boolean.TRUE.equals(body.get("confirm"));
        return Result.ok(erpService.deletePost(id, confirm));
    }

    @PostMapping("/skills")
    public Result<Map<String, Object>> createSkill(@RequestBody Map<String, String> body) {
        String name = body == null ? "" : body.get("name");
        return Result.ok(scheduleService.createSkill(name));
    }

    @PostMapping("/skills/{id}/delete")
    public Result<Map<String, Object>> deleteSkill(@PathVariable String id, @RequestBody Map<String, Object> body) {
        boolean confirm = body != null && Boolean.TRUE.equals(body.get("confirm"));
        java.util.List<String> callers = erpService.skillCallers(id);
        Map<String, Object> result = scheduleService.deleteSkill(id, confirm, callers);
        if (Boolean.TRUE.equals(result.get("deleted"))) {
            Object replacement = result.get("replacement");
            erpService.clearSkill(id, replacement == null ? "" : String.valueOf(replacement));
        }
        return Result.ok(result);
    }
}
