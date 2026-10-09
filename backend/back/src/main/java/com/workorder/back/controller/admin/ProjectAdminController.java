package com.workorder.back.controller.admin;

import com.workorder.back.common.Result;
import com.workorder.back.dto.ProjectDetailVO;
import com.workorder.back.dto.ProjectSaveRequest;
import com.workorder.back.dto.ProjectVO;
import com.workorder.back.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "作品-管理")
@RestController
@RequestMapping("/admin/projects")
public class ProjectAdminController {

    private final ProjectService projectService;

    public ProjectAdminController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "管理端作品列表")
    @GetMapping
    public Result<List<ProjectVO>> list() {
        return Result.ok(projectService.listAdmin());
    }

    @Operation(summary = "管理端作品详情")
    @GetMapping("/{id}")
    public Result<ProjectDetailVO> detail(@PathVariable Long id) {
        return Result.ok(projectService.getAdminDetail(id));
    }

    @Operation(summary = "新增作品")
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ProjectSaveRequest request) {
        Long id = projectService.create(request);
        Map<String, Long> data = new HashMap<String, Long>();
        data.put("id", id);
        return Result.ok(data);
    }

    @Operation(summary = "编辑作品")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectSaveRequest request) {
        projectService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "逻辑删除作品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }
}
