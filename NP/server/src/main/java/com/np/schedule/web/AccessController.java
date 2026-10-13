package com.np.schedule.web;

import com.np.schedule.common.Result;
import com.np.schedule.model.BlockRequest;
import com.np.schedule.model.StaffHome;
import com.np.schedule.model.VisitorBoard;
import com.np.schedule.service.ScheduleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/np")
public class AccessController {

    private final ScheduleService scheduleService;

    public AccessController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("ok");
    }

    @GetMapping("/staff/home")
    public Result<StaffHome> staffHome() {
        return Result.ok(scheduleService.staffHome());
    }

    @PostMapping("/staff/blocks")
    public Result<StaffHome> addBlock(@RequestBody BlockRequest request) {
        return Result.ok(scheduleService.addBlock(request));
    }

    @PostMapping("/staff/tasks/{id}/start")
    public Result<StaffHome> startTask(@PathVariable String id) {
        return Result.ok(scheduleService.startOwnTask(id));
    }

    @PostMapping("/staff/tasks/{id}/complete")
    public Result<StaffHome> completeTask(@PathVariable String id) {
        return Result.ok(scheduleService.completeOwnTask(id));
    }

    @PostMapping("/staff/tasks/{id}/revert")
    public Result<StaffHome> revertTask(@PathVariable String id) {
        return Result.ok(scheduleService.revertOwnTask(id));
    }

    @PutMapping("/staff/blocks/{id}")
    public Result<StaffHome> updateBlock(@PathVariable String id, @RequestBody BlockRequest request) {
        return Result.ok(scheduleService.updateBlock(id, request));
    }

    @DeleteMapping("/staff/blocks/{id}")
    public Result<StaffHome> deleteBlock(@PathVariable String id) {
        return Result.ok(scheduleService.deleteBlock(id));
    }

    @GetMapping("/visitor/tasks")
    public Result<VisitorBoard> visitorTasks() {
        return Result.ok(scheduleService.visitorBoard());
    }
}
