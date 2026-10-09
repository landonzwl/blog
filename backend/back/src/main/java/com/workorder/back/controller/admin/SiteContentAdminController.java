package com.workorder.back.controller.admin;

import com.workorder.back.common.Result;
import com.workorder.back.dto.SiteContentItemVO;
import com.workorder.back.dto.SiteContentUpdateItem;
import com.workorder.back.service.SiteContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "站点文案-管理")
@RestController
@RequestMapping("/admin/site/content")
public class SiteContentAdminController {

    private final SiteContentService siteContentService;

    public SiteContentAdminController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @Operation(summary = "文案列表（含标签分组）")
    @GetMapping
    public Result<List<SiteContentItemVO>> list() {
        return Result.ok(siteContentService.listAdmin());
    }

    @Operation(summary = "批量保存文案")
    @PutMapping
    public Result<Void> batchUpdate(@RequestBody List<SiteContentUpdateItem> items) {
        siteContentService.batchUpdate(items);
        return Result.ok();
    }
}
