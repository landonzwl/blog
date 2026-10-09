package com.workorder.back.controller.admin;

import com.workorder.back.common.PageResult;
import com.workorder.back.common.Result;
import com.workorder.back.dto.ArticleDetailVO;
import com.workorder.back.dto.ArticleSaveRequest;
import com.workorder.back.dto.ArticleStatusRequest;
import com.workorder.back.dto.ArticleVO;
import com.workorder.back.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端文章接口（P3 再加 JWT 鉴权）
 */
@Tag(name = "文章-管理")
@RestController
@RequestMapping("/admin/articles")
public class ArticleAdminController {

    private final ArticleService articleService;

    public ArticleAdminController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "管理端文章分页")
    @GetMapping
    public Result<PageResult<ArticleVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String tag) {
        return Result.ok(articleService.pageAdmin(page, size, status, tag));
    }

    @Operation(summary = "管理端文章详情")
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable Long id) {
        return Result.ok(articleService.getAdminDetail(id));
    }

    @Operation(summary = "新增文章")
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ArticleSaveRequest request) {
        Long id = articleService.create(request);
        Map<String, Long> data = new HashMap<String, Long>();
        data.put("id", id);
        return Result.ok(data);
    }

    @Operation(summary = "编辑文章")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ArticleSaveRequest request) {
        articleService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "逻辑删除文章")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "变更文章状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ArticleStatusRequest request) {
        articleService.updateStatus(id, request.getStatus());
        return Result.ok();
    }
}
