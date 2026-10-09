package com.workorder.back.controller;

import com.workorder.back.common.Result;
import com.workorder.back.dto.ProjectDetailVO;
import com.workorder.back.dto.ProjectVO;
import com.workorder.back.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "作品-前台")
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "已发布作品列表")
    @GetMapping
    public Result<List<ProjectVO>> list() {
        return Result.ok(projectService.listPublished());
    }

    @Operation(summary = "已发布作品详情")
    @GetMapping("/{id}")
    public Result<ProjectDetailVO> detail(@PathVariable Long id) {
        return Result.ok(projectService.getPublishedDetail(id));
    }
}
