package com.workorder.back.controller;

import com.workorder.back.common.PageResult;
import com.workorder.back.common.Result;
import com.workorder.back.dto.ArticleDetailVO;
import com.workorder.back.dto.ArticleVO;
import com.workorder.back.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台文章接口（公开）
 */
@Tag(name = "文章-前台")
@RestController
@RequestMapping("/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "已发布文章分页列表")
    @GetMapping
    public Result<PageResult<ArticleVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String tag) {
        return Result.ok(articleService.pagePublished(page, size, tag));
    }

    @Operation(summary = "已发布文章详情（浏览量 +1）")
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable Long id) {
        return Result.ok(articleService.getPublishedDetail(id));
    }
}
