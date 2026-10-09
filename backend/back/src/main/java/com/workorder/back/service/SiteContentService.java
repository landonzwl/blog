package com.workorder.back.service;

import com.workorder.back.dto.SiteContentItemVO;
import com.workorder.back.dto.SiteContentUpdateItem;

import java.util.List;
import java.util.Map;

public interface SiteContentService {

    /** 前台：key -> value */
    Map<String, String> mapPublic();

    /** 管理端：完整字段列表 */
    List<SiteContentItemVO> listAdmin();

    /** 批量按 key 更新 value */
    void batchUpdate(List<SiteContentUpdateItem> items);
}
