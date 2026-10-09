package com.workorder.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workorder.back.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 文章 Mapper
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /** 浏览量原子自增 */
    @Update("UPDATE t_article SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrViewCount(Long id);
}
