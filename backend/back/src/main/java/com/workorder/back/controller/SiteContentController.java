package com.workorder.back.controller;

import com.workorder.back.common.Result;
import com.workorder.back.service.SiteContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "站点文案-前台")
@RestController
@RequestMapping("/site")
public class SiteContentController {

    private final SiteContentService siteContentService;

    public SiteContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @Operation(summary = "获取首页文案字典")
    @GetMapping("/content")
    public Result<Map<String, String>> content() {
        return Result.ok(siteContentService.mapPublic());
    }
}
