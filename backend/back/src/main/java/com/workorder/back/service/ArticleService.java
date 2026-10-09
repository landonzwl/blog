package com.workorder.back.service;

import com.workorder.back.common.PageResult;
import com.workorder.back.dto.ArticleDetailVO;
import com.workorder.back.dto.ArticleSaveRequest;
import com.workorder.back.dto.ArticleVO;

public interface ArticleService {

    /** 前台：已发布文章分页 */
    PageResult<ArticleVO> pagePublished(long page, long size, String tag);

    /** 前台：已发布详情 + 浏览量自增 */
    ArticleDetailVO getPublishedDetail(Long id);

    /** 管理端：分页（可按状态筛选） */
    PageResult<ArticleVO> pageAdmin(long page, long size, Integer status, String tag);

    /** 管理端：详情（不加浏览量） */
    ArticleDetailVO getAdminDetail(Long id);

    Long create(ArticleSaveRequest request);

    void update(Long id, ArticleSaveRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}
