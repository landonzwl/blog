package com.workorder.back.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workorder.back.common.BusinessException;
import com.workorder.back.common.PageResult;
import com.workorder.back.common.ResultCode;
import com.workorder.back.dto.ArticleDetailVO;
import com.workorder.back.dto.ArticleSaveRequest;
import com.workorder.back.dto.ArticleVO;
import com.workorder.back.entity.Article;
import com.workorder.back.mapper.ArticleMapper;
import com.workorder.back.service.ArticleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;

    public ArticleServiceImpl(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Override
    public PageResult<ArticleVO> pagePublished(long page, long size, String tag) {
        Page<Article> p = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, Article.STATUS_PUBLISHED)
                .orderByDesc(Article::getPublishedAt)
                .orderByDesc(Article::getId);
        if (StringUtils.hasText(tag)) {
            qw.like(Article::getTags, tag.trim());
        }
        articleMapper.selectPage(p, qw);
        return PageResult.of(toVOList(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleDetailVO getPublishedDetail(Long id) {
        Article article = requireArticle(id);
        if (!Integer.valueOf(Article.STATUS_PUBLISHED).equals(article.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        articleMapper.incrViewCount(id);
        article.setViewCount(article.getViewCount() == null ? 1 : article.getViewCount() + 1);
        return toDetailVO(article);
    }

    @Override
    public PageResult<ArticleVO> pageAdmin(long page, long size, Integer status, String tag) {
        Page<Article> p = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<Article>()
                .eq(status != null, Article::getStatus, status)
                .orderByDesc(Article::getUpdateTime)
                .orderByDesc(Article::getId);
        if (StringUtils.hasText(tag)) {
            qw.like(Article::getTags, tag.trim());
        }
        articleMapper.selectPage(p, qw);
        return PageResult.of(toVOList(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public ArticleDetailVO getAdminDetail(Long id) {
        return toDetailVO(requireArticle(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ArticleSaveRequest request) {
        Article article = new Article();
        applyRequest(article, request);
        article.setViewCount(0);
        applyPublishTime(article, null, request.getStatus());
        articleMapper.insert(article);
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ArticleSaveRequest request) {
        Article existing = requireArticle(id);
        LocalDateTime previousPublishedAt = existing.getPublishedAt();
        applyRequest(existing, request);
        applyPublishTime(existing, previousPublishedAt, request.getStatus());
        articleMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireArticle(id);
        articleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Article existing = requireArticle(id);
        existing.setStatus(status);
        applyPublishTime(existing, existing.getPublishedAt(), status);
        articleMapper.updateById(existing);
    }

    private Article requireArticle(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return article;
    }

    private void applyRequest(Article article, ArticleSaveRequest request) {
        article.setTitle(request.getTitle().trim());
        article.setSummary(trimToNull(request.getSummary()));
        article.setContent(request.getContent());
        article.setCoverUrl(trimToNull(request.getCoverUrl()));
        article.setTags(normalizeTags(request.getTags()));
        article.setStatus(request.getStatus());
    }

    /**
     * 首次发布时写入 publishedAt；再次改回草稿保留首次发布时间
     */
    private void applyPublishTime(Article article, LocalDateTime currentPublishedAt, Integer status) {
        if (Integer.valueOf(Article.STATUS_PUBLISHED).equals(status) && currentPublishedAt == null) {
            article.setPublishedAt(LocalDateTime.now());
        } else if (currentPublishedAt != null) {
            article.setPublishedAt(currentPublishedAt);
        }
    }

    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        String[] parts = tags.split(",");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String t = part.trim();
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private long normalizePage(long page) {
        return page < 1 ? 1 : page;
    }

    private long normalizeSize(long size) {
        if (size < 1) {
            return 10;
        }
        return size > 50 ? 50 : size;
    }

    private List<ArticleVO> toVOList(List<Article> records) {
        List<ArticleVO> list = new ArrayList<ArticleVO>();
        if (records == null) {
            return list;
        }
        for (Article article : records) {
            list.add(toVO(article));
        }
        return list;
    }

    private ArticleVO toVO(Article article) {
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setTags(article.getTags());
        vo.setStatus(article.getStatus());
        vo.setViewCount(article.getViewCount());
        vo.setPublishedAt(article.getPublishedAt());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    private ArticleDetailVO toDetailVO(Article article) {
        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setTags(article.getTags());
        vo.setStatus(article.getStatus());
        vo.setViewCount(article.getViewCount());
        vo.setPublishedAt(article.getPublishedAt());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }
}
